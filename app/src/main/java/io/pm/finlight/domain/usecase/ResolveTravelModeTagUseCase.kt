package io.pm.finlight.domain.usecase

import io.pm.finlight.ITagRepository
import io.pm.finlight.Tag
import io.pm.finlight.TravelModeSettings

/**
 * UseCase to resolve the travel mode trip tag for a transaction based on its timestamp.
 * If travel mode is active and the transaction date falls within the trip's start/end dates,
 * the corresponding trip tag is resolved (found or created).
 */
class ResolveTravelModeTagUseCase(
    private val tagRepository: ITagRepository,
) {
    /**
     * Returns the trip tag if travel mode is enabled in [travelSettings] and [transactionDate] is within the trip window,
     * or null otherwise.
     */
    suspend operator fun invoke(
        transactionDate: Long,
        travelSettings: TravelModeSettings?,
    ): Tag? {
        if (travelSettings?.isEnabled == true &&
            transactionDate >= travelSettings.startDate &&
            transactionDate <= travelSettings.endDate
        ) {
            return tagRepository.findOrCreateTag(travelSettings.tripName)
        }
        return null
    }

    /**
     * Returns the combined set of tags including the trip tag if travel mode is active.
     */
    suspend fun getFinalTags(
        transactionDate: Long,
        initialTags: Set<Tag>,
        travelSettings: TravelModeSettings?,
    ): Set<Tag> {
        val tripTag = invoke(transactionDate, travelSettings)
        return if (tripTag != null) initialTags + tripTag else initialTags
    }
}

