# Gson relies on generic type information stored in class files when working with fields.
# ProGuard removes this information by default, so we need to retain it.
-keepattributes Signature

# according to https://stackoverflow.com/a/76224937
# This is also required for R8 in compatibility mode, as several optimizations
# (such as class merging and argument removal) may remove the generic signature.
# For more information, see:
# https://r8.googlesource.com/r8/+/refs/heads/main/compatibility-faq.md#troubleshooting-gson-gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Retain GSON @Expose annotation attributes
-keepattributes AnnotationDefault,RuntimeVisibleAnnotations
-keep class com.google.gson.reflect.TypeToken { <fields>; }
-keepclassmembers class **$TypeAdapterFactory { <fields>; }

# Keep Frontegg classes
-keep class com.frontegg.android.utils.JWT { *; }
-keep class com.frontegg.android.models.** { *; }

# Retain Tink classes used for shared preferences encryption
-keep class com.google.crypto.tink.** { *; }

-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}

-keep public class android.net.http.SslError
-keep public class android.webkit.WebViewClient
