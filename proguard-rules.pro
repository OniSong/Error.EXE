# Retrofit
-keep class com.squareup.retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapter
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Kotlin
-keep class kotlin.** { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# Keep Android resources
-keepattributes *Annotation*
-keep public class com.android.internal.R { *; }
-keep public class com.faitapp.R { *; }
-keep public class com.faitapp.R$* { public static <fields>; }
