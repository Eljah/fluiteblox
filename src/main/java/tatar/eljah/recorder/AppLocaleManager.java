package tatar.eljah.recorder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import java.util.Locale;

public final class AppLocaleManager {
    private static final String PREFS = "app_locale";
    private static final String KEY_LANG = "lang";

    private AppLocaleManager() {
    }

    public static void applySavedLocale(Context context) {
        String lang = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "");
        if (lang == null || lang.length() == 0) {
            return;
        }
        applyLocale(context, lang);
    }

    public static void saveAndApply(Context context, String lang) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANG, lang).commit();
        applyLocale(context, lang);
    }

    public static String savedLanguage(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANG, "");
    }

    private static void applyLocale(Context context, String lang) {
        Locale locale = new Locale(lang);
        Locale.setDefault(locale);
        Resources resources = context.getResources();
        Configuration config = new Configuration(resources.getConfiguration());
        if (Build.VERSION.SDK_INT >= 17) {
            config.setLocale(locale);
            config.setLayoutDirection(locale);
            context.createConfigurationContext(config);
        }
        resources.updateConfiguration(config, resources.getDisplayMetrics());
    }
}
