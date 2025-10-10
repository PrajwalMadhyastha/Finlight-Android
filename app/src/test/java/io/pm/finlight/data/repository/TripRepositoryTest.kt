package io.pm.finlight.data.repository

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.TestApplication
import io.pm.finlight.TripType
import io.pm.finlight.data.db.dao.TripDao
import io.pm.finlight.data.db.entity.Trip
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class TripRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var tripDao: TripDao

    private lateinit var repository: TripRepository

    @Before
    override fun setup() {
        super.setup()
        repository = TripRepository(tripDao)
    }

    @Test
    fun `insert calls DAO`() = runTest {
        val trip = Trip(name = "Test", startDate = 0L, endDate = 1L, tagId = 1, tripType = TripType.DOMESTIC, currencyCode = null, conversionRate = null)
        repository.insert(trip)
        verify(tripDao).insert(trip)
    }

    @Test
    fun `getAllTripsWithStats calls DAO`() {
        repository.getAllTripsWithStats()
        verify(tripDao).getAllTripsWithStats()
    }

    @Test
    fun `getTripWithStatsById calls DAO`() {
        repository.getTripWithStatsById(1)
        verify(tripDao).getTripWithStatsById(1)
    }

    @Test
    fun `getTripByTagId calls DAO`() = runTest {
        repository.getTripByTagId(1)
        verify(tripDao).getTripByTagId(1)
    }

    @Test
    fun `deleteTripById calls DAO`() = runTest {
        repository.deleteTripById(1)
        verify(tripDao).deleteTripById(1)
    }

    @Test
    fun `isTagUsedByTrip returns true when count is greater than zero`() = runTest {
        `when`(tripDao.isTagUsedByTrip(1)).thenReturn(1)
        val result = repository.isTagUsedByTrip(1)
        assertTrue(result)
    }

    @Test
    fun `isTagUsedByTrip returns false when count is zero`() = runTest {
        `when`(tripDao.isTagUsedByTrip(1)).thenReturn(0)
        val result = repository.isTagUsedByTrip(1)
        assertFalse(result)
    }
}
