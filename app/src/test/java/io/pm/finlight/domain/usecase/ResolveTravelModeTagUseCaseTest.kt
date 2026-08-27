package io.pm.finlight.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.Tag
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveTravelModeTagUseCaseTest {
    private val travelSettingsRepository: ITravelSettingsRepository = mockk()
    private val tagRepository: ITagRepository = mockk()
    private val useCase = ResolveTravelModeTagUseCase(travelSettingsRepository, tagRepository)

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
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns settings
            coEvery { tagRepository.findOrCreateTag("Japan Trip") } returns tripTag

            val result = useCase(3000L)

            assertEquals(tripTag, result)
            coVerify(exactly = 1) { tagRepository.findOrCreateTag("Japan Trip") }
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
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns settings

            val result = useCase(3000L)

            assertNull(result)
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }

    @Test
    fun `invoke returns null when settings are null`() =
        runTest {
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns null

            val result = useCase(3000L)

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
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns settings

            val result = useCase(1000L)

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
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns settings

            val result = useCase(6000L)

            assertNull(result)
            coVerify(exactly = 0) { tagRepository.findOrCreateTag(any()) }
        }

    @Test
    fun `invoke with direct travelSettings uses passed value without querying repository`() =
        runTest {
            val tripTag = Tag(id = 10, name = "Paris Trip")
            val directSettings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Paris Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 5000L,
                    currencyCode = "EUR",
                    conversionRate = 1.1f,
                )
            coEvery { tagRepository.findOrCreateTag("Paris Trip") } returns tripTag

            val result = useCase(3000L, directSettings)

            assertEquals(tripTag, result)
            coVerify(exactly = 0) { travelSettingsRepository.getCurrentTravelModeSettings() }
            coVerify(exactly = 1) { tagRepository.findOrCreateTag("Paris Trip") }
        }

    @Test
    fun `getFinalTags returns initialTags plus tripTag when active`() =
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
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns settings
            coEvery { tagRepository.findOrCreateTag("Japan Trip") } returns tripTag

            val finalTags = useCase.getFinalTags(2500L, setOf(existingTag))

            assertEquals(2, finalTags.size)
            assertTrue(finalTags.contains(existingTag))
            assertTrue(finalTags.contains(tripTag))
        }

    @Test
    fun `getFinalTags returns initialTags untouched when inactive`() =
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
            coEvery { travelSettingsRepository.getCurrentTravelModeSettings() } returns settings

            val finalTags = useCase.getFinalTags(2500L, setOf(existingTag))

            assertEquals(1, finalTags.size)
            assertTrue(finalTags.contains(existingTag))
        }

    @Test
    fun `getFinalTags with direct travelSettings uses passed value without querying repository`() =
        runTest {
            val existingTag = Tag(id = 1, name = "Food")
            val tripTag = Tag(id = 10, name = "Paris Trip")
            val directSettings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "Paris Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 5000L,
                    currencyCode = "EUR",
                    conversionRate = 1.1f,
                )
            coEvery { tagRepository.findOrCreateTag("Paris Trip") } returns tripTag

            val finalTags = useCase.getFinalTags(2500L, setOf(existingTag), directSettings)

            assertEquals(2, finalTags.size)
            assertTrue(finalTags.contains(existingTag))
            assertTrue(finalTags.contains(tripTag))
            coVerify(exactly = 0) { travelSettingsRepository.getCurrentTravelModeSettings() }
        }

    @Test
    fun `secondary constructor with ISettingsRepository works correctly`() =
        runTest {
            val settingsRepo: ISettingsRepository = mockk()
            val tripTag = Tag(id = 10, name = "UK Trip")
            val settings =
                TravelModeSettings(
                    isEnabled = true,
                    tripName = "UK Trip",
                    tripType = TripType.INTERNATIONAL,
                    startDate = 1000L,
                    endDate = 5000L,
                    currencyCode = "GBP",
                    conversionRate = 1.2f,
                )
            coEvery { settingsRepo.getCurrentTravelModeSettings() } returns settings
            coEvery { tagRepository.findOrCreateTag("UK Trip") } returns tripTag

            val useCaseFromSettings = ResolveTravelModeTagUseCase(settingsRepo, tagRepository)
            val result = useCaseFromSettings(3000L)

            assertEquals(tripTag, result)
            coVerify(exactly = 1) { settingsRepo.getCurrentTravelModeSettings() }
        }
}
