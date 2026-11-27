package com.kaze.app.crypto

import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

enum class KazeDirection(val byteValue: Byte) {
    CLIENT_TO_SERVER(0x01),
    SERVER_TO_CLIENT(0x02)
}

data class DerivedSessionKeys(
    val c2sKey: ByteArray,
    val s2cKey: ByteArray
)

/**
 * Cryptographic primitives shared byte-for-byte with KazeMac (Apple CryptoKit).
 * Uses ephemeral P-256 ECDH + mutual P-256 ECDSA + HKDF-SHA256 + AES-256-GCM.
 */
object KazeCrypto {
    private val secureRandom = SecureRandom()

    private val p256Spec: ECParameterSpec by lazy {
        val params = AlgorithmParameters.getInstance("EC")
        params.init(ECGenParameterSpec("secp256r1"))
        params.getParameterSpec(ECParameterSpec::class.java)
    }

    fun randomBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        secureRandom.nextBytes(out)
        return out
    }

    fun generateP256KeyPair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        return kpg.generateKeyPair()
    }

    /** Encodes a P-256 ECPublicKey as 65-byte X9.63 uncompressed point: 0x04 || X(32) || Y(32). */
    fun encodeUncompressedP256(publicKey: ECPublicKey): ByteArray {
        val point = publicKey.w
        val x = toFixed32Bytes(point.affineX)
        val y = toFixed32Bytes(point.affineY)
        val out = ByteArray(65)
        out[0] = 0x04
        System.arraycopy(x, 0, out, 1, 32)
        System.arraycopy(y, 0, out, 33, 32)
        return out
    }

    /** Decodes a 65-byte X9.63 uncompressed P-256 point into an ECPublicKey. */
    fun decodeUncompressedP256(raw: ByteArray): ECPublicKey {
        require(raw.size == 65 && raw[0] == 0x04.toByte()) {
            "Invalid P-256 X9.63 public key"
        }
        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))
        val spec = ECPublicKeySpec(ECPoint(x, y), p256Spec)
        return KeyFactory.getInstance("EC").generatePublic(spec) as ECPublicKey
    }

    private fun toFixed32Bytes(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
            else -> {
                val padded = ByteArray(32)
                System.arraycopy(raw, 0, padded, 32 - raw.size, raw.size)
                padded
            }
        }
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun sha256Hex(data: ByteArray): String =
        sha256(data).joinToString("") { "%02x".format(it) }

    fun formatShortFingerprint(hex: String): String =
        hex.uppercase().take(16).chunked(2).joinToString(":")

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        MessageDigest.isEqual(a, b)

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /** HMAC-SHA256 pairing proof so neither QR token nor 6-digit PIN is sent in cleartext. */
    fun pairingProof(
        secret: String,
        clientIdPub: ByteArray,
        clientEphPub: ByteArray,
        clientNonce: ByteArray
    ): ByteArray {
        val prefix = "KAZE-PAIR-V1".toByteArray(Charsets.UTF_8)
        val payload = ByteArray(prefix.size + clientIdPub.size + clientEphPub.size + clientNonce.size)
        var pos = 0
        System.arraycopy(prefix, 0, payload, pos, prefix.size); pos += prefix.size
        System.arraycopy(clientIdPub, 0, payload, pos, clientIdPub.size); pos += clientIdPub.size
        System.arraycopy(clientEphPub, 0, payload, pos, clientEphPub.size); pos += clientEphPub.size
        System.arraycopy(clientNonce, 0, payload, pos, clientNonce.size)
        return hmacSha256(secret.toByteArray(Charsets.UTF_8), payload)
    }

    /** Deterministic 342-byte transcript hash matching KazeMac byte-for-byte. */
    fun transcriptHash(
        clientIdPub: ByteArray,
        clientEphPub: ByteArray,
        clientNonce: ByteArray,
        serverIdPub: ByteArray,
        serverEphPub: ByteArray,
        serverNonce: ByteArray
    ): ByteArray {
        val prefix = "KAZE-V1-TRANSCRIPT".toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-256")
        md.update(prefix)
        md.update(clientIdPub)
        md.update(clientEphPub)
        md.update(clientNonce)
        md.update(serverIdPub)
        md.update(serverEphPub)
        md.update(serverNonce)
        return md.digest()
    }

    fun serverSignatureInput(transcriptHash: ByteArray): ByteArray =
        "KAZE-V1-SERVER".toByteArray(Charsets.UTF_8) + transcriptHash

    fun clientSignatureInput(transcriptHash: ByteArray): ByteArray =
        "KAZE-V1-CLIENT".toByteArray(Charsets.UTF_8) + transcriptHash

    fun verifyEcdsa(publicKeyRaw: ByteArray, signatureDer: ByteArray, message: ByteArray): Boolean {
        return try {
            val pubKey = decodeUncompressedP256(publicKeyRaw)
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pubKey)
            sig.update(message)
            sig.verify(signatureDer)
        } catch (_: Throwable) {
            false
        }
    }

    fun deriveSessionKeys(
        myEphPrivate: PrivateKey,
        peerEphPublicRaw: ByteArray,
        transcriptHash: ByteArray
    ): DerivedSessionKeys {
        val peerPub = decodeUncompressedP256(peerEphPublicRaw)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myEphPrivate)
        ka.doPhase(peerPub, true)
        val sharedSecret = ka.generateSecret()

        val c2s = hkdfSha256(
            ikm = sharedSecret,
            salt = transcriptHash,
            info = "kaze-v1-c2s".toByteArray(Charsets.UTF_8),
            length = 32
        )
        val s2c = hkdfSha256(
            ikm = sharedSecret,
            salt = transcriptHash,
            info = "kaze-v1-s2c".toByteArray(Charsets.UTF_8),
            length = 32
        )
        return DerivedSessionKeys(c2sKey = c2s, s2cKey = s2c)
    }

    /** RFC 5869 HKDF-SHA256 (matching CryptoKit HKDF<SHA256>). */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        val actualSalt = if (salt.isEmpty()) ByteArray(32) else salt
        val prk = hmacSha256(actualSalt, ikm)
        val result = ByteArray(length)
        var offset = 0
        var t = ByteArray(0)
        var counter: Byte = 1
        while (offset < length) {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            mac.update(t)
            mac.update(info)
            mac.update(counter)
            t = mac.doFinal()
            val toCopy = minOf(t.size, length - offset)
            System.arraycopy(t, 0, result, offset, toCopy)
            offset += toCopy
            counter++
        }
        return result
    }
}

/**
 * Stateful directional AES-256-GCM frame cipher with strictly monotonic 64-bit counters and AAD.
 * Reuses Cipher instances and nonce/AAD buffers per direction to minimize per-chunk GC churn.
 */
class KazeSessionCipher(
    c2sKey: ByteArray,
    s2cKey: ByteArray,
    isServer: Boolean
) {
    private val sendKey = SecretKeySpec(if (isServer) s2cKey else c2sKey, "AES")
    private val recvKey = SecretKeySpec(if (isServer) c2sKey else s2cKey, "AES")
    private val sendDir = if (isServer) KazeDirection.SERVER_TO_CLIENT else KazeDirection.CLIENT_TO_SERVER
    private val recvDir = if (isServer) KazeDirection.CLIENT_TO_SERVER else KazeDirection.SERVER_TO_CLIENT

    private val sendCipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val recvCipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")
    private val sendNonce = ByteArray(12).apply { this[3] = sendDir.byteValue }
    private val recvNonce = ByteArray(12).apply { this[3] = recvDir.byteValue }
    private val sendAad = ByteArray(9).apply { this[0] = sendDir.byteValue }
    private val recvAad = ByteArray(9).apply { this[0] = recvDir.byteValue }

    private var sendCounter: Long = 0L
    private var recvCounter: Long = 0L
    private val sendLock = Any()
    private val recvLock = Any()

    fun seal(plaintext: ByteArray): ByteArray = synchronized(sendLock) {
        val counter = sendCounter
        check(counter >= 0L) { "Frame counter exhausted" }
        sendCounter = counter + 1L

        writeCounterBE(sendNonce, 4, counter)
        writeCounterBE(sendAad, 1, counter)
        sendCipher.init(Cipher.ENCRYPT_MODE, sendKey, GCMParameterSpec(128, sendNonce))
        sendCipher.updateAAD(sendAad)
        sendCipher.doFinal(plaintext)
    }

    fun open(ciphertextAndTag: ByteArray): ByteArray = synchronized(recvLock) {
        require(ciphertextAndTag.size >= 16) { "Ciphertext truncated" }
        val counter = recvCounter
        check(counter >= 0L) { "Frame counter exhausted" }
        recvCounter = counter + 1L

        writeCounterBE(recvNonce, 4, counter)
        writeCounterBE(recvAad, 1, counter)
        recvCipher.init(Cipher.DECRYPT_MODE, recvKey, GCMParameterSpec(128, recvNonce))
        recvCipher.updateAAD(recvAad)
        recvCipher.doFinal(ciphertextAndTag)
    }

    private fun writeCounterBE(dest: ByteArray, offset: Int, counter: Long) {
        dest[offset] = (counter ushr 56).toByte()
        dest[offset + 1] = (counter ushr 48).toByte()
        dest[offset + 2] = (counter ushr 40).toByte()
        dest[offset + 3] = (counter ushr 32).toByte()
        dest[offset + 4] = (counter ushr 24).toByte()
        dest[offset + 5] = (counter ushr 16).toByte()
        dest[offset + 6] = (counter ushr 8).toByte()
        dest[offset + 7] = counter.toByte()
    }
}
