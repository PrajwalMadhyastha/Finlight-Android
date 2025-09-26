// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/model/AppDataBackup.kt
// REASON: FEATURE (Backup Phase 2) - The data class has been updated to include
// lists for all remaining user-generated content: Tags, Goals, Trips,
// AccountAliases, and the Transaction-Tag cross-references.
// =================================================================================
package io.pm.finlight.data.model

import io.pm.finlight.*
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.data.db.entity.Trip
import kotlinx.serialization.Serializable

/**
 * A top-level container for all application data to be exported.
 * This class is designed to be easily converted to a single JSON object.
 */
@Serializable
data class AppDataBackup(
    val transactions: List<Transaction>,
    val accounts: List<Account>,
    val categories: List<Category>,
    val budgets: List<Budget>,
    val merchantMappings: List<MerchantMapping>,
    val splitTransactions: List<SplitTransaction> = emptyList(),
    // --- Phase 1: Core Parsing Intelligence ---
    val customSmsRules: List<CustomSmsRule> = emptyList(),
    val merchantRenameRules: List<MerchantRenameRule> = emptyList(),
    val merchantCategoryMappings: List<MerchantCategoryMapping> = emptyList(),
    val ignoreRules: List<IgnoreRule> = emptyList(),
    val smsParseTemplates: List<SmsParseTemplate> = emptyList(),
    // --- Phase 2: Remaining User & App Intelligence ---
    val tags: List<Tag> = emptyList(),
    val transactionTagCrossRefs: List<TransactionTagCrossRef> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val trips: List<Trip> = emptyList(),
    val accountAliases: List<AccountAlias> = emptyList()
)