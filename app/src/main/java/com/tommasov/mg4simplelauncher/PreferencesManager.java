package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Persists the chosen favorite app packages: the three home cards (page 1) and the
 * shortcut grid (page 2), kept in separate key namespaces so they never overwrite
 * each other.
 */
public class PreferencesManager {
    private static final String PREFS_NAME = "mg4_system_launcher";
    private static final String KEY_FAVORITE_PREFIX = "favorite_";
    private static final String KEY_GRID_FAVORITE_PREFIX = "grid_favorite_";
    public static final int FAVORITE_COUNT = 3;
    /** Two rows of six tiles, matching the 1920x720 head unit. */
    public static final int GRID_FAVORITE_COUNT = 12;

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

    /** Returns the package saved for the given grid slot (0..11), or null if empty. */
    public String getGridFavorite(int slot) {
        return prefs.getString(KEY_GRID_FAVORITE_PREFIX + slot, null);
    }

    public void setGridFavorite(int slot, String packageName) {
        prefs.edit().putString(KEY_GRID_FAVORITE_PREFIX + slot, packageName).apply();
    }

    public void clearGridFavorite(int slot) {
        prefs.edit().remove(KEY_GRID_FAVORITE_PREFIX + slot).apply();
    }
}
