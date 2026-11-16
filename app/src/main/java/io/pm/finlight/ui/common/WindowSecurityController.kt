package io.pm.finlight.ui.common

import android.view.Window
import android.view.WindowManager

object WindowSecurityController {
    fun updateSecureFlag(
        window: Window,
        isSecure: Boolean
    ) {
        if (isSecure) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
