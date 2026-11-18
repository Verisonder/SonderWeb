# GeckoView talks to its native engine through reflection and JNI.
-keep class org.mozilla.** { *; }
-dontwarn org.mozilla.**

# Firefox Sync: Mozilla's account and sync libraries call into their native code through JNA,
# which finds classes and fields by name.
-keep class mozilla.appservices.** { *; }
-keep class org.mozilla.appservices.** { *; }
-keep class mozilla.telemetry.glean.** { *; }
-dontwarn java.awt.**
