package io.pm.finlight.domain.usecase

import io.pm.finlight.ISettingsRepository
import io.pm.finlight.ITagRepository
import io.pm.finlight.ITravelSettingsRepository
import io.pm.finlight.Tag
import io.pm.finlight.TravelModeSettings

/**
 * UseCase to resolve the travel mode trip tag for a transaction based on its timestamp.
 * If travel mode is active and the transaction date falls within the trip's start/end dates,
 * the corresponding trip tag is resolved (found or created).
 */
class ResolveTravelModeTagUseCase(
    private val travelSettingsProvider: suspend () -> TravelModeSettings?,
    private val tagRepository: ITagRepository,
) {
    constructor(
        travelSettingsRepository: ITravelSettingsRepository,
        tagRepository: ITagRepository,
    ) : this(
        travelSettingsProvider = { travelSettingsRepository.getCurrentTravelModeSettings() },
        tagRepository = tagRepository,
    )

    constructor(
        settingsRepository: ISettingsRepository,
        tagRepository: ITagRepository,
    ) : this(
        travelSettingsProvider = { settingsRepository.getCurrentTravelModeSettings() },
        tagRepository = tagRepository,
    )

    /**
     * Returns the trip tag if travel mode is enabled and [transactionDate] is within the trip window,
     * or null otherwise.
     * If [travelSettings] is provided, it is used directly without querying the provider.
     */
    suspend operator fun invoke(
        transactionDate: Long,
        travelSettings: TravelModeSettings? = null,
    ): Tag? {
        val settings = travelSettings ?: travelSettingsProvider()
        if (settings?.isEnabled == true &&
            transactionDate >= settings.startDate &&
            transactionDate <= settings.endDate
        ) {
            return tagRepository.findOrCreateTag(settings.tripName)
        }
        return null
    }

    /**
     * Returns the combined set of tags including the trip tag if travel mode is active.
     * If [travelSettings] is provided, it is used directly without querying the provider.
     */
    suspend fun getFinalTags(
        transactionDate: Long,
        initialTags: Set<Tag>,
        travelSettings: TravelModeSettings? = null,
    ): Set<Tag> {
        val tripTag = invoke(transactionDate, travelSettings)
        return if (tripTag != null) initialTags + tripTag else initialTags
    }
}
