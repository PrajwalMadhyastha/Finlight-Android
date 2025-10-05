// =================================================================================
// FILE: ./app/src/test/java/io/pm/finlight/BaseViewModelTest.kt
// REASON: NEW FILE - This abstract base class centralizes common setup logic for
// all ViewModel unit tests. It includes the InstantTaskExecutorRule for LiveData,
// coroutine test dispatcher management, and Mockito initialization.
// =================================================================================
package io.pm.finlight

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

// Helper function for creating ArgumentCaptor in a more Kotlin-friendly way
inline fun <reified T : Any> argumentCaptor(): ArgumentCaptor<T> = ArgumentCaptor.forClass(T::class.java)

/**
 * A helper function to use Mockito's `any()` matcher in a way that is safe
 * for Kotlin's non-nullable types.
 *
 * It calls `Mockito.any()` to register the matcher with Mockito's internal state,
 * but returns a 'null' cast to the required type `T`. This satisfies the Kotlin
 * compiler for the function signature, while Mockito's stubbing mechanism
 * correctly uses the registered 'any' matcher, avoiding a NullPointerException.
 */
fun <T> anyNonNull(): T {
    Mockito.any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
}


/**
 * An abstract base class for all ViewModel unit tests in the Finlight project.
 *
 * This class provides the common setup required for testing Android ViewModels, including:
 * 1.  **InstantTaskExecutorRule**: Ensures that LiveData and other Architecture Components
 * tasks execute synchronously on the test thread.
 * 2.  **TestCoroutineDispatcher**: Replaces the main coroutine dispatcher with a test dispatcher
 * to allow for precise control over coroutine execution in tests.
 * 3.  **Mockito Initialization**: Automatically initializes any fields annotated with `@Mock`.
 *
 * Test classes should extend this base class to inherit this boilerplate setup,
 * making them cleaner and more focused on the actual test logic.
 */
@ExperimentalCoroutinesApi
abstract class BaseViewModelTest {

    /**
     * JUnit rule to execute LiveData and other background tasks from Architecture Components
     * synchronously. This is essential for testing LiveData emissions.
     */
    @get:Rule
    var instantTaskExecutorRule = InstantTaskExecutorRule()

    /**
     * A test dispatcher for coroutines that executes them eagerly, allowing tests to
     * run sequentially without needing to manually advance the clock.
     */
    protected val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()

    /**
     * Sets up the test environment before each test case.
     * - Initializes all fields annotated with `@Mock`.
     * - Replaces the main coroutine dispatcher with our `testDispatcher`.
     */
    @Before
    open fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    /**
     * Tears down the test environment after each test case.
     * - Resets the main coroutine dispatcher to its original state.
     */
    @After
    open fun tearDown() {
        Dispatchers.resetMain()
    }
}