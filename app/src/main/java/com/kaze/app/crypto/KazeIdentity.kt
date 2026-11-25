package com.kaze.app.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * Holds this device's persistent P-256 signing identity key (backed by Android Keystore on device,
 * or in-memory software EC P-256 key in JVM unit tests).
 */
class KazeIdentity private constructor(
    private val privateKey: PrivateKey,
    val publicKeyRaw: ByteArray
) {
    val fingerprintHex: String = KazeCrypto.sha256Hex(publicKeyRaw)
    val shortFingerprint: String = KazeCrypto.formatShortFingerprint(fingerprintHex)

    fun sign(data: ByteArray): ByteArray {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(privateKey)
        signature.update(data)
        return signature.sign()
    }

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS = "kaze_device_p256_v1"

        fun loadOrCreateAndroidKeystore(): KazeIdentity {
            return try {
                val ks = KeyStore.getInstance(KEYSTORE_PROVIDER)
                ks.load(null)
                if (!ks.containsAlias(KEY_ALIAS)) {
                    val kpg = KeyPairGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_EC,
                        KEYSTORE_PROVIDER
                    )
                    val spec = KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                    )
                        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                    kpg.initialize(spec)
                    kpg.generateKeyPair()
                }
                val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
                val ecPub = entry.certificate.publicKey as ECPublicKey
                KazeIdentity(entry.privateKey, KazeCrypto.encodeUncompressedP256(ecPub))
            } catch (_: Throwable) {
                createEphemeralForTest()
            }
        }

        fun createEphemeralForTest(): KazeIdentity {
            val kp: KeyPair = KazeCrypto.generateP256KeyPair()
            val ecPub = kp.public as ECPublicKey
            return KazeIdentity(kp.private, KazeCrypto.encodeUncompressedP256(ecPub))
        }
    }
}
