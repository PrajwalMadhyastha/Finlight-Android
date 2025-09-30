// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/TestApplication.kt
// REASON: NEW FILE - This is a custom Application class specifically for unit
// tests. Its onCreate method is intentionally empty to prevent Robolectric from
// trying to load the native SQLCipher library (`System.loadLibrary("sqlcipher")`),
// which would cause an UnsatisfiedLinkError on the JVM.
// =================================================================================
package io.pm.finlight

import android.app.Application

/**
 * A custom Application class used for Robolectric unit tests.
 *
 * Its primary purpose is to override the default `onCreate` method. This prevents
 * the test environment from executing the real `MainApplication.onCreate()`, which
 * attempts to load the native SQLCipher library and would cause tests to crash with
 * an `UnsatisfiedLinkError`.
 */
class TestApplication : Application() {
    override fun onCreate() {
        // Do nothing. This is the key to preventing the native library load.
        super.onCreate()
    }
}
