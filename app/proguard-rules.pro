# RUK AI \u2014 R8 / ProGuard rules

# ---------------------------------------------------------------- Compose
# Compose ships its own consumer rules; keep runtime-clocked classes reflective
# entry points that R8 cannot see.
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# ------------------------------------------------- kotlinx.serialization
# Generated serializers are looked up by name; without these rules every DTO
# deserializes to nulls in a release build.
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ruk.ai.**$$serializer { *; }
-keepclassmembers class com.ruk.ai.** {
    *** Companion;
}
-keepclasseswithmembers class com.ruk.ai.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ------------------------------------------------------------------ Retrofit
# Retrofit builds requests from the annotations on the RukApi interface and
# resolves the Kotlin suspend Continuation reflectively.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep,allowobfuscation interface com.ruk.ai.data.remote.RukApi
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# ------------------------------------------------------------------- OkHttp
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# -------------------------------------------------- Google Play services auth
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --------------------------------------------------------- Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}
