# ── Retrofit ──────────────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
# Signature + InnerClasses are required for Retrofit to read Call<List<X>>
-keepattributes Signature, InnerClasses, Exceptions, EnclosingMethod
-keepattributes *Annotation*

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# ── Gson ──────────────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
# Keep all model/DTO classes used for JSON serialisation
-keep class com.example.trustlock.models.** { *; }
# Keep every Retrofit interface and its nested request/response classes
-keep interface com.example.trustlock.data.** { *; }
-keep class com.example.trustlock.data.SupabaseAuthApi$** { *; }
-keep class com.example.trustlock.data.SupabaseDbApi$** { *; }
-keep class com.example.trustlock.data.SupabaseEdgeApi$** { *; }
# Keep Room entities and locally-cached data classes (Gson + Room read these)
-keep class com.example.trustlock.data.local.** { *; }

# ── ViewModels (instantiated via reflection by ViewModelProvider) ──────────────
-keep class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}
-keep class * extends androidx.lifecycle.AndroidViewModel {
    <init>(...);
}
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.**

# ── Glide ─────────────────────────────────────────────────────────────────────
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ── AndroidX / Navigation ─────────────────────────────────────────────────────
-keepnames class androidx.navigation.fragment.NavHostFragment
-keep class * extends androidx.fragment.app.Fragment {}

# ── Debug stack traces ────────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
