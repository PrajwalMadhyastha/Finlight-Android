package io.pm.finlight.ui.viewmodel

import app.cash.turbine.test
import io.mockk.*
import io.pm.finlight.*
import io.pm.finlight.core.*
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.data.db.entity.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.capture
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.Mockito.`when` as whenever

class TransactionViewModelSmsTest : TransactionViewModelBaseSetup() {
    @Test
    fun `approveSmsTransaction creates account if not exists and saves transaction`() =
        runTest {
            // ARRANGE
            val potentialTxn =
                PotentialTransaction(1L, "Test", 100.0, "expense", "Test Merchant", "Msg", PotentialAccount("New Account", "Bank"), "hash", originalMerchantName = "Raw Test")
            val transactionCaptor = argumentCaptor<Transaction>()
            whenever(db.accountDao().findByName("New Account")).thenReturn(null).thenReturn(Account(1, "New Account", "Bank"))
            whenever(accountRepository.insert(any())).thenReturn(1L)
            whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

            // ACT
            val result = viewModel.approveSmsTransaction(potentialTxn, "Test Merchant", null, null, emptySet(), false)
            advanceUntilIdle()

            // ASSERT
            assertTrue(result)
            verify(accountRepository).insert(any())
            verify(transactionRepository).insertTransactionWithTags(transactionCaptor.capture(), eq(emptySet()))
            assertEquals("Test Merchant", transactionCaptor.firstValue.description)
            assertEquals("Raw Test", transactionCaptor.firstValue.originalDescription)
        }

    @Test
    fun `approveSmsTransaction saves foreign transaction correctly`() =
        runTest {
            // ARRANGE
            val potentialTxn =
                PotentialTransaction(1L, "Test", 100.0, "expense", "Test Merchant", "Msg", PotentialAccount("New Account", "Bank"), "hash", originalMerchantName = "Raw Foreign")
            val transactionCaptor = argumentCaptor<Transaction>()
            whenever(db.accountDao().findByName("New Account")).thenReturn(Account(1, "New Account", "Bank"))
            val travelSettings =
                TravelModeSettings(
                    isEnabled = true, tripName = "Trip", tripType = TripType.INTERNATIONAL,
                    startDate = 0L, endDate = Long.MAX_VALUE, currencyCode = "EUR", conversionRate = 90f
                )
            whenever(settingsRepository.getTravelModeSettings()).thenReturn(flowOf(travelSettings))
            whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

            // ACT
            val result = viewModel.approveSmsTransaction(potentialTxn, "Test Merchant", null, null, emptySet(), true)
            advanceUntilIdle()

            // ASSERT
            assertTrue(result)
            verify(transactionRepository).insertTransactionWithTags(transactionCaptor.capture(), eq(emptySet()))
            assertEquals("Test Merchant", transactionCaptor.firstValue.description)
            assertEquals("Raw Foreign", transactionCaptor.firstValue.originalDescription)
            assertEquals(9000.0, transactionCaptor.firstValue.amount, 0.01)
        }

    @Test
    fun `autoSaveSmsTransaction saves transaction with correct details`() =
        runTest {
            // ARRANGE
            val potentialTxn =
                PotentialTransaction(1L, "Test", 100.0, "expense", "Auto Merchant", "Msg", PotentialAccount("Cash", "Wallet"), "hash", 1, originalMerchantName = "Raw Auto")
            val transactionCaptor = argumentCaptor<Transaction>()
            whenever(accountAliasDao.findByAlias(anyString())).thenReturn(null)
            whenever(accountDao.findByName("Cash")).thenReturn(Account(1, "Cash", "Wallet"))
            whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

            // ACT
            val result = viewModel.autoSaveSmsTransaction(potentialTxn)
            advanceUntilIdle()

            // ASSERT
            assertTrue(result)
            verify(transactionRepository).insertTransactionWithTags(transactionCaptor.capture(), eq(emptySet()))
            assertEquals("Auto Merchant", transactionCaptor.firstValue.description)
            assertEquals("Raw Auto", transactionCaptor.firstValue.originalDescription)
            assertEquals(1, transactionCaptor.firstValue.categoryId)
        }

    @Test
    fun `autoSaveSmsTransaction handles IGNORE conflict on account creation`() =
        runTest {
            // ARRANGE
            val potentialTxn =
                PotentialTransaction(1L, "Test", 100.0, "expense", "Auto Merchant", "Msg", PotentialAccount("ConcurrentAccount", "Bank"), "hash", 1)
            val transactionCaptor = argumentCaptor<Transaction>()

            whenever(accountAliasDao.findByAlias(anyString())).thenReturn(null)

            // 1st call returns null (simulate race condition start)
            // 2nd call returns the existing account (simulate finding it after IGNORE)
            whenever(accountDao.findByName("ConcurrentAccount"))
                .thenReturn(null)
                .thenReturn(Account(99, "ConcurrentAccount", "Bank"))

            // Simulate IGNORE conflict returning -1
            whenever(accountRepository.insert(any())).thenReturn(-1L)

            whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

            // ACT
            val result = viewModel.autoSaveSmsTransaction(potentialTxn)
            advanceUntilIdle()

            // ASSERT
            assertTrue(result)
            verify(transactionRepository).insertTransactionWithTags(transactionCaptor.capture(), eq(emptySet()))
            assertEquals(99, transactionCaptor.firstValue.accountId)
        }

    @Test
    fun `reparseTransactionFromSms updates transaction with new parsed data`() =
        runTest {
            // ARRANGE
            mockkObject(SmsParser)
            val originalTxn =
                Transaction(
                    id = 1,
                    description = "Old",
                    categoryId = 1,
                    amount = 100.0,
                    date = 0,
                    accountId = 1,
                    notes = null,
                    sourceSmsId = 123L,
                )
            val sms = SmsMessage(123L, "Sender", "New Merchant spent 150", 0L)
            val newParsedTxn = PotentialTransaction(123L, "Sender", 150.0, "expense", "New Merchant", "Msg", categoryId = 2)

            whenever(transactionRepository.getTransactionById(1)).thenReturn(flowOf(originalTxn))
            whenever(smsRepository.getSmsDetailsById(123L)).thenReturn(sms)
            whenever(merchantMappingRepository.allMappings).thenReturn(flowOf(emptyList()))
            whenever(customSmsRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
            whenever(merchantRenameRuleDao.getAllRules()).thenReturn(flowOf(emptyList()))
            whenever(ignoreRuleDao.getEnabledRules()).thenReturn(emptyList())
            whenever(smsParseTemplateDao.getAllTemplates()).thenReturn(emptyList())
            whenever(merchantCategoryMappingDao.getCategoryIdForMerchant(anyString())).thenReturn(null)
            whenever(smsParseTemplateDao.getTemplatesBySignature(anyString())).thenReturn(emptyList())

            // Mock the object SmsParser to return our desired new transaction
            coEvery {
                SmsParser.parseWithReason(any(), any(), any(), any(), any(), any(), any(), any(), any())
            } returns ParseResult.Success(newParsedTxn)

            // ACT
            viewModel.reparseTransactionFromSms(1)
            advanceUntilIdle()

            // ASSERT
            verify(transactionRepository).updateDescription(1, "New Merchant")
            verify(transactionRepository).updateCategoryId(1, 2)

            unmockkObject(SmsParser)
        }

    @Test
    fun `deleteTransaction records sourceSmsHash in deny-list before deleting`() =
        runTest {
            // ARRANGE
            val hash = "sms_hash_abc123"
            val transactionToDelete =
                Transaction(
                    id = 1, description = "Test", amount = 1.0, date = 0,
                    accountId = 1, categoryId = 1, notes = null,
                    sourceSmsHash = hash,
                )

            // ACT
            viewModel.deleteTransaction(transactionToDelete)
            advanceUntilIdle()

            // ASSERT: deny-list insert must be called with the hash
            verify(deletedSmsHashDao).insert(
                io.pm.finlight.data.db.entity.DeletedSmsHash(hash),
            )
            // AND the transaction itself must be deleted
            verify(transactionRepository).delete(transactionToDelete)
        }

    @Test
    fun `deleteTransaction without sourceSmsHash does not insert into deny-list`() =
        runTest {
            // ARRANGE — manual entry, no SMS hash
            val transactionToDelete =
                Transaction(
                    id = 2, description = "Manual", amount = 50.0, date = 0,
                    accountId = 1, categoryId = 1, notes = null,
                    sourceSmsHash = null,
                )

            // ACT
            viewModel.deleteTransaction(transactionToDelete)
            advanceUntilIdle()

            // ASSERT: deny-list must NOT be touched for non-SMS transactions
            verify(deletedSmsHashDao, never()).insert(any())
            verify(transactionRepository).delete(transactionToDelete)
        }

    @Test
    fun `onConfirmDeleteSelection records SMS hashes in deny-list before deleting`() =
        runTest {
            // Arrange
            val id1 = 1
            val id2 = 2
            val hash1 = "sms_hash_bulk_1"
            val hash2 = "sms_hash_bulk_2"
            viewModel.enterSelectionMode(id1)
            viewModel.toggleTransactionSelection(id2)
            viewModel.onDeleteSelectionClick()
            advanceUntilIdle()

            // Stub: both selected transactions have SMS hashes
            whenever(transactionDao.getSmsHashesByIds(any())).thenReturn(listOf(hash1, hash2))

            // Act
            viewModel.onConfirmDeleteSelection()
            advanceUntilIdle()

            // Assert: deny-list must be updated for each hash
            verify(deletedSmsHashDao).insert(io.pm.finlight.data.db.entity.DeletedSmsHash(hash1))
            verify(deletedSmsHashDao).insert(io.pm.finlight.data.db.entity.DeletedSmsHash(hash2))
            // And the bulk delete must still happen
            verify(transactionRepository).deleteByIds(any())
        }

    @Test
    fun `clearOriginalSms clears the sms text flow`() =
        runTest {
            // Arrange
            val transactionId = 1
            val smsId = 123L
            val smsBody = "This is the original SMS body"
            val transaction =
                Transaction(id = transactionId, description = "Test", amount = 1.0, date = 0, accountId = 1, categoryId = 1, notes = null, sourceSmsId = smsId, originalDescription = "Test")

            // Mocks for loadTransactionForDetailScreen
            whenever(transactionRepository.getTransactionById(transactionId)).thenReturn(flowOf(transaction))
            whenever(transactionRepository.getTagsForTransaction(transactionId)).thenReturn(flowOf(emptyList()))
            whenever(transactionRepository.getImagesForTransaction(transactionId)).thenReturn(flowOf(emptyList()))
            whenever(smsRepository.getSmsDetailsById(smsId)).thenReturn(SmsMessage(smsId, "Sender", smsBody, 0L))
            whenever(transactionRepository.getTransactionCountForMerchant(anyString())).thenReturn(flowOf(0))

            initializeViewModel()

            viewModel.originalSmsText.test {
                assertNull("Initial state should be null", awaitItem())

                // Act 1: Load the SMS
                viewModel.loadTransactionForDetailScreen(transactionId)
                advanceUntilIdle()

                // Assert 1: SMS is loaded
                assertEquals(smsBody, awaitItem())

                // Act 2: Clear the SMS
                viewModel.clearOriginalSms()

                // Assert 2: SMS is cleared
                assertNull(awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `autoSaveSmsTransaction uses account alias if found`() =
        runTest {
            // Arrange
            val potentialAccount = PotentialAccount("Alias", "Bank")
            val potentialTxn =
                PotentialTransaction(
                    sourceSmsId = 1L,
                    smsSender = "S1",
                    amount = 100.0,
                    transactionType = "expense",
                    merchantName = "M",
                    originalMessage = "Msg",
                    potentialAccount = potentialAccount,
                    sourceSmsHash = "hash",
                )
            val alias = AccountAlias(aliasName = "Alias", destinationAccountId = 2)
            whenever(accountAliasDao.findByAlias("Alias")).thenReturn(alias)
            whenever(transactionRepository.insertTransactionWithTags(any(), any())).thenReturn(1L)

            // Act
            val result = viewModel.autoSaveSmsTransaction(potentialTxn)
            advanceUntilIdle()

            // Assert
            assertTrue(result)
            val captor = argumentCaptor<Transaction>()
            verify(transactionRepository).insertTransactionWithTags(captor.capture(), any())
            assertEquals(2, captor.firstValue.accountId)
        }
}
