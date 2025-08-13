package io.pm.finlight

/**
 * A decoupled, standalone list of default SMS ignore rules for the application.
 * This allows the core parsing logic to be independent of the Android database implementation.
 */
val DEFAULT_IGNORE_PHRASES = listOf(
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
    "successfully registered for UPI", "Unit Allotment", "Mutual Fund", "E-statement of",
    "order is received",

    // NEW: Rules from latest batch of failed transactions
    // --- UPDATED: Made the rule more flexible to catch variations ---
    "added/modified.*payee", // Catches HDFC payee notifications
    "recharge of.*successfully credited", // Catches Airtel recharge confirmations
    "policy.*successfully converted", // Catches insurance policy updates
    "payment of.*has failed", // Catches failed payment notifications
    "bonus points", // Catches loyalty program updates
    "Delivery Authentication Code", // Catches delivery notifications with codes
    "Voucher Code for", // Catches gift card and voucher code messages
    "Application No.*received", // Catches application/scheme confirmations
    "off per carat" // Catches promotional messages with monetary-like offers

).map { IgnoreRule(pattern = it, type = RuleType.BODY_PHRASE, isDefault = true) } + listOf(
    // Existing Senders
    "*SBIMF", "*WKEFTT", "*BSNL", "*HDFCMF", "*AXISMF", "*KOTAKM", "*QNTAMC", "*NIMFND",
    "*MYNTRA", "*FLPKRT", "*AMAZON", "*SWIGGY", "*ZOMATO", "*BLUDRT", "*EKARTL",
    "*XPBEES", "*OLAMNY", "*Paytm",

    // FIX: Corrected sender pattern to match test cases
    "*DLHVRY"

).map { IgnoreRule(pattern = it, type = RuleType.SENDER, isDefault = true) }
