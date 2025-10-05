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
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.security.Security

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SecurityManagerTest : BaseViewModelTest() {

    private lateinit var context: Application
    private lateinit var securityManager: SecurityManager

    companion object {
        @JvmStatic
        @BeforeClass
        fun beforeClass() {
            // The AndroidKeyStore is not available in the local JVM test environment.
            // We add the Bouncy Castle provider once before any tests run to act as a substitute.
            Security.addProvider(BouncyCastleProvider())
        }
    }

    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()
        securityManager = SecurityManager(context)
    }

    @Test
    @Ignore
    fun `getPassphrase returns a non-empty byte array`() {
        // Act
        val passphrase = securityManager.getPassphrase()

        // Assert
        assertNotNull("Passphrase should not be null", passphrase)
        assertTrue("Passphrase should not be empty", passphrase.isNotEmpty())
    }

    @Test
    @Ignore
    fun `getPassphrase returns the same passphrase on subsequent calls`() {
        // Act
        val passphrase1 = securityManager.getPassphrase()
        val passphrase2 = securityManager.getPassphrase()

        // Assert
        assertArrayEquals("Subsequent calls should return the same passphrase", passphrase1, passphrase2)
    }

    @Test
    @Ignore
    fun `encryption and decryption cycle results in original data`() {
        // This test implicitly validates the entire cryptographic process
        // Arrange
        val originalData = "This is a secret message for the database.".toByteArray()

        // Act
        // We can't directly call encrypt/decrypt as they are private, but getPassphrase()
        // performs this entire cycle internally. We test it by re-instantiating the manager
        // to force it to read from SharedPreferences and decrypt.
        val passphrase1 = securityManager.getPassphrase()

        val newSecurityManager = SecurityManager(context)
        val passphrase2 = newSecurityManager.getPassphrase()

        // Assert
        assertArrayEquals("The decrypted passphrase should match the original", passphrase1, passphrase2)
    }
}