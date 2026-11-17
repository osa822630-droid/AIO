package coreUtils.library.process;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;

import java.util.Locale;

import coreUtils.base.BaseActivity;

/**
 * Manages application-wide Arabic/English/System locale persistence and RTL/LTR layout direction.
 */
public final class LocaleHelper {

    private static final String PREF_NAME = "language_manager_pref";
    private static final String KEY_LANGUAGE = "selected_language";

    private LocaleHelper() {}

    public static void changeLanguage(@NonNull String languageCode,
                                      @NonNull BaseActivity<?> baseActivity) {
        LocaleHelper.setLanguage(baseActivity, languageCode);
        baseActivity.recreate();
    }

    public static void toggleArabicEnglish(@NonNull BaseActivity<?> baseActivity) {
        String current = getEffectiveLanguage(baseActivity);
        String next = "ar".equalsIgnoreCase(current) ? "en" : "ar";
        changeLanguage(next, baseActivity);
    }

    @NonNull
    public static String getEffectiveLanguage(@NonNull Context context) {
        String saved = getSavedLanguage(context);
        if (!saved.isEmpty()) return saved;
        String sysLang = Locale.getDefault().getLanguage();
        return (sysLang != null && !sysLang.isEmpty()) ? sysLang : "en";
    }

    public static void setLanguage(@NonNull Context context,
                                   @NonNull String languageCode) {
        saveLanguage(context, languageCode);
        applyLanguage(context, languageCode);
        applyLanguage(context.getApplicationContext(), languageCode);
    }

    @NonNull
    public static Context applySavedLanguage(@NonNull Context context) {
        String language = getSavedLanguage(context);
        if (language.isEmpty()) language = Locale.getDefault().getLanguage();
        return applyLanguage(context, language);
    }

    @NonNull
    public static Context applyLanguage(@NonNull Context context,
                                        @NonNull String languageCode) {
        Locale locale = new Locale(languageCode);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration resourcesConfiguration = resources.getConfiguration();
        Configuration configuration = new Configuration(resourcesConfiguration);
        configuration.setLocale(locale);
        configuration.setLayoutDirection(locale);
        configuration.uiMode = resources.getConfiguration().uiMode;
        return context.createConfigurationContext(configuration);
    }

    @NonNull
    public static String getSavedLanguage(@NonNull Context context) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_LANGUAGE, "");
    }

    private static void saveLanguage(@NonNull Context context,
                                     @NonNull String languageCode) {
        SharedPreferences preferences =
                context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_LANGUAGE, languageCode).apply();
    }
}
