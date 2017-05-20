package com.gianlu.aria2app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public class Prefs {
    public static final String A2_FORCE_ACTION = "a2_forceAction";
    public static final String A2_SHOW_SUMMARY = "a2_summaryCard";
    private static SharedPreferences prefs;

    public static boolean getBoolean(Context context, Keys key, boolean fallback) {
        init(context);
        return prefs.getBoolean(key.key, fallback);
    }

    public static String getString(Context context, Keys key, String fallback) {
        init(context);
        return prefs.getString(key.key, fallback);
    }

    public static void editString(Context context, Keys key, String value) {
        init(context);
        prefs.edit().putString(key.key, value).apply();
    }

    private static void init(Context context) {
        if (prefs != null) return;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static int getInt(Context context, Keys key, int fallback) {
        init(context);
        return prefs.getInt(key.key, fallback);
    }

    public static void removeFromSet(Context context, Keys key, String value) {
        Set<String> set = new HashSet<>(getSet(context, key, new HashSet<String>()));
        set.remove(value);
        prefs.edit().putStringSet(key.key, set).apply();
    }

    public static void addToSet(Context context, Keys key, String value) {
        Set<String> set = new HashSet<>(getSet(context, key, new HashSet<String>()));
        if (!set.contains(value)) set.add(value);
        prefs.edit().putStringSet(key.key, set).apply();
    }

    public static Set<String> getSet(Context context, Keys key, Set<String> fallback) {
        init(context);
        return prefs.getStringSet(key.key, fallback);
    }

    public enum Keys {
        DD_DOWNLOAD_PATH("dd_downloadPath"),
        A2_ENABLE_NOTIFS("a2_enableNotifications"),
        A2_UPDATE_INTERVAL("a2_updateInterval"),
        A2_HIDE_METADATA("a2_hideMetadata"),
        A2_GLOBAL_QUICK_OPTIONS("a2_globalQuickOptions"),
        A2_QUICK_OPTIONS("a2_quickOptions");
        public final String key;

        Keys(String key) {
            this.key = key;
        }
    }
}
