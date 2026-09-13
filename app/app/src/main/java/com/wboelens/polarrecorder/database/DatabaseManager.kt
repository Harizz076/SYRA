package com.wboelens.polarrecorder.database

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.random.Random

/**
 * Manages creation and retrieval of the SQLCipher database passphrase using Android Keystore.
 *
 * Flow:
 *  1. On first run: generate a random 64-byte passphrase.
 *  2. Encrypt it with an AES/GCM key stored in the Android Keystore.
 *  3. Store the encrypted blob (IV + ciphertext) in EncryptedSharedPreferences.
 *  4. On subsequent runs: decrypt the blob and return the raw passphrase bytes to SyraDatabase.
 */
class DatabaseManager(private val context: Context) {

    companion object {
        private const val TAG = "DatabaseManager"
        private const val KEYSTORE_ALIAS = "syra_db_master_key"
        private const val PREFS_FILE = "syra_db_prefs"
        private const val KEY_ENCRYPTED_PASS = "encrypted_db_pass"
        private const val KEY_IV = "db_pass_iv"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_LENGTH = 128
    }

    private val encryptedPrefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getDatabase(): SyraDatabase {
        val passphrase = getOrCreatePassphrase()
        return SyraDatabase.getInstance(context, passphrase)
    }

    // ── Passphrase management ─────────────────────────────────────────────────

    private fun getOrCreatePassphrase(): ByteArray {
        val stored = encryptedPrefs.getString(KEY_ENCRYPTED_PASS, null)
        return if (stored == null) {
            val raw = generatePassphrase()
            storePassphrase(raw)
            raw
        } else {
            loadPassphrase()
        }
    }

    private fun generatePassphrase(): ByteArray {
        Log.d(TAG, "Generating new database passphrase")
        return Random.nextBytes(64)
    }

    private fun storePassphrase(raw: ByteArray) {
        val key = getOrCreateKeystoreKey()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(raw)
        encryptedPrefs.edit()
            .putString(KEY_ENCRYPTED_PASS, android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP))
            .putString(KEY_IV, android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP))
            .apply()
        Log.d(TAG, "Database passphrase stored securely")
    }

    private fun loadPassphrase(): ByteArray {
        val key = getOrCreateKeystoreKey()
        val encrypted = android.util.Base64.decode(
            encryptedPrefs.getString(KEY_ENCRYPTED_PASS, "")!!, android.util.Base64.NO_WRAP
        )
        val iv = android.util.Base64.decode(
            encryptedPrefs.getString(KEY_IV, "")!!, android.util.Base64.NO_WRAP
        )
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return cipher.doFinal(encrypted)
    }

    private fun getOrCreateKeystoreKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).also { it.load(null) }
        val existingKey = ks.getEntry(KEYSTORE_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingKey != null) return existingKey.secretKey

        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return kg.generateKey()
    }
}
