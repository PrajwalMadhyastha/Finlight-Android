package io.pm.finlight.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionRunnerTest {
    private val mockDb: RoomDatabase = mockk(relaxed = true)

    @Before
    fun setup() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { any<RoomDatabase>().withTransaction<Any?>(any()) } coAnswers {
            val block = secondArg<suspend () -> Any?>()
            block()
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun run_withInjectedDb_executesBlockInTransactionAndReturnsResult() =
        runTest {
            val runner = RoomTransactionRunner(mockDb)
            val result = runner.run { "success" }

            assertEquals("success", result)
            coVerify(exactly = 1) { mockDb.withTransaction<Any?>(any()) }
        }

    @Test(expected = IllegalStateException::class)
    fun run_withInjectedDb_propagatesException() =
        runTest {
            val runner = RoomTransactionRunner(mockDb)
            runner.run { throw IllegalStateException("Transaction failure") }
        }

    @Test
    fun customTransactionRunner_executesBlockSuccessfully() =
        runTest {
            var executed = false
            val customRunner =
                object : TransactionRunner {
                    override suspend fun <R> run(block: suspend () -> R): R {
                        executed = true
                        return block()
                    }
                }

            val result = customRunner.run { "custom_run" }
            assertEquals("custom_run", result)
            assertTrue(executed)
        }
}
