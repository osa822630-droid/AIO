# ─── TubeAIO Speed — Production R8 / ProGuard Configuration ──────────────────

-keepattributes Signature,InnerClasses,EnclosingMethod,Annotation,*Annotation*

# ─── ObjectBox Database & Entities ───────────────────────────────────────────
-keep class io.objectbox.** { *; }
-keep interface io.objectbox.** { *; }
-dontwarn io.objectbox.**
-keep @io.objectbox.annotation.Entity class * { *; }
-keep class dataRepo.** { *; }

# ─── youtubedl-android (yt-dlp / FFmpeg / aria2c JNI & Jackson Mappers) ──────
-keep class com.yausername.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.yausername.**
-dontwarn com.fasterxml.jackson.**

# ─── Retrofit / OkHttp / Gson ────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn retrofit2.**
-dontwarn okhttp3.**

# ─── ExoPlayer ───────────────────────────────────────────────────────────────
-keep class com.google.android.exoplayer2.** { *; }
-keep interface com.google.android.exoplayer2.** { *; }

# ─── MediaSession (AndroidX / Support) ───────────────────────────────────────
-keep class android.support.v4.media.** { *; }
-keep class androidx.media.** { *; }
-keep class android.support.v4.media.session.** { *; }

# ─── NewPipe Extractor ───────────────────────────────────────────────────────
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**

# ─── Player Engine Models ────────────────────────────────────────────────────
-keep class sysModules.sysPlayer.queue.PlayQueue { *; }
-keep class sysModules.sysPlayer.queue.PlayQueueItem { *; }
-keep class sysModules.sysPlayer.model.** { *; }
