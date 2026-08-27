package io.pm.finlight.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.pm.finlight.ITagRepository
import io.pm.finlight.Tag
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveTravelModeTagUseCaseTest {
    private val tagRepository: ITagRepository = mockk()
    private val useCase = ResolveTravelModeTagUseCase(tagRepository)

    @Test
    fun `invoke returns trip tag when travel mode is enabled and date is within range`() =
        runTest {
            val tripTag = Tag(id = 10, name = "Japan Trip")
            val settings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Japan Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 5000L,
                    currencyCode = "JPY",
                    conversionRate = 0.55f,
                )
            coEvery { tagRepository.findOrCreateTag("Japan Trip") } returns tripTag

            val result = useCase(3000L, settings)

            assertEquals(tripTag, result)
            coVerify(exactly = 1) { tagRepository.findOrCreateTag("Japan Trip") }
        }

    @Test
    fun `invoke returns null when travelSettings is null`() =
        runTest {
            val result = useCase(3000L, null)

            assertNull(result)
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }

    @Test
    fun `invoke returns null when travel mode is disabled`() =
        runTest {
            val settings =
                TravelModeSettings(
                    isEnabled = false,
                    tripName = "Japan Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 5000L,
                    currencyCode = "JPY",
                    conversionRate = 0.55f,
                )

            val result = useCase(3000L, settings)

            assertNull(result)
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }

    @Test
    fun `invoke returns null when transaction date is before trip start date`() =
        runTest {
            val settings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Japan Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 2000L,
                    endDate = 5000L,
                    currencyCode = "JPY",
                    conversionRate = 0.55f,
                )

            val result = useCase(1000L, settings)

            assertNull(result)
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }

    @Test
    fun `invoke returns null when transaction date is after trip end date`() =
        runTest {
            val settings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Japan Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 2000L,
                    endDate = 5000L,
                    currencyCode = "JPY",
                    conversionRate = 0.55f,
                )

            val result = useCase(6000L, settings)

            assertNull(result)
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }

    @Test
    fun `getFinalTags returns initialTags plus tripTag when travel mode is active`() =
        runTest {
            val existingTag = Tag(id = 1, name = "Food")
            val tripTag = Tag(id = 10, name = "Japan Trip")
            val settings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Japan Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 5000L,
                    currencyCode = "JPY",
                    conversionRate = 0.55f,
                )
            coEvery { tagRepository.findOrCreateTag("Japan Trip") } returns tripTag

            val finalTags = useCase.getFinalTags(2500L, setOf(existingTag), settings)

            assertEquals(2, finalTags.size)
            assertTrue(finalTags.contains(existingTag))
            assertTrue(finalTags.contains(tripTag))
            coVerify(exactly = 1) { tagRepository.findOrCreateTag("Japan Trip") }
        }

    @Test
    fun `getFinalTags returns initialTags untouched when travelSettings is null`() =
        runTest {
            val existingTag = Tag(id = 1, name = "Food")

            val finalTags = useCase.getFinalTags(2500L, setOf(existingTag), null)

            assertEquals(1, finalTags.size)
            assertTrue(finalTags.contains(existingTag))
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }

    @Test
    fun `getFinalTags returns initialTags untouched when travel mode is disabled`() =
        runTest {
            val existingTag = Tag(id = 1, name = "Food")
            val settings =
                TravelModeSettings(
                    isEnabled = false,
                    tripName = "Japan Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 5000L,
                    currencyCode = "JPY",
                    conversionRate = 0.55f,
                )

            val finalTags = useCase.getFinalTags(2500L, setOf(existingTag), settings)

            assertEquals(1, finalTags.size)
            assertTrue(finalTags.contains(existingTag))
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }
}
