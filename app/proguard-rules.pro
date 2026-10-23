# Radx keeps shrinking disabled for the first stable release.
# Rules below are ready for when minification is enabled.

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.radx.app.**$$serializer { *; }
-keepclassmembers class com.radx.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.radx.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# SnakeYAML (Clash Meta configuration parsing)
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**

# sing-box libbox bindings are only present when the AAR is bundled.
-dontwarn io.nekohasekai.libbox.**
-keep class io.nekohasekai.libbox.** { *; }
-keep class com.radx.app.libbox.** { *; }
