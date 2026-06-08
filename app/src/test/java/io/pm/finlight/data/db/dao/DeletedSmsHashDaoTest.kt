package io.pm.finlight.data.db.dao

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.TestApplication
import io.pm.finlight.data.db.entity.DeletedSmsHash
import io.pm.finlight.util.DatabaseTestRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class DeletedSmsHashDaoTest {
    @get:Rule
    val dbRule = DatabaseTestRule()

    private lateinit var dao: DeletedSmsHashDao

    @Before
    fun setup() {
        dao = dbRule.db.deletedSmsHashDao()
    }

    @Test
    fun `insert and getAllHashes returns correct hashes`() =
        runTest {
            dao.insert(DeletedSmsHash("hash_abc"))
            dao.insert(DeletedSmsHash("hash_def"))

            val result = dao.getAllHashes()

            assertEquals(2, result.size)
            assertTrue(result.containsAll(listOf("hash_abc", "hash_def")))
        }

    @Test
    fun `insert duplicate hash is silently ignored`() =
        runTest {
            dao.insert(DeletedSmsHash("hash_abc"))
            dao.insert(DeletedSmsHash("hash_abc")) // duplicate

            val result = dao.getAllHashes()

            assertEquals(1, result.size)
            assertEquals("hash_abc", result.first())
        }

    @Test
    fun `getAllHashes returns empty list when no hashes stored`() =
        runTest {
            val result = dao.getAllHashes()
            assertTrue(result.isEmpty())
        }
}
