package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.SharedPreferences;

/** Persists the three chosen favorite app packages (one per home card). */
public class PreferencesManager {
    private static final String PREFS_NAME = "mg4_system_launcher";
    private static final String KEY_FAVORITE_PREFIX = "favorite_";
    public static final int FAVORITE_COUNT = 3;

    private final SharedPreferences prefs;

    public PreferencesManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the package saved for the given slot (0..2), or null if empty. */
    public String getFavorite(int slot) {
        return prefs.getString(KEY_FAVORITE_PREFIX + slot, null);
    }

    public void setFavorite(int slot, String packageName) {
        prefs.edit().putString(KEY_FAVORITE_PREFIX + slot, packageName).apply();
    }

    public void clearFavorite(int slot) {
        prefs.edit().remove(KEY_FAVORITE_PREFIX + slot).apply();
    }
}