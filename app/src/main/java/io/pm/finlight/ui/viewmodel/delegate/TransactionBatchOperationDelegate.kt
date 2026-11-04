package io.pm.finlight.ui.viewmodel.delegate

import android.content.Context
import android.util.Log
import io.pm.finlight.IMerchantCategoryMappingRepository
import io.pm.finlight.IMerchantRenameRuleRepository
import io.pm.finlight.ISmsParseTemplateRepository
import io.pm.finlight.ISmsRepository
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.SmsParseTemplate
import io.pm.finlight.SmsParser
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.core.utils.StringSimilarity
import io.pm.finlight.ui.components.ShareableField
import io.pm.finlight.utils.DefaultDispatcherProvider
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.ShareImageGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "TransactionBatchDelegate"

data class RetroUpdateSheetState(
    val originalDescription: String,
    val newDescription: String? = null,
    val newCategoryId: Int? = null,
    val similarTransactions: List<Transaction> = emptyList(),
    val selectedIds: Set<Int> = emptySet(),
    val isLoading: Boolean = true,
    val totalMatchingCount: Int = 0,
    val updateFutureTransactions: Boolean = true,
)

data class CanonicalVariant(
    val rawName: String,
    val transactionCount: Int,
    val transactionIds: List<Int>,
)

data class CanonicalNudgeSheetState(
    val canonicalName: String,
    val variants: List<CanonicalVariant>,
    val selectedRawNames: Set<String> = variants.map { it.rawName }.toSet(),
)

class TransactionBatchOperationDelegate(
    private val coroutineScope: CoroutineScope,
    private val transactionRepository: ITransactionRepository,
    private val merchantRenameRuleRepository: IMerchantRenameRuleRepository,
    private val merchantCategoryMappingRepository: IMerchantCategoryMappingRepository,
    private val smsRepository: ISmsRepository,
    private val smsParseTemplateRepository: ISmsParseTemplateRepository,
    private val dispatcherProvider: DispatcherProvider = DefaultDispatcherProvider(),
) {
    // -------------------------------------------------------------------------
    // Multi-Select Batch Operations State
    // -------------------------------------------------------------------------
    private val _isSelectionModeActive = MutableStateFlow(false)
    val isSelectionModeActive: StateFlow<Boolean> = _isSelectionModeActive.asStateFlow()

    private val _selectedTransactionIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedTransactionIds: StateFlow<Set<Int>> = _selectedTransactionIds.asStateFlow()

    private val _showDeleteConfirmation = MutableStateFlow(false)
    val showDeleteConfirmation: StateFlow<Boolean> = _showDeleteConfirmation.asStateFlow()

    private val _showShareSheet = MutableStateFlow(false)
    val showShareSheet: StateFlow<Boolean> = _showShareSheet.asStateFlow()

    private val _shareableFields =
        MutableStateFlow(
            setOf(
                ShareableField.Date,
                ShareableField.Description,
                ShareableField.Amount,
                ShareableField.Category,
                ShareableField.Tags,
            ),
        )
    val shareableFields: StateFlow<Set<ShareableField>> = _shareableFields.asStateFlow()

    // -------------------------------------------------------------------------
    // Retro / Smart Update Batch State
    // -------------------------------------------------------------------------
    private val _retroUpdateSheetState = MutableStateFlow<RetroUpdateSheetState?>(null)
    val retroUpdateSheetState: StateFlow<RetroUpdateSheetState?> = _retroUpdateSheetState.asStateFlow()

    private val _canonicalNudgeState = MutableStateFlow<CanonicalNudgeSheetState?>(null)
    val canonicalNudgeState: StateFlow<CanonicalNudgeSheetState?> = _canonicalNudgeState.asStateFlow()

    private val _navigateBackEvent = Channel<Unit>(Channel.CONFLATED)
    val navigateBackEvent = _navigateBackEvent.receiveAsFlow()

    // -------------------------------------------------------------------------
    // Multi-Select Operations
    // -------------------------------------------------------------------------
    fun enterSelectionMode(initialTransactionId: Int) {
        _isSelectionModeActive.value = true
        _selectedTransactionIds.value = setOf(initialTransactionId)
    }

    fun toggleTransactionSelection(transactionId: Int) {
        _selectedTransactionIds.update { currentSelection ->
            if (transactionId in currentSelection) {
                currentSelection - transactionId
            } else {
                currentSelection + transactionId
            }
        }
    }

    fun clearSelectionMode() {
        _isSelectionModeActive.value = false
        _selectedTransactionIds.value = emptySet()
    }

    fun onDeleteSelectionClick() {
        _showDeleteConfirmation.value = true
    }

    fun onCancelDeleteSelection() {
        _showDeleteConfirmation.value = false
    }

    fun onConfirmDeleteSelection(onEvent: suspend (String) -> Unit) {
        coroutineScope.launch {
            try {
                val idsToDelete = _selectedTransactionIds.value.toList()
                if (idsToDelete.isNotEmpty()) {
                    val hashes = transactionRepository.getSmsHashesByIds(idsToDelete)
                    hashes.forEach { hash ->
                        transactionRepository.insertDeletedSmsHash(hash)
                    }
                    transactionRepository.deleteByIds(idsToDelete)
                }
                _showDeleteConfirmation.value = false
                clearSelectionMode()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete selected transactions", e)
                onEvent("Failed to delete transactions. Please try again.")
            }
        }
    }

    fun onShareClick() {
        _showShareSheet.value = true
    }

    fun onShareSheetDismiss() {
        _showShareSheet.value = false
    }

    fun onShareableFieldToggled(field: ShareableField) {
        _shareableFields.update { currentFields ->
            if (field in currentFields) {
                currentFields - field
            } else {
                currentFields + field
            }
        }
    }

    fun generateAndShareSnapshot(
        context: Context,
        allTransactions: List<TransactionDetails>,
    ) {
        coroutineScope.launch {
            val selectedIds = _selectedTransactionIds.value
            if (selectedIds.isEmpty()) return@launch

            val selectedTransactionsDetails = allTransactions.filter { it.transaction.id in selectedIds }

            if (selectedTransactionsDetails.isNotEmpty()) {
                val transactionsWithData =
                    withContext(dispatcherProvider.io) {
                        selectedTransactionsDetails.map { details ->
                            val tags = transactionRepository.getTagsForTransactionSimple(details.transaction.id)
                            ShareImageGenerator.TransactionSnapshotData(details = details, tags = tags)
                        }
                    }

                ShareImageGenerator.shareTransactionsAsImage(
                    context = context,
                    transactionsWithData = transactionsWithData,
                    fields = _shareableFields.value,
                )
            }
            onShareSheetDismiss()
            clearSelectionMode()
        }
    }

    // -------------------------------------------------------------------------
    // Retro / Smart Update Batch Operations
    // -------------------------------------------------------------------------
    fun onAttemptToLeaveScreen(
        initial: Transaction?,
        onNavigationAllowed: () -> Unit,
    ) {
        coroutineScope.launch {
            if (initial == null) {
                onNavigationAllowed()
                return@launch
            }
            val current =
                transactionRepository.getTransactionById(initial.id).firstOrNull() ?: run {
                    onNavigationAllowed()
                    return@launch
                }

            val descriptionChanged = !initial.description.equals(current.description, ignoreCase = true)
            val categoryChanged = initial.categoryId != current.categoryId

            if (!descriptionChanged && !categoryChanged) {
                onNavigationAllowed()
                return@launch
            }

            val originalDescriptionForSearch = initial.originalDescription ?: initial.description
            val similar = transactionRepository.findSimilarTransactions(originalDescriptionForSearch, initial.id)

            val actionableSimilar =
                similar.filter { txn ->
                    val needsDescUpdate = descriptionChanged && !txn.description.equals(current.description, ignoreCase = true)
                    val needsCatUpdate = categoryChanged && txn.categoryId != current.categoryId
                    needsDescUpdate || needsCatUpdate
                }

            if (actionableSimilar.isNotEmpty()) {
                _retroUpdateSheetState.value =
                    RetroUpdateSheetState(
                        originalDescription = originalDescriptionForSearch,
                        newDescription = current.description.takeIf { descriptionChanged },
                        newCategoryId = current.categoryId.takeIf { categoryChanged },
                        similarTransactions = actionableSimilar,
                        selectedIds = actionableSimilar.map { it.id }.toSet(),
                        isLoading = false,
                        totalMatchingCount = actionableSimilar.size + 1,
                        updateFutureTransactions = true,
                    )
            } else {
                _retroUpdateSheetState.value =
                    RetroUpdateSheetState(
                        originalDescription = originalDescriptionForSearch,
                        newDescription = current.description.takeIf { descriptionChanged },
                        newCategoryId = current.categoryId.takeIf { categoryChanged },
                        similarTransactions = emptyList(),
                        selectedIds = emptySet(),
                        isLoading = false,
                        totalMatchingCount = 1,
                        updateFutureTransactions = true,
                    )
            }
        }
    }

    fun dismissRetroUpdateSheet() {
        _retroUpdateSheetState.value = null
    }

    fun toggleRetroUpdateSelection(id: Int) {
        _retroUpdateSheetState.update { currentState ->
            currentState?.copy(
                selectedIds =
                    currentState.selectedIds.toMutableSet().apply {
                        if (id in this) remove(id) else add(id)
                    },
            )
        }
    }

    fun toggleRetroUpdateSelectAll() {
        _retroUpdateSheetState.update { currentState ->
            currentState?.let {
                if (it.selectedIds.size == it.similarTransactions.size) {
                    it.copy(selectedIds = emptySet())
                } else {
                    it.copy(selectedIds = it.similarTransactions.map { t -> t.id }.toSet())
                }
            }
        }
    }

    fun toggleUpdateFutureTransactions() {
        _retroUpdateSheetState.update { it?.copy(updateFutureTransactions = !it.updateFutureTransactions) }
    }

    fun performBatchUpdate(
        initialTxn: Transaction?,
        onEvent: suspend (String) -> Unit,
    ) {
        coroutineScope.launch {
            val state = _retroUpdateSheetState.value ?: return@launch
            val idsToUpdate = state.selectedIds.toList()

            if (idsToUpdate.isEmpty() && !state.updateFutureTransactions) {
                _retroUpdateSheetState.value = null
                _navigateBackEvent.send(Unit)
                return@launch
            }

            try {
                var ruleSaved = false
                var savedCanonical: String? = null
                var savedOriginal: String? = null

                if (state.updateFutureTransactions) {
                    var effectiveCategoryId: Int? = state.newCategoryId
                    if (effectiveCategoryId == null && state.originalDescription.isNotBlank()) {
                        effectiveCategoryId = merchantCategoryMappingRepository.getCategoryIdForMerchant(state.originalDescription)
                    }

                    state.newDescription?.let { newDesc ->
                        val originalDesc = state.originalDescription
                        if (originalDesc.isNotBlank() && !originalDesc.equals(newDesc, ignoreCase = true)) {
                            val rule = MerchantRenameRule(originalName = originalDesc, newName = newDesc)
                            merchantRenameRuleRepository.insert(rule)
                            Log.d(TAG, "Smart Update: saved rename rule '$originalDesc' -> '$newDesc'.")
                            ruleSaved = true
                            savedCanonical = newDesc
                            savedOriginal = originalDesc

                            if (effectiveCategoryId != null) {
                                merchantCategoryMappingRepository.insert(
                                    MerchantCategoryMapping(parsedName = newDesc, categoryId = effectiveCategoryId),
                                )
                                Log.d(TAG, "Smart Update: propagated category mapping '$newDesc' -> $effectiveCategoryId.")
                            }

                            if (initialTxn != null && initialTxn.sourceSmsId != null) {
                                val originalSms = smsRepository.getSmsDetailsById(initialTxn.sourceSmsId)
                                if (originalSms != null) {
                                    val merchantIndex = originalSms.body.indexOf(originalDesc, ignoreCase = true)
                                    if (merchantIndex != -1) {
                                        createAndStoreTemplate(
                                            smsBody = originalSms.body,
                                            transaction = initialTxn,
                                            correctedMerchant = newDesc,
                                            originalMerchantStartIndex = merchantIndex,
                                            originalMerchantEndIndex = merchantIndex + originalDesc.length,
                                        )
                                    }
                                }
                            }
                        } else if (originalDesc.isNotBlank() && originalDesc.equals(newDesc, ignoreCase = true)) {
                            merchantRenameRuleRepository.deleteByOriginalName(originalDesc)
                        }
                    }
                    state.newCategoryId?.let { newCategoryId ->
                        val originalDesc = state.originalDescription
                        if (originalDesc.isNotBlank()) {
                            merchantCategoryMappingRepository.insert(
                                MerchantCategoryMapping(parsedName = originalDesc, categoryId = newCategoryId),
                            )
                            Log.d(TAG, "Smart Update: upserted category mapping '$originalDesc' -> $newCategoryId.")
                        }
                    }
                } else {
                    Log.d(TAG, "Smart Update: user opted out of future rule — skipping rule save.")
                }

                if (idsToUpdate.isNotEmpty()) {
                    state.newDescription?.let { transactionRepository.updateDescriptionForIds(idsToUpdate, it) }
                    state.newCategoryId?.let { transactionRepository.updateCategoryForIds(idsToUpdate, it) }
                    onEvent("Updated ${idsToUpdate.size} transaction(s).")
                }

                if (ruleSaved && savedCanonical != null && savedOriginal != null) {
                    val foundVariants = findCrossAccountVariants(savedCanonical, savedOriginal)
                    if (!foundVariants) _navigateBackEvent.send(Unit)
                } else {
                    _navigateBackEvent.send(Unit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to perform batch update", e)
                onEvent("Batch update failed. Please try again.")
                _navigateBackEvent.send(Unit)
            } finally {
                _retroUpdateSheetState.value = null
            }
        }
    }

    fun onRetroSheetSkipped() {
        coroutineScope.launch {
            _retroUpdateSheetState.value = null
            _navigateBackEvent.send(Unit)
        }
    }

    private suspend fun findCrossAccountVariants(
        canonicalName: String,
        savedOriginalName: String,
    ): Boolean {
        if (canonicalName.trim().length < 5) return false
        return try {
            val existingRules =
                merchantRenameRuleRepository
                    .getAliasesAsMap()
                    .first()
                    .keys
                    .map { it.lowercase() }
                    .toSet()

            val allOriginalDescs = transactionRepository.getDistinctOriginalDescriptions()
            val variants = mutableListOf<CanonicalVariant>()

            for (rawDesc in allOriginalDescs) {
                if (rawDesc.equals(savedOriginalName, ignoreCase = true)) continue
                if (rawDesc.lowercase() in existingRules) continue
                if (rawDesc.equals(canonicalName, ignoreCase = true)) continue

                if (StringSimilarity.isCanonicalSubset(canonicalName, rawDesc)) {
                    val ids = transactionRepository.getTransactionIdsByOriginalDescription(rawDesc)
                    if (ids.isNotEmpty()) {
                        variants.add(CanonicalVariant(rawDesc, ids.size, ids))
                    }
                }
            }

            if (variants.isNotEmpty()) {
                _canonicalNudgeState.value = CanonicalNudgeSheetState(canonicalName, variants)
                Log.d(TAG, "Canonical nudge: found ${variants.size} cross-account variant(s) for '$canonicalName'.")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning for cross-account variants", e)
            false
        }
    }

    fun toggleCanonicalVariant(rawName: String) {
        _canonicalNudgeState.update { state ->
            state?.copy(
                selectedRawNames =
                    state.selectedRawNames.toMutableSet().apply {
                        if (rawName in this) remove(rawName) else add(rawName)
                    },
            )
        }
    }

    fun confirmCanonicalNudge(onEvent: suspend (String) -> Unit) {
        coroutineScope.launch {
            val state =
                _canonicalNudgeState.value ?: run {
                    _navigateBackEvent.send(Unit)
                    return@launch
                }
            try {
                val selected = state.variants.filter { it.rawName in state.selectedRawNames }
                for (variant in selected) {
                    merchantRenameRuleRepository.insert(
                        MerchantRenameRule(
                            originalName = variant.rawName,
                            newName = state.canonicalName,
                        ),
                    )
                    transactionRepository.updateDescriptionForIds(variant.transactionIds, state.canonicalName)
                    Log.d(
                        TAG,
                        "Canonical nudge: applied '${state.canonicalName}' to ${variant.transactionIds.size} txn(s) with raw name '${variant.rawName}'.",
                    )
                }
                if (selected.isNotEmpty()) {
                    onEvent("Applied '${state.canonicalName}' to ${selected.sumOf { it.transactionCount }} more transaction(s).")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply canonical nudge", e)
                onEvent("Failed to apply rename to similar transactions.")
            } finally {
                _canonicalNudgeState.value = null
                _navigateBackEvent.send(Unit)
            }
        }
    }

    fun dismissCanonicalNudge() {
        coroutineScope.launch {
            _canonicalNudgeState.value = null
            _navigateBackEvent.send(Unit)
        }
    }

    private suspend fun createAndStoreTemplate(
        smsBody: String,
        transaction: Transaction,
        correctedMerchant: String,
        originalMerchantStartIndex: Int,
        originalMerchantEndIndex: Int,
    ) {
        val amountToFind = transaction.originalAmount ?: transaction.amount

        val amountRegex = "([\\d,]+\\.?\\d*)".toRegex()
        val allNumericValuesInSms = amountRegex.findAll(smsBody).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }.toList()
        val matchingAmountValue = allNumericValuesInSms.find { it == amountToFind }

        if (matchingAmountValue == null) {
            Log.w(TAG, "Could not find the exact amount '$amountToFind' in the SMS body to create a template.")
            return
        }

        val amountStr =
            amountRegex.findAll(smsBody).find {
                it.value.replace(",", "").toDoubleOrNull() == matchingAmountValue
            }?.value ?: return

        val amountIndex = smsBody.indexOf(amountStr)

        if (amountIndex == -1) {
            Log.w(TAG, "Could not find amount index in SMS body for template creation.")
            return
        }

        val signature = SmsParser.generateSmsSignature(smsBody)

        val template =
            SmsParseTemplate(
                templateSignature = signature,
                correctedMerchantName = correctedMerchant,
                originalSmsBody = smsBody,
                originalAmountStartIndex = amountIndex,
                originalAmountEndIndex = amountIndex + amountStr.length,
                originalMerchantStartIndex = originalMerchantStartIndex,
                originalMerchantEndIndex = originalMerchantEndIndex,
            )

        smsParseTemplateRepository.insert(template)
        Log.d(TAG, "Successfully created and stored a new SMS parse template.")
    }
}
