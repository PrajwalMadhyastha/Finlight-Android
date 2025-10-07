package io.pm.finlight.security

import android.app.Application
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.KeyStore
import java.security.Security
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.P], application = TestApplication::class)
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

        testKeyStore = KeyStore.getInstance("BKS", "BC").apply {
            load(null, "test_password".toCharArray())
        }

        securityManager = object : SecurityManager(context) {
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
                    protectionParameter
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
        val newSecurityManager = object : SecurityManager(context) {
            override val keyStoreProvider: String = "BC"

            override fun getKeyStore(): KeyStore {
                return testKeyStore
            }

            override val protectionParameter: KeyStore.ProtectionParameter =
                KeyStore.PasswordProtection("test_password".toCharArray())


            override fun generateSecretKey(): SecretKey {
                // This override is only needed if a key doesn't exist yet.
                // In this test, it will already exist from the first call.
                // We provide it for completeness.
                val keyGenerator = KeyGenerator.getInstance("AES", "BC")
                keyGenerator.init(256)
                return keyGenerator.generateKey()
            }
        }
        val passphrase2 = newSecurityManager.getPassphrase()

        // Assert
        assertArrayEquals("The decrypted passphrase should match the original", passphrase1, passphrase2)
    }
}