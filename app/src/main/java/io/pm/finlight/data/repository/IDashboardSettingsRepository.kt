package io.pm.finlight

import kotlinx.coroutines.flow.Flow

interface IDashboardSettingsRepository {
    suspend fun saveDashboardLayout(
        order: List<DashboardCardType>,
        visible: Set<DashboardCardType>,
    )

    fun getDashboardCardOrder(): Flow<List<DashboardCardType>>

    fun getDashboardVisibleCards(): Flow<Set<DashboardCardType>>
}
