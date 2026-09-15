package io.pm.finlight.domain.usecase

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.Goal
import io.pm.finlight.GoalDao
import io.pm.finlight.RecurringPattern
import io.pm.finlight.RecurringPatternDao
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MergeAccountsUseCaseIntegrationTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var accountDao: AccountDao
    private lateinit var accountAliasDao: AccountAliasDao
    private lateinit var recurringTransactionDao: RecurringTransactionDao
    private lateinit var goalDao: GoalDao
    private lateinit var transactionWriteDao: TransactionWriteDao
    private lateinit var transactionQueryDao: TransactionQueryDao
    private lateinit var mergeRecordDao: MergeRecordDao
    private lateinit var recurringPatternDao: RecurringPatternDao

    private lateinit var mergeTransactionsUseCase: MergeTransactionsUseCase
    private lateinit var useCase: MergeAccountsUseCase

    @Before
    fun setup() {
        val db = dbRule.db
        accountDao = db.accountDao()
        accountAliasDao = db.accountAliasDao()
        recurringTransactionDao = db.recurringTransactionDao()
        goalDao = db.goalDao()
        transactionWriteDao = db.transactionWriteDao()
        transactionQueryDao = db.transactionQueryDao()
        mergeRecordDao = db.mergeRecordDao()
        recurringPatternDao = db.recurringPatternDao()

        mergeTransactionsUseCase =
            MergeTransactionsUseCase(
                transactionQueryDao = db.transactionQueryDao(),
                transactionWriteDao = db.transactionWriteDao(),
                transactionReimbursementDao = db.transactionReimbursementDao(),
                mergeRecordDao = db.mergeRecordDao(),
                deletedSmsHashDao = db.deletedSmsHashDao(),
                db = db,
            )

        useCase = MergeAccountsUseCase(db)
    }

    @Test
    fun `mergeAccounts successfully merges accounts, repoints aliases, reassigns rules and entities without cascade deletion`() =
        runTest {
            // Arrange
            val destId = accountDao.insert(Account(name = "Main Bank", type = "Bank")).toInt()
            val sourceId1 = accountDao.insert(Account(name = "Old Bank 1", type = "Bank")).toInt()
            val sourceId2 = accountDao.insert(Account(name = "Old Bank 2", type = "Card")).toInt()

            // Existing alias pointing to sourceId1
            val preExistingAlias = AccountAlias(aliasName = "Legacy Nickname", destinationAccountId = sourceId1)
            accountAliasDao.insertAll(listOf(preExistingAlias))

            // Recurring transaction linked to sourceId1
            val recurringTxn =
                RecurringTransaction(
                    description = "Subscription",
                    amount = 500.0,
                    transactionType = TransactionType.EXPENSE,
                    recurrenceInterval = "Monthly",
                    startDate = 1000L,
                    accountId = sourceId1,
                    categoryId = null,
                )
            val recurringTxnId = recurringTransactionDao.insert(recurringTxn).toInt()

            // Goal linked to sourceId1
            val goal =
                Goal(
                    name = "Trip Fund",
                    targetAmount = 50000.0,
                    targetDate = 2000L,
                    accountId = sourceId1,
                )
            goalDao.insert(goal)
            val seededGoals = goalDao.getAll()
            val goalId = seededGoals.first { it.name == "Trip Fund" }.id

            // Transaction linked to sourceId2
            val transaction =
                Transaction(
                    description = "Dinner",
                    amount = 1200.0,
                    date = 1500L,
                    accountId = sourceId2,
                    categoryId = null,
                    notes = "Family dinner",
                )
            val transactionId = transactionWriteDao.insert(transaction).toInt()

            // Act
            useCase(destId, listOf(sourceId1, sourceId2))

            // Assert: Source accounts are deleted
            assertNull(accountDao.getAccountByIdSync(sourceId1))
            assertNull(accountDao.getAccountByIdSync(sourceId2))

            // Assert: Destination account still exists
            assertNotNull(accountDao.getAccountByIdSync(destId))

            // Assert: New aliases created for merged source accounts
            val alias1 = accountAliasDao.findByAlias("Old Bank 1")
            val alias2 = accountAliasDao.findByAlias("Old Bank 2")
            assertNotNull(alias1)
            assertEquals(destId, alias1?.destinationAccountId)
            assertNotNull(alias2)
            assertEquals(destId, alias2?.destinationAccountId)

            // Assert: Pre-existing alias is repointed to destination account (not deleted by cascade)
            val repointedAlias = accountAliasDao.findByAlias("Legacy Nickname")
            assertNotNull(repointedAlias)
            assertEquals(destId, repointedAlias?.destinationAccountId)

            // Assert: Recurring transaction rule is reassigned to destination account (not deleted by cascade)
            val updatedRecurringRules = recurringTransactionDao.getAllRulesList()
            val updatedRecurringRule = updatedRecurringRules.find { it.id == recurringTxnId }
            assertNotNull(updatedRecurringRule)
            assertEquals(destId, updatedRecurringRule?.accountId)

            // Assert: Goal is reassigned to destination account (not deleted by cascade)
            val updatedGoal = goalDao.getAll().find { it.id == goalId }
            assertNotNull(updatedGoal)
            assertEquals(destId, updatedGoal?.accountId)

            // Assert: Transaction is reassigned to destination account
            val updatedTransaction = transactionQueryDao.getTransactionByIdSync(transactionId)
            assertNotNull(updatedTransaction)
            assertEquals(destId, updatedTransaction?.accountId)
        }

    @Test
    fun `mergeAccounts with duplicate sourceAccountIds succeeds cleanly`() =
        runTest {
            // Arrange
            val destId = accountDao.insert(Account(name = "Main Account", type = "Bank")).toInt()
            val sourceId = accountDao.insert(Account(name = "Duplicate Source", type = "Bank")).toInt()

            val transaction =
                Transaction(
                    description = "Groceries",
                    amount = 450.0,
                    date = 1000L,
                    accountId = sourceId,
                    categoryId = null,
                    notes = null,
                )
            val txnId = transactionWriteDao.insert(transaction).toInt()

            // Act: passing duplicated IDs
            useCase(destId, listOf(sourceId, sourceId, sourceId))

            // Assert
            assertNull(accountDao.getAccountByIdSync(sourceId))
            val updatedTxn = transactionQueryDao.getTransactionByIdSync(txnId)
            assertEquals(destId, updatedTxn?.accountId)

            val alias = accountAliasDao.findByAlias("Duplicate Source")
            assertNotNull(alias)
            assertEquals(destId, alias?.destinationAccountId)
        }

    @Test
    fun `mergeAccounts when destination account does not exist leaves all data unchanged`() =
        runTest {
            // Arrange
            val sourceId = accountDao.insert(Account(name = "Untouched Source", type = "Bank")).toInt()
            val transaction =
                Transaction(
                    description = "Coffee",
                    amount = 150.0,
                    date = 1000L,
                    accountId = sourceId,
                    categoryId = null,
                    notes = null,
                )
            val txnId = transactionWriteDao.insert(transaction).toInt()

            // Act with non-existent destination account
            useCase(destinationAccountId = 99999, sourceAccountIds = listOf(sourceId))

            // Assert: Source account and transaction are completely untouched
            assertNotNull(accountDao.getAccountByIdSync(sourceId))
            val txn = transactionQueryDao.getTransactionByIdSync(txnId)
            assertEquals(sourceId, txn?.accountId)
        }

    @Test
    fun `mergeAccounts when sourceAccountIds contains only destinationId early returns`() =
        runTest {
            // Arrange
            val destId = accountDao.insert(Account(name = "Self Target", type = "Bank")).toInt()

            // Act
            useCase(destId, listOf(destId))

            // Assert
            assertNotNull(accountDao.getAccountByIdSync(destId))
        }

    @Test
    fun `mergeAccounts reassigns merge_records and unmerging succeeds without SQLiteConstraintException`() =
        runTest {
            // Arrange: create destination and source accounts
            val destId = accountDao.insert(Account(name = "Main Checking", type = "Bank")).toInt()
            val sourceId = accountDao.insert(Account(name = "Old Card", type = "Card")).toInt()

            // Create anchor transaction on destination account
            val anchorTxn =
                Transaction(
                    description = "Supermarket",
                    amount = 100.0,
                    date = 1000L,
                    accountId = destId,
                    categoryId = null,
                    notes = "anchor note",
                    transactionType = TransactionType.EXPENSE,
                )
            val anchorId = transactionWriteDao.insert(anchorTxn).toInt()

            // Create child transaction on source account
            val childTxn =
                Transaction(
                    description = "Supermarket Bag Fee",
                    amount = 5.0,
                    date = 1001L,
                    accountId = sourceId,
                    categoryId = null,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )
            val childId = transactionWriteDao.insert(childTxn).toInt()

            // Merge child into anchor
            mergeTransactionsUseCase.manualMerge(anchorId, listOf(childId))

            // Seed recurring pattern associated with source account
            val pattern =
                RecurringPattern(
                    smsSignature = "sig-supermarket-rule",
                    description = "Supermarket",
                    amount = 5.0,
                    transactionType = TransactionType.EXPENSE,
                    accountId = sourceId,
                    categoryId = null,
                    occurrences = 2,
                    firstSeen = 500L,
                    lastSeen = 1001L,
                )
            recurringPatternDao.insert(pattern)

            // Verify pre-conditions: merge record has childAccountId == sourceId, child txn is deleted
            val preMergeRecords = mergeRecordDao.getAll()
            assertEquals(1, preMergeRecords.size)
            assertEquals(sourceId, preMergeRecords.first().childAccountId)
            assertNull(transactionQueryDao.getTransactionByIdSync(childId))

            // Act: merge source account into destination account
            useCase(destId, listOf(sourceId))

            // Assert: source account is deleted
            assertNull(accountDao.getAccountByIdSync(sourceId))

            // Assert: recurring pattern accountId is repointed to destId
            val updatedPattern = recurringPatternDao.getPatternBySignature("sig-supermarket-rule")
            assertNotNull(updatedPattern)
            assertEquals(destId, updatedPattern?.accountId)

            // Assert: merge record childAccountId is repointed to destId
            val postMergeRecords = mergeRecordDao.getAll()
            assertEquals(1, postMergeRecords.size)
            assertEquals(destId, postMergeRecords.first().childAccountId)

            // Act: unmerge transaction - MUST NOT throw SQLiteConstraintException even though sourceId was deleted!
            mergeTransactionsUseCase.unmerge(anchorId)

            // Assert: anchor restored to original amount
            val restoredAnchor = transactionQueryDao.getTransactionByIdSync(anchorId)
            assertNotNull(restoredAnchor)
            assertEquals(100.0, restoredAnchor?.amount ?: 0.0, 0.001)

            // Assert: child transaction was restored with accountId == destId
            val allTxns = transactionQueryDao.getAllTransactionsSimple().first()
            val restoredChild = allTxns.find { it.description == "Supermarket Bag Fee" }
            assertNotNull(restoredChild)
            assertEquals(destId, restoredChild?.accountId)
            assertEquals(5.0, restoredChild?.amount ?: 0.0, 0.001)

            // Assert: merge record was cleaned up after successful unmerge
            assertTrue(mergeRecordDao.getAll().isEmpty())
        }
}
