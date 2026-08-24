package io.pm.finlight

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class FirstLaunchSettingsRepository(
    private val prefs: SharedPreferences,
    private val internalPrefs: SharedPreferences,
) {
    constructor(context: Context) : this(
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE),
        internalPrefs = context.getSharedPreferences(INTERNAL_PREF_NAME, Context.MODE_PRIVATE),
    )

    companion object {
        private const val PREF_NAME = "finance_app_settings"
        private const val INTERNAL_PREF_NAME = "finlight_internal_state" // Must match data_extraction_rules.xml
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding"
        private const val KEY_IS_FIRST_LAUNCH_COMPLETE = "is_first_launch_complete"
    }

    fun hasSeenOnboarding(): Boolean {
        return prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)
    }

    fun setHasSeenOnboarding(hasSeen: Boolean) {
        prefs.edit {
            putBoolean(KEY_HAS_SEEN_ONBOARDING, hasSeen)
        }
    }

    /**
     * Checks if the app has completed its first launch sequence.
     * This is a blocking call and should only be used during app startup.
     */
    fun isFirstLaunchCompleteBlocking(): Boolean {
        return internalPrefs.getBoolean(KEY_IS_FIRST_LAUNCH_COMPLETE, false)
    }

    /**
     * Sets the flag indicating that the first launch sequence is complete.
     */
    fun setFirstLaunchComplete() {
        internalPrefs.edit {
            putBoolean(KEY_IS_FIRST_LAUNCH_COMPLETE, true)
        }
    }
}
