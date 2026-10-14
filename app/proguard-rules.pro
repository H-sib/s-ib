# Keep kotlinx.serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep,includedescriptorclasses class com.hsib.sib.**$$serializer { *; }
-keepclassmembers class com.hsib.sib.** { *** Companion; }
# SQLCipher native bindings.
-keep class net.zetetic.database.** { *; }
# Release builds drop verbose, debug and info logging from every library, so nothing can reach the log.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
