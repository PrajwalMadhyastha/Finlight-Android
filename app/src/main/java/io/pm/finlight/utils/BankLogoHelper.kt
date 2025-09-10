package io.pm.finlight.utils

import androidx.annotation.DrawableRes
import io.pm.finlight.R

/**
 * A helper object to provide logos for various Indian banks.
 */
object BankLogoHelper {

    /**
     * Returns the drawable resource ID for a bank's logo based on the account name.
     *
     * @param accountName The name of the account (e.g., "HDFC Bank Savings", "My SBI Card").
     * @return The resource ID for the matching logo, or a default bank icon if no match is found.
     */
    @DrawableRes
    fun getLogoForAccount(accountName: String): Int {
        val lowerCaseName = accountName.lowercase()
        return when {
            "amazon" in lowerCaseName -> R.drawable.ic_amazon_logo
            "airtel" in lowerCaseName -> R.drawable.ic_airtel_logo
            "jio" in lowerCaseName -> R.drawable.ic_jio_logo
            "paytm" in lowerCaseName -> R.drawable.ic_paytm_logo
            "phonepe" in lowerCaseName -> R.drawable.ic_phonepe_logo
            "ola" in lowerCaseName -> R.drawable.ic_ola_logo
            "pluxee" in lowerCaseName || "sodexo" in lowerCaseName -> R.drawable.ic_pluxee_logo
            "simpl" in lowerCaseName -> R.drawable.ic_simpl_logo
            "hdfc" in lowerCaseName -> R.drawable.ic_hdfc_logo
            "icici" in lowerCaseName -> R.drawable.ic_icici_logo
            "sbi" in lowerCaseName || "state bank" in lowerCaseName -> R.drawable.ic_sbi_logo
            "axis" in lowerCaseName -> R.drawable.ic_axis_logo
            "kotak" in lowerCaseName || "kotak mahindra" in lowerCaseName -> R.drawable.ic_kotak_logo
            "pnb" in lowerCaseName || "punjab national" in lowerCaseName -> R.drawable.ic_pnb_logo
            "baroda" in lowerCaseName -> R.drawable.ic_bob_logo
            "canara" in lowerCaseName -> R.drawable.ic_canara_logo
            "yes" in lowerCaseName -> R.drawable.ic_yes_logo
            "indusind" in lowerCaseName -> R.drawable.ic_indusind_logo
            "idfc" in lowerCaseName -> R.drawable.ic_idfc_logo
            "citi" in lowerCaseName -> R.drawable.ic_citi_logo
            "bandan" in lowerCaseName -> R.drawable.ic_bandan_logo
            "bank of america" in lowerCaseName || "boa" in lowerCaseName -> R.drawable.ic_boa_logo
            "bank of india" in lowerCaseName || "boi" in lowerCaseName -> R.drawable.ic_boi_logo
            "bank of maharastra" in lowerCaseName || "bom" in lowerCaseName -> R.drawable.ic_bom_logo
            "central" in lowerCaseName -> R.drawable.ic_cbi_logo
            "union" in lowerCaseName -> R.drawable.ic_cub_logo
            "credit suisse" in lowerCaseName -> R.drawable.ic_creditsuisse_logo
            "hsbc" in lowerCaseName -> R.drawable.ic_hsbc_logo
            "idbi" in lowerCaseName -> R.drawable.ic_idbi_logo
            "indian overseas" in lowerCaseName || "iob" in lowerCaseName -> R.drawable.ic_iob_logo
            "jpmorgan chase" in lowerCaseName || "jpm" in lowerCaseName -> R.drawable.ic_jpm_logo
            "karnataka" in lowerCaseName -> R.drawable.ic_kb_logo
            "natwest" in lowerCaseName -> R.drawable.ic_natwest_logo
            "standard chartered" in lowerCaseName -> R.drawable.ic_standardchartered_logo
            "cash" in lowerCaseName -> R.drawable.ic_cash_spends
            else -> R.drawable.ic_default_bank_logo // Default fallback icon
        }
    }
}