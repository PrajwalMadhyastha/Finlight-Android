package io.pm.finlight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.pm.finlight.di.ServiceLocator
import kotlinx.coroutines.launch

/**
 * A BroadcastReceiver that listens for device boot completion and package update events.
 * Reconciles background workers via [IWorkScheduler] on startup.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val pendingResult = goAsync()
            val applicationScope = ServiceLocator.provideApplicationScope(context)
            val workScheduler = ServiceLocator.provideWorkScheduler(context)
            applicationScope.launch {
                try {
                    workScheduler.reconcileWorkOnStartup()
                } catch (t: Throwable) {
                    Log.e("BootReceiver", "Failed to reconcile work on startup", t)
                } finally {
                    pendingResult?.finish()
                }
            }
        }
    }
}
