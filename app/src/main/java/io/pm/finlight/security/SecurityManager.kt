// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/security/SecurityManager.kt
// REASON: NEW FILE - This class encapsulates all logic for database encryption.
// It handles the generation of a secure passphrase and its storage within the
// hardware-backed Android Keystore, providing a secure, transparent method for
// encrypting the Room database at rest.
// =================================================================================
package io.pm.finlight.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Manages the creation, storage, and retrieval of the database encryption key.
 * This class uses the Android Keystore system to securely store the key,
 * making the encryption process transparent to the user.
 */
class SecurityManager(private val context: Context) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "finlight_db_key"
        private const val PREFS_NAME = "finlight_secure_prefs"
        private const val PREF_ENCRYPTED_PASSPHRASE = "db_passphrase"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Retrieves the database passphrase. If it doesn't exist, it generates a new one.
     * @return The passphrase as a ByteArray.
     */
    fun getPassphrase(): ByteArray {
        var encryptedPassphrase = getEncryptedPassphrase()
        if (encryptedPassphrase == null) {
            val newPassphrase = generateRandomPassphrase()
            encryptedPassphrase = encrypt(newPassphrase)
            saveEncryptedPassphrase(encryptedPassphrase)
        }
        return decrypt(encryptedPassphrase)
    }

    /**
     * Generates a SecretKey for encryption/decryption, creating it if it doesn't already exist.
     */
    private fun getSecretKey(): SecretKey {
        val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: generateSecretKey()
    }

    /**
     * Generates a new AES key and stores it in the Android Keystore.
     */
    private fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).apply {
            setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            setKeySize(256)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                setUnlockedDeviceRequired(false)
                setIsStrongBoxBacked(false)
            }
        }.build()
        keyGenerator.init(parameterSpec)
        return keyGenerator.generateKey()
    }

    /**
     * Generates a new 32-byte random passphrase.
     */
    private fun generateRandomPassphrase(): ByteArray {
        val key = ByteArray(32)
        java.security.SecureRandom().nextBytes(key)
        return key
    }

    /**
     * Encrypts the given data using the key from the Keystore.
     * @param data The ByteArray to encrypt.
     * @return A map containing the encrypted data and the initialization vector (IV).
     */
    private fun encrypt(data: ByteArray): Map<String, ByteArray> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val encryptedData = cipher.doFinal(data)
        return mapOf("data" to encryptedData, "iv" to cipher.iv)
    }

    /**
     * Decrypts the given encrypted data using the key from the Keystore.
     * @param encryptedData A map containing the encrypted data and the IV.
     * @return The decrypted ByteArray.
     */
    private fun decrypt(encryptedData: Map<String, ByteArray>): ByteArray {
        val data = encryptedData["data"] ?: return ByteArray(0)
        val iv = encryptedData["iv"] ?: return ByteArray(0)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        return cipher.doFinal(data)
    }

    /**
     * Saves the encrypted passphrase and its IV to SharedPreferences.
     */
    private fun saveEncryptedPassphrase(encryptedData: Map<String, ByteArray>) {
        val dataBase64 = Base64.encodeToString(encryptedData["data"], Base64.DEFAULT)
        val ivBase64 = Base64.encodeToString(encryptedData["iv"], Base64.DEFAULT)
        sharedPrefs.edit().putString(PREF_ENCRYPTED_PASSPHRASE, "$dataBase64,$ivBase64").apply()
    }

    /**
     * Retrieves the encrypted passphrase and IV from SharedPreferences.
     * @return A map containing the encrypted data and IV, or null if not found.
     */
    private fun getEncryptedPassphrase(): Map<String, ByteArray>? {
        val savedString = sharedPrefs.getString(PREF_ENCRYPTED_PASSPHRASE, null) ?: return null
        val parts = savedString.split(",")
        if (parts.size != 2) return null
        return try {
            val data = Base64.decode(parts[0], Base64.DEFAULT)
            val iv = Base64.decode(parts[1], Base64.DEFAULT)
            mapOf("data" to data, "iv" to iv)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}
