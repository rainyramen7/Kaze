package com.kaze.app.net

import com.kaze.app.crypto.KazeCrypto
import com.kaze.app.crypto.KazeEnvelope
import com.kaze.app.crypto.KazeFrameType
import com.kaze.app.crypto.KazeIdentity
import com.kaze.app.crypto.KazeSessionCipher
import com.kaze.app.crypto.KazeWireCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * End-to-end loopback integration tests exercising the real KazeConnectionManager
 * (initialization order, QR pairing, pinned reconnect, MITM rejection, encrypted clipboard push,
 * mDNS lifecycle, stale cache sweep, and unpair) over real TCP sockets on 127.0.0.1.
 */
class KazeConnectionManagerIntegrationTest {

    private class FakeMacTrustRepository(initial: PairedMac? = null) : MacTrustRepository {
        @Volatile var paired: PairedMac? = initial
        @Volatile var showJp: Boolean = true
        @Volatile var bgListen: Boolean = false

        override fun loadPairedMac(): PairedMac? = paired
        override fun savePairedMac(mac: PairedMac) { paired = mac }
        override fun clearPairedMac() { paired = null }
        override fun showJapaneseLabels(): Boolean = showJp
        override fun setShowJapaneseLabels(show: Boolean) { showJp = show }
        override fun backgroundListenEnabled(): Boolean = bgListen
        override fun setBackgroundListenEnabled(enabled: Boolean) { bgListen = enabled }
    }

    private class FakeDiscoverySource : MacDiscoverySource {
        override val discovered: StateFlow<List<DiscoveredMac>> = MutableStateFlow(emptyList())
        val isRunning = AtomicBoolean(false)
        @Volatile var startCount = 0
        @Volatile var stopCount = 0

        override fun start() {
            startCount++
            isRunning.set(true)
        }

        override fun stop() {
            stopCount++
            isRunning.set(false)
        }
    }

    /** Minimal KazeMac-compatible loopback TCP server implementing the exact 3-step P-256 handshake. */
    private class LoopbackKazeServer(
        val identity: KazeIdentity = KazeIdentity.createEphemeralForTest(),
        val deviceName: String = "Studio MacBook",
        val pairingToken: String? = "0123456789abcdef0123456789abcdef",
        val trustedClientFingerprints: MutableSet<String> = mutableSetOf()
    ) : AutoCloseable {
        val serverSocket = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        val port: Int get() = serverSocket.localPort
        val receivedEnvelopes = CopyOnWriteArrayList<KazeEnvelope>()
        val clientConnectedLatch = CountDownLatch(1)
        val clipboardReceivedLatch = CountDownLatch(1)
        val unpairReceivedLatch = CountDownLatch(1)
        val activeCipher = AtomicReference<KazeSessionCipher?>(null)
        private val activeOut = AtomicReference<DataOutputStream?>(null)
        private val activeSocket = AtomicReference<Socket?>(null)
        private val writeLock = Any()

        @Volatile private var running = true
        private val acceptThread = Thread {
            while (running && !serverSocket.isClosed) {
                val client = runCatching { serverSocket.accept() }.getOrNull() ?: break
                handleClient(client)
            }
        }.apply {
            isDaemon = true
            start()
        }

        private fun handleClient(socket: Socket) {
            try {
                socket.tcpNoDelay = true
                val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
                val output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

                val helloBytes = KazeWireCodec.readLengthPrefixed(input)
                val hello = JSONObject(String(helloBytes, Charsets.UTF_8))
                val dec = Base64.getDecoder()
                val enc = Base64.getEncoder()

                val cIdPub = dec.decode(hello.getString("clientIdPubB64"))
                val cEphPub = dec.decode(hello.getString("clientEphPubB64"))
                val cNonce = dec.decode(hello.getString("clientNonceB64"))
                val clientFp = KazeCrypto.sha256Hex(cIdPub)

                if (clientFp !in trustedClientFingerprints) {
                    val proofB64 = hello.optString("pairingProofB64", "")
                    val expected = pairingToken?.let { KazeCrypto.pairingProof(it, cIdPub, cEphPub, cNonce) }
                    if (expected == null || proofB64.isEmpty() || !KazeCrypto.constantTimeEquals(dec.decode(proofB64), expected)) {
                        KazeWireCodec.writeLengthPrefixed(output, JSONObject().put("error", "Untrusted peer").toString().toByteArray())
                        socket.close()
                        return
                    }
                }

                val sEph = KazeCrypto.generateP256KeyPair()
                val sEphPub = KazeCrypto.encodeUncompressedP256(sEph.public as ECPublicKey)
                val sNonce = KazeCrypto.randomBytes(32)
                val tHash = KazeCrypto.transcriptHash(cIdPub, cEphPub, cNonce, identity.publicKeyRaw, sEphPub, sNonce)
                val serverSig = identity.sign(KazeCrypto.serverSignatureInput(tHash))

                val serverHello = JSONObject().apply {
                    put("version", 1)
                    put("deviceName", deviceName)
                    put("serverIdPubB64", enc.encodeToString(identity.publicKeyRaw))
                    put("serverEphPubB64", enc.encodeToString(sEphPub))
                    put("serverNonceB64", enc.encodeToString(sNonce))
                    put("serverSigB64", enc.encodeToString(serverSig))
                    put("paired", true)
                }.toString().toByteArray(Charsets.UTF_8)
                KazeWireCodec.writeLengthPrefixed(output, serverHello)

                val authBytes = KazeWireCodec.readLengthPrefixed(input)
                val auth = JSONObject(String(authBytes, Charsets.UTF_8))
                val clientSig = dec.decode(auth.getString("clientSigB64"))
                if (!KazeCrypto.verifyEcdsa(cIdPub, clientSig, KazeCrypto.clientSignatureInput(tHash))) {
                    socket.close()
                    return
                }

                trustedClientFingerprints.add(clientFp)
                val keys = KazeCrypto.deriveSessionKeys(sEph.private, cEphPub, tHash)
                val cipher = KazeSessionCipher(keys.c2sKey, keys.s2cKey, isServer = true)
                activeSocket.set(socket)
                activeOut.set(output)
                activeCipher.set(cipher)
                clientConnectedLatch.countDown()

                while (running && !socket.isClosed) {
                    val encrypted = KazeWireCodec.readLengthPrefixed(input)
                    val plain = cipher.open(encrypted)
                    if (plain.isNotEmpty() && plain[0] == KazeFrameType.JSON_CONTROL.byteValue) {
                        val env = KazeEnvelope.fromJsonBytes(plain.copyOfRange(1, plain.size))
                        receivedEnvelopes.add(env)
                        when (env.type) {
                            "ping" -> sendEnvelope(KazeEnvelope(type = "pong", id = env.id))
                            "clipboard" -> clipboardReceivedLatch.countDown()
                            "unpair" -> {
                                unpairReceivedLatch.countDown()
                                socket.close()
                                return
                            }
                        }
                    }
                }
            } catch (_: Throwable) {
                runCatching { socket.close() }
            }
        }

        fun sendEnvelope(env: KazeEnvelope) {
            synchronized(writeLock) {
                val out = activeOut.get() ?: return
                val cipher = activeCipher.get() ?: return
                val sealed = cipher.seal(KazeWireCodec.encodeControlFrame(env))
                KazeWireCodec.writeLengthPrefixed(out, sealed)
            }
        }

        fun disconnectClient() {
            runCatching { activeSocket.getAndSet(null)?.close() }
        }

        override fun close() {
            running = false
            disconnectClient()
            runCatching { serverSocket.close() }
            acceptThread.join(1_000)
        }
    }

    private fun awaitCondition(timeoutMs: Long = 5_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(20)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    private fun newTempCacheDir(): File = Files.createTempDirectory("kaze_test_cache").toFile()

    @Test
    fun `cold start QR pairing connects cleanly, pins Mac, stops NSD, and pushes clipboards bidirectionally`() {
        val cacheDir = newTempCacheDir()
        // Plant stale temp file to verify startup sweep deletes it
        val staleFile = File(File(cacheDir, "kaze_incoming").apply { mkdirs() }, "stale.tmp").apply {
            writeText("orphan")
        }
        val store = FakeMacTrustRepository()
        val nsd = FakeDiscoverySource()

        LoopbackKazeServer().use { server ->
            val manager = KazeConnectionManager(
                appContext = null,
                store = store,
                nsd = nsd,
                cacheRootDir = cacheDir,
                identityLoader = { KazeIdentity.createEphemeralForTest() },
                clockMs = System::currentTimeMillis,
                deviceName = "Test Pixel"
            )
            try {
                // Verify startup sweep & async identity loading (catches v1.1.0 init-order NPE!)
                awaitCondition { !staleFile.exists() }
                awaitCondition { manager.uiState.value.deviceShortFingerprint.isNotEmpty() }

                // Simulate user opening PairingScreen and scanning QR URI
                manager.setPairingScreenVisible(true)
                assertTrue(nsd.isRunning.get())

                val qrUri = "kaze://pair?v=1&h=127.0.0.1&p=${server.port}&fp=${server.identity.fingerprintHex}&t=${server.pairingToken}&n=Studio+MacBook"
                manager.pairWithQrUri(qrUri)
                manager.setPairingScreenVisible(false)

                assertTrue(server.clientConnectedLatch.await(5, TimeUnit.SECONDS))
                awaitCondition { manager.uiState.value.status == ConnectionStatus.CONNECTED }

                // Mac fingerprint must be pinned and mDNS must have stopped to save Wi-Fi battery
                val pinned = store.loadPairedMac()
                assertNotNull(pinned)
                assertEquals(server.identity.fingerprintHex, pinned!!.fingerprintHex)
                assertFalse("mDNS discovery should stop once connected", nsd.isRunning.get())

                // Android -> Mac encrypted clipboard push
                manager.pushClipboardText("https://example.com/kaze-test")
                assertTrue(server.clipboardReceivedLatch.await(5, TimeUnit.SECONDS))
                assertTrue(server.receivedEnvelopes.any { it.type == "clipboard" && it.text == "https://example.com/kaze-test" })

                // Mac -> Android encrypted clipboard push
                server.sendEnvelope(KazeEnvelope(type = "clipboard", text = "Breeze from Studio MacBook"))
                awaitCondition {
                    manager.uiState.value.sessionHistory.any {
                        it.direction == BreezeDirection.FROM_MAC && it.textPayload == "Breeze from Studio MacBook"
                    }
                }

                // Opening PairingScreen while connected restarts NSD; closing stops it again
                manager.setPairingScreenVisible(true)
                assertTrue(nsd.isRunning.get())
                manager.setPairingScreenVisible(false)
                assertFalse(nsd.isRunning.get())
            } finally {
                manager.shutdownForTest()
                cacheDir.deleteRecursively()
            }
        }
    }

    @Test
    fun `pinned reconnect works without pairing token and rejects MITM fingerprint mismatch`() {
        val cacheDir = newTempCacheDir()
        val clientIdentity = KazeIdentity.createEphemeralForTest()
        val realServer = LoopbackKazeServer(
            pairingToken = null,
            trustedClientFingerprints = mutableSetOf(clientIdentity.fingerprintHex)
        )
        val mitmServer = LoopbackKazeServer(
            pairingToken = null,
            trustedClientFingerprints = mutableSetOf(clientIdentity.fingerprintHex)
        )

        try {
            val store = FakeMacTrustRepository(
                initial = PairedMac(
                    deviceName = "Studio MacBook",
                    host = "127.0.0.1",
                    port = realServer.port,
                    fingerprintHex = realServer.identity.fingerprintHex
                )
            )
            val nsd = FakeDiscoverySource()
            val manager = KazeConnectionManager(
                appContext = null,
                store = store,
                nsd = nsd,
                cacheRootDir = cacheDir,
                identityLoader = { clientIdentity },
                clockMs = System::currentTimeMillis,
                deviceName = "Test Pixel"
            )
            try {
                // 1. Pinned reconnect succeeds without pairing token
                manager.connectToPairedMac()
                assertTrue(realServer.clientConnectedLatch.await(5, TimeUnit.SECONDS))
                awaitCondition { manager.uiState.value.status == ConnectionStatus.CONNECTED }

                // 2. Unpair wipes pinned key and notifies Mac
                manager.unpairMac()
                assertTrue(realServer.unpairReceivedLatch.await(5, TimeUnit.SECONDS))
                awaitCondition { manager.uiState.value.status == ConnectionStatus.UNPAIRED }
                assertNull(store.loadPairedMac())
            } finally {
                manager.shutdownForTest()
            }

            // 3. Pointing paired store at MITM server (wrong identity key) MUST be rejected
            val mitmStore = FakeMacTrustRepository(
                initial = PairedMac(
                    deviceName = "Studio MacBook",
                    host = "127.0.0.1",
                    port = mitmServer.port,
                    fingerprintHex = realServer.identity.fingerprintHex // expects real Mac, hits MITM
                )
            )
            val mitmManager = KazeConnectionManager(
                appContext = null,
                store = mitmStore,
                nsd = FakeDiscoverySource(),
                cacheRootDir = cacheDir,
                identityLoader = { clientIdentity },
                clockMs = System::currentTimeMillis,
                deviceName = "Test Pixel"
            )
            try {
                mitmManager.connectToPairedMac()
                // Wait briefly and verify MITM never completes ClientAuth or becomes CONNECTED
                assertFalse(mitmServer.clientConnectedLatch.await(1, TimeUnit.SECONDS))
                assertTrue(mitmManager.uiState.value.status != ConnectionStatus.CONNECTED)
            } finally {
                mitmManager.shutdownForTest()
            }
        } finally {
            realServer.close()
            mitmServer.close()
            cacheDir.deleteRecursively()
        }
    }
}
