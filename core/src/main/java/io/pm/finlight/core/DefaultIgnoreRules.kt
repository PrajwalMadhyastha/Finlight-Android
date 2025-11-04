// =================================================================================
// FILE: ./core/src/main/java/io/pm/finlight/core/DefaultIgnoreRules.kt
// REASON: FIX - Replaced the overly broad "credited to your.*card" rule with the
// more specific "NEFT money transfer.*has been credited to". This prevents the
// parser from incorrectly ignoring valid credit card reversal/refund messages
// while still filtering out informational NEFT confirmations.
// =================================================================================
package io.pm.finlight

import io.pm.finlight.IgnoreRule
import io.pm.finlight.RuleType

/**
 * A decoupled, standalone list of default SMS ignore rules for the application.
 * This allows the core parsing logic to be independent of the Android database implementation.
 */
val DEFAULT_IGNORE_PHRASES = listOf(
    // Existing Rules
    "invoice of", "payment of.*is successful",
    "credited to Beneficiary",
    "payment of.*has been received towards", // This correctly handles bill payments
    "NEFT money transfer.*has been credited to", // This is more specific and safer
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
    "successfully registered for UPI", "Unit Allotment", "Mutual Fund", "E-statement of",
    "order is received",
    "added/modified.*payee",
    "recharge of.*successfully credited",
    "policy.*successfully converted",
    "payment of.*has failed",
    "bonus points",
    "Delivery Authentication Code",
    "Voucher Code for",
    "Application No.*received",
    "off per carat",
    "booking ID.*is confirmed",
    "is Credited on your wallet account",
    "has been delivered",
    "Spam",
    "NEFT from Ac",
    "NEFT of Rs.*credited to Beneficiary",
    "Refund of Rs.*processed",
    "FLAT.*OFF on purchase",
    "get FLAT.*OFF",
    "Money Deposited~",
    "worth points credited",
    "will be activated on Jio network",
    "advise your remitter to use new IFSC",
    "Receipt will be sent shortly",
    "Insurance claim u/s",
    "RT-PCR sample collected",
    "OTP for online purchase",
    "UPI mandate collect",
    "Thank you for applying",
    "facing some technical issues",
    "Total Due.*Min Due",
    "UPI mandate collect request",

    // Rules for recently found informational messages
    "renewal premium",
    "Email Id.*has been added",
    "activate your eSIM",
    "BHK frm",
    "shares of.*towards bonus",

    // Rules for the latest batch of non-financial messages
    "Lok Adalat Notice",
    "FD.*opened", "FD.*closed", "Fixed Deposit", "Recurring Deposit", "RD.*closed", "RD.*opened",
    "will be unavailable",
    "worth Rs.1000 & above",
    "medical records",
    "Article No:",
    "cheques sent to you",
    "added you as a family member",
    "Watch.*on JioTV",
    "My11Circle",
    "Insurance Provider",
    "NeuCoin.*", "TataNeu",
    "Received with thanks.*by receipt number",
    "credited to the beneficiary account",

    // Rules for incorrectly parsed informational messages
    "Order ID.*have been sent",
    "Insurance Policy.*is",
    "processed visa application",
    "get refund of",
    "get paid for order",
    "SHARES OF.*TOWARDS SCHEME",
    "Voluntary Contribution.*credited to PRAN",
    "registered in Cash Equity",
    "PAYMENT OF.*RECEIVED TOWARDS YOUR CREDIT CARD",
    "Complaint not responded to by your bank/NBFC/e-wallet",
    "cashless claim.*Medi Assist",
    "Tata Play.*deactivated",
    "Simpl bill payment",
    "Delivered.*Card",

    // --- NEW: Rules for latest batch of non-transactional messages ---
    "Dispatched: Credit Card",
    "payment of.*has been received on",
    "E-stmt for",
    "has requested money",
    "booking is confirmed",
    "invoice sent",
    "Payment Received. Your Receipt No",
    "Request Received!",
    "Airtel Xstream.*renewal",
    "will be debited",
    "Your request.* is received",
    "personal loan",
    "Withdrawal request",
    "Withdrawal of.*credited to bank account", "PNR-", "added your A/c", "Welcome Bonus", "credited to your NPS",
    "Card.*has been activated", "Order Cancelled", "Dispatched.*Courier", "Return Picked Up", "EMI Received",
    "CIBIL report", "Namma Metro card recharge", "Rummy", "Gujjadi Swarna",
    "Contribution of.*has been received", // --- NEW: For passbook/PF/NPS updates
    "will be deducted", "Reward Points Credited",
    "Statement is sent to", "into SmartEMIS",

).map { IgnoreRule(pattern = it, type = RuleType.BODY_PHRASE, isDefault = true) } + listOf(
    // Existing Senders
    "*SBIMF", "*WKEFTT", "*BSNL", "*HDFCMF", "*AXISMF", "*KOTAKM", "*QNTAMC", "*NIMFND",
    "*MYNTRA", "*FLPKRT", "*AMAZON", "*SWIGGY", "*ZOMATO", "*BLUDRT", "*EKARTL",
    "*XPBEES", "*OLAMNY", "*Paytm",
    "*DLHVRY",
    "*Jio*", "*AIR*", "*MAX*", "*NES*",
    "*SBLIFE",
    "*DICGCI",
    "*MYGOVT",
    "*EPFOHO", "*UIICHO", "*RMATIC", "*TPPLAY",
    "*JERUMY", "*LODSHR", "*JEERMY", "*ADHAAR", "*IPRCTO", "*IRSMSa", "*LFSSTL*", "*BNCEWR", "*HGLOYL", "*CARSEL", "*RummyC",
    "*OLACAB", "*AQALEN", "*ZEPTON", "*FSTCRY", "*ACTGRP", "*INYKAA", "*PTECOM", "*NSEIPO", "*LNKART", "*GMSCLK", "*NIGAME", "*NIRAG*",
    "*SOCIAI", "*FINSAB", "*JGLRMM", "*SxCIAL", "*CARTFT", "*AZORTE", "*iOneMG", "*CLKG*", "*EASEBZ", "*PAYRWD", "*CDSL*",

    // Senders for the latest batch of non-financial messages
    "*LOKADL",
    "*PORTER",
    "*APOLLO",
    "*JIOHH",
    "*IndPst",
    "*PRACTO",
    "*JIOCIN",
    "*MY11CE",
    "MYTNEU"

).map { IgnoreRule(pattern = it, type = RuleType.SENDER, isDefault = true) }
