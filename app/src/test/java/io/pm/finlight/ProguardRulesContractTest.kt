package io.pm.finlight

import io.pm.finlight.data.db.entity.MergeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProguardRulesContractTest {
    private val proguardRulesFile by lazy {
        val rootDir = File(System.getProperty("user.dir") ?: ".")
        val candidates =
            listOf(
                File(rootDir, "app/proguard-rules.pro"),
                File(rootDir, "proguard-rules.pro"),
            )
        candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Could not find proguard-rules.pro")
    }

    @Test
    fun `proguard rules contain keep rules for all Room query DTOs`() {
        val content = proguardRulesFile.readText()
        val expectedDtos =
            listOf(
                "io.pm.finlight.AccountWithBalance",
                "io.pm.finlight.TransactionWithSplits",
                "io.pm.finlight.SplitTransactionDetails",
                "io.pm.finlight.TransactionDetails",
                "io.pm.finlight.CategorySpending",
                "io.pm.finlight.DailyTotal",
                "io.pm.finlight.DailyTrend",
                "io.pm.finlight.WeeklyTrend",
                "io.pm.finlight.MonthlyTrend",
                "io.pm.finlight.MerchantSpendingSummary",
                "io.pm.finlight.PeriodTotal",
                "io.pm.finlight.FinancialSummary",
                "io.pm.finlight.BudgetWithSpending",
                "io.pm.finlight.GoalWithAccountName",
                "io.pm.finlight.data.db.dao.TripWithStats",
                "io.pm.finlight.data.db.dao.OriginalDescriptionCount",
                "io.pm.finlight.data.model.SpendingAnalysisItem",
                "io.pm.finlight.data.model.MerchantPrediction",
            )

        for (dto in expectedDtos) {
            assertTrue(
                "Expected keep rule for Room query DTO: $dto",
                content.contains(dto),
            )
        }

        // Verify domain-only models are not classified as Room DTOs
        assertFalse(
            "MergedTransactionItem is not a Room DTO and should not be in Room DTO rules",
            content.contains("-keepclassmembers class io.pm.finlight.data.model.MergedTransactionItem"),
        )
        assertFalse(
            "MonthlySummaryItem is not a Room DTO and should not be in Room DTO rules",
            content.contains("-keepclassmembers class io.pm.finlight.MonthlySummaryItem"),
        )
    }

    @Test
    fun `proguard rules preserve global enums without stripping or obfuscation`() {
        val content = proguardRulesFile.readText()
        assertTrue(
            "Global enum preservation rule for io.pm.finlight.** must be present",
            content.contains("-keep enum io.pm.finlight.**"),
        )

        // Verify one-off fragile enum rules are removed
        assertFalse(
            "Fragile one-off rule for TripType should be superseded by global enum rule",
            content.contains("-keepclassmembers enum io.pm.finlight.TripType"),
        )
    }

    @Test
    fun `proguard rules preserve kotlinx serialization companions and serializers`() {
        val content = proguardRulesFile.readText()
        assertTrue("Companion keep rule must be present", content.contains("*** Companion;"))
        assertTrue("serializer method keep rule must be present", content.contains("*** serializer(...);"))
        assertTrue("Generated serializer class keep rule must be present", content.contains("-keep class *$\$serializer"))
        assertFalse(
            "Blanket kotlinx serialization rule must be absent to allow library optimization",
            content.contains("-keep class kotlinx.serialization.**"),
        )
    }

    @Test
    fun `proguard rules preserve screen navigation and serialization models with full class keep`() {
        val content = proguardRulesFile.readText()
        assertTrue(
            "Must preserve PotentialTransaction with full class keep",
            content.contains("-keep class io.pm.finlight.PotentialTransaction"),
        )
        assertTrue(
            "Must preserve PotentialAccount with full class keep",
            content.contains("-keep class io.pm.finlight.PotentialAccount"),
        )
        assertTrue(
            "Must preserve TravelModeSettings with full class keep",
            content.contains("-keep class io.pm.finlight.TravelModeSettings"),
        )
        assertFalse(
            "PotentialTransaction should not use fragile keepclassmembers",
            content.contains("-keepclassmembers class io.pm.finlight.PotentialTransaction"),
        )
        assertFalse(
            "PotentialAccount should not use fragile keepclassmembers",
            content.contains("-keepclassmembers class io.pm.finlight.PotentialAccount"),
        )
    }

    @Test
    fun `proguard rules preserve correct package for TravelModeSettings`() {
        val content = proguardRulesFile.readText()
        assertTrue(
            "Must preserve io.pm.finlight.TravelModeSettings",
            content.contains("-keep class io.pm.finlight.TravelModeSettings"),
        )
        assertFalse(
            "Must not reference wrong package io.pm.finlight.data.repository.TravelModeSettings",
            content.contains("io.pm.finlight.data.repository.TravelModeSettings"),
        )
    }

    @Test
    fun `proguard rules do not contain blanket project keep rule`() {
        val content = proguardRulesFile.readText()
        assertFalse(
            "Blanket keep rule must be absent for Google Play R8 compliance",
            content.contains("-keep class io.pm.finlight.** { *; }"),
        )
    }

    @Test
    fun `mergeType fromString resolves correctly`() {
        assertEquals(MergeType.AUTO, MergeType.fromString("AUTO"))
        assertEquals(MergeType.AUTO, MergeType.fromString("auto"))
        assertEquals(MergeType.MANUAL, MergeType.fromString("MANUAL"))
        assertEquals(MergeType.MANUAL, MergeType.fromString("manual"))
        assertEquals(MergeType.AUTO, MergeType.fromString("UNKNOWN"))
    }

    @Test
    fun `dashboardCardType enum constants maintain expected names`() {
        val cardTypes =
            listOf(
                "HERO_BUDGET",
                "QUICK_ACTIONS",
                "RECENT_TRANSACTIONS",
                "SPENDING_CONSISTENCY",
                "BUDGET_WATCH",
                "SAVINGS_GOALS",
                "UPCOMING_PAYMENTS",
                "ACCOUNTS_CAROUSEL",
                "RECURRING_SUGGESTIONS",
                "FINANCIAL_SIMULATORS",
            )
        for (name in cardTypes) {
            val enumValue = DashboardCardType.valueOf(name)
            assertEquals(name, enumValue.name)
        }
    }
}
