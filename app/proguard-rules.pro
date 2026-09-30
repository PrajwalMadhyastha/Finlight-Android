# =================================================================================
# FILE: ./app/proguard-rules.pro
# REASON: REFACTOR (Issue #322) - Google Play Compliance: Enable R8 shrinking,
# remove blanket keep rules, add granular rules for Room, Kotlinx Serialization,
# and Gson, update SQLCipher rules, and remove obsolete Compose rules.
# =================================================================================

# --- General Android & Kotlin ---
-keep class kotlin.jvm.internal.DefaultConstructorMarker
-keep class kotlin.text.RegexOption { *; }
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes *Annotation*

# --- Coroutines ---
-keep class kotlin.coroutines.Continuation

# --- Room Entities, DAOs, and Database ---
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.TypeConverter { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
-keep class io.pm.finlight.data.db.Converters { *; }
-dontwarn androidx.room.paging.**

# --- Room Query Result DTOs ---
# Preserve non-entity query DTOs and their constructors used by Room DAOs and relations
-keepclassmembers class io.pm.finlight.AccountWithBalance { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.TransactionWithSplits { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.SplitTransactionDetails { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.TransactionDetails { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.CategorySpending { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.DailyTotal { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.DailyTrend { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.WeeklyTrend { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.MonthlyTrend { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.MerchantSpendingSummary { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.PeriodTotal { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.FinancialSummary { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.BudgetWithSpending { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.GoalWithAccountName { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.data.db.dao.TripWithStats { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.data.db.dao.OriginalDescriptionCount { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.data.model.SpendingAnalysisItem { <fields>; <init>(...); }
-keepclassmembers class io.pm.finlight.data.model.MerchantPrediction { <fields>; <init>(...); }

# --- Enums (Room TypeConverters, Gson, Kotlinx Serialization, and Preferences) ---
-keep enum io.pm.finlight.** {
    <fields>;
    <methods>;
}

# --- Kotlinx Serialization ---
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepnames class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    *** serializer(...);
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class *$$serializer {
    public static final *$$serializer INSTANCE;
}

# --- Gson ---
# Keep data classes used with Gson for passing data between screens and in repositories.
-keep class io.pm.finlight.PotentialTransaction { <fields>; <init>(...); }
-keep class io.pm.finlight.PotentialAccount { <fields>; <init>(...); }
-keep class io.pm.finlight.TravelModeSettings {
    <fields>;
    <init>(...);
}
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }

# --- SQLCipher ---
-keep class net.zetetic.database.sqlcipher.** { *; }
