# Add project specific ProGuard rules here.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Kotlin metadata for Compose, Room, DataStore, and Glance reflection/codegen
-keep class kotlin.Metadata { *; }

# Keep Room entity & database subclasses even if referenced reflectively
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <methods>;
}
