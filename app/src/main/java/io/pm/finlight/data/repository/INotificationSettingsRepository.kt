package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface INotificationSettingsRepository {
    suspend fun saveDailyReportEnabled(isEnabled: Boolean)

    fun getDailyReportEnabled(): Flow<Boolean>

    suspend fun saveDailyReportTime(
        hour: Int,
        minute: Int,
    )

    fun getDailyReportTime(): Flow<Pair<Int, Int>>

    suspend fun saveWeeklySummaryEnabled(isEnabled: Boolean)

    fun getWeeklySummaryEnabled(): Flow<Boolean>

    suspend fun saveWeeklyReportTime(
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
    )

    fun getWeeklyReportTime(): Flow<Triple<Int, Int, Int>>

    suspend fun saveMonthlySummaryEnabled(isEnabled: Boolean)

    fun getMonthlySummaryEnabled(): Flow<Boolean>

    suspend fun saveMonthlyReportTime(
        dayOfMonth: Int,
        hour: Int,
        minute: Int,
    )

    fun getMonthlyReportTime(): Flow<Triple<Int, Int, Int>>

    suspend fun saveAutoCaptureNotificationEnabled(isEnabled: Boolean)

    fun getAutoCaptureNotificationEnabled(): Flow<Boolean>

    suspend fun saveUnknownTransactionPopupEnabled(isEnabled: Boolean)

    fun getUnknownTransactionPopupEnabled(): Flow<Boolean>

    suspend fun setLastMonthSummaryDismissed()

    fun hasLastMonthSummaryBeenDismissed(): Flow<Boolean>
}
