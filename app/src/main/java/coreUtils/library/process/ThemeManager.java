package coreUtils.library.process;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;

import coreUtils.base.BaseActivity;

/**
 * Manages Day (Light) and Night (Dark) theme persistence and runtime toggling.
 */
public final class ThemeManager {

    private static final String PREF_NAME = "theme_manager_pref";
    private static final String KEY_NIGHT_MODE = "night_mode";

    private ThemeManager() {}

    public static void applySavedTheme(@NonNull Context context) {
        int savedMode = getSavedNightMode(context);
        AppCompatDelegate.setDefaultNightMode(savedMode);
    }

    public static int getSavedNightMode(@NonNull Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(KEY_NIGHT_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    public static boolean isDarkModeActive(@NonNull Context context) {
        int mode = getSavedNightMode(context);
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false;
        int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return uiMode == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void setNightMode(@NonNull Context context, int mode) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .putInt(KEY_NIGHT_MODE, mode)
                .apply();
        AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static void toggleTheme(@NonNull BaseActivity<?> activity) {
        boolean currentlyDark = isDarkModeActive(activity);
        int nextMode = currentlyDark ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
        setNightMode(activity, nextMode);
        activity.recreate();
    }
}
