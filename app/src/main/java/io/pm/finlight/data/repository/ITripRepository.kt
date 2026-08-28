package io.pm.finlight.data.repository

import io.pm.finlight.data.db.dao.TripWithStats
import io.pm.finlight.data.db.entity.Trip
import kotlinx.coroutines.flow.Flow

interface ITripRepository {
    suspend fun insert(trip: Trip): Long

    fun getAllTripsWithStats(): Flow<List<TripWithStats>>

    fun getTripWithStatsById(tripId: Int): Flow<TripWithStats?>

    suspend fun getTripByTagId(tagId: Int): Trip?

    suspend fun deleteTripById(tripId: Int)

    suspend fun isTagUsedByTrip(tagId: Int): Boolean
}
