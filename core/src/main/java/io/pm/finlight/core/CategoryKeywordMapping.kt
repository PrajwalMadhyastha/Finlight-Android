// =================================================================================
// FILE: ./core/src/main/java/io/pm/finlight/core/CategoryKeywordMapping.kt
// REASON: FEATURE - The keyword mapping has been significantly expanded with a
// much more extensive list of synonyms, common merchants, and India-specific
// brand names. This will dramatically improve the accuracy and "magic" of the
// heuristic auto-categorization feature.
// =================================================================================
package io.pm.finlight.core

/**
 * A map of category names to a list of keywords. This is used as a fallback
 * for auto-categorization when no user-defined mapping exists.
 */
internal val CATEGORY_KEYWORD_MAP: Map<String, List<String>> = mapOf(
    "Bills" to listOf(
        "bill", "electricity", "water", "gas", "mobile", "phone", "internet", "subscription",
        "postpaid", "broadband", "dth", "recharge", "bescom", "bwssb", "jiofiber", "airtel", "act",
        "airtel xstream", "act fibernet", "vi postpaid", "bsnl", "statement due", "credit card bill"
    ),
    "EMI" to listOf("emi", "loan", "installment", "finance", "bajaj finserv", "hdfc fin", "idfc first"),
    "Entertainment" to listOf(
        "movie", "cinema", "tickets", "pvr", "inox", "bookmyshow", "netflix", "prime video",
        "hotstar", "disney", "spotify", "wynk", "jiosaavn", "gaana", "concert", "theatre", "youtube"
    ),
    "Food & Drinks" to listOf(
        "food", "restaurant", "cafe", "swiggy", "zomato", "eats", "kitchen", "dining", "pizza",
        "burger", "thindi", "khana", "hotel", "sagar", "bhavan", "udupi", "andhra", "domino's",
        "pizza", "mcdonald's", "kfc", "box8", "biryani", "dosa", "juice", "coffee",
        "starbucks", "cafe coffee day", "barista", "chai", "bakery", "sweets", "eatsure"
    ),
    "Fuel" to listOf(
        "fuel", "petrol", "diesel", "gas station", "shell", "bp", "hpcl", "iocl", "indian oil",
        "bharat petroleum"
    ),
    "Groceries" to listOf(
        "groceries", "supermarket", "bigbasket", "dunzo", "instamart", "blinkit", "zepto",
        "reliance fresh", "more", "mart", "dmart", "spar", "nature's basket", "milk", "bread",
        "vegetables", "fruits"
    ),
    "Health" to listOf(
        "health", "medical", "doctor", "pharmacy", "apollo", "pharmeasy", "1mg", "netmeds",
        "hospital", "clinic", "wellness", "medplus", "practo", "lab test", "consultation",
        "doctor's fee", "dental"
    ),
    "Investment" to listOf(
        "investment", "stocks", "mutual fund", "zerodha", "groww", "upstox", "etmoney", "sip",
        "coin"
    ),
    "Shopping" to listOf(
        "shopping", "amazon", "flipkart", "myntra", "ajio", "nykaa", "lifestyle", "shoppers stop",
        "westside", "croma", "reliance digital", "clothes", "electronics", "zara", "h&m",
        "pantaloons", "max fashion", "meesho", "tatacliq", "decathlon", "ikea", "trends footwear"
    ),
    "Transfer" to listOf(
        "transfer", "neft", "imps", "rtgs", "sent to", "received from", "credited by", "debited to"
    ),
    "Travel" to listOf(
        "travel", "flight", "hotel", "train", "bus", "irctc", "ola", "uber", "makemytrip",
        "goibibo", "redbus", "indigo", "vistara", "airindia", "spicejet", "ride", "drive",
        "metro", "bmtc", "local train", "cleartrip", "yatra"
    ),
    "Salary" to listOf("salary", "payroll", "wages"),
    "Refund" to listOf("refund", "reversal"),
    "Bike" to listOf("bike", "scooter", "two wheeler", "rapido", "service", "spares", "ather", "ola electric"),
    "Car" to listOf("car", "vehicle service", "parking", "tolls", "fastag", "maruti", "hyundai", "tata motors"),
    "Debt" to listOf("debt", "loan payment", "credit card payment", "interest"),
    "Family" to listOf("family", "kids", "parents", "school fees"),
    "Friends" to listOf("friends", "outing"),
    "Gift" to listOf("gift", "present", "bouquet", "igp.com"),
    "Fitness" to listOf("fitness", "gym", "cult.fit", "healthifyme", "supplements", "yoga"),
    "Home Maintenance" to listOf(
        "home", "maintenance", "repair", "housing", "society", "plumber", "electrician",
        "urban company", "nobroker", "carpenter", "painting", "pest control"
    ),
    "Insurance" to listOf("insurance", "lic", "policy", "premium", "hdfc ergo", "star health", "acko", "digit"),
    "Learning & Education" to listOf(
        "education", "school", "college", "course", "books", "udemy", "byjus", "coursera", "skillshare"
    ),
    "Rent" to listOf("rent", "nobroker pay", "cred rentpay"),
    // --- NEW CATEGORIES ---
    "Pets" to listOf("pet", "veterinary", "vet", "grooming", "pet food", "heads up for tails"),
    "Charity" to listOf("charity", "donation", "giveindia", "unicef", "cry", "ketto"),
    "Personal Care" to listOf("salon", "spa", "barber", "haircut", "manicure", "pedicure", "urbanclap")
)