package io.pm.finlight.utils

import android.os.Build
import io.pm.finlight.BaseViewModelTest
import io.pm.finlight.R
import io.pm.finlight.TestApplication
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.robolectric.annotation.Config

@RunWith(Parameterized::class)
@Config(sdk = [Build.VERSION_CODES.UPSIDE_DOWN_CAKE], application = TestApplication::class)
class BankLogoHelperTest(
    private val accountName: String,
    private val expectedLogoRes: Int,
) : BaseViewModelTest() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: getLogoForAccount(\"{0}\") should return {1}")
        fun data(): Collection<Array<Any>> =
            listOf(
                // Tech / Wallets
                arrayOf("Amazon Pay Balance", R.drawable.ic_amazon_logo),
                arrayOf("Airtel Money", R.drawable.ic_airtel_logo),
                arrayOf("Jio Payments Bank", R.drawable.ic_jio_logo),
                arrayOf("Paytm Wallet", R.drawable.ic_paytm_logo),
                arrayOf("PhonePe Wallet", R.drawable.ic_phonepe_logo),
                arrayOf("Ola Money", R.drawable.ic_ola_logo),
                arrayOf("Pluxee Card", R.drawable.ic_pluxee_logo),
                arrayOf("Sodexo Meal Card", R.drawable.ic_pluxee_logo),
                arrayOf("Simpl Pay", R.drawable.ic_simpl_logo),
                arrayOf("Cash Spends", R.drawable.ic_cash_spends),
                // Major Indian Banks
                arrayOf("HDFC Bank Savings", R.drawable.ic_hdfc_logo),
                arrayOf("my sbi card", R.drawable.ic_sbi_logo),
                arrayOf("State Bank of India", R.drawable.ic_sbi_logo),
                arrayOf("ICICI Credit Card", R.drawable.ic_icici_logo),
                arrayOf("Axis Bank", R.drawable.ic_axis_logo),
                arrayOf("Kotak Mahindra", R.drawable.ic_kotak_logo),
                arrayOf("PNB Account", R.drawable.ic_pnb_logo),
                arrayOf("Punjab National Bank", R.drawable.ic_pnb_logo),
                arrayOf("Bank of Baroda", R.drawable.ic_bob_logo),
                arrayOf("BOB Savings Acct", R.drawable.ic_bob_logo),
                arrayOf("Canara Bank", R.drawable.ic_canara_logo),
                arrayOf("Yes Bank", R.drawable.ic_yes_logo),
                arrayOf("IndusInd Bank", R.drawable.ic_indusind_logo),
                arrayOf("IDFC First Bank", R.drawable.ic_idfc_logo),
                arrayOf("Citi Credit Card", R.drawable.ic_citi_logo),
                arrayOf("Bandhan Bank Account", R.drawable.ic_bandan_logo),
                arrayOf("Bandan Bank", R.drawable.ic_bandan_logo),
                arrayOf("Bank of America", R.drawable.ic_boa_logo),
                arrayOf("BOA Checking", R.drawable.ic_boa_logo),
                arrayOf("Bank of India", R.drawable.ic_boi_logo),
                arrayOf("BOI Savings Acct XX823", R.drawable.ic_boi_logo),
                arrayOf("Bank of Maharashtra", R.drawable.ic_bom_logo),
                arrayOf("BOM Current Acct", R.drawable.ic_bom_logo),
                arrayOf("Central Bank of India", R.drawable.ic_cbi_logo),
                arrayOf("CBI Salary Account", R.drawable.ic_cbi_logo),
                arrayOf("City Union Bank", R.drawable.ic_cub_logo),
                arrayOf("CUB Savings", R.drawable.ic_cub_logo),
                arrayOf("Union Bank of India", R.drawable.ic_cub_logo),
                arrayOf("Credit Suisse", R.drawable.ic_creditsuisse_logo),
                arrayOf("HSBC Premier", R.drawable.ic_hsbc_logo),
                arrayOf("IDBI Bank", R.drawable.ic_idbi_logo),
                arrayOf("Indian Overseas Bank", R.drawable.ic_iob_logo),
                arrayOf("IOB Savings", R.drawable.ic_iob_logo),
                arrayOf("JPMorgan Chase", R.drawable.ic_jpm_logo),
                arrayOf("Chase Sapphire", R.drawable.ic_jpm_logo),
                arrayOf("Karnataka Bank", R.drawable.ic_kb_logo),
                arrayOf("KB Savings", R.drawable.ic_kb_logo),
                arrayOf("NatWest Current Account", R.drawable.ic_natwest_logo),
                arrayOf("Standard Chartered", R.drawable.ic_standardchartered_logo),
                arrayOf("SCB Platinum", R.drawable.ic_standardchartered_logo),
                // Fallbacks
                arrayOf("Unknown Financial Institution", R.drawable.ic_default_bank_logo),
                arrayOf("", R.drawable.ic_default_bank_logo),
            )
    }

    @Test
    fun `getLogoForAccount returns correct logo`() {
        val actualLogoRes = BankLogoHelper.getLogoForAccount(accountName)
        assertEquals(expectedLogoRes, actualLogoRes)
    }
}
