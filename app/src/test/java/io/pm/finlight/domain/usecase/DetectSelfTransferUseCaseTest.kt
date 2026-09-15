package io.pm.finlight.domain.usecase

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.pm.finlight.Account
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.ITransactionRepository
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountAliasDao
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.dao.TransactionAnalyticsDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetectSelfTransferUseCaseTest : BaseViewModelTest() {
    private val transactionRepository: ITransactionRepository = mockk(relaxed = true)
    private val accountDao: AccountDao = mockk(relaxed = true)
    private val accountAliasDao: AccountAliasDao = mockk(relaxed = true)
    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var useCase: DetectSelfTransferUseCase

    @Before
    override fun setup() {
        super.setup()
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        useCase =
            DetectSelfTransferUseCase(
                transactionRepository = transactionRepository,
                accountDao = accountDao,
                accountAliasDao = accountAliasDao,
                dispatcherProvider = testDispatcherProvider,
            )
    }

    @Test
    fun `strict time match within 5 minutes links transactions atomically`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Withdrawal",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Deposit",
                    amount = 500.0,
                    date = 1000000L + (4 * 60 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 500.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate)

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match with alias digit match links transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Transfer",
                    originalDescription = "Transfer to 1234",
                    amount = 1000.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Received",
                    originalDescription = "Received from a/c",
                    amount = 1000.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 1000.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate)

            val alias = AccountAlias(aliasName = "HDFC-1234", destinationAccountId = 2)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns listOf(alias)
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Account1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Account2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match with account alias token overlap links transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Transfer",
                    originalDescription = "Sent to my savings account",
                    amount = 1500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Deposit",
                    originalDescription = "Received cash",
                    amount = 1500.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 1500.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate)

            val alias = AccountAlias(aliasName = "savings account", destinationAccountId = 2)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns listOf(alias)
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Primary Checking", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Secondary Savings", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match with candidate bank name token overlap links transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Transfer",
                    originalDescription = "Sent money to State Bank of India main branch",
                    amount = 2500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Received",
                    originalDescription = "Received from other account",
                    amount = 2500.0,
                    date = 1000000L + (3 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 2500.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "ICICI Bank", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "State Bank of India", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match with new transaction bank name token overlap links transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Transfer",
                    originalDescription = "Sent money",
                    amount = 2500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Received",
                    originalDescription = "Received from ICICI Bank salary account",
                    amount = 2500.0,
                    date = 1000000L + (3 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 2500.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "ICICI Bank", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Axis Bank", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match with NEFT transfer keywords links transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "NEFT transfer sent",
                    originalDescription = "neft transfer sent ref 9988",
                    amount = 1200.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "NEFT transfer recd",
                    originalDescription = "neft transfer received ref 9988",
                    amount = 1200.0,
                    date = 1000000L + (1 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 1200.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match without keyword or alias match does not link`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "grocery store purchase",
                    amount = 100.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "freelance payment",
                    amount = 100.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 100.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }

    @Test
    fun `skips execution when transaction is invalid for transfer linking`() =
        runTest(testDispatcher) {
            val noSms =
                Transaction(
                    id = 1,
                    description = "A",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = null,
                    categoryId = null,
                    notes = null,
                )
            val alreadyLinked =
                Transaction(
                    id = 2,
                    description = "B",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    linkedTransferId = 99,
                    categoryId = null,
                    notes = null,
                )
            val excluded =
                Transaction(
                    id = 3,
                    description = "C",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    isExcluded = true,
                    categoryId = null,
                    notes = null,
                )
            val split =
                Transaction(
                    id = 4,
                    description = "D",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    isSplit = true,
                    categoryId = null,
                    notes = null,
                )

            useCase(noSms)
            useCase(alreadyLinked)
            useCase(excluded)
            useCase(split)

            coVerify(exactly = 0) {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            }
        }

    @Test
    fun `multiple candidates links only the first match`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Transfer",
                    amount = 100.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate1 =
                Transaction(
                    id = 2,
                    description = "Match1",
                    amount = 100.0,
                    date = 1000000L + 60000L,
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )
            val candidate2 =
                Transaction(
                    id = 3,
                    description = "Match2",
                    amount = 100.0,
                    date = 1000000L + 120000L,
                    accountId = 3,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 30,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(
                    amount = 100.0,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    startTime = any(),
                    endTime = any(),
                )
            } returns listOf(candidate1, candidate2)

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
            coVerify(exactly = 0) { transactionRepository.linkTransfer(1, 3) }
            coVerify(exactly = 0) { transactionRepository.linkTransfer(3, 1) }
        }

    @Test
    fun `detectAndLinkSelfTransfer delegation method delegates to invoke`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Withdrawal",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Deposit",
                    amount = 500.0,
                    date = 1000000L + (2 * 60 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            useCase.detectAndLinkSelfTransfer(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `secondary constructor initializes and works with AppDatabase`() =
        runTest(testDispatcher) {
            val db: AppDatabase = mockk(relaxed = true)
            val writeDao: TransactionWriteDao = mockk(relaxed = true)
            val queryDao: TransactionQueryDao = mockk(relaxed = true)
            val analyticsDao: TransactionAnalyticsDao = mockk(relaxed = true)
            val reimbursementDao: TransactionReimbursementDao = mockk(relaxed = true)
            val accDao: AccountDao = mockk(relaxed = true)
            val aliasDao: AccountAliasDao = mockk(relaxed = true)

            every { db.transactionWriteDao() } returns writeDao
            every { db.transactionQueryDao() } returns queryDao
            every { db.transactionAnalyticsDao() } returns analyticsDao
            every { db.transactionReimbursementDao() } returns reimbursementDao
            every { db.accountDao() } returns accDao
            every { db.accountAliasDao() } returns aliasDao

            val secondaryUseCase = DetectSelfTransferUseCase(db, testDispatcherProvider)
            val newTxn =
                Transaction(
                    id = 1,
                    description = "No SMS",
                    amount = 10.0,
                    date = 1000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = null,
                    categoryId = null,
                    notes = null,
                )

            secondaryUseCase(newTxn)
            coVerify(exactly = 0) { queryDao.findPotentialTransfers(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `loose time match with new transaction alias digit match links transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Sent",
                    originalDescription = "Sent money",
                    amount = 1000.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Received",
                    originalDescription = "Deposit from a/c ending in 1234",
                    amount = 1000.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val alias = AccountAlias(aliasName = "HDFC-1234", destinationAccountId = 1)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns listOf(alias)
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Account1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Account2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match with new transaction alias token overlap links transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Sent",
                    originalDescription = "Sent money",
                    amount = 1000.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Received",
                    originalDescription = "Deposit into secondary savings account",
                    amount = 1000.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val alias = AccountAlias(aliasName = "secondary savings account", destinationAccountId = 1)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns listOf(alias)
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Primary Checking", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Other Bank", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `loose time match with new transaction alias digit mismatch and low overlap does not match`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Sent money",
                    amount = 1000.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Received funds",
                    amount = 1000.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val alias = AccountAlias(aliasName = "Axis-9999", destinationAccountId = 1)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns listOf(alias)
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }

    @Test
    fun `fallback to description when originalDescription is null on both transactions`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "neft transfer sent",
                    originalDescription = null,
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "neft transfer received",
                    originalDescription = null,
                    amount = 500.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `fallback to description when originalDescription is null on only new transaction`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "imps transfer out",
                    originalDescription = null,
                    amount = 750.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "imps transfer in",
                    amount = 750.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `fallback to description when originalDescription is null on only candidate`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "imps transfer out",
                    amount = 750.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "imps transfer in",
                    originalDescription = null,
                    amount = 750.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `candidate with isSplit true is skipped and next valid candidate is linked`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Transfer",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val splitCandidate =
                Transaction(
                    id = 2,
                    description = "Split Parent",
                    amount = 500.0,
                    date = 1000000L + 60000L,
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    isSplit = true,
                    categoryId = null,
                    notes = null,
                )
            val validCandidate =
                Transaction(
                    id = 3,
                    description = "Valid Match",
                    amount = 500.0,
                    date = 1000000L + 120000L,
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 30,
                    isSplit = false,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(splitCandidate, validCandidate)

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(1, 2) }
            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 3) }
        }

    @Test
    fun `null candidateAccount and newTxnAccount are handled safely without linking`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Payment sent",
                    amount = 1000.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Payment received",
                    amount = 1000.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns null
            coEvery { accountDao.getAccountByIdSync(2) } returns null

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }

    @Test
    fun `candidateAccount name overlap low does not match`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Payment sent to grocery store",
                    amount = 1000.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Payment received for freelance work",
                    amount = 1000.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Barclays Bank", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Citibank", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }

    @Test
    fun `only one transaction contains keyword does not match`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "neft transfer sent",
                    amount = 1000.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "salary credited from employer",
                    amount = 1000.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }

    @Test
    fun `candidate alias without digits but token overlap greater than 0_6 matches`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Sent to emergency savings pool",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Received deposit",
                    amount = 500.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val candidateAlias = AccountAlias(aliasName = "emergency savings pool", destinationAccountId = 2)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns listOf(candidateAlias)
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `candidate alias without digits and token overlap low does not match`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Sent to grocery store",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Received deposit",
                    amount = 500.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val candidateAlias = AccountAlias(aliasName = "Emergency Fund", destinationAccountId = 2)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns listOf(candidateAlias)
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }

    @Test
    fun `candidate alias with digits not in newTxnDesc but token overlap greater than 0_6 matches`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Sent to my emergency vault fund",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Received deposit",
                    amount = 500.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val candidateAlias = AccountAlias(aliasName = "emergency vault fund 9876", destinationAccountId = 2)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns listOf(candidateAlias)
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `newTxn alias without digits and token overlap low does not match`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Payment sent",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Payment received from investment dividends",
                    amount = 500.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val newTxnAlias = AccountAlias(aliasName = "Payroll Account", destinationAccountId = 1)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns listOf(newTxnAlias)
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }

    @Test
    fun `newTxn alias with digits not in candidateDesc but token overlap greater than 0_6 matches`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "Sent money",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate =
                Transaction(
                    id = 2,
                    description = "Income",
                    originalDescription = "Received from payroll salary account",
                    amount = 500.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate)

            val newTxnAlias = AccountAlias(aliasName = "payroll salary account 5555", destinationAccountId = 1)
            coEvery { accountAliasDao.getAliasesForAccount(1) } returns listOf(newTxnAlias)
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")

            useCase(newTxn)

            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 2) }
        }

    @Test
    fun `Tier 1 caches newTxnAliases and newTxnAccount across multiple candidates without N+1 queries`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    originalDescription = "transfer between accounts ref 4321",
                    amount = 800.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )
            val candidate1 =
                Transaction(
                    id = 2,
                    description = "Income Non Match",
                    originalDescription = "cash deposit at counter",
                    amount = 800.0,
                    date = 1000000L + (1 * 3600 * 1000L),
                    accountId = 2,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 20,
                    categoryId = null,
                    notes = null,
                )
            val candidate2 =
                Transaction(
                    id = 3,
                    description = "Income Match",
                    originalDescription = "transfer received ref 4321",
                    amount = 800.0,
                    date = 1000000L + (2 * 3600 * 1000L),
                    accountId = 3,
                    transactionType = TransactionType.INCOME,
                    sourceSmsId = 30,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns listOf(candidate1, candidate2)

            coEvery { accountAliasDao.getAliasesForAccount(1) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(2) } returns emptyList()
            coEvery { accountAliasDao.getAliasesForAccount(3) } returns emptyList()
            coEvery { accountDao.getAccountByIdSync(1) } returns Account(id = 1, name = "Acc1", type = "bank")
            coEvery { accountDao.getAccountByIdSync(2) } returns Account(id = 2, name = "Acc2", type = "bank")
            coEvery { accountDao.getAccountByIdSync(3) } returns Account(id = 3, name = "Acc3", type = "bank")

            useCase(newTxn)

            // Caching verification: newTxn account and aliases should be queried at most ONCE
            coVerify(exactly = 1) { accountAliasDao.getAliasesForAccount(1) }
            coVerify(exactly = 1) { accountDao.getAccountByIdSync(1) }
            // Candidate 2 matched and linked
            coVerify(exactly = 0) { transactionRepository.linkTransfer(1, 2) }
            coVerify(exactly = 1) { transactionRepository.linkTransfer(1, 3) }
        }

    @Test
    fun `no candidates found terminates without action`() =
        runTest(testDispatcher) {
            val newTxn =
                Transaction(
                    id = 1,
                    description = "Expense",
                    amount = 500.0,
                    date = 1000000L,
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsId = 10,
                    categoryId = null,
                    notes = null,
                )

            coEvery {
                transactionRepository.findPotentialTransfers(any(), any(), any(), any(), any())
            } returns emptyList()

            useCase(newTxn)

            coVerify(exactly = 0) { transactionRepository.linkTransfer(any(), any()) }
        }
}
