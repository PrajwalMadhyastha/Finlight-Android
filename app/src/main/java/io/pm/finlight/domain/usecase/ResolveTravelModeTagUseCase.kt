package io.pm.finlight.domain.usecase

import io.pm.finlight.SettingsRepository
import io.pm.finlight.Tag
import io.pm.finlight.TagRepository
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TravelSettingsRepository
import kotlinx.coroutines.flow.first

/**
 * UseCase to resolve the travel mode trip tag for a transaction based on its timestamp.
 * If travel mode is active and the transaction date falls within the trip's start/end dates,
 * the corresponding trip tag is resolved (found or created).
 */
class ResolveTravelModeTagUseCase(
    private val travelSettingsProvider: suspend () -> TravelModeSettings?,
    private val tagRepository: TagRepository,
) {
    constructor(
        travelSettingsRepository: TravelSettingsRepository,
        tagRepository: TagRepository,
    ) : this(
        travelSettingsProvider = { travelSettingsRepository.getTravelModeSettings().first() },
        tagRepository = tagRepository,
    )

    constructor(
        settingsRepository: SettingsRepository,
        tagRepository: TagRepository,
    ) : this(
        travelSettingsProvider = { settingsRepository.getTravelModeSettings().first() },
        tagRepository = tagRepository,
    )

    /**
     * Returns the trip tag if travel mode is enabled and [transactionDate] is within the trip window,
     * or null otherwise.
     */
    suspend operator fun invoke(transactionDate: Long): Tag? {
        val travelSettings = travelSettingsProvider()
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
    ): Set<Tag> {
        val tripTag = invoke(transactionDate)
        return if (tripTag != null) initialTags + tripTag else initialTags
    }
}
