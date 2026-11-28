# GeckoView talks to its native engine through reflection and JNI.
-keep class org.mozilla.** { *; }
-dontwarn org.mozilla.**

# Firefox Sync: Mozilla's account and sync libraries call into their native code through JNA,
# which finds classes and fields by name.
-keep class mozilla.appservices.** { *; }
-keep class org.mozilla.appservices.** { *; }
-keep class mozilla.telemetry.glean.** { *; }
-dontwarn java.awt.**

# Google's text reader (ML Kit, circle to search): the shrinker's optimizations break it (a field
# of its own read from the wrong place). Kept as it ships.
-dontwarn com.google.android.gms.**
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keep class com.google.android.odml.** { *; }
