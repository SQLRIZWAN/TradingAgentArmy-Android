# kotlinx-serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.rizwan.tradingagentarmy.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.rizwan.tradingagentarmy.**$$serializer { *; }

# Retrofit
-keepattributes Signature, Exceptions
-keepclasseswithmembers class * { @retrofit2.http.* <methods>; }
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class com.squareup.okhttp3.** { *; }
-keep interface com.squareup.okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# OkHttp
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Play Core (unused split refs)
-dontwarn com.google.android.play.core.**

# MediaPipe tasks-genai: protobuf annotations are compile-time only (runtime = protobuf-javalite via Firebase)
-dontwarn com.google.protobuf.**

# ---- Hilt @LazyClassKey / R8 class merging -------------------------------------
# DaggerApp_HiltComponents_SingletonC.getViewModelKeys() builds an
# ImmutableMap<String, Boolean> from @IdentifierNameString constants that hold the
# @HiltViewModel class names. R8 merged com...ui.bots.BotsViewModel into another
# class, so its constant was rewritten to that class's obfuscated name and two
# entries shared one key — the app died on the first hiltViewModel() call with:
#   java.lang.IllegalArgumentException: Multiple entries with same key: X=true and X=true
# Keeping every ViewModel class distinct keeps those constants distinct.
# https://github.com/google/dagger/issues/4323
-keep,allowobfuscation class * extends androidx.lifecycle.ViewModel { *; }
