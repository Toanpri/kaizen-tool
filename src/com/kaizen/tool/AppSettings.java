package com.kaizen.tool;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * M5 — app settings: theme (light/dark), language (vi/en).
 * Stored in SharedPreferences "kaizen_settings". Applied before
 * Activity.super.onCreate() via {@link #apply(Activity)}.
 */
public final class AppSettings {

    private static final String PREFS = "kaizen_settings";
    private static final String KEY_THEME = "theme"; // "light" | "dark"
    private static final String KEY_LANG = "lang";   // "vi" | "en"

    private AppSettings() {}

    public static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean isDark(Context ctx) {
        return "dark".equals(prefs(ctx).getString(KEY_THEME, "light"));
    }

    public static void setDark(Context ctx, boolean dark) {
        prefs(ctx).edit().putString(KEY_THEME, dark ? "dark" : "light").apply();
    }

    public static String getLang(Context ctx) {
        return prefs(ctx).getString(KEY_LANG, "vi");
    }

    public static void setLang(Context ctx, String lang) {
        prefs(ctx).edit().putString(KEY_LANG, lang).apply();
    }

    /**
     * Force light/dark (via values-night) and locale before the activity
     * is created. Call this before super.onCreate().
     */
    public static void apply(Activity a) {
        boolean dark = isDark(a);
        String lang = getLang(a);
        Configuration base = a.getResources().getConfiguration();
        int wantNight = dark ? Configuration.UI_MODE_NIGHT_YES
                             : Configuration.UI_MODE_NIGHT_NO;
        boolean needNight = (base.uiMode & Configuration.UI_MODE_NIGHT_MASK) != wantNight;
        Locale wantLocale = new Locale(lang);
        boolean needLocale = !wantLocale.getLanguage()
                .equals(base.locale != null ? base.locale.getLanguage() : "");
        if (!needNight && !needLocale) return;
        Configuration c = new Configuration(base);
        if (needNight) {
            c.uiMode = (c.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | wantNight;
        }
        if (needLocale) {
            Locale.setDefault(wantLocale);
            c.setLocale(wantLocale);
        }
        //noinspection deprecation
        a.getResources().updateConfiguration(
                c, a.getResources().getDisplayMetrics());
    }
}
