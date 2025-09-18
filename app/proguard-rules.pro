########################################
# Preserve generic type information
# (needed for Retrofit, Gson, Moshi, coroutines)
########################################
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes *Annotation*
-keepattributes Exceptions
-keepattributes SourceFile,LineNumberTable

########################################
# --- Your app models ---
########################################
-keep class com.kharagedition.tibetankeyboard.util.User { *; }
-keep class com.kharagedition.tibetankeyboard.util.DeviceInfo { *; }
-keep class com.kharagedition.tibetankeyboard.util.** { *; }
-keep class com.kharagedition.tibetankeyboard.model.** { *; }

########################################
# --- Retrofit / OkHttp ---
########################################
-keep interface com.kharagedition.tibetankeyboard.network.** { *; }
-keep class com.kharagedition.tibetankeyboard.network.** { *; }

# Keep all Retrofit classes and methods
-keep class retrofit2.** { *; }
-keepclassmembers class retrofit2.** { *; }
-dontwarn retrofit2.**

# Keep service interface methods
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Keep generic signature of Call, Response (R8 full mode strips signatures from non-kept items).
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# With R8 full mode generic signatures are stripped for classes that are not
# kept. Suspend functions are wrapped in continuations where the type argument
# is used.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

########################################
# --- Gson / Moshi ---
########################################
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Keep generic signatures for Gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keep class com.google.gson.stream.** { *; }

# For Gson serialization
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

########################################
# --- Firebase / Firestore ---
########################################
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

########################################
# --- Kotlin Coroutines ---
########################################
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Most of volatile fields are updated with AFU and should not be mangled
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

########################################
# --- Additional Kotlin rules ---
########################################
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn kotlin.reflect.**

########################################
# --- Reflection (important for Retrofit) ---
########################################
-keepclassmembers class * {
    @retrofit2.http.* <methods>;
}

# Keep Retrofit annotation classes and their methods
-keep class retrofit2.http.** { *; }
-keep @interface retrofit2.http.**

########################################
# Optional: hide source filenames in logs
# -renamesourcefileattribute SourceFile
########################################