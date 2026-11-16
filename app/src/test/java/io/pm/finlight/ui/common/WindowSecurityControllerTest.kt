package io.pm.finlight.ui.common

import android.view.Window
import android.view.WindowManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class WindowSecurityControllerTest {
    @Test
    fun updateSecureFlag_whenIsSecureTrue_setsFlagSecure() {
        val window = mockk<Window>(relaxed = true)

        WindowSecurityController.updateSecureFlag(window, isSecure = true)

        verify(exactly = 1) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        verify(exactly = 0) {
            window.clearFlags(any())
        }
    }

    @Test
    fun updateSecureFlag_whenIsSecureFalse_clearsFlagSecure() {
        val window = mockk<Window>(relaxed = true)

        WindowSecurityController.updateSecureFlag(window, isSecure = false)

        verify(exactly = 1) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        verify(exactly = 0) {
            window.setFlags(any(), any())
        }
    }
}
