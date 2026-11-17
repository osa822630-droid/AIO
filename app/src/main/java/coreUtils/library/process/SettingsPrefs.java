package coreUtils.library.process;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Environment;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;

import coreUtils.base.BaseApplication;
import dataRepo.appConfigs.AppConfigs;
import dataRepo.appConfigs.AppConfigsRepo;

/**
 * Centralized preferences & storage-path resolver for TubeAIO Speed Settings.
 * Complements ObjectBox AppConfigs without modifying database schema.
 */
public final class SettingsPrefs {

    private static final String PREF_NAME = "tubeaio_speed_settings";
    private static final String KEY_DEFAULT_SPEED = "default_speed";
    private static final String KEY_DEFAULT_QUALITY = "default_quality";
    private static final String KEY_DEFAULT_TURBO = "default_turbo_mode";
    private static final String KEY_DEFAULT_VOICE_BOOST = "default_voice_boost";
    private static final String KEY_DEFAULT_SYNC_SUBS = "default_sync_subs";
    private static final String KEY_SMART_SUBFOLDERS = "smart_subfolders";

    private SettingsPrefs() {}

    private static SharedPreferences prefs(@NonNull Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static float getDefaultSpeed() {
        return prefs(BaseApplication.getInstance()).getFloat(KEY_DEFAULT_SPEED, 1.5f);
    }

    public static void setDefaultSpeed(float speed) {
        float clamped = Math.round(Math.max(0.25f, Math.min(4.0f, speed)) * 100f) / 100f;
        prefs(BaseApplication.getInstance()).edit().putFloat(KEY_DEFAULT_SPEED, clamped).apply();
    }

    @NonNull
    public static String getDefaultQuality() {
        return prefs(BaseApplication.getInstance()).getString(KEY_DEFAULT_QUALITY, "720p");
    }

    public static void setDefaultQuality(@NonNull String quality) {
        prefs(BaseApplication.getInstance()).edit().putString(KEY_DEFAULT_QUALITY, quality).apply();
    }

    public static boolean isDefaultTurboMode() {
        return prefs(BaseApplication.getInstance()).getBoolean(KEY_DEFAULT_TURBO, true);
    }

    public static void setDefaultTurboMode(boolean turbo) {
        prefs(BaseApplication.getInstance()).edit().putBoolean(KEY_DEFAULT_TURBO, turbo).apply();
    }

    public static boolean isDefaultVoiceBoost() {
        return prefs(BaseApplication.getInstance()).getBoolean(KEY_DEFAULT_VOICE_BOOST, false);
    }

    public static void setDefaultVoiceBoost(boolean enabled) {
        prefs(BaseApplication.getInstance()).edit().putBoolean(KEY_DEFAULT_VOICE_BOOST, enabled).apply();
    }

    public static boolean isDefaultSyncSubtitles() {
        return prefs(BaseApplication.getInstance()).getBoolean(KEY_DEFAULT_SYNC_SUBS, false);
    }

    public static void setDefaultSyncSubtitles(boolean enabled) {
        prefs(BaseApplication.getInstance()).edit().putBoolean(KEY_DEFAULT_SYNC_SUBS, enabled).apply();
    }

    public static boolean isSmartSubfoldersEnabled() {
        return prefs(BaseApplication.getInstance()).getBoolean(KEY_SMART_SUBFOLDERS, true);
    }

    public static void setSmartSubfoldersEnabled(boolean enabled) {
        prefs(BaseApplication.getInstance()).edit().putBoolean(KEY_SMART_SUBFOLDERS, enabled).apply();
    }

    public static int getMaxConcurrentDownloads() {
        AppConfigs cfg = AppConfigsRepo.getConfig();
        if (cfg == null) return 2;
        int limit = cfg.downloadConcurrencyLimit;
        if (limit < 1) return 2;
        return Math.min(4, limit);
    }

    public static void setMaxConcurrentDownloads(int limit) {
        AppConfigs cfg = AppConfigsRepo.getConfig();
        if (cfg != null) {
            cfg.downloadConcurrencyLimit = Math.max(1, Math.min(4, limit));
            cfg.save();
        }
    }

    @NonNull
    public static File getPublicDefaultFolder() {
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return new File(dl, "TubeAIO Speed");
    }

    @NonNull
    public static File getPrivateAppFolder(@NonNull Context ctx) {
        File ext = ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (ext == null) ext = ctx.getFilesDir();
        return new File(ext, "TubeAIO Speed");
    }

    /**
     * Resolves the currently configured base download directory (custom, public Downloads, or private app folder).
     */
    @NonNull
    public static File resolveBaseDownloadDirectory(@NonNull Context ctx) {
        AppConfigs cfg = AppConfigsRepo.getConfig();
        if (cfg != null) {
            if (cfg.selectedDownloadDirectoryPath != null && !cfg.selectedDownloadDirectoryPath.trim().isEmpty()) {
                File custom = new File(cfg.selectedDownloadDirectoryPath.trim());
                if (custom.exists() || custom.mkdirs()) {
                    return custom;
                }
            }
            if (cfg.defaultDownloadLocationType == AppConfigs.PRIVATE_FOLDER) {
                File priv = getPrivateAppFolder(ctx);
                if (!priv.exists()) priv.mkdirs();
                return priv;
            }
        }
        File pub = getPublicDefaultFolder();
        if (!pub.exists()) {
            if (!pub.mkdirs()) {
                File priv = getPrivateAppFolder(ctx);
                if (!priv.exists()) priv.mkdirs();
                return priv;
            }
        }
        return pub;
    }

    /**
     * Best-effort conversion of an Android SAF tree URI (e.g. primary:Download/MyFolder) to a filesystem path.
     */
    @Nullable
    public static String resolveTreeUriToFilePath(@NonNull Uri treeUri) {
        try {
            String docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri);
            if (docId == null) return null;
            String[] split = docId.split(":", 2);
            if (split.length == 0) return null;
            String type = split[0];
            String rel = split.length > 1 ? split[1] : "";
            if ("primary".equalsIgnoreCase(type)) {
                File base = Environment.getExternalStorageDirectory();
                return rel.isEmpty() ? base.getAbsolutePath() : new File(base, rel).getAbsolutePath();
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static boolean isWifiConnected(@NonNull Context ctx) {
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network active = cm.getActiveNetwork();
            if (active == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(active);
            return caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
        } catch (Exception e) {
            return true;
        }
    }

    public static long calculateTempCacheBytes(@NonNull Context ctx) {
        long total = dirSize(ctx.getCacheDir());
        File base = resolveBaseDownloadDirectory(ctx);
        total += tempFilesSize(base);
        return total;
    }

    public static void clearTempCache(@NonNull Context ctx) {
        deleteChildren(ctx.getCacheDir());
        deleteTempDownloadFiles(resolveBaseDownloadDirectory(ctx));
    }

    private static long dirSize(@Nullable File dir) {
        if (dir == null || !dir.exists()) return 0L;
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        long sum = 0L;
        for (File f : files) {
            sum += f.isDirectory() ? dirSize(f) : f.length();
        }
        return sum;
    }

    private static long tempFilesSize(@Nullable File dir) {
        if (dir == null || !dir.exists()) return 0L;
        File[] files = dir.listFiles();
        if (files == null) return 0L;
        long sum = 0L;
        for (File f : files) {
            if (f.isDirectory()) sum += tempFilesSize(f);
            else if (f.getName().startsWith(".tmp_raw_")) sum += f.length();
        }
        return sum;
    }

    private static void deleteChildren(@Nullable File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) deleteChildren(f);
            f.delete();
        }
    }

    private static void deleteTempDownloadFiles(@Nullable File dir) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) deleteTempDownloadFiles(f);
            else if (f.getName().startsWith(".tmp_raw_")) f.delete();
        }
    }
}
