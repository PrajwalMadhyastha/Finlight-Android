package io.pm.finlight.utils

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.pm.finlight.Tag
import io.pm.finlight.TestApplication
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDetails
import io.pm.finlight.TransactionType
import io.pm.finlight.ui.components.ShareableField
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class ShareImageGeneratorTest {
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    }

    @After
    fun tearDown() {
        ShareImageGenerator.bitmapProvider = ShareImageGenerator::createBitmapFromComposable
    }

    private fun createSampleData(): List<ShareImageGenerator.TransactionSnapshotData> {
        val txn =
            Transaction(
                id = 1,
                description = "Whole Foods Grocery",
                categoryId = 1,
                amount = 1500.0,
                date = System.currentTimeMillis(),
                accountId = 1,
                notes = null,
                transactionType = TransactionType.EXPENSE,
            )
        val details =
            TransactionDetails(
                transaction = txn,
                images = emptyList(),
                accountName = "Primary Account",
                categoryName = "Groceries",
                categoryIconKey = null,
                categoryColorKey = null,
                tagNames = "Organic, Dinner",
            )
        val tags = listOf(Tag(id = 1, name = "Organic"), Tag(id = 2, name = "Dinner"))
        return listOf(ShareImageGenerator.TransactionSnapshotData(details = details, tags = tags))
    }

    @Test
    fun `shareTransactionsAsImage recycles bitmap on successful share`() {
        val realBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        ShareImageGenerator.bitmapProvider = { _, _, _ -> realBitmap }

        ShareImageGenerator.shareTransactionsAsImage(
            context = activity,
            transactionsWithData = createSampleData(),
            fields = setOf(ShareableField.Amount, ShareableField.Description, ShareableField.Category),
        )

        // Acceptance Criteria: bitmap.recycle() must be called
        assertTrue("Bitmap should be recycled after sharing", realBitmap.isRecycled)

        // Verify share intent chooser was started
        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull("An intent should have been dispatched", startedIntent)
        assertEquals(Intent.ACTION_CHOOSER, startedIntent.action)
        val targetIntent = IntentCompat.getParcelableExtra(startedIntent, Intent.EXTRA_INTENT, Intent::class.java)
        assertNotNull(targetIntent)
        assertEquals(Intent.ACTION_SEND, targetIntent?.action)
        assertEquals("image/png", targetIntent?.type)
        assertTrue(targetIntent!!.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test
    fun `shareTransactionsAsImage recycles bitmap when compression throws exception`() {
        val mockBitmap = mockk<Bitmap>()
        every { mockBitmap.compress(any(), any(), any()) } throws RuntimeException("Disk full simulated")
        every { mockBitmap.recycle() } returns Unit
        ShareImageGenerator.bitmapProvider = { _, _, _ -> mockBitmap }

        assertThrows(RuntimeException::class.java) {
            ShareImageGenerator.shareTransactionsAsImage(
                context = activity,
                transactionsWithData = createSampleData(),
                fields = setOf(ShareableField.Amount),
            )
        }

        // Acceptance Criteria: bitmap.recycle() must be called in finally block even on error
        verify(exactly = 1) { mockBitmap.recycle() }
    }

    @Test
    fun `shareTransactionsAsImage throws IllegalArgumentException when context is not an Activity`() {
        val appContext = ApplicationProvider.getApplicationContext<android.content.Context>()

        assertThrows(IllegalArgumentException::class.java) {
            ShareImageGenerator.shareTransactionsAsImage(
                context = appContext,
                transactionsWithData = createSampleData(),
                fields = setOf(ShareableField.Amount),
            )
        }
    }

    @Test
    fun `TransactionSnapshotData model properties match expectations`() {
        val txn =
            Transaction(
                id = 42,
                description = "Coffee",
                categoryId = 2,
                amount = 99.0,
                date = 1000L,
                accountId = 1,
                notes = "Morning cup",
                transactionType = TransactionType.EXPENSE,
            )
        val details =
            TransactionDetails(
                transaction = txn,
                images = emptyList(),
                accountName = "Wallet",
                categoryName = "Cafe",
                categoryIconKey = null,
                categoryColorKey = null,
                tagNames = null,
            )
        val tags = listOf(Tag(1, "Morning"))
        val snapshot = ShareImageGenerator.TransactionSnapshotData(details = details, tags = tags)

        assertEquals(details, snapshot.details)
        assertEquals(tags, snapshot.tags)
    }
}
