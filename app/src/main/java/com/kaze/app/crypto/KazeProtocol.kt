package com.kaze.app.crypto

import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.URI
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.security.KeyPair
import java.security.interfaces.ECPublicKey
import java.util.Base64
import java.util.UUID

enum class KazeFrameType(val byteValue: Byte) {
    JSON_CONTROL(0x01),
    FILE_CHUNK(0x02)
}

data class KazeEnvelope(
    val type: String,
    val id: String = UUID.randomUUID().toString(),
    val text: String? = null,
    val fileName: String? = null,
    val mimeType: String? = null,
    val fileSize: Long? = null,
    val sha256Hex: String? = null,
    val timestampMs: Long? = System.currentTimeMillis(),
    val error: String? = null
) {
    fun toJsonBytes(): ByteArray {
        val obj = JSONObject()
        obj.put("type", type)
        obj.put("id", id)
        if (text != null) obj.put("text", text)
        if (fileName != null) obj.put("fileName", fileName)
        if (mimeType != null) obj.put("mimeType", mimeType)
        if (fileSize != null) obj.put("fileSize", fileSize)
        if (sha256Hex != null) obj.put("sha256Hex", sha256Hex)
        if (timestampMs != null) obj.put("timestampMs", timestampMs)
        if (error != null) obj.put("error", error)
        return obj.toString().toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun fromJsonBytes(bytes: ByteArray): KazeEnvelope {
            val obj = JSONObject(String(bytes, Charsets.UTF_8))
            return KazeEnvelope(
                type = obj.getString("type"),
                id = obj.optString("id", UUID.randomUUID().toString()),
                text = if (obj.has("text") && !obj.isNull("text")) obj.getString("text") else null,
                fileName = if (obj.has("fileName") && !obj.isNull("fileName")) obj.getString("fileName") else null,
                mimeType = if (obj.has("mimeType") && !obj.isNull("mimeType")) obj.getString("mimeType") else null,
                fileSize = if (obj.has("fileSize") && !obj.isNull("fileSize")) obj.getLong("fileSize") else null,
                sha256Hex = if (obj.has("sha256Hex") && !obj.isNull("sha256Hex")) obj.getString("sha256Hex") else null,
                timestampMs = if (obj.has("timestampMs") && !obj.isNull("timestampMs")) obj.getLong("timestampMs") else null,
                error = if (obj.has("error") && !obj.isNull("error")) obj.getString("error") else null
            )
        }
    }
}

data class FileChunkFrame(
    val transferId: String,
    val chunk: ByteArray
)

data class FileChunkSlice(
    val transferId: String,
    val offset: Int,
    val length: Int
)

object KazeWireCodec {
    const val MAX_FRAME_BYTES = 2 * 1024 * 1024

    fun writeLengthPrefixed(output: DataOutputStream, payload: ByteArray) {
        output.writeInt(payload.size)
        output.write(payload)
        output.flush()
    }

    fun readLengthPrefixed(input: DataInputStream): ByteArray {
        val len = try {
            input.readInt()
        } catch (e: EOFException) {
            throw e
        }
        require(len in 1..MAX_FRAME_BYTES) { "Invalid frame length: $len" }
        val buf = ByteArray(len)
        input.readFully(buf)
        return buf
    }

    fun encodeControlFrame(envelope: KazeEnvelope): ByteArray {
        val json = envelope.toJsonBytes()
        val out = ByteArray(1 + json.size)
        out[0] = KazeFrameType.JSON_CONTROL.byteValue
        System.arraycopy(json, 0, out, 1, json.size)
        return out
    }

    fun encodeFileChunkFrame(transferId: String, chunk: ByteArray, length: Int = chunk.size): ByteArray {
        val idBytes = transferId.toByteArray(Charsets.UTF_8)
        return encodeFileChunkFrame(idBytes, chunk, length)
    }

    fun encodeFileChunkFrame(transferIdBytes: ByteArray, chunk: ByteArray, length: Int): ByteArray {
        val out = ByteArray(1 + 2 + transferIdBytes.size + length)
        out[0] = KazeFrameType.FILE_CHUNK.byteValue
        out[1] = ((transferIdBytes.size ushr 8) and 0xFF).toByte()
        out[2] = (transferIdBytes.size and 0xFF).toByte()
        System.arraycopy(transferIdBytes, 0, out, 3, transferIdBytes.size)
        System.arraycopy(chunk, 0, out, 3 + transferIdBytes.size, length)
        return out
    }

    fun decodeFileChunkSlice(payload: ByteArray): FileChunkSlice? {
        if (payload.size < 3 || payload[0] != KazeFrameType.FILE_CHUNK.byteValue) return null
        val idLen = ((payload[1].toInt() and 0xFF) shl 8) or (payload[2].toInt() and 0xFF)
        if (payload.size < 3 + idLen) return null
        val transferId = String(payload, 3, idLen, Charsets.UTF_8)
        val offset = 3 + idLen
        return FileChunkSlice(transferId, offset, payload.size - offset)
    }

    fun decodeFileChunkFrame(payload: ByteArray): FileChunkFrame? {
        val slice = decodeFileChunkSlice(payload) ?: return null
        val chunk = payload.copyOfRange(slice.offset, slice.offset + slice.length)
        return FileChunkFrame(slice.transferId, chunk)
    }
}

data class KazePairingPayload(
    val host: String,
    val port: Int,
    val fingerprintHex: String,
    val tokenHex: String,
    val deviceName: String
) {
    companion object {
        fun parse(uriString: String): KazePairingPayload? {
            return try {
                val uri = URI(uriString.trim())
                if (uri.scheme != "kaze" || uri.host != "pair") return null
                val rawQuery = uri.rawQuery ?: return null
                val params = rawQuery.split("&").mapNotNull { part ->
                    val idx = part.indexOf('=')
                    if (idx <= 0) return@mapNotNull null
                    val k = URLDecoder.decode(part.substring(0, idx), "UTF-8")
                    val v = URLDecoder.decode(part.substring(idx + 1), "UTF-8")
                    k to v
                }.toMap()

                if (params["v"] != "1") return null
                val host = params["h"]?.takeIf { it.isNotBlank() } ?: return null
                val port = params["p"]?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
                val fp = params["fp"]?.lowercase()?.takeIf { it.length == 64 } ?: return null
                val token = params["t"]?.lowercase()?.takeIf { it.length >= 16 } ?: return null
                val name = params["n"]?.takeIf { it.isNotBlank() } ?: "Mac"
                KazePairingPayload(host, port, fp, token, name)
            } catch (_: Throwable) {
                null
            }
        }
    }
}

data class HandshakeOutcome(
    val peerName: String,
    val peerFingerprintHex: String,
    val cipher: KazeSessionCipher,
    val wasNewPairing: Boolean
)

/**
 * Android client-side 3-step mutual-auth ephemeral P-256 handshake (byte-compatible with KazeMac).
 */
class KazeClientHandshake(
    private val identity: KazeIdentity,
    private val deviceName: String,
    private val expectedServerFingerprintHex: String?,
    private val pairingSecret: String? = null
) {
    private var ephKeyPair: KeyPair? = null
    private var clientNonce: ByteArray? = null

    fun createClientHelloBytes(): ByteArray {
        val eph = KazeCrypto.generateP256KeyPair()
        val nonce = KazeCrypto.randomBytes(32)
        this.ephKeyPair = eph
        this.clientNonce = nonce

        val ephPubRaw = KazeCrypto.encodeUncompressedP256(eph.public as ECPublicKey)
        val b64 = Base64.getEncoder()

        val obj = JSONObject()
        obj.put("version", 1)
        obj.put("deviceName", deviceName)
        obj.put("clientIdPubB64", b64.encodeToString(identity.publicKeyRaw))
        obj.put("clientEphPubB64", b64.encodeToString(ephPubRaw))
        obj.put("clientNonceB64", b64.encodeToString(nonce))

        if (!pairingSecret.isNullOrEmpty()) {
            val proof = KazeCrypto.pairingProof(
                secret = pairingSecret,
                clientIdPub = identity.publicKeyRaw,
                clientEphPub = ephPubRaw,
                clientNonce = nonce
            )
            obj.put("pairingProofB64", b64.encodeToString(proof))
        }
        return obj.toString().toByteArray(Charsets.UTF_8)
    }

    fun processServerHello(serverHelloBytes: ByteArray): Pair<ByteArray, HandshakeOutcome> {
        val eph = checkNotNull(ephKeyPair) { "ClientHello not yet created" }
        val cNonce = checkNotNull(clientNonce) { "ClientHello not yet created" }

        val obj = JSONObject(String(serverHelloBytes, Charsets.UTF_8))
        if (obj.has("error")) {
            throw SecurityException("Mac rejected handshake: ${obj.optString("error")}")
        }
        val version = obj.optInt("version", -1)
        if (version != 1) throw SecurityException("Unsupported Kaze protocol version: $version")

        val dec = Base64.getDecoder()
        val sIdPub = dec.decode(obj.getString("serverIdPubB64"))
        val sEphPub = dec.decode(obj.getString("serverEphPubB64"))
        val sNonce = dec.decode(obj.getString("serverNonceB64"))
        val sSig = dec.decode(obj.getString("serverSigB64"))
        val peerName = obj.optString("deviceName", "Mac")

        if (sIdPub.size != 65 || sEphPub.size != 65 || sNonce.size != 32) {
            throw SecurityException("Malformed ServerHello keys")
        }

        val serverFp = KazeCrypto.sha256Hex(sIdPub)
        if (expectedServerFingerprintHex != null &&
            !expectedServerFingerprintHex.equals(serverFp, ignoreCase = true)
        ) {
            throw SecurityException("Mac fingerprint mismatch! Possible MITM attack.")
        }

        val cEphPubRaw = KazeCrypto.encodeUncompressedP256(eph.public as ECPublicKey)
        val tHash = KazeCrypto.transcriptHash(
            clientIdPub = identity.publicKeyRaw,
            clientEphPub = cEphPubRaw,
            clientNonce = cNonce,
            serverIdPub = sIdPub,
            serverEphPub = sEphPub,
            serverNonce = sNonce
        )

        val serverMsg = KazeCrypto.serverSignatureInput(tHash)
        if (!KazeCrypto.verifyEcdsa(sIdPub, sSig, serverMsg)) {
            throw SecurityException("Mac ECDSA signature verification failed")
        }

        val clientSig = identity.sign(KazeCrypto.clientSignatureInput(tHash))
        val keys = KazeCrypto.deriveSessionKeys(
            myEphPrivate = eph.private,
            peerEphPublicRaw = sEphPub,
            transcriptHash = tHash
        )
        val cipher = KazeSessionCipher(keys.c2sKey, keys.s2cKey, isServer = false)
        val authJson = JSONObject().apply {
            put("clientSigB64", Base64.getEncoder().encodeToString(clientSig))
        }.toString().toByteArray(Charsets.UTF_8)

        val outcome = HandshakeOutcome(
            peerName = peerName,
            peerFingerprintHex = serverFp,
            cipher = cipher,
            wasNewPairing = !pairingSecret.isNullOrEmpty()
        )
        return authJson to outcome
    }
}
