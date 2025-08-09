// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/data/db/AppDatabase.kt
// REASON: FEATURE - Added final set of default ignore rules for payment wallets,
// failed transactions, and mandate confirmations to achieve production-grade
// noise filtering in the SMS parser.
// =================================================================================
package io.pm.finlight.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.pm.finlight.Account
import io.pm.finlight.Budget
import io.pm.finlight.BudgetDao
import io.pm.finlight.Category
import io.pm.finlight.CategoryDao
import io.pm.finlight.CustomSmsRule
import io.pm.finlight.CustomSmsRuleDao
import io.pm.finlight.Goal
import io.pm.finlight.GoalDao
import io.pm.finlight.IgnoreRule
import io.pm.finlight.IgnoreRuleDao
import io.pm.finlight.MerchantCategoryMapping
import io.pm.finlight.MerchantCategoryMappingDao
import io.pm.finlight.MerchantMapping
import io.pm.finlight.MerchantMappingDao
import io.pm.finlight.MerchantRenameRule
import io.pm.finlight.MerchantRenameRuleDao
import io.pm.finlight.RecurringPattern
import io.pm.finlight.RecurringPatternDao
import io.pm.finlight.RecurringTransaction
import io.pm.finlight.RecurringTransactionDao
import io.pm.finlight.RuleType
import io.pm.finlight.SplitTransaction
import io.pm.finlight.SplitTransactionDao
import io.pm.finlight.Tag
import io.pm.finlight.TagDao
import io.pm.finlight.Transaction
import io.pm.finlight.TransactionDao
import io.pm.finlight.TransactionImage
import io.pm.finlight.TransactionTagCrossRef
import io.pm.finlight.data.db.dao.AccountDao
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.sqlcipher.database.SupportFactory
import io.pm.finlight.security.SecurityManager

@Database(
    entities = [
        Transaction::class,
        Account::class,
        Category::class,
        Budget::class,
        MerchantMapping::class,
        RecurringTransaction::class,
        Tag::class,
        TransactionTagCrossRef::class,
        TransactionImage::class,
        CustomSmsRule::class,
        MerchantRenameRule::class,
        MerchantCategoryMapping::class,
        IgnoreRule::class,
        Goal::class,
        RecurringPattern::class,
        SplitTransaction::class
    ],
    version = 33,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun merchantMappingDao(): MerchantMappingDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun tagDao(): TagDao
    abstract fun customSmsRuleDao(): CustomSmsRuleDao
    abstract fun merchantRenameRuleDao(): MerchantRenameRuleDao
    abstract fun merchantCategoryMappingDao(): MerchantCategoryMappingDao
    abstract fun ignoreRuleDao(): IgnoreRuleDao
    abstract fun goalDao(): GoalDao
    abstract fun recurringPatternDao(): RecurringPatternDao
    abstract fun splitTransactionDao(): SplitTransactionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // --- UPDATED: Refined the default ignore rules ---
        private val DEFAULT_IGNORE_PHRASES = listOf(
            // Existing Rules
            "invoice of", "payment of.*is successful", "has been credited to",
            "payment of.*has been received towards", "credited to your.*card",
            "Payment of.*has been received on your.*Credit Card", "We have received",
            "has been initiated", "redemption", "requested money from you", "Folio No.",
            "NAV of", "purchase experience", "your OTP", "recharge of.*is successful",
            "thanks for the payment of", "premium due", "bill is generated", "missed call alert",
            "pre-approved", "offer", "due on", "statement for", "KYC", "cheque book",
            "is approved", "congratulations", "eligible for", "SIP Purchase", "towards your SIP",
            "EMI Alert", "due by", "has requested money from you", "order.*has been delivered",
            "shipped", "Arriving today", "out for delivery", "from Paytm Balance", "using OlaMoney Postpaid",
            "is declined", "Request Failure", "AutoPay (E-mandate) Active", "mandate is successfully revoked",
            "mandate has been successfully created", "has been dispatched", "is now active",
            "successfully registered for UPI",

            // --- NEW: Rules to fix parsing issues ---
            "Unit Allotment", "Mutual Fund", "has been delivered"

        ).map { IgnoreRule(pattern = it, type = RuleType.BODY_PHRASE, isDefault = true) } + listOf(
            // Existing Senders
            "*SBIMF", "*WKEFTT", "*BSNL", "*HDFCMF", "*AXISMF", "*KOTAKM", "*QNTAMC", "*NIMFND",
            "*MYNTRA", "*FLPKRT", "*AMAZON", "*SWIGGY", "*ZOMATO", "*BLUDRT", "*EKARTL",
            "*DLHVRY", "*XPBEES", "*OLAMNY", "*Paytm",

            // --- NEW: Sender rule to fix parsing issue ---
            "*Delhivery"

        ).map { IgnoreRule(pattern = it, type = RuleType.SENDER, isDefault = true) }


        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN transactionType TEXT NOT NULL DEFAULT 'expense'")
                db.execSQL("UPDATE transactions SET transactionType = 'income' WHERE amount > 0")
                db.execSQL("UPDATE transactions SET amount = ABS(amount)")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_mappings` (`smsSender` TEXT NOT NULL, `merchantName` TEXT NOT NULL, PRIMARY KEY(`smsSender`))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourceSmsId INTEGER")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `description` TEXT NOT NULL, `amount` REAL NOT NULL, `transactionType` TEXT NOT NULL, `recurrenceInterval` TEXT NOT NULL, `startDate` INTEGER NOT NULL, `accountId` INTEGER NOT NULL, `categoryId` INTEGER, FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_accountId` ON `recurring_transactions` (`accountId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_transactions_categoryId` ON `recurring_transactions` (`categoryId`)")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tags` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_tags_name` ON `tags` (`name`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `transaction_tag_cross_ref` (`transactionId` INTEGER NOT NULL, `tagId` INTEGER NOT NULL, PRIMARY KEY(`transactionId`, `tagId`), FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, FOREIGN KEY(`tagId`) REFERENCES `tags`(`id`) ON DELETE CASCADE)")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN sourceSmsHash TEXT")
            }
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN source TEXT NOT NULL DEFAULT 'Manual Entry'")
                db.execSQL("UPDATE transactions SET source = 'Reviewed Import' WHERE sourceSmsId IS NOT NULL")
            }
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN iconKey TEXT NOT NULL DEFAULT 'category'")
            }
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN colorKey TEXT NOT NULL DEFAULT 'gray_light'")
            }
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `transaction_images` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `transactionId` INTEGER NOT NULL,
                        `imageUri` TEXT NOT NULL,
                        FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_images_transactionId` ON `transaction_images` (`transactionId`)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_sms_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `smsSender` TEXT NOT NULL,
                        `ruleType` TEXT NOT NULL,
                        `regexPattern` TEXT NOT NULL,
                        `priority` INTEGER NOT NULL
                    )
                """)
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `custom_sms_rules`")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `custom_sms_rules` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `triggerPhrase` TEXT NOT NULL,
                        `merchantRegex` TEXT,
                        `amountRegex` TEXT,
                        `priority` INTEGER NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_custom_sms_rules_triggerPhrase` ON `custom_sms_rules` (`triggerPhrase`)")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_sms_rules` ADD COLUMN `merchantNameExample` TEXT")
                db.execSQL("ALTER TABLE `custom_sms_rules` ADD COLUMN `amountExample` TEXT")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_sms_rules` ADD COLUMN `accountRegex` TEXT")
                db.execSQL("ALTER TABLE `custom_sms_rules` ADD COLUMN `accountNameExample` TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN originalDescription TEXT")
                db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_rename_rules` (`originalName` TEXT NOT NULL, `newName` TEXT NOT NULL, PRIMARY KEY(`originalName`))")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isExcluded INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `merchant_category_mapping` (`parsedName` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, PRIMARY KEY(`parsedName`))")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `ignore_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `phrase` TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ignore_rules_phrase` ON `ignore_rules` (`phrase`)")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ignore_rules` ADD COLUMN `isEnabled` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `ignore_rules` ADD COLUMN `isDefault` INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `custom_sms_rules` ADD COLUMN `sourceSmsBody` TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (16, 'Bike', 'two_wheeler', 'red_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (17, 'Car', 'directions_car', 'blue_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (18, 'Debt', 'credit_score', 'brown_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (19, 'Family', 'people', 'pink_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (20, 'Friends', 'group', 'cyan_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (21, 'Gift', 'card_giftcard', 'purple_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (22, 'Fitness', 'fitness_center', 'green_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (23, 'Home Maintenance', 'home', 'teal_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (24, 'Insurance', 'shield', 'indigo_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (25, 'Learning & Education', 'school', 'orange_light')")
                db.execSQL("INSERT OR IGNORE INTO categories (id, name, iconKey, colorKey) VALUES (26, 'Rent', 'house', 'deep_purple_light')")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `recurring_transactions` ADD COLUMN `lastRunDate` INTEGER")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `goals` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL, 
                        `targetAmount` REAL NOT NULL, 
                        `savedAmount` REAL NOT NULL, 
                        `targetDate` INTEGER, 
                        `accountId` INTEGER NOT NULL, 
                        FOREIGN KEY(`accountId`) REFERENCES `accounts`(`id`) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_goals_accountId` ON `goals` (`accountId`)")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE `accounts_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL COLLATE NOCASE, 
                        `type` TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX `index_accounts_name_nocase` ON `accounts_new` (`name`)")
                db.execSQL("INSERT INTO `accounts_new` (id, name, type) SELECT id, name, type FROM accounts")
                db.execSQL("DROP TABLE `accounts`")
                db.execSQL("ALTER TABLE `accounts_new` RENAME TO `accounts`")

                db.execSQL("""
                    CREATE TABLE `categories_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `name` TEXT NOT NULL COLLATE NOCASE, 
                        `iconKey` TEXT NOT NULL, 
                        `colorKey` TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX `index_categories_name_nocase` ON `categories_new` (`name`)")
                db.execSQL("INSERT INTO `categories_new` (id, name, iconKey, colorKey) SELECT id, name, iconKey, colorKey FROM categories")
                db.execSQL("DROP TABLE `categories`")
                db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")

                db.execSQL("""
                    CREATE TABLE `merchant_rename_rules_new` (
                        `originalName` TEXT NOT NULL COLLATE NOCASE, 
                        `newName` TEXT NOT NULL, 
                        PRIMARY KEY(`originalName`)
                    )
                """)
                db.execSQL("INSERT INTO `merchant_rename_rules_new` (originalName, newName) SELECT originalName, newName FROM merchant_rename_rules")
                db.execSQL("DROP TABLE `merchant_rename_rules`")
                db.execSQL("ALTER TABLE `merchant_rename_rules_new` RENAME TO `merchant_rename_rules`")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recurring_patterns` (
                        `smsSignature` TEXT NOT NULL, 
                        `description` TEXT NOT NULL, 
                        `amount` REAL NOT NULL, 
                        `transactionType` TEXT NOT NULL, 
                        `accountId` INTEGER NOT NULL, 
                        `categoryId` INTEGER, 
                        `occurrences` INTEGER NOT NULL, 
                        `firstSeen` INTEGER NOT NULL, 
                        `lastSeen` INTEGER NOT NULL, 
                        PRIMARY KEY(`smsSignature`)
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_patterns_lastSeen` ON `recurring_patterns` (`lastSeen`)")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `smsSignature` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_smsSignature` ON `transactions` (`smsSignature`)")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `originalAmount` REAL")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `currencyCode` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `conversionRate` REAL")
            }
        }

        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isSplit` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `split_transactions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `parentTransactionId` INTEGER NOT NULL, 
                        `amount` REAL NOT NULL, 
                        `categoryId` INTEGER, 
                        `notes` TEXT, 
                        FOREIGN KEY(`parentTransactionId`) REFERENCES `transactions`(`id`) ON DELETE CASCADE, 
                        FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON DELETE SET NULL
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_transactions_parentTransactionId` ON `split_transactions` (`parentTransactionId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_split_transactions_categoryId` ON `split_transactions` (`categoryId`)")
            }
        }

        val MIGRATION_29_30 = object : Migration(29, 30) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `split_transactions` ADD COLUMN `originalAmount` REAL")
            }
        }

        val MIGRATION_30_31 = object : Migration(30, 31) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("INSERT OR IGNORE INTO ignore_rules (phrase, isEnabled, isDefault) VALUES ('We have received', 1, 1)")
                db.execSQL("INSERT OR IGNORE INTO ignore_rules (phrase, isEnabled, isDefault) VALUES ('has been initiated', 1, 1)")
                db.execSQL("INSERT OR IGNORE INTO ignore_rules (phrase, isEnabled, isDefault) VALUES ('redemption', 1, 1)")
                db.execSQL("INSERT OR IGNORE INTO ignore_rules (phrase, isEnabled, isDefault) VALUES ('requested money from you', 1, 1)")
            }
        }

        val MIGRATION_31_32 = object : Migration(31, 32) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE `ignore_rules_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `type` TEXT NOT NULL DEFAULT 'BODY_PHRASE', 
                        `pattern` TEXT NOT NULL, 
                        `isEnabled` INTEGER NOT NULL DEFAULT 1, 
                        `isDefault` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("""
                    INSERT INTO `ignore_rules_new` (id, pattern, isEnabled, isDefault)
                    SELECT id, phrase, isEnabled, isDefault FROM `ignore_rules`
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ignore_rules_pattern` ON `ignore_rules_new` (`pattern`)")
                db.execSQL("DROP TABLE `ignore_rules`")
                db.execSQL("ALTER TABLE `ignore_rules_new` RENAME TO `ignore_rules`")
            }
        }

        val MIGRATION_32_33 = object : Migration(32, 33) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE `ignore_rules_new` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `type` TEXT NOT NULL DEFAULT 'BODY_PHRASE', 
                        `pattern` TEXT NOT NULL COLLATE NOCASE, 
                        `isEnabled` INTEGER NOT NULL DEFAULT 1, 
                        `isDefault` INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_ignore_rules_pattern_nocase` ON `ignore_rules_new` (`pattern`)")
                db.execSQL("""
                    INSERT OR IGNORE INTO `ignore_rules_new` (id, type, pattern, isEnabled, isDefault)
                    SELECT id, type, pattern, isEnabled, isDefault FROM `ignore_rules`
                """)
                db.execSQL("DROP TABLE `ignore_rules`")
                db.execSQL("ALTER TABLE `ignore_rules_new` RENAME TO `ignore_rules`")
            }
        }


        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val securityManager = SecurityManager(context)
                val passphrase = securityManager.getPassphrase()
                val factory = SupportFactory(passphrase)

                val instance =
                    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "finance_database")
                        .openHelperFactory(factory)
                        .addMigrations(MIGRATION_32_33)
                        .fallbackToDestructiveMigration()
                        .addCallback(DatabaseCallback(context))
                        .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(private val context: Context) : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                CoroutineScope(Dispatchers.IO).launch {
                    populateDatabase(getInstance(context))
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
            }

            suspend fun populateDatabase(db: AppDatabase) {
                val accountDao = db.accountDao()
                val categoryDao = db.categoryDao()
                val ignoreRuleDao = db.ignoreRuleDao()

                // --- UPDATED: Seed only the essential data ---
                categoryDao.insertAll(CategoryIconHelper.predefinedCategories)
                ignoreRuleDao.insertAll(DEFAULT_IGNORE_PHRASES)
                accountDao.insert(Account(id = 1, name = "Cash Spends", type = "Cash"))
            }
        }
    }
}
