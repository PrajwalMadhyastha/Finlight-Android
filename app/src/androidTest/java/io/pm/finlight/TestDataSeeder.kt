package io.pm.finlight

import io.pm.finlight.data.db.AppDatabase
import kotlinx.coroutines.runBlocking
import java.util.Calendar

/**
 * A utility object for seeding a canonical, deterministic set of test data into the
 * application's live database before instrumented UI tests run.
 *
 * This is the **single source of truth** for all data referenced by UI tests.
 * Any test that checks for "Test Bank" or "Food & Drinks" is reading data
 * inserted by this seeder, never relying on whatever happens to be on the device.
 *
 * Seeded entities (all with fixed IDs to make cross-references reliable):
 *  - Accounts: "Test Wallet" (Cash, id=9001), "Test Bank" (Bank, id=9002)
 *  - Categories: "Food & Drinks" (id=9001), "Transport" (id=9002), "Shopping" (id=9003)
 *  - Transactions: 5 expenses + 2 income entries, all within the current month
 *  - Budget: One "Food & Drinks" budget for the current month at ₹5000
 *  - Tag: "test-tag" (id=9001)
 *
 * IDs are intentionally high (9000+) to avoid collisions with default app data
 * (categories, accounts) which the DatabaseCallback seeds on first launch.
 */
object TestDataSeeder {

    // --- Public constants so tests can reference these without magic strings ---
    const val ACCOUNT_WALLET_NAME = "Test Wallet"
    const val ACCOUNT_WALLET_ID = 9001
    const val ACCOUNT_BANK_NAME = "Test Bank"
    const val ACCOUNT_BANK_ID = 9002

    const val CATEGORY_FOOD_NAME = "Food & Drinks"
    const val CATEGORY_FOOD_ID = 9001
    const val CATEGORY_TRANSPORT_NAME = "Transport"
    const val CATEGORY_TRANSPORT_ID = 9002
    const val CATEGORY_SHOPPING_NAME = "Shopping"
    const val CATEGORY_SHOPPING_ID = 9003

    const val BUDGET_FOOD_AMOUNT = 5000.0

    const val TAG_NAME = "test-tag"
    const val TAG_ID = 9001

    // Stable descriptions so tests can look them up by text
    const val TXN_COFFEE_DESC = "Test Coffee"
    const val TXN_BUS_DESC = "Test Bus Fare"
    const val TXN_GROCERY_DESC = "Test Grocery Run"
    const val TXN_SHIRT_DESC = "Test Shirt Purchase"
    const val TXN_TAXI_DESC = "Test Taxi"
    const val TXN_SALARY_DESC = "Test Salary"
    const val TXN_BONUS_DESC = "Test Bonus"

    /**
     * Seeds all canonical test data into the provided [AppDatabase].
     *
     * This is a blocking call and must NOT be called from the main thread.
     * It is safe to call multiple times — all inserts use IGNORE/REPLACE
     * conflict strategies so re-seeding is idempotent.
     */
    fun seed(db: AppDatabase) = runBlocking {
        seedAccounts(db)
        seedCategories(db)
        seedTransactions(db)
        seedBudget(db)
        seedTag(db)
    }

    // -------------------------------------------------------------------------
    // Private seed helpers
    // -------------------------------------------------------------------------

    private suspend fun seedAccounts(db: AppDatabase) {
        db.accountDao().insertAll(
            listOf(
                Account(id = ACCOUNT_WALLET_ID, name = ACCOUNT_WALLET_NAME, type = "Cash"),
                Account(id = ACCOUNT_BANK_ID, name = ACCOUNT_BANK_NAME, type = "Bank"),
            ),
        )
    }

    private suspend fun seedCategories(db: AppDatabase) {
        db.categoryDao().insertAll(
            listOf(
                Category(
                    id = CATEGORY_FOOD_ID,
                    name = CATEGORY_FOOD_NAME,
                    iconKey = "restaurant",
                    colorKey = "orange_light",
                ),
                Category(
                    id = CATEGORY_TRANSPORT_ID,
                    name = CATEGORY_TRANSPORT_NAME,
                    iconKey = "directions_bus",
                    colorKey = "blue_light",
                ),
                Category(
                    id = CATEGORY_SHOPPING_ID,
                    name = CATEGORY_SHOPPING_NAME,
                    iconKey = "shopping_bag",
                    colorKey = "purple_light",
                ),
            ),
        )
    }

    private suspend fun seedTransactions(db: AppDatabase) {
        val now = System.currentTimeMillis()
        // Place all transactions in the current calendar month so report
        // screens display them without having to navigate to a past period.
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 10)
        val monthStart = cal.timeInMillis

        db.transactionDao().run {
            // ---- Expenses ----
            insert(
                Transaction(
                    description = TXN_COFFEE_DESC,
                    amount = 150.0,
                    categoryId = CATEGORY_FOOD_ID,
                    accountId = ACCOUNT_WALLET_ID,
                    date = monthStart + 1 * 86_400_000L,
                    transactionType = "expense",
                    notes = null,
                ),
            )
            insert(
                Transaction(
                    description = TXN_BUS_DESC,
                    amount = 50.0,
                    categoryId = CATEGORY_TRANSPORT_ID,
                    accountId = ACCOUNT_WALLET_ID,
                    date = monthStart + 2 * 86_400_000L,
                    transactionType = "expense",
                    notes = null,
                ),
            )
            insert(
                Transaction(
                    description = TXN_GROCERY_DESC,
                    amount = 800.0,
                    categoryId = CATEGORY_FOOD_ID,
                    accountId = ACCOUNT_BANK_ID,
                    date = monthStart + 3 * 86_400_000L,
                    transactionType = "expense",
                    notes = "Weekly groceries",
                ),
            )
            insert(
                Transaction(
                    description = TXN_SHIRT_DESC,
                    amount = 600.0,
                    categoryId = CATEGORY_SHOPPING_ID,
                    accountId = ACCOUNT_BANK_ID,
                    date = monthStart + 4 * 86_400_000L,
                    transactionType = "expense",
                    notes = null,
                ),
            )
            insert(
                Transaction(
                    description = TXN_TAXI_DESC,
                    amount = 250.0,
                    categoryId = CATEGORY_TRANSPORT_ID,
                    accountId = ACCOUNT_WALLET_ID,
                    date = monthStart + 5 * 86_400_000L,
                    transactionType = "expense",
                    notes = null,
                ),
            )
            // ---- Income ----
            insert(
                Transaction(
                    description = TXN_SALARY_DESC,
                    amount = 50000.0,
                    categoryId = null,
                    accountId = ACCOUNT_BANK_ID,
                    date = monthStart + 1 * 86_400_000L,
                    transactionType = "income",
                    notes = "Monthly salary",
                ),
            )
            insert(
                Transaction(
                    description = TXN_BONUS_DESC,
                    amount = 10000.0,
                    categoryId = null,
                    accountId = ACCOUNT_BANK_ID,
                    date = monthStart + 6 * 86_400_000L,
                    transactionType = "income",
                    notes = null,
                ),
            )
        }
    }

    private suspend fun seedBudget(db: AppDatabase) {
        val cal = Calendar.getInstance()
        val month = cal.get(Calendar.MONTH) + 1 // Calendar.MONTH is 0-indexed
        val year = cal.get(Calendar.YEAR)
        db.budgetDao().insert(
            Budget(
                categoryName = CATEGORY_FOOD_NAME,
                amount = BUDGET_FOOD_AMOUNT,
                month = month,
                year = year,
            ),
        )
    }

    private suspend fun seedTag(db: AppDatabase) {
        db.tagDao().insert(Tag(id = TAG_ID, name = TAG_NAME))
    }
}
