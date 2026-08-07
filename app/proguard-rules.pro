-keepclassmembers class * { @kotlinx.serialization.Serializable <init>(...); }
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn com.google.errorprone.annotations.**
-keepclassmembers class * {
    @com.google.crypto.tink.config.TinkFipsUseOnlyFips <fields>;
}
