package io.pm.finlight.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

const val FINANCE_SETTINGS_DATASTORE_NAME = "finance_app_settings"
const val INTERNAL_SETTINGS_DATASTORE_NAME = "finlight_internal_state"

val Context.financeSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = FINANCE_SETTINGS_DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, FINANCE_SETTINGS_DATASTORE_NAME))
    },
)

val Context.internalSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = INTERNAL_SETTINGS_DATASTORE_NAME,
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, INTERNAL_SETTINGS_DATASTORE_NAME))
    },
)
