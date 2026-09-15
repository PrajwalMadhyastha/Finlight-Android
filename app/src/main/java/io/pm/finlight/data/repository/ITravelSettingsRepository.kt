package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ITravelSettingsRepository {
    suspend fun saveTravelModeSettings(settings: TravelModeSettings?)

    /**
     * Observes the travel mode settings as a reactive [Flow].
     * Lazily evaluates and filters expired trips to `null` in-memory without mutating storage.
     */
    fun getTravelModeSettings(): Flow<TravelModeSettings?>

    /**
     * Returns a one-shot snapshot of the raw stored travel mode settings without in-memory expiration filtering.
     * Unlike [getTravelModeSettings], this returns expired settings as-is so maintenance tasks (e.g. DailyReportWorker)
     * can inspect and clear them. Callers looking for active trips must guard [TravelModeSettings.endDate].
     */
    suspend fun getCurrentTravelModeSettings(): TravelModeSettings?
}
