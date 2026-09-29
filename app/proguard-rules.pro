# =================================================================================
# FILE: ./app/proguard-rules.pro
# REASON: REFACTOR (Issue #322) - Google Play Compliance: Enable R8 shrinking,
# remove blanket keep rules, add granular rules for Room, Kotlinx Serialization,
# and Gson, update SQLCipher rules, and remove obsolete Compose rules.
# =================================================================================

# --- General Android & Kotlin ---
-keep class kotlin.jvm.internal.DefaultConstructorMarker
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
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.text.RegexOption { *; }

# --- Gson ---
# Keep data classes used with Gson for passing data between screens and in repositories.
-keepclassmembers class io.pm.finlight.PotentialTransaction { <fields>; }
-keepclassmembers class io.pm.finlight.PotentialAccount { <fields>; }
-keepclassmembers class io.pm.finlight.TransactionDetails { <fields>; }
-keepclassmembers class io.pm.finlight.data.repository.TravelModeSettings { <fields>; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# --- MPAndroidChart ---
-keep class com.github.mikephil.charting.** { *; }

# --- SQLCipher ---
-keep class net.zetetic.database.sqlcipher.** { *; }
