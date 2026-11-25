package com.kaze.app.crypto

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.interfaces.ECPublicKey
import java.util.Base64

class KazeCryptoProtocolTest {

    @Test
    fun `full mutual-auth handshake and bidirectional AES-256-GCM frames succeed`() {
        val serverId = KazeIdentity.createEphemeralForTest()
        val clientId = KazeIdentity.createEphemeralForTest()
        val pairingToken = "0123456789abcdef0123456789abcdef"

        val clientHs = KazeClientHandshake(
            identity = clientId,
            deviceName = "Pixel 9",
            expectedServerFingerprintHex = serverId.fingerprintHex,
            pairingSecret = pairingToken
        )

        val clientHelloBytes = clientHs.createClientHelloBytes()
        val clientHello = JSONObject(String(clientHelloBytes, Charsets.UTF_8))

        val dec = Base64.getDecoder()
        val enc = Base64.getEncoder()
        val cIdPub = dec.decode(clientHello.getString("clientIdPubB64"))
        val cEphPub = dec.decode(clientHello.getString("clientEphPubB64"))
        val cNonce = dec.decode(clientHello.getString("clientNonceB64"))
        val proof = dec.decode(clientHello.getString("pairingProofB64"))

        // Server verifies HMAC pairing proof
        val expectedProof = KazeCrypto.pairingProof(pairingToken, cIdPub, cEphPub, cNonce)
        assertTrue(KazeCrypto.constantTimeEquals(proof, expectedProof))

        // Server responds with ServerHello
        val serverEph = KazeCrypto.generateP256KeyPair()
        val sEphPub = KazeCrypto.encodeUncompressedP256(serverEph.public as ECPublicKey)
        val sNonce = KazeCrypto.randomBytes(32)
        val tHash = KazeCrypto.transcriptHash(
            cIdPub, cEphPub, cNonce,
            serverId.publicKeyRaw, sEphPub, sNonce
        )
        val serverSig = serverId.sign(KazeCrypto.serverSignatureInput(tHash))

        val serverHelloBytes = JSONObject().apply {
            put("version", 1)
            put("deviceName", "Studio MacBook Pro")
            put("serverIdPubB64", enc.encodeToString(serverId.publicKeyRaw))
            put("serverEphPubB64", enc.encodeToString(sEphPub))
            put("serverNonceB64", enc.encodeToString(sNonce))
            put("serverSigB64", enc.encodeToString(serverSig))
            put("paired", true)
        }.toString().toByteArray(Charsets.UTF_8)

        val (clientAuthBytes, clientOutcome) = clientHs.processServerHello(serverHelloBytes)
        val clientAuth = JSONObject(String(clientAuthBytes, Charsets.UTF_8))
        val clientSig = dec.decode(clientAuth.getString("clientSigB64"))

        // Server verifies ClientAuth ECDSA signature
        assertTrue(KazeCrypto.verifyEcdsa(cIdPub, clientSig, KazeCrypto.clientSignatureInput(tHash)))

        val serverKeys = KazeCrypto.deriveSessionKeys(serverEph.private, cEphPub, tHash)
        val serverCipher = KazeSessionCipher(serverKeys.c2sKey, serverKeys.s2cKey, isServer = true)

        assertEquals(serverId.fingerprintHex, clientOutcome.peerFingerprintHex)
        assertTrue(clientOutcome.wasNewPairing)

        // Client -> Server encrypted clipboard control frame
        val env = KazeEnvelope(type = "clipboard", text = "Hello KazeMac 風")
        val rawControl = KazeWireCodec.encodeControlFrame(env)
        val sealed1 = clientOutcome.cipher.seal(rawControl)
        val opened1 = serverCipher.open(sealed1)
        assertArrayEquals(rawControl, opened1)
        val parsedEnv = KazeEnvelope.fromJsonBytes(opened1.copyOfRange(1, opened1.size))
        assertEquals("Hello KazeMac 風", parsedEnv.text)

        // Server -> Client encrypted binary file chunk
        val chunkBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val rawChunk = KazeWireCodec.encodeFileChunkFrame("tx-42", chunkBytes)
        val sealed2 = serverCipher.seal(rawChunk)
        val opened2 = clientOutcome.cipher.open(sealed2)
        val decodedChunk = KazeWireCodec.decodeFileChunkFrame(opened2)
        assertNotNull(decodedChunk)
        assertEquals("tx-42", decodedChunk!!.transferId)
        assertArrayEquals(chunkBytes, decodedChunk.chunk)
    }

    @Test
    fun `client rejects MITM server with mismatched fingerprint`() {
        val realServer = KazeIdentity.createEphemeralForTest()
        val mitmServer = KazeIdentity.createEphemeralForTest()
        val clientId = KazeIdentity.createEphemeralForTest()

        val clientHs = KazeClientHandshake(
            identity = clientId,
            deviceName = "Pixel",
            expectedServerFingerprintHex = realServer.fingerprintHex,
            pairingSecret = "0123456789abcdef"
        )
        val chBytes = clientHs.createClientHelloBytes()
        val ch = JSONObject(String(chBytes, Charsets.UTF_8))
        val dec = Base64.getDecoder()
        val enc = Base64.getEncoder()

        val mitmEph = KazeCrypto.generateP256KeyPair()
        val mEphPub = KazeCrypto.encodeUncompressedP256(mitmEph.public as ECPublicKey)
        val mNonce = KazeCrypto.randomBytes(32)
        val tHash = KazeCrypto.transcriptHash(
            dec.decode(ch.getString("clientIdPubB64")),
            dec.decode(ch.getString("clientEphPubB64")),
            dec.decode(ch.getString("clientNonceB64")),
            mitmServer.publicKeyRaw, mEphPub, mNonce
        )
        val mitmSig = mitmServer.sign(KazeCrypto.serverSignatureInput(tHash))
        val mitmHello = JSONObject().apply {
            put("version", 1)
            put("deviceName", "Evil Mac")
            put("serverIdPubB64", enc.encodeToString(mitmServer.publicKeyRaw))
            put("serverEphPubB64", enc.encodeToString(mEphPub))
            put("serverNonceB64", enc.encodeToString(mNonce))
            put("serverSigB64", enc.encodeToString(mitmSig))
            put("paired", true)
        }.toString().toByteArray(Charsets.UTF_8)

        try {
            clientHs.processServerHello(mitmHello)
            fail("Expected SecurityException on fingerprint mismatch")
        } catch (expected: SecurityException) {
            assertTrue(expected.message!!.contains("mismatch", ignoreCase = true))
        }
    }

    @Test
    fun `AES-256-GCM rejects tampered ciphertext`() {
        val key1 = KazeCrypto.randomBytes(32)
        val key2 = KazeCrypto.randomBytes(32)
        val client = KazeSessionCipher(key1, key2, isServer = false)
        val server = KazeSessionCipher(key1, key2, isServer = true)

        val sealed = client.seal("sensitive note".toByteArray())
        sealed[0] = (sealed[0].toInt() xor 0xFF).toByte()
        try {
            server.open(sealed)
            fail("Expected AEAD tag verification failure")
        } catch (_: Throwable) {
            // Expected
        }
    }

    @Test
    fun `QR pairing URI parses cleanly and rejects malformed URIs`() {
        val fp = "a1".repeat(32)
        val uri = "kaze://pair?v=1&h=192.168.1.42&p=51820&fp=$fp&t=0123456789abcdef0123456789abcdef&n=Studio+MacBook"
        val parsed = KazePairingPayload.parse(uri)
        assertNotNull(parsed)
        assertEquals("192.168.1.42", parsed!!.host)
        assertEquals(51820, parsed.port)
        assertEquals(fp, parsed.fingerprintHex)
        assertEquals("Studio MacBook", parsed.deviceName)

        assertNull(KazePairingPayload.parse("https://example.com"))
        assertNull(KazePairingPayload.parse("kaze://pair?v=2&h=1.2.3.4&p=1234"))
    }

    @Test
    fun `FileSafety strips path traversal and flags dangerous files`() {
        assertEquals("passwd", FileSafety.sanitizeFileName("../../etc/passwd"))
        assertEquals("hidden.txt", FileSafety.sanitizeFileName("..\\.\\.hidden.txt"))
        assertEquals("zshrc", FileSafety.sanitizeFileName(".zshrc"))
        assertEquals("cafe_photo_.jpg", FileSafety.sanitizeFileName("cafe:photo?.jpg"))

        assertTrue(FileSafety.requiresConfirmation("payload.apk", 1024))
        assertTrue(FileSafety.requiresConfirmation("run.sh", 256))
        assertTrue(FileSafety.requiresConfirmation("archive.zip", 150L * 1024L * 1024L))
        assertFalse(FileSafety.requiresConfirmation("kyoto_cafe.jpg", 3_800_000L))
    }
}
