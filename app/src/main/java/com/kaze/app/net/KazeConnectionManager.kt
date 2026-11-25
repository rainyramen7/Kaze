package com.kaze.app.net

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.kaze.app.crypto.FileSafety
import com.kaze.app.crypto.KazeClientHandshake
import com.kaze.app.crypto.KazeCrypto
import com.kaze.app.crypto.KazeEnvelope
import com.kaze.app.crypto.KazeFrameType
import com.kaze.app.crypto.KazeIdentity
import com.kaze.app.crypto.KazePairingPayload
import com.kaze.app.crypto.KazeSessionCipher
import com.kaze.app.crypto.KazeWireCodec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.UUID

enum class ConnectionStatus {
    UNPAIRED,
    DISCOVERING,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class BreezeDirection {
    FROM_MAC,
    TO_MAC
}

data class SessionBreeze(
    val id: String,
    val direction: BreezeDirection,
    val badge: String,
    val title: String,
    val meta: String,
    val jpMeta: String,
    val textPayload: String? = null,
    val savedUri: Uri? = null,
    val quarantinedFile: File? = null,
    val fileSizeBytes: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)

data class IncomingToastEvent(
    val id: String,
    val badge: String,
    val text: String,
    val macName: String,
    val latencyMs: Int?
)

data class KazeUiState(
    val status: ConnectionStatus = ConnectionStatus.UNPAIRED,
    val pairedMac: PairedMac? = null,
    val latencyMs: Int? = null,
    val transferProgress: Float? = null,
    val isBursting: Boolean = false,
    val statusMessage: String? = null,
    val statusIsError: Boolean = false,
    val showJapaneseLabels: Boolean = true,
    val discoveredMacs: List<DiscoveredMac> = emptyList(),
    val sessionHistory: List<SessionBreeze> = emptyList(),
    val deviceShortFingerprint: String = ""
)

private class IncomingFileBuffer(
    val id: String,
    val sanitizedName: String,
    val mimeType: String,
    val expectedSize: Long,
    val tempFile: File
) {
    val output = FileOutputStream(tempFile)
    val digest: MessageDigest = MessageDigest.getInstance("SHA-256")
    var bytesReceived: Long = 0L

    fun write(chunk: ByteArray) {
        digest.update(chunk)
        output.write(chunk)
        bytesReceived += chunk.size
    }

    fun finishSha256Hex(): String {
        output.flush()
        output.close()
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun abort() {
        runCatching { output.close() }
        runCatching { tempFile.delete() }
    }
}

/**
 * Central encrypted LAN connection manager between Kaze (Android) and KazeMac.
 */
class KazeConnectionManager(private val appContext: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = TrustedMacStore(appContext)
    private val nsd = KazeNsdDiscovery(appContext)
    val identity: KazeIdentity = KazeIdentity.loadOrCreateAndroidKeystore()
    private val deviceName: String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"

    private val _uiState = MutableStateFlow(
        KazeUiState(
            status = if (store.loadPairedMac() != null) ConnectionStatus.DISCOVERING else ConnectionStatus.UNPAIRED,
            pairedMac = store.loadPairedMac(),
            showJapaneseLabels = store.showJapaneseLabels(),
            deviceShortFingerprint = identity.shortFingerprint
        )
    )
    val uiState: StateFlow<KazeUiState> = _uiState.asStateFlow()

    private val _incomingToasts = MutableSharedFlow<IncomingToastEvent>(extraBufferCapacity = 8)
    val incomingToasts: SharedFlow<IncomingToastEvent> = _incomingToasts.asSharedFlow()

    private val writeLock = Any()
    @Volatile private var activeSocket: Socket? = null
    @Volatile private var activeOut: DataOutputStream? = null
    @Volatile private var activeCipher: KazeSessionCipher? = null
    @Volatile private var lastPingSentAtMs: Long = 0L

    private var connectionJob: Job? = null
    private var pingJob: Job? = null
    private val incomingFiles = mutableMapOf<String, IncomingFileBuffer>()

    init {
        nsd.start()
        scope.launch {
            nsd.discovered.collect { macs ->
                _uiState.update { it.copy(discoveredMacs = macs) }
                // Update paired Mac's LAN IP/port automatically if DHCP changed
                val paired = _uiState.value.pairedMac ?: return@collect
                val matched = macs.firstOrNull { discovered ->
                    discovered.fingerprintPrefix != null &&
                        paired.fingerprintHex.startsWith(discovered.fingerprintPrefix, ignoreCase = true)
                }
                if (matched != null && (matched.host != paired.host || matched.port != paired.port)) {
                    val updated = paired.copy(host = matched.host, port = matched.port)
                    store.savePairedMac(updated)
                    _uiState.update { it.copy(pairedMac = updated) }
                }
                if (_uiState.value.status == ConnectionStatus.DISCOVERING && activeCipher == null) {
                    connectToPairedMac()
                }
            }
        }
    }

    fun startDiscoveryAndConnect() {
        nsd.start()
        if (_uiState.value.pairedMac != null && activeCipher == null) {
            connectToPairedMac()
        }
    }

    fun setShowJapaneseLabels(enabled: Boolean) {
        store.setShowJapaneseLabels(enabled)
        _uiState.update { it.copy(showJapaneseLabels = enabled) }
    }

    fun clearSessionHistory() {
        _uiState.value.sessionHistory.forEach { it.quarantinedFile?.delete() }
        _uiState.update { it.copy(sessionHistory = emptyList()) }
    }

    fun pairWithQrUri(uri: String) {
        val payload = KazePairingPayload.parse(uri)
        if (payload == null) {
            postBanner("Invalid Kaze QR code", isError = true)
            return
        }
        connectInternal(
            host = payload.host,
            port = payload.port,
            expectedFingerprintHex = payload.fingerprintHex,
            pairingSecret = payload.tokenHex,
            fallbackName = payload.deviceName
        )
    }

    fun pairWithPin(host: String, port: Int, pin: String, deviceNameHint: String, expectedFpPrefix: String? = null) {
        val cleanPin = pin.filter { it.isDigit() }
        if (cleanPin.length != 6) {
            postBanner("Enter the 6-digit pairing code from KazeMac", isError = true)
            return
        }
        connectInternal(
            host = host.trim(),
            port = port,
            expectedFingerprintHex = null,
            pairingSecret = cleanPin,
            fallbackName = deviceNameHint,
            expectedPrefix = expectedFpPrefix
        )
    }

    fun connectToPairedMac() {
        val paired = _uiState.value.pairedMac ?: return
        if (connectionJob?.isActive == true && activeCipher != null) return
        connectInternal(
            host = paired.host,
            port = paired.port,
            expectedFingerprintHex = paired.fingerprintHex,
            pairingSecret = null,
            fallbackName = paired.deviceName
        )
    }

    fun unpairMac() {
        scope.launch {
            runCatching { sendEnvelope(KazeEnvelope(type = "unpair")) }
            disconnectSocket()
            store.clearPairedMac()
            _uiState.update {
                it.copy(
                    status = ConnectionStatus.UNPAIRED,
                    pairedMac = null,
                    latencyMs = null,
                    transferProgress = null
                )
            }
            postBanner("Unpaired and wiped pinned Mac key", isError = false)
        }
    }

    // MARK: - Manual Outbound Push

    fun pushClipboardText(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) {
            postBanner("Clipboard is empty", isError = true)
            return
        }
        if (activeCipher == null) {
            postBanner("Not connected to Mac on LAN", isError = true)
            return
        }
        scope.launch {
            runCatching {
                val env = KazeEnvelope(type = "clipboard", text = clean)
                sendEnvelope(env)
                val badge = if (clean.startsWith("http://") || clean.startsWith("https://")) "URL" else "CLIP"
                prependHistory(
                    SessionBreeze(
                        id = env.id,
                        direction = BreezeDirection.TO_MAC,
                        badge = badge,
                        title = clean,
                        meta = "Sent to ${_uiState.value.pairedMac?.deviceName ?: "Mac"} · Manual",
                        jpMeta = "送信済 · 今",
                        textPayload = clean
                    )
                )
                triggerBurst()
                postBanner("Pushed clipboard to Mac", isError = false)
            }.onFailure { err ->
                postBanner("Push failed: ${err.message}", isError = true)
            }
        }
    }

    fun sendFileUri(uri: Uri, onComplete: ((Boolean, String) -> Unit)? = null) {
        if (activeCipher == null) {
            val msg = "Connect to your paired Mac on local Wi-Fi first"
            postBanner(msg, isError = true)
            onComplete?.invoke(false, msg)
            return
        }
        scope.launch {
            try {
                val resolver = appContext.contentResolver
                val (rawName, fileSize) = queryUriMetadata(uri)
                val safeName = FileSafety.sanitizeFileName(rawName)
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val transferId = UUID.randomUUID().toString()

                _uiState.update { it.copy(transferProgress = 0.03f) }
                sendEnvelope(
                    KazeEnvelope(
                        type = "file_start",
                        id = transferId,
                        fileName = safeName,
                        mimeType = mime,
                        fileSize = fileSize
                    )
                )

                val digest = MessageDigest.getInstance("SHA-256")
                var sent = 0L
                resolver.openInputStream(uri)?.use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        val chunk = if (read == buf.size) buf else buf.copyOf(read)
                        digest.update(chunk)
                        sendRawEncryptedFrame(KazeWireCodec.encodeFileChunkFrame(transferId, chunk))
                        sent += read
                        if (fileSize > 0) {
                            _uiState.update { it.copy(transferProgress = (sent.toFloat() / fileSize.toFloat()).coerceIn(0.05f, 0.99f)) }
                        }
                    }
                } ?: error("Cannot read selected file")

                val shaHex = digest.digest().joinToString("") { "%02x".format(it) }
                sendEnvelope(
                    KazeEnvelope(
                        type = "file_end",
                        id = transferId,
                        fileName = safeName,
                        fileSize = sent,
                        sha256Hex = shaHex
                    )
                )

                val ext = safeName.substringAfterLast('.', "").uppercase().take(4).ifEmpty { "FILE" }
                prependHistory(
                    SessionBreeze(
                        id = transferId,
                        direction = BreezeDirection.TO_MAC,
                        badge = ext,
                        title = safeName,
                        meta = "Sent to Mac · ${formatBytes(sent)} · ~/Downloads/Kaze",
                        jpMeta = "送信済 · 今",
                        fileSizeBytes = sent
                    )
                )
                _uiState.update { it.copy(transferProgress = null) }
                triggerBurst()
                postBanner("Delivered $safeName", isError = false)
                onComplete?.invoke(true, "Delivered $safeName")
            } catch (e: Throwable) {
                _uiState.update { it.copy(transferProgress = null) }
                val msg = "Drop failed: ${e.message}"
                postBanner(msg, isError = true)
                onComplete?.invoke(false, msg)
            }
        }
    }

    fun approveQuarantinedFile(itemId: String) {
        val item = _uiState.value.sessionHistory.firstOrNull { it.id == itemId } ?: return
        val temp = item.quarantinedFile ?: return
        scope.launch {
            val savedUri = saveFileToDownloadsKaze(temp, item.title, "application/octet-stream")
            temp.delete()
            _uiState.update { state ->
                state.copy(
                    sessionHistory = state.sessionHistory.map {
                        if (it.id == itemId) {
                            it.copy(
                                badge = item.title.substringAfterLast('.', "FILE").uppercase().take(4),
                                meta = "Saved to Downloads/Kaze · ${formatBytes(item.fileSizeBytes)}",
                                savedUri = savedUri,
                                quarantinedFile = null
                            )
                        } else it
                    }
                )
            }
            postBanner("Saved ${item.title} to Downloads/Kaze", isError = false)
        }
    }

    fun discardQuarantinedFile(itemId: String) {
        val item = _uiState.value.sessionHistory.firstOrNull { it.id == itemId } ?: return
        item.quarantinedFile?.delete()
        _uiState.update { state ->
            state.copy(sessionHistory = state.sessionHistory.filterNot { it.id == itemId })
        }
        postBanner("Discarded quarantined file", isError = false)
    }

    // MARK: - Internal Socket & Handshake Loop

    private fun connectInternal(
        host: String,
        port: Int,
        expectedFingerprintHex: String?,
        pairingSecret: String?,
        fallbackName: String,
        expectedPrefix: String? = null
    ) {
        connectionJob?.cancel()
        disconnectSocket()

        connectionJob = scope.launch {
            _uiState.update { it.copy(status = ConnectionStatus.CONNECTING) }
            try {
                val socket = Socket()
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(host, port), 5_000)

                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

                val hs = KazeClientHandshake(
                    identity = identity,
                    deviceName = deviceName,
                    expectedServerFingerprintHex = expectedFingerprintHex,
                    pairingSecret = pairingSecret
                )
                KazeWireCodec.writeLengthPrefixed(output, hs.createClientHelloBytes())
                val serverHelloBytes = KazeWireCodec.readLengthPrefixed(input)
                val (clientAuthBytes, outcome) = hs.processServerHello(serverHelloBytes)

                if (expectedPrefix != null &&
                    !outcome.peerFingerprintHex.startsWith(expectedPrefix, ignoreCase = true)
                ) {
                    socket.close()
                    throw SecurityException("Mac fingerprint did not match discovered device")
                }

                KazeWireCodec.writeLengthPrefixed(output, clientAuthBytes)

                activeSocket = socket
                activeOut = output
                activeCipher = outcome.cipher

                val pairedMac = PairedMac(
                    deviceName = outcome.peerName.ifBlank { fallbackName },
                    host = host,
                    port = port,
                    fingerprintHex = outcome.peerFingerprintHex
                )
                store.savePairedMac(pairedMac)
                _uiState.update {
                    it.copy(
                        status = ConnectionStatus.CONNECTED,
                        pairedMac = pairedMac
                    )
                }
                if (outcome.wasNewPairing) {
                    postBanner("Paired & pinned ${pairedMac.deviceName}", isError = false)
                }
                triggerBurst()
                startPingLoop()

                // Encrypted receive loop
                while (isActive && !socket.isClosed) {
                    val encrypted = KazeWireCodec.readLengthPrefixed(input)
                    val plaintext = outcome.cipher.open(encrypted)
                    handleDecryptedFrame(plaintext)
                }
            } catch (e: Throwable) {
                disconnectSocket()
                val hasPaired = _uiState.value.pairedMac != null
                _uiState.update {
                    it.copy(
                        status = if (hasPaired) ConnectionStatus.DISCOVERING else ConnectionStatus.UNPAIRED,
                        latencyMs = null,
                        transferProgress = null
                    )
                }
                if (pairingSecret != null) {
                    postBanner("Pairing failed: ${e.message}", isError = true)
                }
            }
        }
    }

    private fun startPingLoop() {
        pingJob?.cancel()
        pingJob = scope.launch {
            while (isActive && activeCipher != null) {
                lastPingSentAtMs = System.currentTimeMillis()
                runCatching { sendEnvelope(KazeEnvelope(type = "ping")) }
                delay(20_000)
            }
        }
    }

    private fun handleDecryptedFrame(payload: ByteArray) {
        if (payload.isEmpty()) return
        when (payload[0]) {
            KazeFrameType.JSON_CONTROL.byteValue -> {
                val jsonBytes = payload.copyOfRange(1, payload.size)
                val env = runCatching { KazeEnvelope.fromJsonBytes(jsonBytes) }.getOrNull() ?: return
                handleEnvelope(env)
            }
            KazeFrameType.FILE_CHUNK.byteValue -> {
                val frame = KazeWireCodec.decodeFileChunkFrame(payload) ?: return
                val buf = incomingFiles[frame.transferId] ?: return
                runCatching {
                    buf.write(frame.chunk)
                    if (buf.expectedSize > 0) {
                        _uiState.update {
                            it.copy(transferProgress = (buf.bytesReceived.toFloat() / buf.expectedSize.toFloat()).coerceIn(0.05f, 0.99f))
                        }
                    }
                }.onFailure {
                    buf.abort()
                    incomingFiles.remove(frame.transferId)
                    _uiState.update { it.copy(transferProgress = null) }
                }
            }
        }
    }

    private fun handleEnvelope(env: KazeEnvelope) {
        when (env.type) {
            "ping" -> runCatching { sendEnvelope(KazeEnvelope(type = "pong", id = env.id)) }
            "pong" -> {
                val delta = (System.currentTimeMillis() - lastPingSentAtMs).toInt().coerceAtLeast(1)
                _uiState.update { it.copy(latencyMs = delta) }
            }
            "clipboard" -> {
                val text = env.text?.takeIf { it.isNotEmpty() } ?: return
                val badge = if (text.startsWith("http://") || text.startsWith("https://")) "URL" else "CLIP"
                val macName = _uiState.value.pairedMac?.deviceName ?: "MacBook"
                prependHistory(
                    SessionBreeze(
                        id = env.id,
                        direction = BreezeDirection.FROM_MAC,
                        badge = badge,
                        title = text,
                        meta = "$macName · ${_uiState.value.latencyMs?.let { "${it}ms · " } ?: ""}P-256",
                        jpMeta = "受信 · 今",
                        textPayload = text
                    )
                )
                runCatching { sendEnvelope(KazeEnvelope(type = "ack", id = env.id)) }
                triggerBurst()
                _incomingToasts.tryEmit(
                    IncomingToastEvent(
                        id = env.id,
                        badge = badge,
                        text = text,
                        macName = macName,
                        latencyMs = _uiState.value.latencyMs
                    )
                )
            }
            "file_start" -> {
                val safeName = FileSafety.sanitizeFileName(env.fileName ?: "kaze_file")
                val cacheDir = File(appContext.cacheDir, "kaze_incoming").apply { mkdirs() }
                val temp = File(cacheDir, "${UUID.randomUUID()}_$safeName")
                incomingFiles[env.id] = IncomingFileBuffer(
                    id = env.id,
                    sanitizedName = safeName,
                    mimeType = env.mimeType ?: "application/octet-stream",
                    expectedSize = env.fileSize ?: 0L,
                    tempFile = temp
                )
                _uiState.update { it.copy(transferProgress = 0.03f) }
            }
            "file_end" -> {
                val buf = incomingFiles.remove(env.id) ?: return
                _uiState.update { it.copy(transferProgress = null) }
                val actualSha = buf.finishSha256Hex()
                if (env.sha256Hex != null && !env.sha256Hex.equals(actualSha, ignoreCase = true)) {
                    buf.abort()
                    postBanner("Rejected file: SHA-256 mismatch", isError = true)
                    return
                }
                val needsConfirm = FileSafety.requiresConfirmation(buf.sanitizedName, buf.bytesReceived)
                if (needsConfirm) {
                    prependHistory(
                        SessionBreeze(
                            id = env.id,
                            direction = BreezeDirection.FROM_MAC,
                            badge = "HOLD",
                            title = buf.sanitizedName,
                            meta = "Quarantined · ${formatBytes(buf.bytesReceived)} · Tap Save to keep",
                            jpMeta = "確認待ち",
                            quarantinedFile = buf.tempFile,
                            fileSizeBytes = buf.bytesReceived
                        )
                    )
                    postBanner("Review ${buf.sanitizedName} before saving", isError = true)
                } else {
                    val savedUri = saveFileToDownloadsKaze(buf.tempFile, buf.sanitizedName, buf.mimeType)
                    buf.tempFile.delete()
                    val ext = buf.sanitizedName.substringAfterLast('.', "FILE").uppercase().take(4)
                    prependHistory(
                        SessionBreeze(
                            id = env.id,
                            direction = BreezeDirection.FROM_MAC,
                            badge = ext,
                            title = buf.sanitizedName,
                            meta = "Saved to Downloads/Kaze · ${formatBytes(buf.bytesReceived)}",
                            jpMeta = "受信 · 今",
                            savedUri = savedUri,
                            fileSizeBytes = buf.bytesReceived
                        )
                    )
                    runCatching { sendEnvelope(KazeEnvelope(type = "ack", id = env.id)) }
                    triggerBurst()
                    postBanner("Saved ${buf.sanitizedName}", isError = false)
                }
            }
            "unpair" -> {
                disconnectSocket()
                store.clearPairedMac()
                _uiState.update {
                    it.copy(status = ConnectionStatus.UNPAIRED, pairedMac = null, latencyMs = null)
                }
                postBanner("Mac unpaired this phone", isError = true)
            }
        }
    }

    private fun sendEnvelope(envelope: KazeEnvelope) {
        sendRawEncryptedFrame(KazeWireCodec.encodeControlFrame(envelope))
    }

    private fun sendRawEncryptedFrame(rawFrame: ByteArray) {
        synchronized(writeLock) {
            val out = activeOut ?: error("Disconnected")
            val cipher = activeCipher ?: error("No session cipher")
            val sealed = cipher.seal(rawFrame)
            KazeWireCodec.writeLengthPrefixed(out, sealed)
        }
    }

    private fun disconnectSocket() {
        pingJob?.cancel()
        pingJob = null
        incomingFiles.values.forEach { it.abort() }
        incomingFiles.clear()
        runCatching { activeSocket?.close() }
        activeSocket = null
        activeOut = null
        activeCipher = null
    }

    private fun saveFileToDownloadsKaze(source: File, sanitizedName: String, mimeType: String): Uri? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, sanitizedName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Kaze")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = appContext.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return null
                resolver.openOutputStream(uri)?.use { out ->
                    source.inputStream().use { input -> input.copyTo(out) }
                }
                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } else {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Kaze"
                ).apply { mkdirs() }
                val dest = File(dir, sanitizedName)
                source.copyTo(dest, overwrite = true)
                Uri.fromFile(dest)
            }
        }.getOrNull()
    }

    private suspend fun queryUriMetadata(uri: Uri): Pair<String, Long> = withContext(Dispatchers.IO) {
        var name = "kaze_drop"
        var size = -1L
        appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = cursor.getString(nameIdx) ?: name
                if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
            }
        }
        name to size
    }

    private fun prependHistory(item: SessionBreeze) {
        _uiState.update { state ->
            state.copy(sessionHistory = (listOf(item) + state.sessionHistory).take(12))
        }
    }

    private fun triggerBurst() {
        scope.launch {
            _uiState.update { it.copy(isBursting = true) }
            delay(1_100)
            _uiState.update { it.copy(isBursting = false) }
        }
    }

    private fun postBanner(message: String, isError: Boolean) {
        scope.launch {
            _uiState.update { it.copy(statusMessage = message, statusIsError = isError) }
            delay(3_800)
            _uiState.update {
                if (it.statusMessage == message) it.copy(statusMessage = null) else it
            }
        }
    }

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
