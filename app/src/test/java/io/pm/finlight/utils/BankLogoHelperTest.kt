package io.pm.finlight.utils

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    private val expectedLogoRes: Int
) : BaseViewModelTest() {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{index}: getLogoForAccount(\"{0}\") should return {1}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("HDFC Bank Savings", R.drawable.ic_hdfc_logo),
                arrayOf("my sbi card", R.drawable.ic_sbi_logo),
                arrayOf("ICICI Credit Card", R.drawable.ic_icici_logo),
                arrayOf("Axis Bank", R.drawable.ic_axis_logo),
                arrayOf("Kotak Mahindra", R.drawable.ic_kotak_logo),
                arrayOf("Amazon Pay Balance", R.drawable.ic_amazon_logo),
                arrayOf("Cash Spends", R.drawable.ic_cash_spends),
                arrayOf("PhonePe Wallet", R.drawable.ic_phonepe_logo),
                arrayOf("Unknown Financial Institution", R.drawable.ic_default_bank_logo)
            )
        }
    }

    @Test
    fun `getLogoForAccount returns correct logo`() {
        val actualLogoRes = BankLogoHelper.getLogoForAccount(accountName)
        assertEquals(expectedLogoRes, actualLogoRes)
    }
}
