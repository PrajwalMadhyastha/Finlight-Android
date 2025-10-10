package io.pm.finlight.data.repository

import android.content.ContentResolver
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.SmsRepository
import io.pm.finlight.TestApplication
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.robolectric.annotation.Config

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class SmsRepositoryTest : BaseViewModelTest() {

    @Mock
    private lateinit var contentResolver: ContentResolver

    @Mock
    private lateinit var mockContext: TestApplication

    private lateinit var repository: SmsRepository

    private val smsColumns = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE
    )

    @Before
    override fun setup() {
        super.setup()
        `when`(mockContext.contentResolver).thenReturn(contentResolver)
        repository = SmsRepository(mockContext)
    }

    @Test
    fun `fetchAllSms queries content resolver with correct parameters`() {
        // Arrange
        val cursor = MatrixCursor(smsColumns)
        cursor.addRow(arrayOf(1L, "Sender1", "Body1", 1000L))
        `when`(contentResolver.query(any(Uri::class.java), any(), any(), any(), any())).thenReturn(cursor)

        // Act
        val result = repository.fetchAllSms(null)

        // Assert
        assertEquals(1, result.size)
        assertEquals("Sender1", result[0].sender)
        verify(contentResolver).query(
            eq(Telephony.Sms.Inbox.CONTENT_URI),
            any(),
            eq(null), // No date selection
            eq(null),
            eq("date DESC")
        )
    }

    @Test
    fun `fetchAllSms with startDate applies correct selection`() {
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
            any()
        )
    }

    @Test
    fun `getSmsDetailsById first attempts to query by _ID`() {
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
    }

    @Test
    fun `getSmsDetailsById falls back to querying by closest date if ID fails`() {
        // Arrange
        val timestamp = 2000L
        // First query by ID returns an empty cursor
        `when`(contentResolver.query(any(), any(), eq("${Telephony.Sms._ID} = ?"), eq(arrayOf(timestamp.toString())), any())).thenReturn(MatrixCursor(smsColumns))

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
    }
}

