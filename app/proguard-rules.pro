# ScrollMeter ProGuard / R8 configuration
-keep class com.scrollmeter.app.accessibility.ScrollAccessibilityService { *; }
-keep class com.scrollmeter.app.accessibility.BootCompletedReceiver { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keepattributes *Annotation*
