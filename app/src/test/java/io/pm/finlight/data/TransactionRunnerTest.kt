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
    private val customDb: RoomDatabase = mockk(relaxed = true)

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
    fun run_withExplicitDb_executesBlockInTransactionOnPassedDb() =
        runTest {
            val runner = RoomTransactionRunner(mockDb)
            val result = runner.run(customDb) { 42 }

            assertEquals(42, result)
            coVerify(exactly = 1) { customDb.withTransaction<Any?>(any()) }
            coVerify(exactly = 0) { mockDb.withTransaction<Any?>(any()) }
        }

    @Test
    fun defaultInterfaceMethod_delegatesToZeroArgRun() =
        runTest {
            var called = false
            val customRunner =
                object : TransactionRunner {
                    override suspend fun <R> run(block: suspend () -> R): R {
                        called = true
                        return block()
                    }
                }

            val result = customRunner.run(mockDb) { "default_run" }
            assertEquals("default_run", result)
            assertTrue(called)
        }
}
