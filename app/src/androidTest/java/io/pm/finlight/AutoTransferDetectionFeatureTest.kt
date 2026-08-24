package io.pm.finlight

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.data.db.AppDatabase
import io.pm.finlight.data.db.entity.AccountAlias
import io.pm.finlight.utils.SmsTransactionSaver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoTransferDetectionFeatureTest {
    private lateinit var db: AppDatabase
    private lateinit var smsTransactionSaver: SmsTransactionSaver

    @Before
    fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        db = AppDatabase.getInstance(context)

        runBlocking {
            db.transactionDao().deleteAll()
            db.accountDao().deleteAll()
            db.accountAliasDao().deleteAll()

            // Seed two accounts
            val acc1 = Account(name = "Account A", type = "bank")
            val acc2 = Account(name = "Account B", type = "bank")
            db.accountDao().insert(acc1)
            db.accountDao().insert(acc2)

            // Setup sms transaction saver
            val settingsRepo = io.pm.finlight.SettingsRepository(context)
            smsTransactionSaver = SmsTransactionSaver(db, settingsRepo)
        }
    }

    @After
    fun teardown() {
        // Clean up
        runBlocking {
            db.transactionDao().deleteAll()
            db.accountDao().deleteAll()
            db.accountAliasDao().deleteAll()
        }
    }

    @Test
    fun test_autoTransferDetection_linksTransactions() =
        runBlocking {
            // Find exactly the created accounts
            val accounts = db.accountDao().getAllAccounts().first()
            val accountAId = accounts.first { it.name == "Account A" }.id
            val accountBId = accounts.first { it.name == "Account B" }.id

            // Add an alias for Account B to simulate how SMS matching works
            db.accountAliasDao().insertAll(listOf(AccountAlias(aliasName = "AccB-Alias", destinationAccountId = accountBId)))

            // Create an expense from Account A
            val expenseTxn =
                io.pm.finlight.PotentialTransaction(
                    sourceSmsId = 100L,
                    smsSender = "BANK-A",
                    amount = 500.0,
                    transactionType = "expense",
                    // Matches the alias of Account B
                    merchantName = "AccB-Alias",
                    originalMessage = "Sent 500 to AccB-Alias",
                    date = System.currentTimeMillis(),
                    potentialAccount = io.pm.finlight.PotentialAccount(formattedName = "Account A", accountType = "bank")
                )

            // Create an income to Account B within 5 mins (strict timeframe)
            val incomeTxn =
                io.pm.finlight.PotentialTransaction(
                    sourceSmsId = 101L,
                    smsSender = "BANK-B",
                    amount = 500.0,
                    transactionType = "income",
                    merchantName = "Account A Transfer",
                    originalMessage = "Received 500 from Account A",
                    // 1 min later
                    date = System.currentTimeMillis() + 60 * 1000L,
                    potentialAccount = io.pm.finlight.PotentialAccount(formattedName = "Account B", accountType = "bank")
                )

            // Save both through SmsTransactionSaver
            smsTransactionSaver.resolveAndSaveTransaction(
                potentialTxn = expenseTxn,
                isForeign = false,
                travelSettings = null,
                source = "AUTO"
            )

            smsTransactionSaver.resolveAndSaveTransaction(
                potentialTxn = incomeTxn,
                isForeign = false,
                travelSettings = null,
                source = "AUTO"
            )

            // Verify that they are linked and excluded
            val allTxns = db.transactionDao().getAllTransactionsSimple().first()
            assert(allTxns.size == 2) { "Both transactions should be saved" }

            val savedExpense = allTxns.find { it.transactionType == TransactionType.EXPENSE }!!
            val savedIncome = allTxns.find { it.transactionType == TransactionType.INCOME }!!

            assert(savedExpense.linkedTransferId == savedIncome.id) { "Expense should link to Income" }
            assert(savedIncome.linkedTransferId == savedExpense.id) { "Income should link to Expense" }
            assert(savedExpense.isExcluded) { "Expense should be marked excluded" }
            assert(savedIncome.isExcluded) { "Income should be marked excluded" }
        }
}
