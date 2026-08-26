package io.pm.finlight

import io.pm.finlight.ui.theme.AppTheme
import kotlinx.coroutines.flow.Flow

interface IAppConfigRepository {
    suspend fun saveUserName(name: String)

    fun getUserName(): Flow<String>

    suspend fun saveProfilePictureUri(uriString: String?)

    fun getProfilePictureUri(): Flow<String?>

    suspend fun saveSelectedTheme(theme: AppTheme)

    fun getSelectedTheme(): Flow<AppTheme>

    suspend fun saveHomeCurrency(currencyCode: String)

    fun getHomeCurrency(): Flow<String>
}
