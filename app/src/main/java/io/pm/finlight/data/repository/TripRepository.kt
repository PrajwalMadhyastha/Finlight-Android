// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TripRepository.kt
// REASON: FEATURE - Added the `isTagUsedByTrip` function. This exposes the new
// DAO query to the ViewModel layer, allowing it to check for trip associations
// before permitting a tag deletion.
// =================================================================================
package io.pm.finlight.data.repository

import io.pm.finlight.data.db.dao.TripDao
import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.db.entity.Trip
import kotlinx.coroutines.flow.Flow

class TripRepository(private val tripDao: TripDao) {

    suspend fun insert(trip: Trip): Long {
        return tripDao.insert(trip)
    }

    fun getAllTripsWithStats(): Flow<List<TripWithStats>> {
        return tripDao.getAllTripsWithStats()
    }

    fun getTripWithStatsById(tripId: Int): Flow<TripWithStats?> {
        return tripDao.getTripWithStatsById(tripId)
    }

    suspend fun getTripByTagId(tagId: Int): Trip? {
        return tripDao.getTripByTagId(tagId)
    }

    suspend fun deleteTripById(tripId: Int) {
        tripDao.deleteTripById(tripId)
    }

    // --- NEW: Check if a tag is associated with any trip ---
    suspend fun isTagUsedByTrip(tagId: Int): Boolean {
        return tripDao.isTagUsedByTrip(tagId) > 0
    }
}
