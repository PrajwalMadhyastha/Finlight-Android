package io.pm.finlight.domain.usecase

import android.os.Build
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.pm.finlight.Account
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionType
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.data.db.dao.DeletedSmsHashDao
import io.pm.finlight.data.db.dao.MergeRecordDao
import io.pm.finlight.data.db.dao.TransactionQueryDao
import io.pm.finlight.data.db.dao.TransactionReimbursementDao
import io.pm.finlight.data.db.dao.TransactionWriteDao
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.data.db.entity.MergeRecord
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MergeTransactionsUseCaseTest : BaseViewModelTest() {
    @Mock
    private lateinit var transactionQueryDao: TransactionQueryDao

    @Mock
    private lateinit var transactionWriteDao: TransactionWriteDao

    @Mock
    private lateinit var transactionReimbursementDao: TransactionReimbursementDao

    @Mock
    private lateinit var mergeRecordDao: MergeRecordDao

    @Mock
    private lateinit var deletedSmsHashDao: DeletedSmsHashDao

    @Mock
    private lateinit var accountDao: AccountDao

    @Mock
    private lateinit var db: AppDatabase

    private lateinit var useCase: MergeTransactionsUseCase

    @Before
    override fun setup() {
        super.setup()

        `when`(db.accountDao()).thenReturn(accountDao)
        `when`(transactionReimbursementDao.getReimbursementsCountSync(org.mockito.ArgumentMatchers.anyInt())).thenReturn(0)
        kotlinx.coroutines.test.runTest {
            org.mockito.kotlin.whenever(transactionQueryDao.getTagsForTransactionSimple(org.mockito.ArgumentMatchers.anyInt())).thenReturn(emptyList())
        }

        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<AppDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }

        useCase =
            MergeTransactionsUseCase(
                transactionQueryDao = transactionQueryDao,
                transactionWriteDao = transactionWriteDao,
                transactionReimbursementDao = transactionReimbursementDao,
                mergeRecordDao = mergeRecordDao,
                deletedSmsHashDao = deletedSmsHashDao,
                db = db,
            )
    }

    @After
    override fun tearDown() {
        unmockkAll()
    }

    // ── Tests: Auto Merge ─────────────────────────────────────────────────────

    @Test
    fun `autoMerge with null parent auto-heals by finding recent transaction`() =
        runTest {
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(null)

            val childTxn =
                Transaction(
                    id = 2,
                    description = "Amazon",
                    amount = 50.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = "Child note",
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsHash = "hash123",
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            val newParent =
                Transaction(
                    id = 3,
                    description = "Amazon",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = "Parent note",
                    transactionType = TransactionType.EXPENSE,
                )
            `when`(
                transactionQueryDao.findRecentTransactionForMerge(
                    merchant = "Amazon",
                    accountId = 1,
                    transactionType = TransactionType.EXPENSE,
                    timeWindowStart = 2000L - (3 * 60 * 60 * 1000L),
                    newTxnId = 2,
                ),
            ).thenReturn(newParent)

            useCase(parentTxnId = 1, childTxnId = 2, childSmsBody = "SMS body", childSmsDate = 3000L)

            verify(transactionWriteDao).updateAmount(3, 150.0)

            @Suppress("UNCHECKED_CAST")
            val notesCaptor = ArgumentCaptor.forClass(String::class.java) as ArgumentCaptor<String>
            verify(transactionWriteDao).updateNotes(eq(3), notesCaptor.capture())
            val capturedNotes = notesCaptor.value

            assertTrue(capturedNotes.contains("Parent note"))
            assertTrue(capturedNotes.contains("Merged on"))
            assertTrue(capturedNotes.contains("SMS body"))
            assertTrue(capturedNotes.contains("Child note"))

            verify(mergeRecordDao).insert(any())
            verify(deletedSmsHashDao).insert(eq(DeletedSmsHash(smsHash = "hash123")))
            verify(transactionWriteDao).delete(childTxn)
        }

    @Test
    fun `autoMerge with valid parent preserves child notes`() =
        runTest {
            val parentTxn =
                Transaction(
                    id = 1,
                    description = "Amazon",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = "Parent note",
                    transactionType = TransactionType.EXPENSE,
                )
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(parentTxn)

            val childTxn =
                Transaction(
                    id = 2,
                    description = "Amazon",
                    amount = 50.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = "Child note",
                    transactionType = TransactionType.EXPENSE,
                )
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(childTxn)

            useCase.autoMerge(parentTxnId = 1, childTxnId = 2, childSmsBody = null, childSmsDate = null)

            @Suppress("UNCHECKED_CAST")
            val notesCaptor = ArgumentCaptor.forClass(String::class.java) as ArgumentCaptor<String>
            verify(transactionWriteDao).updateNotes(eq(1), notesCaptor.capture())
            val capturedNotes = notesCaptor.value

            assertTrue(capturedNotes.contains("Parent note"))
            assertTrue(capturedNotes.contains("Merged Transaction:"))
            assertTrue(capturedNotes.contains("Child note"))
        }

    @Test
    fun `autoMerge when neither parent nor recent transaction found returns early`() =
        runTest {
            val childTxn = Transaction(id = 2, description = "Amazon", amount = 50.0, date = 2000L, accountId = 1, categoryId = 1, notes = null, transactionType = TransactionType.EXPENSE)
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(null)
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(childTxn)
            `when`(transactionQueryDao.findRecentTransactionForMerge(any(), any(), any(), any(), any())).thenReturn(null)

            useCase(parentTxnId = 1, childTxnId = 2)

            verify(transactionWriteDao, never()).updateAmount(any(), any())
        }

    // ── Tests: Manual Merge ───────────────────────────────────────────────────

    @Test
    fun `manualMerge successfully merges multiple children into anchor`() =
        runTest {
            val anchor =
                Transaction(
                    id = 1,
                    description = "Anchor",
                    amount = 100.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = "Anchor note",
                    transactionType = TransactionType.EXPENSE,
                )
            val child1 =
                Transaction(
                    id = 2,
                    description = "Child 1",
                    amount = 50.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 2,
                    notes = "Child note",
                    transactionType = TransactionType.EXPENSE,
                    sourceSmsHash = "childHash1",
                )
            val child2 =
                Transaction(
                    id = 3,
                    description = "Child 2",
                    amount = 20.0,
                    date = 3000L,
                    accountId = 1,
                    categoryId = 3,
                    notes = null,
                    transactionType = TransactionType.INCOME,
                )

            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(anchor)
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(child1)
            `when`(transactionQueryDao.getTransactionByIdSync(3)).thenReturn(child2)

            `when`(transactionQueryDao.getTagsForTransactionSimple(1)).thenReturn(listOf(Tag(id = 1, name = "A")))
            `when`(transactionQueryDao.getTagsForTransactionSimple(2)).thenReturn(listOf(Tag(id = 2, name = "B")))
            `when`(transactionQueryDao.getTagsForTransactionSimple(3)).thenReturn(emptyList())

            useCase.manualMerge(anchor.id, listOf(child1.id, child2.id))

            // Net signed: anchor(-100) + child1(-50) + child2(+20) = -130 (EXPENSE)
            verify(transactionWriteDao).updateAmount(1, 130.0)
            verify(transactionWriteDao).updateDate(1, 3000L) // max date

            @Suppress("UNCHECKED_CAST")
            val notesCaptor = ArgumentCaptor.forClass(String::class.java) as ArgumentCaptor<String>
            verify(transactionWriteDao).updateNotes(eq(1), notesCaptor.capture())
            val capturedNotes = notesCaptor.value
            assertTrue(capturedNotes.contains("Anchor note"))
            assertTrue(capturedNotes.contains("[Merged] Child 1"))
            assertTrue(capturedNotes.contains("[Merged] Child 2"))

            verify(deletedSmsHashDao).insert(DeletedSmsHash(smsHash = "childHash1"))
            verify(transactionWriteDao).delete(child1)
            verify(transactionWriteDao).delete(child2)
        }

    @Test
    fun `manualMerge with anchor having reimbursements sets finalType to EXPENSE`() =
        runTest {
            val anchor =
                Transaction(
                    id = 1,
                    description = "Anchor",
                    amount = 10.0,
                    date = 1000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    transactionType = TransactionType.EXPENSE,
                )
            val child =
                Transaction(
                    id = 2,
                    description = "Child",
                    amount = 100.0,
                    date = 2000L,
                    accountId = 1,
                    categoryId = 1,
                    notes = null,
                    transactionType = TransactionType.INCOME,
                )

            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(anchor)
            `when`(transactionQueryDao.getTransactionByIdSync(2)).thenReturn(child)
            `when`(transactionReimbursementDao.getReimbursementsCountSync(1)).thenReturn(1)

            useCase.manualMerge(1, listOf(2))

            verify(transactionWriteDao).updateAmount(1, -90.0)
        }

    // ── Tests: Breakdown ──────────────────────────────────────────────────────

    @Test
    fun `getMergedTransactionBreakdown calculates correct anchor and child items`() =
        runTest {
            val anchor = Transaction(id = 1, description = "Anchor", amount = 150.0, date = 3000L, accountId = 10, categoryId = 1, notes = null, transactionType = TransactionType.EXPENSE)
            val record =
                MergeRecord(
                    id = 1,
                    parentTxnId = 1,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = null,
                    childDescription = "Child",
                    childAmount = 50.0,
                    childDate = 2000L,
                    childAccountId = 20,
                    childCategoryId = 1,
                    childTransactionType = TransactionType.EXPENSE,
                    childSource = "SMS",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = null,
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                    mergeGroupId = "group1",
                    mergeType = "MANUAL",
                )

            `when`(mergeRecordDao.getAllForParentAnyType(1)).thenReturn(listOf(record))
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(anchor)
            `when`(accountDao.getAccountByIdBlocking(10)).thenReturn(Account(id = 10, name = "Account 1", type = "CHECKING"))
            `when`(accountDao.getAccountByIdBlocking(20)).thenReturn(Account(id = 20, name = "Account 2", type = "SAVINGS"))

            val breakdown = useCase.getMergedTransactionBreakdown(1)

            assertEquals(2, breakdown.size)
            val anchorItem = breakdown[0]
            assertEquals(10, anchorItem.accountId)
            assertEquals("Account 1", anchorItem.accountName)
            assertEquals(100.0, anchorItem.amount)
            assertTrue(anchorItem.isAnchor)

            val childItem = breakdown[1]
            assertEquals(20, childItem.accountId)
            assertEquals("Account 2", childItem.accountName)
            assertEquals(50.0, childItem.amount)
        }

    // ── Tests: Unmerge ────────────────────────────────────────────────────────

    @Test
    fun `unmerge for MANUAL groupId restores anchor and reinserts children`() =
        runTest {
            val record =
                MergeRecord(
                    id = 1,
                    parentTxnId = 1,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = "Original notes",
                    childDescription = "Child",
                    childAmount = 50.0,
                    childDate = 2000L,
                    childAccountId = 1,
                    childCategoryId = 1,
                    childTransactionType = TransactionType.EXPENSE,
                    childSource = "SMS",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = "hash1",
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                    mergeGroupId = "group-123",
                    mergeType = "MANUAL",
                )

            val parentTxn = Transaction(id = 1, description = "Merged Parent", amount = 150.0, date = 2000L, accountId = 1, categoryId = 1, notes = "Merged notes", transactionType = TransactionType.EXPENSE)

            `when`(mergeRecordDao.getForParentSync(1)).thenReturn(record)
            `when`(mergeRecordDao.getAllForGroup("group-123")).thenReturn(listOf(record))
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(parentTxn)

            useCase.unmerge(1)

            verify(transactionWriteDao).updateAmount(1, 100.0)
            verify(transactionWriteDao).updateDate(1, 1000L)
            verify(transactionWriteDao).updateNotes(1, "Original notes")
            verify(transactionWriteDao).insert(any())
            verify(deletedSmsHashDao).deleteByHash("hash1")
            verify(mergeRecordDao).deleteByGroupId("group-123")
        }

    @Test
    fun `unmerge for AUTO records restores parent and removes merge records`() =
        runTest {
            val record =
                MergeRecord(
                    id = 5,
                    parentTxnId = 1,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = "Original notes",
                    childDescription = "Child",
                    childAmount = 30.0,
                    childDate = 2000L,
                    childAccountId = 1,
                    childCategoryId = 1,
                    childTransactionType = TransactionType.EXPENSE,
                    childSource = "SMS",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = "hash2",
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                    mergeGroupId = "",
                    mergeType = "AUTO",
                )

            val parentTxn = Transaction(id = 1, description = "Parent", amount = 130.0, date = 2000L, accountId = 1, categoryId = 1, notes = "Merged notes", transactionType = TransactionType.EXPENSE)

            `when`(mergeRecordDao.getForParentSync(1)).thenReturn(record)
            `when`(mergeRecordDao.getAllForParentSync(1)).thenReturn(listOf(record))
            `when`(transactionQueryDao.getTransactionByIdSync(1)).thenReturn(parentTxn)

            useCase.unmerge(1)

            verify(transactionWriteDao).updateAmount(1, 100.0)
            verify(transactionWriteDao).updateDate(1, 1000L)
            verify(transactionWriteDao).updateNotes(1, "Original notes")
            verify(transactionWriteDao).insert(any())
            verify(deletedSmsHashDao).deleteByHash("hash2")
            verify(mergeRecordDao).deleteById(5)
        }

    @Test
    fun `observeMergeRecord delegates to MergeRecordDao`() =
        runTest {
            val record =
                MergeRecord(
                    id = 1,
                    parentTxnId = 1,
                    originalParentAmount = 100.0,
                    originalParentDate = 1000L,
                    originalParentNotes = null,
                    childDescription = "Child",
                    childAmount = 50.0,
                    childDate = 2000L,
                    childAccountId = 1,
                    childCategoryId = 1,
                    childTransactionType = TransactionType.EXPENSE,
                    childSource = "SMS",
                    childNotes = null,
                    childSourceSmsId = null,
                    childSourceSmsHash = null,
                    childSmsSignature = null,
                    childOriginalDescription = null,
                    childOriginalAmount = null,
                    childCurrencyCode = null,
                    childConversionRate = null,
                    mergeGroupId = "g1",
                    mergeType = "MANUAL",
                )
            `when`(mergeRecordDao.observeForParent(1)).thenReturn(flowOf(record))

            useCase.observeMergeRecord(1).test {
                val item = awaitItem()
                assertNotNull(item)
                assertEquals(1, item.parentTxnId)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
