package io.pm.finlight.data.model

import io.pm.finlight.*
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
    val smsParseTemplates: List<SmsParseTemplate> = emptyList()
)