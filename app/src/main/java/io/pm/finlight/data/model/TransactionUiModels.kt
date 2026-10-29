package io.pm.finlight.data.model

import android.net.Uri
import io.pm.finlight.Account
import io.pm.finlight.Category
import io.pm.finlight.Tag
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.TravelModeSettings
import io.pm.finlight.TripType
import io.pm.finlight.ui.viewmodel.AnalysisTransactionType

data class TransactionFilterState(
    val keyword: String = "",
    val account: Account? = null,
    val category: Category? = null,
    val transactionType: AnalysisTransactionType = AnalysisTransactionType.EXPENSE,
)

data class ManualTransactionData(
    val description: String,
    val amountStr: String,
    val accountId: Int,
    val notes: String?,
    val date: Long,
    val transactionType: TransactionType,
    val imageUris: List<Uri>,
    val tags: Set<Tag>,
) {
    fun toTransaction(
        categoryId: Int?,
        travelSettings: TravelModeSettings?,
    ): Transaction {
        val enteredAmount = amountStr.toDoubleOrNull() ?: 0.0
        val isInternationalTravel =
            travelSettings?.isEnabled == true &&
                travelSettings.tripType == TripType.INTERNATIONAL &&
                date >= travelSettings.startDate &&
                date <= travelSettings.endDate

        return Transaction(
            description = description,
            originalDescription = description,
            categoryId = categoryId,
            amount = if (isInternationalTravel) enteredAmount * (travelSettings?.conversionRate ?: 1f) else enteredAmount,
            date = date,
            accountId = accountId,
            notes = notes,
            transactionType = transactionType,
            isExcluded = false,
            sourceSmsId = null,
            sourceSmsHash = null,
            source = "Manual Entry",
            originalAmount = enteredAmount,
            currencyCode = if (isInternationalTravel) travelSettings?.currencyCode else null,
            conversionRate = if (isInternationalTravel) travelSettings?.conversionRate?.toDouble() else null,
        )
    }
}

fun AnalysisTransactionType.toTransactionType(): TransactionType? =
    when (this) {
        AnalysisTransactionType.EXPENSE -> TransactionType.EXPENSE
        AnalysisTransactionType.INCOME -> TransactionType.INCOME
        AnalysisTransactionType.ALL -> null
    }

typealias RetroUpdateSheetState = io.pm.finlight.ui.viewmodel.delegate.RetroUpdateSheetState
typealias CanonicalVariant = io.pm.finlight.ui.viewmodel.delegate.CanonicalVariant
typealias CanonicalNudgeSheetState = io.pm.finlight.ui.viewmodel.delegate.CanonicalNudgeSheetState
