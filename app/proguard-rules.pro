# ProGuard/R8 optimization rules for AegisAgent

# Keep Kotlin Serialization structures
-keepclassmembers class * {
    *** Companion;
}
-keep class kotlinx.serialization.json.Json { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# Keep OkHttp components
-keepattributes Signature, InnerClasses, AnnotationDefault
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**

# Keep coroutines debug properties
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**
