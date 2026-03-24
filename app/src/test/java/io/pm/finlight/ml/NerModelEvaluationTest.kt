package io.pm.finlight.ml

import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * NER Model Evaluation Harness.
 *
 * Runs the TFLite NER model on a curated set of SMS messages (extracted from the
 * existing SmsParser unit tests) and compares the model's entity extractions
 * against the ground-truth amounts and merchants defined in those tests.
 *
 * This is a benchmark, not an assertion-based test — it always passes.
 * Results are printed to stdout and written to app/build/reports/.
 *
 * Run with:
 *   ./gradlew :app:testReleaseUnitTest --tests "io.pm.finlight.ml.NerModelEvaluationTest"
 */
class NerModelEvaluationTest {

    // ---- Ground truth test cases ----

    data class EvalCase(
        val bank: String,
        val smsBody: String,
        val sender: String,
        val expectedAmount: Double?,
        val expectedMerchant: String?,
        val expectedAccount: String?,
        val expectedType: String?, // "expense" or "income"
    )

    /** Curated test cases from the existing SmsParser unit tests. */
    private val evalCases: List<EvalCase> = listOf(
        // ---- HDFC ----
        EvalCase("HDFC", "Sent Rs.11.00\nFrom HDFC Bank A/C *1243\nTo Raju\nOn 07/08/25\nRef 558523453508\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", "AM-HDFCBK", 11.0, "Raju", "HDFC Bank - *1243", "expense"),
        EvalCase("HDFC", "[JD-HDFCBK-S] Spent Rs.388.19 On HDFC Bank Card 9922 At ..MC DONALDS_ on2025-06-22:08:01:24.Not You> To Block+Reissue Call 18002323232/SMS BLOCK CC 9922 to 123098123", "JD-HDFCBK", 388.19, "MC DONALDS", "HDFC Bank Card - xx9922", "expense"),
        EvalCase("HDFC", "You've spent Rs.349 On HDFC Bank CREDIT Card xx1335 At RAZ*StickON...", "AM-HDFCBK", 349.0, "RAZ*StickON", null, "expense"),
        EvalCase("HDFC", "Money Sent! Rs.250.00\nFrom HDFC Bank A/C **4321\nTo VPA priyanka@ybl\nOn 10/07/25\nRef 517843765876", "AM-HDFCBK", 250.0, "priyanka@ybl", null, "expense"),
        EvalCase("HDFC", "HDFC Bank: Rs 10,000.00 credited to a/c XXXXXX1234 on 15-Jul-25 by a]c linked to VPA ravi@icici (UPI Ref No 518700000000).", "AM-HDFCBK", 10000.0, "ravi@icici", null, "income"),
        EvalCase("HDFC", "Rs 1,500.00 debited from a/c **4321 on 12-07-25 to VPA swiggy@hdfcbank (UPI Ref No 519400000000).", "AM-HDFCBK", 1500.0, "swiggy@hdfcbank", null, "expense"),
        EvalCase("HDFC", "HDFC Bank CREDIT Card ending 8967 - Txn of Rs.599.00 on 14-07-25 at NETFLIX.COM.Info:NETFLIX.COM.Not you?Call 18002586161/SMS BLOCK CC 8967 to 7308080808", "AM-HDFCBK", 599.0, "NETFLIX.COM", null, "expense"),
        EvalCase("HDFC", "Update: HDFC Bank A/c XX1234 credited with Rs. 25,000. NEFT Ref SBIN825072300003211 on 23-JUL-25.", "AM-HDFCBK", 25000.0, "NEFT Ref SBIN825072300003211", null, "income"),

        // ---- ICICI ----
        EvalCase("ICICI", "ICICI Bank Acc XX244 debited Rs. 8,700.00 on 02-Aug-25 InfoACH*ZERODHA B.Avl Bal Rs. 3,209.31.To dispute call 18002662 or SMS BLOCK 646 to 9215676766", "DM-ICIBNK", 8700.0, "ACH*ZERODHA B", null, "expense"),
        EvalCase("ICICI", "ICICI Bank Acct XX823 debited for Rs 240.00 on 28-Jul-25; DAKSHIN CAFE credited. UPI: 552200221100. Call 18002661 for dispute. SMS BLOCK 823 to 123123123", "DM-ICIBNK", 240.0, "DAKSHIN CAFE", null, "expense"),
        EvalCase("ICICI", "Dear Customer, Acct XX823 is credited with Rs 6000.00 on 26-Jun-25 from GANGA MANGA. UPI:5577822323232-ICICI Bank", "QP-ICIBNK", 6000.0, "GANGA MANGA", null, "income"),
        EvalCase("ICICI", "ICICI Bank Account XX823 credited:Rs. 1,133.00 on 01-Jul-25. Info NEFT-HDFCN5202507024345356218-. Available Balance is Rs. 1,858.35.", "VM-ICIBNK", 1133.0, "NEFT-HDFCN5202507024345356218-", null, "income"),
        EvalCase("ICICI", "Dear Customer, your ICICI Bank Account XX508 has been credited with INR 328.00 on 30-Aug-22. Info:ACH*RELIANCEINDUSTRIES*000000000002. The Available Balance is INR 42,744.71.", "DM-ICIBNK", 328.0, "ACH*RELIANCEINDUSTRIES*000000000002", null, "income"),
        EvalCase("ICICI", "INR 7,502.00 spent on ICICI Bank Card XX8011 on 31-Aug-22 at Amazon. Avl Lmt: INR 2,51,545.65. To dispute,call 18002662/SMS BLOCK 8011 to4082711530", "DM-ICIBNK", 7502.0, "Amazon", null, "expense"),
        EvalCase("ICICI", "INR 782.00 spent using ICICI Bank Card XX1001 on 29-Jul-25 on IND*Amazon.in -. Avl Limit: INR 4,94,835.00. If not you, call 1800 2662/SMS BLOCK 1001 to4082711530.", "DM-ICIBNK", 782.0, "IND*Amazon.in", null, "expense"),
        EvalCase("ICICI", "Your account has been successfully debited with Rs 149.00 on 17-Sep-22 towards JioHotstar for Autopay AutoPay, RRN 506941017769-ICICI Bank.", "DM-ICIBNK", 149.0, "JioHotstar", null, "expense"),
        EvalCase("ICICI", "ICICI Bank Acct XX123 debited for Rs 600.00 on 20-Sep-25; KOMALSHREE BHAT credited. UPI:562098765250. Call 18002662 for dispute. SMS BLOCK 646 to 9215676766.", "DM-ICIBNK", 600.0, "KOMALSHREE BHAT", null, "expense"),

        // ---- SBI ----
        EvalCase("SBI", "Dear UPI user A/C X9876 debited by 150.0 on date 25Jul25 trf to Mr.RAJESH Ref No 552512345678. If not u? call 1800111109. -Loss Rs 0 SBI", "AM-SBI", 150.0, "Mr.RAJESH", null, "expense"),
        EvalCase("SBI", "Your a/c no. XXXXXXXX1234 is credited by Rs.2,500.00 on 20-Aug-25 by NEFT-SBIN723072300005. (Bal: Rs.15,600.50). -SBI", "AM-SBI", 2500.0, "NEFT-SBIN723072300005", null, "income"),

        // ---- Axis ----
        EvalCase("Axis", "INR 450.00 sent from Axis Bank A/C XX6789 to VPA zomato@icici on 15-Aug-25. UPI Ref:525001234567.", "AM-AXIS", 450.0, "zomato@icici", null, "expense"),
        EvalCase("Axis", "INR 1,200.00 received in your Axis Bank A/C XX6789 from VPA salary@hdfcbank on 01-Sep-25. UPI Ref:524401234567.", "AM-AXIS", 1200.0, "salary@hdfcbank", null, "income"),

        // ---- Canara Bank ----
        EvalCase("Canara", "Rs.300.00 debited from A/C No. XX5678 on 18/08/2025 for UPI-AMAZON-merchant@paytm Bal:Rs.4500.50 -CanaraBank", "AM-CANBNK", 300.0, "AMAZON", null, "expense"),

        // ---- Pluxee ----
        EvalCase("Pluxee", "Dear User, your Pluxee Card ending 1234 has been debited for Rs.350.00 at DOMINOS on 22-Aug-2025.", "AM-PLUXEE", 350.0, "DOMINOS", null, "expense"),

        // ---- Generic/Multi-bank ----
        EvalCase("Generic", "Thank you for using your Debit Card ending 5678 for Rs 899.00 at FLIPKART on 10-Sep-25. If not authorised call 1800-xxx.", "AM-BANK", 899.0, "FLIPKART", null, "expense"),
        EvalCase("Generic", "Txn of INR 1,499.00 done on Card ending 9012 at UBER INDIA on 05-Aug-25.", "AM-BANK", 1499.0, "UBER INDIA", null, "expense"),
        EvalCase("Generic", "Your account XX3456 has been credited with Rs 5,000.00 from JOHN DOE via IMPS on 12-Sep-25. Ref:525601234567.", "AM-BANK", 5000.0, "JOHN DOE", null, "income"),
    )

    @Test
    fun `benchmark NER model against ground truth`() {
        // ---- Locate assets ----
        val projectRoot = File(System.getProperty("user.dir")).parentFile
            ?: File(System.getProperty("user.dir"))
        val assetsDir = findAssetsDir(projectRoot)
        if (assetsDir == null) {
            println("⚠️  Could not locate app/src/main/assets/ — skipping NER evaluation.")
            return
        }

        val modelFile = File(assetsDir, "sms_ner.tflite")
        val vocabFile = File(assetsDir, "ner_vocab.txt")
        val labelMapFile = File(assetsDir, "ner_label_map.json")

        if (!modelFile.exists() || !vocabFile.exists() || !labelMapFile.exists()) {
            println("⚠️  Missing model files in ${assetsDir.absolutePath} — skipping NER evaluation.")
            return
        }

        // ---- Locate Python inference script ----
        val scriptFile = findInferenceScript(projectRoot)
        if (scriptFile == null) {
            println("⚠️  Could not find run_tflite_inference.py — skipping NER evaluation.")
            return
        }

        // ---- Check Python/TF availability ----
        val pythonPath = getPythonPath(projectRoot)
        if (!isPythonAvailable(pythonPath)) {
            println("⚠️  python3 not available or tensorflow not installed at $pythonPath — skipping NER evaluation.")
            return
        }

        println("=" .repeat(80))
        println("  NER MODEL EVALUATION HARNESS")
        println("  ${evalCases.size} test cases from SmsParser unit tests")
        println("=" .repeat(80))

        val extractor = DesktopNerExtractor(
            modelPath = modelFile.absolutePath,
            vocabPath = vocabFile.absolutePath,
            labelMapPath = labelMapFile.absolutePath,
            pythonPath = pythonPath,
            inferenceScriptPath = scriptFile.absolutePath,
        )

        // ---- Run evaluation ----
        data class EvalResult(
            val case: EvalCase,
            val nerEntities: Map<String, String>,
            val nerAmount: String?,
            val nerMerchant: String?,
            val nerAccount: String?,
            val amountMatch: Boolean,
            val merchantMatch: Boolean,
            val accountMatch: Boolean,
            val verdict: String,
        )

        val results = mutableListOf<EvalResult>()

        for (case in evalCases) {
            val entities = try {
                extractor.extract(case.smsBody)
            } catch (e: Exception) {
                System.err.println("  ERROR extracting for ${case.bank}: ${e.message}")
                emptyMap()
            }

            val nerAmount = entities["AMOUNT"]
            val nerMerchant = entities["MERCHANT"]
            val nerAccount = entities["ACCOUNT"]

            // Amount comparison: normalize to double
            val amountMatch = if (case.expectedAmount != null && nerAmount != null) {
                val parsed = parseAmountString(nerAmount)
                parsed != null && kotlin.math.abs(parsed - case.expectedAmount) < 0.01
            } else {
                case.expectedAmount == null && nerAmount == null
            }

            // Merchant comparison: case-insensitive substring or exact
            val merchantMatch = if (case.expectedMerchant != null && nerMerchant != null) {
                val expected = case.expectedMerchant.lowercase().trim()
                val actual = nerMerchant.lowercase().trim()
                expected == actual || actual.contains(expected) || expected.contains(actual)
            } else {
                case.expectedMerchant == null && nerMerchant == null
            }

            // Account comparison: case-insensitive substring or exact
            val accountMatch = if (case.expectedAccount != null && nerAccount != null) {
                val expected = case.expectedAccount.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
                val actual = nerAccount.lowercase().trim().replace(Regex("[^a-z0-9]"), "")
                expected.contains(actual) && actual.isNotEmpty()
            } else {
                case.expectedAccount == null && nerAccount == null
            }

            val verdict = when {
                amountMatch && merchantMatch && accountMatch -> "MATCH"
                amountMatch && merchantMatch -> "ACCOUNT_DIFFERS"
                amountMatch && accountMatch -> "MERCHANT_DIFFERS"
                merchantMatch && accountMatch -> "AMOUNT_DIFFERS"
                else -> "MULTIPLES_DIFFER"
            }

            results.add(EvalResult(case, entities, nerAmount, nerMerchant, nerAccount, amountMatch, merchantMatch, accountMatch, verdict))
        }

        // ---- Compute statistics ----
        val total = results.size
        val matchCount = results.count { it.verdict == "MATCH" }
        val amountDiffers = results.count { it.verdict == "AMOUNT_DIFFERS" }
        val merchantDiffers = results.count { it.verdict == "MERCHANT_DIFFERS" }
        val accountDiffers = results.count { it.verdict == "ACCOUNT_DIFFERS" }
        val multiplesDiffer = results.count { it.verdict == "MULTIPLES_DIFFER" }

        // Per-entity precision/recall
        val amountTP = results.count { it.amountMatch && it.case.expectedAmount != null }
        val amountFP = results.count { !it.amountMatch && it.nerAmount != null }
        val amountFN = results.count { !it.amountMatch && it.case.expectedAmount != null }

        val merchantTP = results.count { it.merchantMatch && it.case.expectedMerchant != null }
        val merchantFP = results.count { !it.merchantMatch && it.nerMerchant != null }
        val merchantFN = results.count { !it.merchantMatch && it.case.expectedMerchant != null }

        val accountTP = results.count { it.accountMatch && it.case.expectedAccount != null }
        val accountFP = results.count { !it.accountMatch && it.nerAccount != null }
        val accountFN = results.count { !it.accountMatch && it.case.expectedAccount != null }

        fun precision(tp: Int, fp: Int) = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
        fun recall(tp: Int, fn: Int) = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
        fun f1(p: Double, r: Double) = if (p + r > 0) 2 * p * r / (p + r) else 0.0

        val amountP = precision(amountTP, amountFP)
        val amountR = recall(amountTP, amountFN)
        val amountF1 = f1(amountP, amountR)

        val merchantP = precision(merchantTP, merchantFP)
        val merchantR = recall(merchantTP, merchantFN)
        val merchantF1 = f1(merchantP, merchantR)

        val accountP = precision(accountTP, accountFP)
        val accountR = recall(accountTP, accountFN)
        val accountF1 = f1(accountP, accountR)

        val overallTP = amountTP + merchantTP + accountTP
        val overallFP = amountFP + merchantFP + accountFP
        val overallFN = amountFN + merchantFN + accountFN
        val microP = precision(overallTP, overallFP)
        val microR = recall(overallTP, overallFN)
        val microF1 = f1(microP, microR)

        // ---- Print summary ----
        val summary = buildString {
            appendLine("=" .repeat(60))
            appendLine("NER EVALUATION SUMMARY")
            appendLine("=" .repeat(60))
            appendLine("Total cases:       $total")
            appendLine("Full match:        $matchCount (${pct(matchCount, total)})")
            appendLine("Amount differs:    $amountDiffers (${pct(amountDiffers, total)})")
            appendLine("Merchant differs:  $merchantDiffers (${pct(merchantDiffers, total)})")
            appendLine("Account differs:   $accountDiffers (${pct(accountDiffers, total)})")
            appendLine("Multiples differ:  $multiplesDiffer (${pct(multiplesDiffer, total)})")
            appendLine()
            appendLine("--- Per-Entity Metrics ---")
            appendLine("AMOUNT:   P=${fmt(amountP)}  R=${fmt(amountR)}  F1=${fmt(amountF1)}  (TP=$amountTP FP=$amountFP FN=$amountFN)")
            appendLine("MERCHANT: P=${fmt(merchantP)}  R=${fmt(merchantR)}  F1=${fmt(merchantF1)}  (TP=$merchantTP FP=$merchantFP FN=$merchantFN)")
            appendLine("ACCOUNT:  P=${fmt(accountP)}  R=${fmt(accountR)}  F1=${fmt(accountF1)}  (TP=$accountTP FP=$accountFP FN=$accountFN)")
            appendLine()
            appendLine("--- Overall Micro ---")
            appendLine("Precision: ${fmt(microP)}  Recall: ${fmt(microR)}  F1: ${fmt(microF1)}")
            appendLine("=" .repeat(60))

            // List mismatches
            val mismatches = results.filter { it.verdict != "MATCH" }
            if (mismatches.isNotEmpty()) {
                appendLine()
                appendLine("--- MISMATCHES (${mismatches.size}) ---")
                for (r in mismatches) {
                    appendLine("[${r.verdict}] ${r.case.bank}")
                    appendLine("  SMS: ${r.case.smsBody.take(80)}...")
                    appendLine("  Expected: amount=${r.case.expectedAmount}  merchant=${r.case.expectedMerchant}  account=${r.case.expectedAccount}")
                    appendLine("  NER:      amount=${r.nerAmount}  merchant=${r.nerMerchant}  account=${r.nerAccount}")
                    appendLine()
                }
            }
        }

        println(summary)

        // ---- Write output files ----
        val timestamp = SimpleDateFormat("MMdd_HHmm", Locale.US).format(Date())
        val reportsDir = File(projectRoot, "app/build/reports").also { it.mkdirs() }

        // Summary TXT
        val summaryFile = File(reportsDir, "ner_eval_summary_$timestamp.txt")
        summaryFile.writeText(summary)
        println("📄 Summary written to: ${summaryFile.absolutePath}")

        // Detailed CSV
        val csvFile = File(reportsDir, "ner_eval_$timestamp.csv")
        csvFile.bufferedWriter().use { w ->
            w.write("bank,verdict,expected_amount,ner_amount,amount_match,expected_merchant,ner_merchant,merchant_match,expected_account,ner_account,account_match,sms_body")
            w.newLine()
            for (r in results) {
                w.write(csvEscape(r.case.bank))
                w.write(",${r.verdict}")
                w.write(",${r.case.expectedAmount ?: ""}")
                w.write(",${csvEscape(r.nerAmount ?: "")}")
                w.write(",${r.amountMatch}")
                w.write(",${csvEscape(r.case.expectedMerchant ?: "")}")
                w.write(",${csvEscape(r.nerMerchant ?: "")}")
                w.write(",${r.merchantMatch}")
                w.write(",${csvEscape(r.case.expectedAccount ?: "")}")
                w.write(",${csvEscape(r.nerAccount ?: "")}")
                w.write(",${r.accountMatch}")
                w.write(",${csvEscape(r.case.smsBody)}")
                w.newLine()
            }
        }
        println("📊 CSV written to: ${csvFile.absolutePath}")
    }

    // ---- Helpers ----

    private fun parseAmountString(s: String): Double? {
        // Handle "Rs.11.00", "8,700.00", "Rs 240.00", "INR 328.00", etc.
        val cleaned = s.replace(Regex("[^0-9.,]"), "")
            .replace(Regex(",(?=\\d{2,3})"), "") // Remove Indian-style commas
        return cleaned.toDoubleOrNull()
    }

    private fun pct(n: Int, total: Int) = if (total > 0) "%.1f%%".format(n * 100.0 / total) else "0.0%"
    private fun fmt(d: Double) = "%.3f".format(d)
    private fun csvEscape(s: String) = "\"${s.replace("\"", "\"\"").replace("\n", " ")}\""

    private fun findAssetsDir(projectRoot: File): File? {
        // Try common locations
        val candidates = listOf(
            File(projectRoot, "app/src/main/assets"),
            File(projectRoot, "src/main/assets"),
        )
        return candidates.firstOrNull { it.isDirectory }
    }

    private fun findInferenceScript(projectRoot: File): File? {
        val candidates = listOf(
            File(projectRoot, "app/src/test/resources/run_tflite_inference.py"),
            File(projectRoot, "src/test/resources/run_tflite_inference.py"),
        )
        return candidates.firstOrNull { it.isFile }
    }

    private fun getPythonPath(projectRoot: File): String {
        val venvPython = File(projectRoot, "ml_training/venv/bin/python3")
        return if (venvPython.exists()) venvPython.absolutePath else "python3"
    }

    private fun isPythonAvailable(pythonPath: String): Boolean {
        return try {
            val p = ProcessBuilder(pythonPath, "-c", "import tensorflow; print('OK')")
                .redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().readText()
            p.waitFor()
            output.contains("OK")
        } catch (_: Exception) {
            false
        }
    }
}
