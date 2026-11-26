package com.kaze.app.net

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class PairedMac(
    val deviceName: String,
    val host: String,
    val port: Int,
    val fingerprintHex: String,
    val pairedAtMs: Long = System.currentTimeMillis()
)

/**
 * Stores the paired Mac's pinned P-256 fingerprint encrypted at rest with an Android Keystore AES-256-GCM key.
 */
class TrustedMacStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPairedMac(): PairedMac? {
        val blob = prefs.getString(KEY_PAIRED_MAC_ENC, null) ?: return null
        val jsonStr = decrypt(blob) ?: return null
        return try {
            val obj = JSONObject(jsonStr)
            PairedMac(
                deviceName = obj.getString("deviceName"),
                host = obj.getString("host"),
                port = obj.getInt("port"),
                fingerprintHex = obj.getString("fingerprintHex").lowercase(),
                pairedAtMs = obj.optLong("pairedAtMs", System.currentTimeMillis())
            )
        } catch (_: Throwable) {
            null
        }
    }

    fun savePairedMac(mac: PairedMac) {
        val obj = JSONObject().apply {
            put("deviceName", mac.deviceName)
            put("host", mac.host)
            put("port", mac.port)
            put("fingerprintHex", mac.fingerprintHex.lowercase())
            put("pairedAtMs", mac.pairedAtMs)
        }
        val encrypted = encrypt(obj.toString())
        prefs.edit().putString(KEY_PAIRED_MAC_ENC, encrypted).apply()
    }

    fun clearPairedMac() {
        prefs.edit().remove(KEY_PAIRED_MAC_ENC).apply()
    }

    fun showJapaneseLabels(): Boolean = prefs.getBoolean(KEY_SHOW_JP, true)

    fun setShowJapaneseLabels(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_JP, show).apply()
    }

    fun backgroundListenEnabled(): Boolean = prefs.getBoolean(KEY_BG_LISTEN, false)

    fun setBackgroundListenEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BG_LISTEN, enabled).apply()
    }

    private fun getOrCreateKey(): SecretKey? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
                val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                kg.init(spec)
                kg.generateKey()
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun encrypt(plainText: String): String {
        val key = getOrCreateKey() ?: return "plain:$plainText"
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ct = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ctB64 = Base64.encodeToString(ct, Base64.NO_WRAP)
        return "enc:$ivB64:$ctB64"
    }

    private fun decrypt(blob: String): String? {
        if (blob.startsWith("plain:")) return blob.removePrefix("plain:")
        if (!blob.startsWith("enc:")) return null
        val parts = blob.removePrefix("enc:").split(":")
        if (parts.size != 2) return null
        val key = getOrCreateKey() ?: return null
        return try {
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ct = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ct), Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        private const val PREFS_NAME = "kaze_secure_store"
        private const val KEY_ALIAS = "kaze_prefs_aes_v1"
        private const val KEY_PAIRED_MAC_ENC = "paired_mac_enc"
        private const val KEY_SHOW_JP = "show_jp_labels"
        private const val KEY_BG_LISTEN = "background_listen_enabled"
    }
}
