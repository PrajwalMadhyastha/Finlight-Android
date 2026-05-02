package io.pm.finlight.ml

import android.os.Environment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.pm.finlight.*
import io.pm.finlight.utils.CategoryIconHelper
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * On-device evaluation test that compares NER-enabled parsing against baseline regex-only parsing.
 *
 * For each SMS body from the existing unit tests, it runs:
 * 1. Baseline: SmsParser with nerEntities = null (regex only)
 * 2. NER-enabled: SmsParser with entities from NerExtractor
 *
 * Results are written to a CSV file on device storage for analysis.
 *
 * To pull the report after running:
 *   adb pull /sdcard/Download/ner_evaluation_report.csv
 */
@RunWith(AndroidJUnit4::class)
class NerEvaluationTest {
    // ---- Test data: (category, sender, smsBody, expectedAmount, expectedMerchant) ----
    private val testCases =
        listOf(
            // HDFC
            TestCase(
                "HDFC",
                "AM-HDFCBK",
                "Sent Rs.11.00\nFrom HDFC Bank A/C *1243\nTo Raju\nOn 07/08/25\nRef 558523453508",
                11.0,
                "Raju",
            ),
            TestCase(
                "HDFC",
                "JD-HDFCBK",
                "[JD-HDFCBK-S] Spent Rs.388.19 On HDFC Bank Card 9922 At ..MC DONALDS_ on2025-06-22:08:01:24.",
                388.19,
                "MC DONALDS",
            ),
            TestCase(
                "HDFC",
                "AM-HDFCBK",
                "You've spent Rs.349 On HDFC Bank CREDIT Card xx1335 At RAZ*StickON...",
                349.0,
                "RAZ*StickON",
            ),
            TestCase(
                "HDFC",
                "AM-HDFCBK",
                "Update! INR 11,000.00 deposited in HDFC Bank A/c XX2536 on 15-OCT-24 for XXXXXXXXXX0461-TPT-Transfer-PRAJWAL MADHYASTHA K P.Avl bal INR 26,475.53.",
                11000.0,
                "TPT-Transfer-PRAJWAL MADHYASTHA K P",
            ),
            TestCase(
                "HDFC",
                "AM-HDFCBK",
                "Rs.6175 spent on HDFC Bank Card x2477 at ..SUDARSHAN FAMILY_ on 2024-11-13:17:18:38.",
                6175.0,
                "SUDARSHAN FAMILY",
            ),
            TestCase(
                "HDFC",
                "AM-HDFCBK",
                "Sent Rs.30.00\nFrom HDFC Bank A/C x2536\nTo Sukrith pharma\nOn 06/12/24",
                30.0,
                "Sukrith pharma",
            ),
            TestCase(
                "HDFC",
                "AM-HDFCBK",
                "Credit Alert!\nRs.2.00 credited to HDFC Bank A/c xx2536 on 07-12-24 from VPA user34@axisbank (UPI 526830463424)",
                2.0,
                "user34@axisbank",
            ),
            TestCase(
                "HDFC",
                "AM-HDFCBK",
                "Rs.199 without OTP/PIN HDFC Bank Card x4433 At NETFLIX On 2024-07-05:23:03:33.",
                199.0,
                "NETFLIX",
            ),
            TestCase(
                "HDFC",
                "VM-HDFCBK",
                "Dear Customer, Rs.180,000.00 is debited from A/c XXXX5733 for NEFT transaction via HDFC Bank NetBanking.",
                180000.0,
                "NEFT transaction",
            ),
            TestCase(
                "HDFC",
                "VM-HDFCBK",
                "Money Sent-INR 15,000.00 From HDFC Bank A/c XX5733 on 28-10-23 To A/c xxxxxxxx9709 IMPS Ref-330120320348 Avl bal:INR 5,132.30",
                15000.0,
                "A/c xxxxxxxx9709",
            ),
            TestCase(
                "HDFC",
                "VM-HDFCBK",
                "Amt Deducted! Rs.100000 from your HDFC Bank A/c XX1736 for NEFT transaction via HDFC Bank Online Banking",
                100000.0,
                "NEFT transaction",
            ),
            TestCase(
                "HDFC",
                "VM-HDFCBK",
                "Paid: INR 100 on HDFC Bank Prepaid Card 2742 at PAY*SWIGGY on 13-MAY-25 01:05 PM.",
                100.0,
                "PAY*SWIGGY",
            ),
            TestCase(
                "HDFC",
                "VM-HDFCBK",
                "PAYMENT ALERT! INR 4000.00 deducted from HDFC Bank A/C No 4677 towards Quant Mutual Fund UMRN: HDFC0000000012345678",
                4000.0,
                "Quant Mutual Fund",
            ),
            // ICICI
            TestCase(
                "ICICI",
                "DM-ICIBNK",
                "ICICI Bank Acct XX823 debited for Rs 240.00 on 21-Jun-25; DAKSHIN CAFE credited.",
                240.0,
                "DAKSHIN CAFE",
            ),
            TestCase(
                "ICICI",
                "DM-ICIBNK",
                "Dear Customer, your ICICI Bank Account XXX508 has been credited with Rs 208.42 on 03-Sep-22 as reversal of transaction with UPI: 224679541058.",
                208.42,
                "reversal of transaction",
            ),
            TestCase(
                "ICICI",
                "DM-ICIBNK",
                "ICICI Bank Acct XX823 debited for Rs 500.00 on 21-Jun-25; ZOMATO credited.",
                500.0,
                "ZOMATO",
            ),
            // SBI
            TestCase(
                "SBI",
                "DM-SBIBNK",
                "Rs.267.00 spent on your SBI Credit Card ending with 3201 at DAKSHIN CAFE.",
                267.0,
                "DAKSHIN CAFE",
            ),
            TestCase(
                "SBI",
                "DM-SBIBNK",
                "Your SB A/c *3618 Debited for Rs:147.5 on 16-11-2021 16:45:07 by Transfer Avl Bal Rs:21949.7 -Union Bank of India",
                147.5,
                "Transfer",
            ),
            TestCase(
                "SBI",
                "DM-SBIBNK",
                "Your SB A/c **23618 is Credited for Rs.1743 on 31-01-2021 03:42:47 by Transfer. Avl Bal Rs:5811.3 -Union Bank of India",
                1743.0,
                "Transfer",
            ),
            // Axis
            TestCase(
                "AXIS",
                "AM-AXISBK",
                "Rs.1,200.00 spent on AXIS Bank Card ending 5678 at AMAZON on 01-Jan-25.",
                1200.0,
                "AMAZON",
            ),
            // Generic / Other
            TestCase(
                "GENERIC",
                "AM-HDFCBK",
                "You have spent MYR 55.50 at STARBUCKS.",
                55.5,
                "STARBUCKS",
            ),
            TestCase(
                "GENERIC",
                "AM-HDFCBK",
                "You have spent INR 120.00 at CCD.",
                120.0,
                "CCD",
            ),
            TestCase(
                "GENERIC",
                "AM-HDFCBK",
                "Your account with HDFC Bank has been debited for Rs. 750.50 at Amazon on 22-Jun-2025.",
                750.5,
                "Amazon",
            ),
            TestCase(
                "GENERIC",
                "DM-SOMEBK",
                "You have received a credit of INR 5,000.00 from Freelance Client.",
                5000.0,
                "Freelance Client",
            ),
            TestCase(
                "GENERIC",
                "CP-BOBTXN",
                "Rs.1656 transferred from A/c ...7656 to:UPI/429269093079. Total Bal:Rs.6114.76CR. - Bank of Baroda",
                1656.0,
                "UPI/429269093079",
            ),
            TestCase(
                "GENERIC",
                "VM-BOB",
                "Rs.918.00 debited from A/C XXXXXX9130 and credited to user12@apl UPI Ref:466970233165. Not you? Call 18005700 -BOB",
                918.0,
                "user12@apl",
            ),
            TestCase(
                "GENERIC",
                "VM-BOB",
                "Rs.1600 Credited to A/c ...7656 thru UPI/069871591309 by 9686714029_axl. Total Bal:Rs.33438.48CR. - Bank of Baroda",
                1600.0,
                "9686714029 axl",
            ),
            TestCase(
                "GENERIC",
                "VM-BOB",
                "Rs.6327 Credited to A/c ...7656 thru NEFT UTR RBI3532499914098 by AIIMS JODHPUR. Total Bal:Rs.7092.4CR. - Bank of Baroda",
                6327.0,
                "AIIMS JODHPUR",
            ),
            TestCase(
                "GENERIC",
                "VM-BOB",
                "Rs.711.52 Dr. from A/C XXXXXX1236 and Cr. to techmashsolutionspvtl.payu@mairtel. Ref:567123450825. -BOB",
                711.52,
                "techmashsolutionspvtl.payu@mairtel",
            ),
            TestCase(
                "GENERIC",
                "VM-IndusB",
                "user204@yescred linked to Credit Card XX4062 is Dr with INR.26.00 by VPA user941@ybl (UPI Ref no 558529523785) - Indusind Bank",
                26.0,
                "user941@ybl",
            ),
            TestCase(
                "GENERIC",
                "VM-SODEXO",
                "Your Sodexo Card has been successfully loaded with Rs.1250 towards Meal Card A/c on 15:00,15 Dec.",
                1250.0,
                null,
            ),
            TestCase(
                "GENERIC",
                "BH-DOPBNK",
                "Account  No. XXXXXX5755 CREDIT with amount Rs. 5700.00 on 30-12-2022. Balance: Rs.216023.00.",
                5700.0,
                null,
            ),
            TestCase(
                "GENERIC",
                "BH-DOPBNK",
                "Account  No. XXXXXX5755 DEBIT with amount Rs. 15000.00 on 15-07-2022. Balance: Rs.80173.00.",
                15000.0,
                null,
            ),
            // Canara Bank
            TestCase(
                "CANARA",
                "AM-CANBK",
                "Your A/C XXXXX650077 Credited INR 1,41,453.00 on 15/07/25 - Canara Bank",
                141453.0,
                null,
            ),
            // Pluxee
            TestCase(
                "PLUXEE",
                "VM-PLUXEE",
                "Rs.267.00 spent from Pluxee Meal Card wallet, card no. xx1234 at HALLI THOTA.",
                267.0,
                "HALLI THOTA",
            ),
        )

    @Test
    fun runNerEvaluationAndWriteReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val nerExtractor = NerExtractor(context)

        // Set up providers (all empty/no-op for fair baseline vs NER comparison)
        val emptyMappings = emptyMap<String, String>()
        val customSmsRuleProvider =
            object : CustomSmsRuleProvider {
                override suspend fun getAllRules() = emptyList<CustomSmsRule>()
            }
        val merchantRenameRuleProvider =
            object : MerchantRenameRuleProvider {
                override suspend fun getAllRules() = emptyList<MerchantRenameRule>()
            }
        val ignoreRuleProvider =
            object : IgnoreRuleProvider {
                override suspend fun getEnabledRules() = emptyList<IgnoreRule>()
            }
        val merchantCategoryMappingProvider =
            object : MerchantCategoryMappingProvider {
                override suspend fun getCategoryIdForMerchant(merchantName: String) = null
            }
        val categoryFinderProvider =
            object : CategoryFinderProvider {
                override fun getCategoryIdByName(name: String) = CategoryIconHelper.getCategoryIdByName(name)
            }
        val smsParseTemplateProvider =
            object : SmsParseTemplateProvider {
                override suspend fun getAllTemplates() = emptyList<SmsParseTemplate>()

                override suspend fun getTemplatesBySignature(signature: String) = emptyList<SmsParseTemplate>()
            }

        val results = mutableListOf<EvalResult>()

        runBlocking {
            for ((idx, tc) in testCases.withIndex()) {
                val smsMessage =
                    SmsMessage(
                        id = idx.toLong(),
                        sender = tc.sender,
                        body = tc.smsBody,
                        date = System.currentTimeMillis(),
                    )

                // Run NER extraction
                val nerEntities =
                    try {
                        nerExtractor.extract(tc.smsBody)
                    } catch (e: Exception) {
                        emptyMap()
                    }

                // Baseline (no NER)
                val baselineResult =
                    SmsParser.parseWithReason(
                        sms = smsMessage,
                        mappings = emptyMappings,
                        customSmsRuleProvider = customSmsRuleProvider,
                        merchantRenameRuleProvider = merchantRenameRuleProvider,
                        ignoreRuleProvider = ignoreRuleProvider,
                        merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                        categoryFinderProvider = categoryFinderProvider,
                        smsParseTemplateProvider = smsParseTemplateProvider,
                        nerEntities = null,
                    )

                // NER-enabled
                val nerResult =
                    SmsParser.parseWithReason(
                        sms = smsMessage,
                        mappings = emptyMappings,
                        customSmsRuleProvider = customSmsRuleProvider,
                        merchantRenameRuleProvider = merchantRenameRuleProvider,
                        ignoreRuleProvider = ignoreRuleProvider,
                        merchantCategoryMappingProvider = merchantCategoryMappingProvider,
                        categoryFinderProvider = categoryFinderProvider,
                        smsParseTemplateProvider = smsParseTemplateProvider,
                        nerEntities = nerEntities,
                    )

                val baselineTxn = (baselineResult as? ParseResult.Success)?.transaction
                val nerTxn = (nerResult as? ParseResult.Success)?.transaction

                val baselineAmount = baselineTxn?.amount
                val nerAmount = nerTxn?.amount
                val baselineMerchant = baselineTxn?.merchantName
                val nerMerchant = nerTxn?.merchantName

                val amountMatch = baselineAmount == nerAmount
                val merchantMatch = baselineMerchant.equals(nerMerchant, ignoreCase = true)

                val amountCorrect = nerAmount != null && Math.abs(nerAmount - tc.expectedAmount) < 0.01
                val baselineAmountCorrect = baselineAmount != null && Math.abs(baselineAmount - tc.expectedAmount) < 0.01

                val verdict =
                    when {
                        amountCorrect && !baselineAmountCorrect -> "NER_BETTER"
                        baselineAmountCorrect && !amountCorrect -> "REGRESSION"
                        amountMatch && merchantMatch -> "SAME"
                        amountMatch && !merchantMatch -> "MERCHANT_DIFFERS"
                        else -> "AMOUNT_DIFFERS"
                    }

                results.add(
                    EvalResult(
                        category = tc.category,
                        smsSnippet = tc.smsBody.take(60).replace("\n", " "),
                        nerRaw = nerEntities.toString(),
                        baselineAmount = baselineAmount,
                        nerAmount = nerAmount,
                        expectedAmount = tc.expectedAmount,
                        baselineMerchant = baselineMerchant,
                        nerMerchant = nerMerchant,
                        expectedMerchant = tc.expectedMerchant,
                        verdict = verdict,
                    ),
                )
            }
        }

        nerExtractor.close()

        // Write CSV report
        val csv =
            buildString {
                appendLine(
                    "Category,SMS_Snippet,Verdict,Expected_Amount,Baseline_Amount,NER_Amount,Expected_Merchant,Baseline_Merchant,NER_Merchant,NER_Raw",
                )
                for (r in results) {
                    appendLine(
                        listOf(
                            r.category,
                            "\"${r.smsSnippet}\"",
                            r.verdict,
                            r.expectedAmount,
                            r.baselineAmount ?: "NULL",
                            r.nerAmount ?: "NULL",
                            "\"${r.expectedMerchant ?: ""}\"",
                            "\"${r.baselineMerchant ?: ""}\"",
                            "\"${r.nerMerchant ?: ""}\"",
                            "\"${r.nerRaw}\"",
                        ).joinToString(","),
                    )
                }
            }

        // Write summary
        val summary =
            buildString {
                val total = results.size
                val same = results.count { it.verdict == "SAME" }
                val nerBetter = results.count { it.verdict == "NER_BETTER" }
                val regression = results.count { it.verdict == "REGRESSION" }
                val merchantDiffers = results.count { it.verdict == "MERCHANT_DIFFERS" }
                val amountDiffers = results.count { it.verdict == "AMOUNT_DIFFERS" }
                appendLine("=== NER EVALUATION SUMMARY ===")
                appendLine("Total cases: $total")
                appendLine("SAME (no change): $same (${100 * same / total}%)")
                appendLine("NER_BETTER (NER fixed a wrong result): $nerBetter (${100 * nerBetter / total}%)")
                appendLine("MERCHANT_DIFFERS (amounts match, merchants differ): $merchantDiffers (${100 * merchantDiffers / total}%)")
                appendLine("AMOUNT_DIFFERS: $amountDiffers (${100 * amountDiffers / total}%)")
                appendLine("REGRESSION (NER broke something): $regression (${100 * regression / total}%)")
                appendLine("")
                appendLine("REGRESSIONS:")
                results.filter { it.verdict == "REGRESSION" }.forEach {
                    appendLine("  [${it.category}] ${it.smsSnippet}")
                    appendLine("    Baseline: ${it.baselineAmount} / ${it.baselineMerchant}")
                    appendLine("    NER:      ${it.nerAmount} / ${it.nerMerchant}")
                }
                appendLine("")
                appendLine("MERCHANT_DIFFERS:")
                results.filter { it.verdict == "MERCHANT_DIFFERS" }.forEach {
                    appendLine("  [${it.category}] ${it.smsSnippet}")
                    appendLine("    Baseline: ${it.baselineMerchant}")
                    appendLine("    NER:      ${it.nerMerchant}")
                    appendLine("    Expected: ${it.expectedMerchant}")
                }
            }

        // Save to device
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val ts = SimpleDateFormat("MMdd_HHmm", Locale.US).format(Date())
        val csvFile = File(downloads, "ner_eval_$ts.csv")
        val summaryFile = File(downloads, "ner_eval_summary_$ts.txt")

        csvFile.writeText(csv)
        summaryFile.writeText(summary)

        // Also print to logcat
        android.util.Log.d("NerEvaluation", summary)
        android.util.Log.d("NerEvaluation", "CSV written to: ${csvFile.absolutePath}")
        android.util.Log.d("NerEvaluation", "Summary written to: ${summaryFile.absolutePath}")
    }

    private data class TestCase(
        val category: String,
        val sender: String,
        val smsBody: String,
        val expectedAmount: Double,
        val expectedMerchant: String?,
    )

    private data class EvalResult(
        val category: String,
        val smsSnippet: String,
        val nerRaw: String,
        val baselineAmount: Double?,
        val nerAmount: Double?,
        val expectedAmount: Double,
        val baselineMerchant: String?,
        val nerMerchant: String?,
        val expectedMerchant: String?,
        val verdict: String,
    )
}
