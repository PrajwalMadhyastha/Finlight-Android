// =================================================================================
// FILE: ./app/src/main/java/io/pm/finlight/utils/HeuristicCategorizer.kt
// REASON: NEW FILE - This utility contains the logic for the new "smart
// auto-categorization" feature on the Add Transaction screen. It checks a
// transaction's description against a map of keywords to suggest a category,
// creating a zero-friction user experience.
// =================================================================================
package io.pm.finlight.utils

import io.pm.finlight.Category

/**
 * A utility object to find a category suggestion based on keywords in a transaction description.
 */
object HeuristicCategorizer {

    // This map is a copy of the one in the :core module.
    // Ideally, this would be shared, but this approach avoids changing visibility scopes.
    private val CATEGORY_KEYWORD_MAP: Map<String, List<String>> = mapOf(
        "Bills" to listOf(
            "bill", "electricity", "water", "gas", "mobile", "phone", "internet", "subscription",
            "postpaid", "broadband", "dth", "recharge", "bescom", "bwssb", "jiofiber", "airtel", "act",
            "airtel xstream", "act fibernet", "vi postpaid", "bsnl", "statement", "credit card"
        ),
        "EMI" to listOf("emi", "loan", "installment", "finance", "bajaj finserv", "hdfc fin", "idfc first"),
        "Entertainment" to listOf(
            "movie", "cinema", "tickets", "pvr", "inox", "bookmyshow", "netflix", "prime video",
            "hotstar", "disney", "spotify", "wynk", "jiosaavn", "gaana", "concert", "theatre", "youtube", "prime"
        ),
        "Food & Drinks" to listOf(
            "food", "restaurant", "cafe", "swiggy", "zomato", "eats", "kitchen", "dining", "pizza",
            "burger", "thindi", "khana", "hotel", "sagar", "bhavan", "udupi", "andhra", "domino's",
            "pizza", "mcdonald's", "kfc", "box8", "biryani", "dosa", "juice", "coffee",
            "starbucks", "cafe coffee day", "barista", "chai", "bakery", "sweets", "eatsure", "donald",
            "domino", "truffles", "kanavali", "dakshin"
        ),
        "Fuel" to listOf(
            "fuel", "petrol", "diesel", "gas station", "shell", "bp", "hpcl", "iocl", "indian",
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
            "pantaloons", "max fashion", "meesho", "tatacliq", "decathlon", "ikea", "trends",
            "footware"
        ),
        "Transfer" to listOf(
            "transfer", "neft", "imps", "rtgs", "sent to", "received from", "credited by", "debited to"
        ),
        "Travel" to listOf(
            "travel", "flight", "hotel", "train", "bus", "irctc", "ola", "uber", "makemytrip",
            "goibibo", "redbus", "indigo", "vistara", "airindia", "spicejet", "ride", "drive",
            "metro", "bmtc", "local train", "cleartrip", "yatra", "namma"
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
        "Pets" to listOf("pet", "veterinary", "vet", "grooming", "pet food", "heads up for tails"),
        "Charity" to listOf("charity", "donation", "giveindia", "unicef", "cry", "ketto"),
        "Personal Care" to listOf("salon", "spa", "barber", "haircut", "manicure", "pedicure", "urbanclap")
    )

    /**
     * Finds a category for a given description by matching keywords.
     *
     * @param description The transaction description.
     * @param allCategories A list of all available Category objects.
     * @return The matching Category, or null if no match is found.
     */
    fun findCategoryForDescription(description: String, allCategories: List<Category>): Category? {
        val lowerCaseDescription = description.lowercase()
        for ((categoryName, keywords) in CATEGORY_KEYWORD_MAP) {
            if (keywords.any { keyword -> lowerCaseDescription.contains(keyword) }) {
                // Find the full Category object that matches the found category name
                return allCategories.find { it.name.equals(categoryName, ignoreCase = true) }
            }
        }
        return null
    }
}
