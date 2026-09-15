package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.Account
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.data.db.entity.MergeRecord
import io.pm.finlight.data.db.entity.MergeType
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class MergeRecordDaoTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var mergeRecordDao: MergeRecordDao
    private lateinit var accountDao: AccountDao
    private lateinit var transactionWriteDao: TransactionWriteDao

    private var defaultAccountId: Int = 0
    private var parentTxnId: Int = 0

    @Before
    fun setup() =
        runTest {
            val db = dbRule.db
            mergeRecordDao = db.mergeRecordDao()
            accountDao = db.accountDao()
            transactionWriteDao = db.transactionWriteDao()

            defaultAccountId = accountDao.insert(Account(name = "Test Account", type = "Bank")).toInt()
            parentTxnId =
                transactionWriteDao.insert(
                    Transaction(
                        description = "Parent Transaction",
                        amount = 100.0,
                        date = 1000L,
                        accountId = defaultAccountId,
                        categoryId = null,
                        notes = "parent notes",
                    ),
                ).toInt()
        }

    private fun createRecord(
        parentTxnId: Int,
        childAccountId: Int,
        childDesc: String = "Child",
        groupId: String = "",
        mergeType: MergeType = MergeType.AUTO,
    ): MergeRecord {
        return MergeRecord(
            parentTxnId = parentTxnId,
            originalParentAmount = 100.0,
            originalParentDate = 1000L,
            originalParentNotes = "parent notes",
            childDescription = childDesc,
            childAmount = 50.0,
            childDate = 1000L,
            childAccountId = childAccountId,
            childCategoryId = null,
            mergeGroupId = groupId,
            mergeType = mergeType,
        )
    }

    @Test
    fun `reassignChildAccount updates matching childAccountId to destinationAccountId`() =
        runTest {
            val sourceId1 = accountDao.insert(Account(name = "Source 1", type = "Bank")).toInt()
            val sourceId2 = accountDao.insert(Account(name = "Source 2", type = "Bank")).toInt()
            val otherSourceId = accountDao.insert(Account(name = "Other Source", type = "Bank")).toInt()
            val destId = accountDao.insert(Account(name = "Target Dest", type = "Bank")).toInt()

            mergeRecordDao.insert(createRecord(parentTxnId, sourceId1, childDesc = "Child 1"))
            mergeRecordDao.insert(createRecord(parentTxnId, sourceId2, childDesc = "Child 2"))
            mergeRecordDao.insert(createRecord(parentTxnId, otherSourceId, childDesc = "Child 3"))

            mergeRecordDao.reassignChildAccount(listOf(sourceId1, sourceId2), destId)

            val records = mergeRecordDao.getAll()
            assertEquals(3, records.size)
            val updated1 = records.first { it.childDescription == "Child 1" }
            val updated2 = records.first { it.childDescription == "Child 2" }
            val untouched = records.first { it.childDescription == "Child 3" }

            assertEquals(destId, updated1.childAccountId)
            assertEquals(destId, updated2.childAccountId)
            assertEquals(otherSourceId, untouched.childAccountId)
        }

    @Test
    fun `observeForParent and getForParentSync return most recent record`() =
        runTest {
            val rec1 = createRecord(parentTxnId, defaultAccountId, childDesc = "Old").copy(mergedAt = 1000L)
            val rec2 = createRecord(parentTxnId, defaultAccountId, childDesc = "New").copy(mergedAt = 2000L)
            mergeRecordDao.insert(rec1)
            mergeRecordDao.insert(rec2)

            val observed = mergeRecordDao.observeForParent(parentTxnId).first()
            val fetched = mergeRecordDao.getForParentSync(parentTxnId)

            assertNotNull(observed)
            assertEquals("New", observed.childDescription)
            assertNotNull(fetched)
            assertEquals("New", fetched.childDescription)
        }

    @Test
    fun `getAllForParentSync and getAllForParentAnyType return chronological records`() =
        runTest {
            val rec1 = createRecord(parentTxnId, defaultAccountId, childDesc = "First").copy(mergedAt = 1000L)
            val rec2 = createRecord(parentTxnId, defaultAccountId, childDesc = "Second").copy(mergedAt = 2000L)
            mergeRecordDao.insert(rec2)
            mergeRecordDao.insert(rec1)

            val syncRecords = mergeRecordDao.getAllForParentSync(parentTxnId)
            val anyTypeRecords = mergeRecordDao.getAllForParentAnyType(parentTxnId)

            assertEquals(2, syncRecords.size)
            assertEquals("First", syncRecords[0].childDescription)
            assertEquals("Second", syncRecords[1].childDescription)

            assertEquals(2, anyTypeRecords.size)
            assertEquals("First", anyTypeRecords[0].childDescription)
            assertEquals("Second", anyTypeRecords[1].childDescription)
        }

    @Test
    fun `group lifecycle getAllForGroup and deleteByGroupId operate correctly`() =
        runTest {
            val groupId = "group-123"
            val rec1 = createRecord(parentTxnId, defaultAccountId, childDesc = "A", groupId = groupId, mergeType = MergeType.MANUAL)
            val rec2 = createRecord(parentTxnId, defaultAccountId, childDesc = "B", groupId = groupId, mergeType = MergeType.MANUAL)
            val recOther = createRecord(parentTxnId, defaultAccountId, childDesc = "Other", groupId = "other-group", mergeType = MergeType.MANUAL)

            mergeRecordDao.insert(rec1)
            mergeRecordDao.insert(rec2)
            mergeRecordDao.insert(recOther)

            val groupRecords = mergeRecordDao.getAllForGroup(groupId)
            assertEquals(2, groupRecords.size)

            val fetchedGroupId = mergeRecordDao.getGroupIdForParent(parentTxnId)
            assertNotNull(fetchedGroupId)

            mergeRecordDao.deleteByGroupId(groupId)

            val remainingGroupRecords = mergeRecordDao.getAllForGroup(groupId)
            assertTrue(remainingGroupRecords.isEmpty())

            val remainingAll = mergeRecordDao.getAll()
            assertEquals(1, remainingAll.size)
            assertEquals("Other", remainingAll.first().childDescription)
        }

    @Test
    fun `deleteById and deleteAll remove records`() =
        runTest {
            mergeRecordDao.insert(createRecord(parentTxnId, defaultAccountId, childDesc = "One"))
            val all1 = mergeRecordDao.getAll()
            assertEquals(1, all1.size)

            mergeRecordDao.deleteById(all1.first().id)
            assertTrue(mergeRecordDao.getAll().isEmpty())

            mergeRecordDao.insert(createRecord(parentTxnId, defaultAccountId, childDesc = "Two"))
            assertEquals(1, mergeRecordDao.getAll().size)

            mergeRecordDao.deleteAll()
            assertTrue(mergeRecordDao.getAll().isEmpty())
        }
}
