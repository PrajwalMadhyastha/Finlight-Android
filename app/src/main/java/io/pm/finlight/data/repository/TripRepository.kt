// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/repository/TripRepository.kt
// REASON: FEATURE - Added `getTripWithStatsById` to expose the new DAO method
// to the ViewModel layer, which is required for the Trip Detail screen.
// =================================================================================
package io.pm.finlight.data.repository

import io.pm.finlight.Trip
import io.pm.finlight.data.db.dao.TripDao
import io.pm.finlight.data.db.dao.TripWithStats
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
}