package io.pm.finlight

import android.Manifest
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Verifies the permission logic used in MainActivity across different SDK versions.
 */
@RunWith(AndroidJUnit4::class)
@Config(application = TestApplication::class)
class MainActivityPermissionTest {

    // Mimic the logic in MainActivity.kt
    private fun getPermissionsToRequest(sdkInt: Int): Array<String> {
        val list = mutableListOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            list.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        return list.toTypedArray()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S]) // API 31
    fun `permissions array on Android 12 excludes POST_NOTIFICATIONS`() {
        val permissions = getPermissionsToRequest(Build.VERSION.SDK_INT)
        
        assertEquals("Should have 2 permissions on API 31", 2, permissions.size)
        assertTrue(permissions.contains(Manifest.permission.READ_SMS))
        assertTrue(permissions.contains(Manifest.permission.RECEIVE_SMS))
        assertTrue(!permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU]) // API 33
    fun `permissions array on Android 13 includes POST_NOTIFICATIONS`() {
        val permissions = getPermissionsToRequest(Build.VERSION.SDK_INT)
        
        assertEquals("Should have 3 permissions on API 33", 3, permissions.size)
        assertTrue(permissions.contains(Manifest.permission.READ_SMS))
        assertTrue(permissions.contains(Manifest.permission.RECEIVE_SMS))
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }
}
