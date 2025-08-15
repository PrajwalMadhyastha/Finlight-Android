package io.pm.finlight

/**
 * A decoupled, standalone list of default SMS ignore rules for the application.
 * This allows the core parsing logic to be independent of the Android database implementation.
 */
val DEFAULT_IGNORE_PHRASES = listOf(
    // Existing Rules
    "invoice of", "payment of.*is successful",
    "credited to Beneficiary",
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
    "Refund of Rs.*has been processed",
    "FLAT.*OFF on purchase",
    "get FLAT.*OFF",
    "Money Deposited~",
    "worth points credited",
    "will be activated on Jio network",
    "advise your remitter to use new IFSC",
    "Receipt will be sent shortly",
    "Insurance claim u/s",
    "RT-PCR sample collected",
    "will be debited from your account",
    "OTP for online purchase",

    // Rules for recently found informational messages
    "renewal premium",
    "Email Id.*has been added",
    "activate your eSIM",
    "BHK frm",

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

    // --- NEW: Rules for incorrectly parsed informational messages ---
    "Order ID.*have been sent",
    "Insurance Policy.*is",
    "processed visa application",
    "get refund of",
    "get paid for order",
    "SHARES OF.*TOWARDS SCHEME",
    "Voluntary Contribution.*credited to PRAN",
    "registered in Cash Equity",
    "PAYMENT OF.*RECEIVED TOWARDS YOUR CREDIT CARD",
    "Complaint not responded to by your bank/NBFC/e-wallet"

).map { IgnoreRule(pattern = it, type = RuleType.BODY_PHRASE, isDefault = true) } + listOf(
    // Existing Senders
    "*SBIMF", "*WKEFTT", "*BSNL", "*HDFCMF", "*AXISMF", "*KOTAKM", "*QNTAMC", "*NIMFND",
    "*MYNTRA", "*FLPKRT", "*AMAZON", "*SWIGGY", "*ZOMATO", "*BLUDRT", "*EKARTL",
    "*XPBEES", "*OLAMNY", "*Paytm",
    "*DLHVRY",
    "*Jio",
    "*SBLIFE",
    "*DICGCI",
    "*MYGOVT",
    "*EPFOHO", "*UIICHO",

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
