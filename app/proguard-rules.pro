# Room Database, Entities, TypeConverters & Persisted Enums
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keepclassmembers class * {
    @androidx.room.TypeConverter <methods>;
}
-keepclassmembers enum com.proximity.app.data.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontwarn androidx.room.paging.**

# Strip verbose/debug Android logging in R8 release builds
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# SQLCipher Native & Support Bindings
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# CameraX & ZXing
-keep class androidx.camera.core.** { *; }
-keep class androidx.camera.lifecycle.** { *; }
-keep class androidx.camera.view.** { *; }
-dontwarn androidx.camera.**
-dontwarn com.google.zxing.**

# Lottie Animation & Transitive Okio Annotations
-keep class com.airbnb.lottie.** { *; }
-dontwarn javax.annotation.**

# Accessibility, Telecom & Broadcast Receivers
-keep class com.proximity.app.accessibility.UssdDialogReaderService { *; }
-keep class com.proximity.app.telephony.CallRouterService { *; }
-keep class com.proximity.app.receivers.SmsMonitorReceiver { *; }

# Parcelable Models
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Jetpack Compose - Keep snapshot classes to avoid lock verification issues
-keep class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateList { *; }
-keep class androidx.compose.runtime.snapshots.SnapshotStateMap { *; }
-keepclassmembers class androidx.compose.runtime.snapshots.SnapshotStateMap { *; }

# Jetpack Compose - Keep composition local and state classes
-keep class androidx.compose.runtime.CompositionLocal { *; }
-keepclassmembers class androidx.compose.runtime.CompositionLocal { *; }
-keep class androidx.compose.runtime.MutableState { *; }
-keepclassmembers class androidx.compose.runtime.MutableState { *; }
-keep class androidx.compose.runtime.SnapshotStateKt { *; }
