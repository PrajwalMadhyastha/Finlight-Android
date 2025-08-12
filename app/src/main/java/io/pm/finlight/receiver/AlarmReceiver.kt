// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/receiver/AlarmReceiver.kt
// REASON: NEW FILE - This BroadcastReceiver is the target for the new, precise
// alarms set by AlarmManager. When an alarm fires, its onReceive method
// is triggered. It immediately enqueues the appropriate, non-delayed WorkManager
// task (e.g., DailyReportWorker) and then calls back to the ReminderManager
// to schedule the *next* alarm, ensuring the chain of punctual notifications
// continues reliably.
// =================================================================================
package io.pm.finlight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.pm.finlight.DailyReportWorker
import io.pm.finlight.MonthlySummaryWorker
import io.pm.finlight.WeeklySummaryWorker
import io.pm.finlight.utils.ReminderManager

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_WORK_TAG = "work_tag"
        const val DAILY_REPORT_WORK_TAG = "daily_expense_report_work"
        const val WEEKLY_SUMMARY_WORK_TAG = "weekly_summary_work"
        const val MONTHLY_SUMMARY_WORK_TAG = "monthly_summary_work"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val workTag = intent.getStringExtra(EXTRA_WORK_TAG)
        Log.d("AlarmReceiver", "Alarm received for work tag: $workTag")

        if (workTag == null) {
            Log.e("AlarmReceiver", "Received alarm with no work tag.")
            return
        }

        // Immediately start the appropriate worker with no initial delay
        val workRequest = when (workTag) {
            DAILY_REPORT_WORK_TAG -> OneTimeWorkRequestBuilder<DailyReportWorker>().build()
            WEEKLY_SUMMARY_WORK_TAG -> OneTimeWorkRequestBuilder<WeeklySummaryWorker>().build()
            MONTHLY_SUMMARY_WORK_TAG -> OneTimeWorkRequestBuilder<MonthlySummaryWorker>().build()
            else -> {
                Log.e("AlarmReceiver", "Unknown work tag received: $workTag")
                return
            }
        }
        WorkManager.getInstance(context).enqueue(workRequest)

        // Schedule the next alarm in the chain
        when (workTag) {
            DAILY_REPORT_WORK_TAG -> ReminderManager.scheduleDailyReport(context)
            WEEKLY_SUMMARY_WORK_TAG -> ReminderManager.scheduleWeeklySummary(context)
            MONTHLY_SUMMARY_WORK_TAG -> ReminderManager.scheduleMonthlySummary(context)
        }
    }
}
