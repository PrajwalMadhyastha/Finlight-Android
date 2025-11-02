package io.pm.finlight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage as AndroidSmsMessage
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.pm.finlight.*
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.dao.*
import io.pm.finlight.ml.SmsClassifier
import io.pm.finlight.utils.NotificationHelper
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
// --- FIX: Add missing kotlin.test imports ---
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@Config(
    sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE],
    application = TestApplication::class,
    shadows = [SmsReceiverTest.ShadowTelephonyIntents::class]
)
class SmsReceiverTest : BaseViewModelTest() {

    private lateinit var context: Context
    private lateinit var receiver: SmsReceiver
    private lateinit var mockPendingResult: BroadcastReceiver.PendingResult

    // Mocks for dependencies
    private lateinit var db: AppDatabase
    private lateinit var smsClassifier: SmsClassifier
    private lateinit var transactionDao: TransactionDao
    private lateinit var merchantMappingDao: MerchantMappingDao
    private lateinit var customSmsRuleDao: CustomSmsRuleDao
    private lateinit var ignoreRuleDao: IgnoreRuleDao
    private lateinit var merchantRenameRuleDao: MerchantRenameRuleDao
    private lateinit var merchantCategoryMappingDao: MerchantCategoryMappingDao
    private lateinit var smsParseTemplateDao: SmsParseTemplateDao
    private lateinit var accountDao: AccountDao
    private lateinit var accountAliasDao: AccountAliasDao
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var tagDao: TagDao


    @Implements(Telephony.Sms.Intents::class)
    object ShadowTelephonyIntents {
        var mockSmsMessages: Array<AndroidSmsMessage> = emptyArray()

        @JvmStatic
        @Implementation
        fun getMessagesFromIntent(intent: Intent?): Array<AndroidSmsMessage> {
            return mockSmsMessages
        }
    }


    @Before
    override fun setup() {
        super.setup()
        context = ApplicationProvider.getApplicationContext()

        db = mockk()
        transactionDao = mockk(relaxed = true) {
            coEvery { insert(any()) } returns 1L // Ensure insert returns a value
        }
        merchantMappingDao = mockk(relaxed = true)
        customSmsRuleDao = mockk(relaxed = true)
        ignoreRuleDao = mockk(relaxed = true)
        merchantRenameRuleDao = mockk(relaxed = true)
        merchantCategoryMappingDao = mockk(relaxed = true)
        smsParseTemplateDao = mockk(relaxed = true)
        accountDao = mockk(relaxed = true)
        accountAliasDao = mockk(relaxed = true)
        smsClassifier = mockk(relaxed = true)
        tagDao = mockk(relaxed = true)

        mockkObject(AppDatabase)
        mockkConstructor(SettingsRepository::class)
        mockkObject(NotificationHelper)

        receiver = spyk(SmsReceiver())
        receiver.smsClassifier = smsClassifier
        mockPendingResult = mockk(relaxed = true)
        every { receiver.goAsync() } returns mockPendingResult

        every { AppDatabase.getInstance(any()) } returns db
        every { db.transactionDao() } returns transactionDao
        every { db.merchantMappingDao() } returns merchantMappingDao
        every { db.customSmsRuleDao() } returns customSmsRuleDao
        every { db.ignoreRuleDao() } returns ignoreRuleDao
        every { db.merchantRenameRuleDao() } returns merchantRenameRuleDao
        every { db.merchantCategoryMappingDao() } returns merchantCategoryMappingDao
        every { db.smsParseTemplateDao() } returns smsParseTemplateDao
        every { db.accountDao() } returns accountDao
        every { db.accountAliasDao() } returns accountAliasDao
        every { db.tagDao() } returns tagDao


        every { smsClassifier.classify(any()) } returns 0.9f
        every { anyConstructed<SettingsRepository>().isAutoCaptureNotificationEnabledBlocking() } returns true
        every { anyConstructed<SettingsRepository>().getTravelModeSettings() } returns flowOf(null)
        every { anyConstructed<SettingsRepository>().getHomeCurrency() } returns flowOf("INR")
        every { NotificationHelper.showTravelModeSmsNotification(any(), any(), any()) } just runs

        coEvery { transactionDao.getAllSmsHashes() } returns flowOf(emptyList())
        coEvery { merchantMappingDao.getAllMappings() } returns flowOf(emptyList())
        coEvery { customSmsRuleDao.getAllRules() } returns flowOf(emptyList())
        coEvery { ignoreRuleDao.getEnabledRules() } returns emptyList()
        coEvery { merchantRenameRuleDao.getAllRules() } returns flowOf(emptyList())
        coEvery { merchantCategoryMappingDao.getCategoryIdForMerchant(any()) } returns null
        coEvery { smsParseTemplateDao.getAllTemplates() } returns emptyList()
        coEvery { smsParseTemplateDao.getTemplatesBySignature(any()) } returns emptyList()
        coEvery { accountDao.findByName(any()) } returns null
        coEvery { accountDao.insert(any()) } returns 1L
        coEvery { accountDao.getAccountByIdBlocking(any()) } returns Account(1, "Test", "Test")
        coEvery { accountAliasDao.findByAlias(any()) } returns null
        coEvery { tagDao.findByName(any()) } returns null
        coEvery { tagDao.insert(any()) } returns 1L
        // --- FIX: This mock was incorrect (method not on DAO) and has been removed ---
        // coEvery { tagDao.findOrCreateTag(any()) } returns Tag(1, "Test Tag")
        coEvery { transactionDao.addTagsToTransaction(any()) } just runs
    }

    @After
    override fun tearDown() {
        ShadowTelephonyIntents.mockSmsMessages = emptyArray()
        unmockkAll()
        super.tearDown()
    }

    private fun createSmsIntent(sender: String, body: String): Intent {
        val mockMessage = mockk<AndroidSmsMessage>()
        every { mockMessage.originatingAddress } returns sender
        every { mockMessage.messageBody } returns body
        every { mockMessage.timestampMillis } returns System.currentTimeMillis()
        ShadowTelephonyIntents.mockSmsMessages = arrayOf(mockMessage)

        return Intent(Telephony.Sms.Intents.SMS_RECEIVED_ACTION)
    }

    @Test
    fun `valid new SMS is parsed and saved`() = runTest {
        // Arrange
        val sender = "AM-HDFCBK"
        val body = "Spent Rs.100 at Starbucks"
        val intent = createSmsIntent(sender, body)
        receiver.coroutineScope = this // Inject TestScope

        // Act
        receiver.onReceive(context, intent)
        advanceUntilIdle() // Ensure the launched coroutine completes

        // Assert
        coVerify(exactly = 1) { transactionDao.insert(any()) }
        verify(exactly = 1) { mockPendingResult.finish() }
    }

    @Test
    fun `message matching ignore rule is ignored`() = runTest {
        // Arrange
        val sender = "AM-PROMO"
        val body = "Your OTP is 1234"
        val intent = createSmsIntent(sender, body)
        coEvery { ignoreRuleDao.getEnabledRules() } returns listOf(IgnoreRule(pattern = "OTP", type = RuleType.BODY_PHRASE, isDefault = true))
        receiver.coroutineScope = this


        // Act
        receiver.onReceive(context, intent)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { transactionDao.insert(any()) }
        verify(exactly = 1) { mockPendingResult.finish() }
    }

    @Test
    fun `ML model filters out non-transactional message`() = runTest {
        // Arrange
        val sender = "AM-JUNK"
        val body = "This is not a transaction"
        val intent = createSmsIntent(sender, body)
        every { smsClassifier.classify("This is not a transaction") } returns 0.05f
        receiver.coroutineScope = this

        // Act
        receiver.onReceive(context, intent)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { transactionDao.insert(any()) }
        verify(exactly = 1) { mockPendingResult.finish() }
    }

    @Test
    fun `already processed SMS hash is ignored`() = runTest {
        // Arrange
        val sender = "AM-HDFCBK"
        val body = "Spent Rs.100 at Starbucks"
        val intent = createSmsIntent(sender, body)
        val normalizedBody = body.replace(Regex("\\s+"), " ").trim()
        val hash = (sender.filter { it.isDigit() }.takeLast(10) + normalizedBody).hashCode().toString()
        coEvery { transactionDao.getAllSmsHashes() } returns flowOf(listOf(hash))
        receiver.coroutineScope = this

        // Act
        receiver.onReceive(context, intent)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 0) { transactionDao.insert(any()) }
        verify(exactly = 1) { mockPendingResult.finish() }
    }

    @Test
    fun `international travel mode with ambiguous currency defaults to home currency`() = runTest {
        // Arrange
        val sender = "AM-HDFCBK"
        val body = "Spent 50 at Starbucks" // No currency
        val intent = createSmsIntent(sender, body)

        val travelSettings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 0L, Long.MAX_VALUE, "USD", 83.5f)
        every { anyConstructed<SettingsRepository>().getTravelModeSettings() } returns flowOf(travelSettings)
        // The parser will default to INR, which is our mocked home currency.
        every { anyConstructed<SettingsRepository>().getHomeCurrency() } returns flowOf("INR")
        receiver.coroutineScope = this

        // Act
        receiver.onReceive(context, intent)
        advanceUntilIdle()

        // Assert
        // Verify that the notification is NOT called, and instead the transaction is saved directly as a home currency transaction.
        verify(exactly = 0) { NotificationHelper.showTravelModeSmsNotification(any(), any(), any()) }
        coVerify(exactly = 1) { transactionDao.insert(any()) }
        verify(exactly = 1) { mockPendingResult.finish() }
    }

    @Test
    fun `travel mode international SMS is saved with currency conversion`() = runTest {
        // Arrange
        val sender = "AM-CITI"
        val body = "Spent USD 10.00 at Starbucks"
        val intent = createSmsIntent(sender, body)
        val travelSettings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 0L, Long.MAX_VALUE, "USD", 80.0f)
        val transactionCaptor = slot<Transaction>()

        every { anyConstructed<SettingsRepository>().getTravelModeSettings() } returns flowOf(travelSettings)
        every { anyConstructed<SettingsRepository>().getHomeCurrency() } returns flowOf("INR")
        coEvery { transactionDao.insert(capture(transactionCaptor)) } returns 1L
        receiver.coroutineScope = this

        // Act
        receiver.onReceive(context, intent)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { transactionDao.insert(any()) }
        val savedTx = transactionCaptor.captured
        assertEquals(800.0, savedTx.amount, 0.0) // 10.0 * 80.0
        // --- FIX: Assert non-null first, then use !! for the type-safe assertEquals ---
        assertNotNull(savedTx.originalAmount)
        assertEquals(10.0, savedTx.originalAmount!!, 0.0)
        assertEquals("USD", savedTx.currencyCode)
        // --- FIX: Assert non-null first, then use !! for the type-safe assertEquals ---
        assertNotNull(savedTx.conversionRate)
        assertEquals(80.0, savedTx.conversionRate!!, 0.0)
        coVerify(exactly = 1) { transactionDao.addTagsToTransaction(any()) } // Travel tag was added
    }

    @Test
    fun `travel mode SMS matching home currency is saved without conversion`() = runTest {
        // Arrange
        val sender = "AM-HDFCBK"
        val body = "Spent Rs. 500 at Airport Lounge"
        val intent = createSmsIntent(sender, body)
        val travelSettings = TravelModeSettings(true, "US Trip", TripType.INTERNATIONAL, 0L, Long.MAX_VALUE, "USD", 80.0f)
        val transactionCaptor = slot<Transaction>()

        every { anyConstructed<SettingsRepository>().getTravelModeSettings() } returns flowOf(travelSettings)
        every { anyConstructed<SettingsRepository>().getHomeCurrency() } returns flowOf("INR")
        coEvery { transactionDao.insert(capture(transactionCaptor)) } returns 1L
        receiver.coroutineScope = this

        // Act
        receiver.onReceive(context, intent)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { transactionDao.insert(any()) }
        val savedTx = transactionCaptor.captured
        assertEquals(500.0, savedTx.amount, 0.0) // Amount is the original INR amount
        assertNull(savedTx.originalAmount) // No conversion
        assertNull(savedTx.currencyCode)
        assertNull(savedTx.conversionRate)
        coVerify(exactly = 1) { transactionDao.addTagsToTransaction(any()) } // Travel tag was added
    }
}

