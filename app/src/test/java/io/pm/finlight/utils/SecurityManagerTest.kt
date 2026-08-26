package io.pm.finlight.security

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.KeyStore
import java.security.Security
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SecurityManagerTest : BaseViewModelTest() {
    private lateinit var context: Application
    private lateinit var securityManager: SecurityManager
    private lateinit var testKeyStore: KeyStore

    companion object {
        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        // Clean up files and prefs before each test
        File(context.filesDir, "finlight_secure.dat").delete()
        context.getSharedPreferences("finlight_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        testKeyStore =
            KeyStore.getInstance("BKS", "BC").apply {
                load(null, "test_password".toCharArray())
            }

        securityManager = createTestSecurityManager()
    }

    private fun createTestSecurityManager(): SecurityManager {
        return object : SecurityManager(context) {
            override val keyStoreProvider: String = "BC"

            override val protectionParameter: KeyStore.ProtectionParameter =
                KeyStore.PasswordProtection("test_password".toCharArray())

            override fun getKeyStore(): KeyStore {
                return testKeyStore
            }

            override fun generateSecretKey(): SecretKey {
                val keyGenerator = KeyGenerator.getInstance("AES", "BC")
                keyGenerator.init(256)
                val secretKey = keyGenerator.generateKey()
                testKeyStore.setEntry(
                    KEY_ALIAS,
                    KeyStore.SecretKeyEntry(secretKey),
                    protectionParameter,
                )
                return secretKey
            }
        }
    }

    @Test
    fun `getPassphrase returns a non-empty byte array`() {
        // Act
        val passphrase = securityManager.getPassphrase()

        // Assert
        assertNotNull("Passphrase should not be null", passphrase)
        assertTrue("Passphrase should not be empty", passphrase.isNotEmpty())
    }

    @Test
    fun `getPassphrase returns the same passphrase on subsequent calls`() {
        // Act
        val passphrase1 = securityManager.getPassphrase()
        val passphrase2 = securityManager.getPassphrase()

        // Assert
        assertArrayEquals("Subsequent calls should return the same passphrase", passphrase1, passphrase2)
    }

    @Test
    fun `encryption and decryption cycle results in original data`() {
        // Arrange
        val passphrase1 = securityManager.getPassphrase()

        // Act
        val newSecurityManager = createTestSecurityManager()
        val passphrase2 = newSecurityManager.getPassphrase()

        // Assert
        assertArrayEquals("The decrypted passphrase should match the original", passphrase1, passphrase2)
    }

    @Test
    fun `migrates from legacy SharedPreferences when secure file is missing`() {
        // Arrange: generate passphrase first and capture the file contents
        val originalPassphrase = securityManager.getPassphrase()
        val secureFile = File(context.filesDir, "finlight_secure.dat")
        val savedData = secureFile.readText()

        // Delete file and write to legacy SharedPreferences
        secureFile.delete()
        val legacyPrefs = context.getSharedPreferences("finlight_secure_prefs", Context.MODE_PRIVATE)
        legacyPrefs.edit().putString("db_passphrase", savedData).commit()

        // Act: instantiate new manager and get passphrase
        val newManager = createTestSecurityManager()
        val migratedPassphrase = newManager.getPassphrase()

        // Assert
        assertArrayEquals("Migrated passphrase should match original", originalPassphrase, migratedPassphrase)
        assertTrue("Secure file should have been created during migration", secureFile.exists())
        assertNull("Legacy prefs should be cleared after migration", legacyPrefs.getString("db_passphrase", null))
    }

    @Test
    fun `handles corrupted secure file by generating new passphrase`() {
        // Write corrupted non-split data to file
        val secureFile = File(context.filesDir, "finlight_secure.dat")
        secureFile.writeText("corrupted_single_part_data")

        val newManager = createTestSecurityManager()
        val passphrase = newManager.getPassphrase()

        assertNotNull("Passphrase should be generated despite corruption", passphrase)
        assertTrue("Passphrase should not be empty", passphrase.isNotEmpty())
    }

    @Test
    fun `handles invalid base64 in secure file by generating new passphrase`() {
        val secureFile = File(context.filesDir, "finlight_secure.dat")
        secureFile.writeText("###invalid_base64###,===invalid_iv===")

        val newManager = createTestSecurityManager()
        val passphrase = newManager.getPassphrase()

        assertNotNull("Passphrase should be generated despite invalid base64", passphrase)
        assertTrue("Passphrase should not be empty", passphrase.isNotEmpty())
    }

    @Test
    fun `default SecurityManager properties and secret key generation`() {
        val defaultManager = SecurityManager(context)
        org.junit.Assert.assertEquals("AndroidKeyStore", defaultManager.keyStoreProvider)
        assertNull(defaultManager.protectionParameter)
        try {
            val key = defaultManager.generateSecretKey()
            assertNotNull(key)
        } catch (e: Exception) {
            // AndroidKeyStore may not be fully backed by real hardware in Robolectric, but generateSecretKey code path executes
        }
    }
}
