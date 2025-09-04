// =================================================================================
// FILE: ./core/src/main/java/io/pm/finlight/core/CategoryKeywordMapping.kt
// REASON: NEW FILE - This file contains the keyword-to-category mapping for the
// new heuristic auto-categorization feature. Decoupling this into its own file
// keeps the main SmsParser logic clean and makes the keyword list easier to maintain.
// =================================================================================
package io.pm.finlight.core

/**
 * A map of category names to a list of keywords. This is used as a fallback
 * for auto-categorization when no user-defined mapping exists.
 */
internal val CATEGORY_KEYWORD_MAP: Map<String, List<String>> = mapOf(
    "Bills" to listOf("bill", "electricity", "water", "gas", "mobile", "phone", "internet", "subscription", "postpaid", "broadband", "dth", "recharge"),
    "EMI" to listOf("emi", "loan", "installment", "finance"),
    "Entertainment" to listOf("movie", "cinema", "tickets", "pvr", "inox", "bookmyshow", "netflix", "prime video", "hotstar", "spotify", "wynk", "concert", "theatre"),
    "Food & Drinks" to listOf("food", "restaurant", "cafe", "swiggy", "zomato", "eats", "kitchen", "dining", "pizza", "burger", "thindi", "khana", "hotel", "sagar", "bhavan", "udupi", "andhra"),
    "Fuel" to listOf("fuel", "petrol", "diesel", "gas station", "shell", "bp", "hpcl", "iocl", "indian oil"),
    "Groceries" to listOf("groceries", "supermarket", "bigbasket", "dunzo", "instamart", "blinkit", "reliance fresh", "more", "mart"),
    "Health" to listOf("health", "medical", "doctor", "pharmacy", "apollo", "pharmeasy", "1mg", "hospital", "clinic", "wellness"),
    "Investment" to listOf("investment", "stocks", "mutual fund", "zerodha", "groww", "upstox", "etmoney", "sip"),
    "Shopping" to listOf("shopping", "amazon", "flipkart", "myntra", "ajio", "nykaa", "lifestyle", "shoppers stop", "westside", "croma", "reliance digital", "clothes", "electronics"),
    "Transfer" to listOf("transfer", "neft", "imps", "rtgs", "sent to", "received from", "credited by", "debited to"),
    "Travel" to listOf("travel", "flight", "hotel", "train", "bus", "irctc", "ola", "uber", "makemytrip", "goibibo", "redbus", "indigo", "vistara", "airindia"),
    "Salary" to listOf("salary", "payroll", "wages"),
    "Refund" to listOf("refund", "reversal"),
    "Bike" to listOf("bike", "scooter", "two wheeler", "rapido", "service", "spares"),
    "Car" to listOf("car", "vehicle service", "parking", "tolls", "fastag"),
    "Debt" to listOf("debt", "loan payment", "credit card payment", "interest"),
    "Family" to listOf("family", "kids", "parents"),
    "Friends" to listOf("friends", "outing"),
    "Gift" to listOf("gift", "present", "bouquet"),
    "Fitness" to listOf("fitness", "gym", "cult.fit", "healthifyme", "supplements"),
    "Home Maintenance" to listOf("home", "maintenance", "repair", "housing", "society", "plumber", "electrician"),
    "Insurance" to listOf("insurance", "lic", "policy", "premium", "hdfc ergo", "star health"),
    "Learning & Education" to listOf("education", "school", "college", "course", "books", "udemy", "byjus"),
    "Rent" to listOf("rent")
)
