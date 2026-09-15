package io.pm.finlight.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SmsRepository
import io.pm.finlight.TestApplication
import io.pm.finlight.utils.DispatcherProvider
import io.pm.finlight.utils.TestDispatcherProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsRepositoryTest : BaseViewModelTest() {
    @Mock
    private lateinit var contentResolver: ContentResolver

    @Mock
    private lateinit var mockContext: TestApplication

    private lateinit var testDispatcherProvider: TestDispatcherProvider
    private lateinit var repository: SmsRepository

    private val smsColumns =
        arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
        )

    @Before
    override fun setup() {
        super.setup()
        `when`(mockContext.contentResolver).thenReturn(contentResolver)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
        testDispatcherProvider = TestDispatcherProvider(testDispatcher)
        repository = SmsRepository(mockContext, testDispatcherProvider)
    }

    @Test
    fun `fetchAllSms queries content resolver with correct parameters`() =
        runTest {
            // Arrange
            val cursor = MatrixCursor(smsColumns)
            cursor.addRow(arrayOf(1L, "Sender1", "Body1", 1000L))
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any())).thenReturn(cursor)

            // Act
            val result = repository.fetchAllSms(null)

            // Assert
            assertEquals(1, result.size)
            assertEquals("Sender1", result[0].sender)
            assertEquals("Body1", result[0].body)
            assertEquals(1L, result[0].id)
            assertEquals(1000L, result[0].date)
            verify(contentResolver).query(
                eq(Telephony.Sms.Inbox.CONTENT_URI),
                any(),
                // No date selection
                eq(null),
                eq(null),
                eq("date DESC"),
            )
        }

    @Test
    fun `fetchAllSms with startDate applies correct selection`() =
        runTest {
            // Arrange
            val startDate = 500L
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any())).thenReturn(MatrixCursor(smsColumns))

            // Act
            repository.fetchAllSms(startDate)

            // Assert
            verify(contentResolver).query(
                any(),
                any(),
                eq("${Telephony.Sms.DATE} >= ?"),
                eq(arrayOf(startDate.toString())),
                any(),
            )
        }

    @Test
    fun `fetchAllSms with startDate and endDate applies correct selection`() =
        runTest {
            val startDate = 500L
            val endDate = 1500L
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any())).thenReturn(MatrixCursor(smsColumns))

            repository.fetchAllSms(startDate, endDate)

            verify(contentResolver).query(
                any(),
                any(),
                eq("${Telephony.Sms.DATE} >= ? AND ${Telephony.Sms.DATE} <= ?"),
                eq(arrayOf(startDate.toString(), endDate.toString())),
                any(),
            )
        }

    @Test
    fun `fetchAllSms with endDate only applies correct selection`() =
        runTest {
            val endDate = 1500L
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any())).thenReturn(MatrixCursor(smsColumns))

            repository.fetchAllSms(null, endDate)

            verify(contentResolver).query(
                any(),
                any(),
                eq("${Telephony.Sms.DATE} <= ?"),
                eq(arrayOf(endDate.toString())),
                any(),
            )
        }

    @Test
    fun `fetchAllSms handles null cursor gracefully`() =
        runTest {
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any())).thenReturn(null)

            val result = repository.fetchAllSms(null)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `fetchAllSms handles null values in cursor columns with fallbacks`() =
        runTest {
            val cursor = MatrixCursor(smsColumns)
            cursor.addRow(arrayOf(10L, null, null, 2000L))
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any())).thenReturn(cursor)

            val result = repository.fetchAllSms(null)

            assertEquals(1, result.size)
            assertEquals("Unknown", result[0].sender)
            assertEquals("", result[0].body)
        }

    @Test
    fun `getSmsDetailsById first attempts to query by _ID`() =
        runTest {
            // Arrange
            val smsId = 123L
            val cursor = MatrixCursor(smsColumns)
            cursor.addRow(arrayOf(smsId, "SenderID", "BodyByID", 1000L))
            `when`(contentResolver.query(any(), any(), eq("${Telephony.Sms._ID} = ?"), eq(arrayOf(smsId.toString())), any())).thenReturn(cursor)

            // Act
            val result = repository.getSmsDetailsById(smsId)

            // Assert
            assertNotNull(result)
            assertEquals(smsId, result?.id)
            assertEquals("SenderID", result?.sender)
            assertEquals("BodyByID", result?.body)
            assertEquals(1000L, result?.date)
        }

    @Test
    fun `getSmsDetailsById falls back to querying by closest date if ID fails`() =
        runTest {
            // Arrange
            val timestamp = 2000L
            // First query by ID returns an empty cursor
            `when`(
                contentResolver.query(any(), any(), eq("${Telephony.Sms._ID} = ?"), eq(arrayOf(timestamp.toString())), any()),
            ).thenReturn(MatrixCursor(smsColumns))

            // Second query by closest date returns a result
            val dateCursor = MatrixCursor(smsColumns)
            dateCursor.addRow(arrayOf(456L, "SenderDate", "BodyByDate", timestamp))
            `when`(contentResolver.query(any(), any(), eq(null), eq(null), eq("ABS(date - $timestamp) ASC LIMIT 1"))).thenReturn(dateCursor)

            // Act
            val result = repository.getSmsDetailsById(timestamp)

            // Assert
            assertNotNull(result)
            assertEquals(456L, result?.id)
            assertEquals("SenderDate", result?.sender)
            assertEquals("BodyByDate", result?.body)
            assertEquals(timestamp, result?.date)
        }

    @Test
    fun `getSmsDetailsById returns null if both ID and fallback queries fail`() =
        runTest {
            val lookupValue = 9999L
            `when`(
                contentResolver.query(any(), any(), eq("${Telephony.Sms._ID} = ?"), eq(arrayOf(lookupValue.toString())), any()),
            ).thenReturn(null)
            `when`(contentResolver.query(any(), any(), eq(null), eq(null), eq("ABS(date - $lookupValue) ASC LIMIT 1"))).thenReturn(null)

            val result = repository.getSmsDetailsById(lookupValue)

            assertNull(result)
        }

    @Test
    fun `getSmsDetailsById handles null sender and body in result`() =
        runTest {
            val smsId = 777L
            val cursor = MatrixCursor(smsColumns)
            cursor.addRow(arrayOf(smsId, null, null, 1000L))
            `when`(contentResolver.query(any(), any(), eq("${Telephony.Sms._ID} = ?"), eq(arrayOf(smsId.toString())), any())).thenReturn(cursor)

            val result = repository.getSmsDetailsById(smsId)

            assertNotNull(result)
            assertEquals("Unknown", result?.sender)
            assertEquals("", result?.body)
        }

    @Test
    fun `retains applicationContext to prevent leaking activity context`() =
        runTest {
            val mockActivityContext = mock(Context::class.java)
            val mockAppContext = mock(Context::class.java)
            val appContentResolver = mock(ContentResolver::class.java)

            `when`(mockActivityContext.applicationContext).thenReturn(mockAppContext)
            `when`(mockAppContext.contentResolver).thenReturn(appContentResolver)

            val repo = SmsRepository(mockActivityContext, testDispatcherProvider)
            val cursor = MatrixCursor(smsColumns)
            `when`(appContentResolver.query(any(), any(), any(), any(), any())).thenReturn(cursor)

            val result = repo.fetchAllSms(null)

            assertTrue(result.isEmpty())
            verify(appContentResolver).query(any(), any(), any(), any(), any())
            verify(mockActivityContext).applicationContext
        }

    @Test
    fun `falls back to passed context if applicationContext is null`() =
        runTest {
            val mockContextWithoutApp = mock(Context::class.java)
            val directContentResolver = mock(ContentResolver::class.java)

            `when`(mockContextWithoutApp.applicationContext).thenReturn(null)
            `when`(mockContextWithoutApp.contentResolver).thenReturn(directContentResolver)

            val repo = SmsRepository(mockContextWithoutApp, testDispatcherProvider)
            `when`(directContentResolver.query(any(), any(), any(), any(), any())).thenReturn(null)

            val result = repo.fetchAllSms(null)

            assertTrue(result.isEmpty())
            verify(directContentResolver).query(any(), any(), any(), any(), any())
        }

    @Test
    fun `executes content resolver operations on dispatcherProvider io`() =
        runTest {
            var ioDispatcherCalled = false
            val trackedDispatcherProvider =
                object : DispatcherProvider {
                    override val main: CoroutineDispatcher get() = testDispatcher
                    override val io: CoroutineDispatcher
                        get() {
                            ioDispatcherCalled = true
                            return testDispatcher
                        }
                    override val default: CoroutineDispatcher get() = testDispatcher
                    override val unconfined: CoroutineDispatcher get() = testDispatcher
                }

            val repo = SmsRepository(mockContext, trackedDispatcherProvider)
            `when`(contentResolver.query(any(), any(), any(), any(), any())).thenReturn(null)

            repo.fetchAllSms(null)

            assertTrue(ioDispatcherCalled)
        }

    @Test
    fun `fetchAllSms handles SecurityException gracefully`() =
        runTest {
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any()))
                .thenThrow(SecurityException("Permission denied"))

            val result = repository.fetchAllSms(null)

            assertTrue(result.isEmpty())
        }

    @Test
    fun `getSmsDetailsById handles SecurityException gracefully`() =
        runTest {
            `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any()))
                .thenThrow(SecurityException("Permission denied"))

            val result = repository.getSmsDetailsById(123L)

            assertNull(result)
        }
}
