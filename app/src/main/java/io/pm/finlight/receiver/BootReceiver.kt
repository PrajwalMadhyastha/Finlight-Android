// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/receiver/BootReceiver.kt
// REASON: FIX - The receiver's logic has been completely replaced with a single
// call to the new, centralized `ReminderManager.rescheduleAllWork` function.
// This ensures that ALL necessary background tasks (not just some) are
// correctly re-scheduled after a device reboot, fixing the core bug.
// =================================================================================
package io.pm.finlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.pm.finlight.utils.ReminderManager

/**
 * A BroadcastReceiver that listens for the device boot completion event.
 * Its purpose is to re-schedule all necessary background workers (like daily,
 * weekly, and monthly reports) to ensure they persist across device reboots.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Device boot completed. Delegating to ReminderManager to reschedule all work.")
            // --- UPDATED: Call the centralized rescheduling function ---
            ReminderManager.rescheduleAllWork(context)
        }
    }
}
