package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface ITravelSettingsRepository {
    suspend fun saveTravelModeSettings(settings: TravelModeSettings?)

    fun getTravelModeSettings(): Flow<TravelModeSettings?>

    suspend fun getCurrentTravelModeSettings(): TravelModeSettings?
}
