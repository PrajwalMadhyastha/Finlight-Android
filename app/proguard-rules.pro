# =================================================================================
# FILE: ./app/proguard-rules.pro
# REASON: REFACTOR - Removed the ProGuard rules for the Google Drive API, as
# the corresponding dependencies have been removed from the project.
# =================================================================================

# --- General Android & Kotlin ---
-keep class kotlin.jvm.internal.DefaultConstructorMarker
#-dontwarn kotlin.collections.List
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes *Annotation*

# --- Coroutines ---
-keep class kotlin.coroutines.Continuation

# --- Room ---
# Keep all data classes used as Room entities, DTOs, and their fields.
-keep class io.pm.finlight.** { *; }
-keep class io.pm.finlight.core.** { *; }
-keep class io.pm.finlight.data.model.** { *; }
-keep class io.pm.finlight.data.db.dao.** { *; }

# --- Kotlinx Serialization ---
# Keep classes annotated with @Serializable and their members.
-keepclasseswithmembers,allowobfuscation class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepnames class * {
    @kotlinx.serialization.Serializable *;
}
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.text.RegexOption { *; }

# --- Gson ---
# Keep the data class used with Gson for passing data between screens.
-keepclassmembers class io.pm.finlight.PotentialTransaction {
    <fields>;
}
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken

# --- MPAndroidChart ---
# This library can have issues with R8, so keep the entire package.
-keep class com.github.mikephil.charting.** { *; }

# --- SQLCipher ---
-keep class net.sqlcipher.** { *; }

# --- REMOVED: Google Drive API rules are no longer needed ---

# --- Compose ---
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *(...);
}
-keep class androidx.compose.runtime.internal.ComposableLambdaImpl