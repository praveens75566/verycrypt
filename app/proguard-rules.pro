# R8 Optimization and Inlining Rules
-allowaccessmodification

# Room Database Preservation
-keep class androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.paging.**

# Keep ViewModels for ViewModelProvider instantiation
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Android Framework & Biometric Preservation
-keep class com.example.MainActivity { *; }
-keep class androidx.biometric.** { *; }

# Cryptographic engine preservation for hardware security providers
-keep class com.example.data.** { *; }
-keep class com.example.security.** { *; }


# Keep Compose Runtime and Coroutines attributes
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature



