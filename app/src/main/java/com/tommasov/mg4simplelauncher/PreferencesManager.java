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
    private static final String KEY_HOME_PAGE = "home_page";
    private static final String KEY_SHORTCUTS_ENABLED = "shortcuts_enabled";
    private static final String KEY_BETA_CHANNEL = "beta_channel";
    public static final int FAVORITE_COUNT = 3;
    /** Four columns of two half cards, matching the 1920x720 head unit. */
    public static final int GRID_FAVORITE_COUNT = 8;
    /** Tile count shipped in 1.5, before the grid was resized to half cards. */
    private static final int LEGACY_GRID_FAVORITE_COUNT = 12;

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

    /** Returns the package saved for the given grid slot (0..7), or null if empty. */
    public String getGridFavorite(int slot) {
        return prefs.getString(KEY_GRID_FAVORITE_PREFIX + slot, null);
    }

    public void setGridFavorite(int slot, String packageName) {
        prefs.edit().putString(KEY_GRID_FAVORITE_PREFIX + slot, packageName).apply();
    }

    public void clearGridFavorite(int slot) {
        prefs.edit().remove(KEY_GRID_FAVORITE_PREFIX + slot).apply();
    }

    /**
     * Which carousel page opens on launch, as one of the {@code HomePagerAdapter.PAGE_*}
     * kinds. Stored by kind rather than by position, so the value survives the shortcuts
     * page being switched off and back on.
     */
    public int getHomePage() {
        return prefs.getInt(KEY_HOME_PAGE, HomePagerAdapter.PAGE_HOME);
    }

    public void setHomePage(int pageKind) {
        prefs.edit().putInt(KEY_HOME_PAGE, pageKind).apply();
    }

    /** Whether the shortcuts page appears in the carousel at all. */
    public boolean isShortcutsPageEnabled() {
        return prefs.getBoolean(KEY_SHORTCUTS_ENABLED, true);
    }

    public void setShortcutsPageEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHORTCUTS_ENABLED, enabled).apply();
    }

    /**
     * Whether this install follows the beta channel. Opt-in and off by default: a beta can
     * be broken, and Android will not install an older versionCode over a newer one, so a
     * tester cannot simply step back to the stable build.
     */
    public boolean isBetaChannelEnabled() {
        return prefs.getBoolean(KEY_BETA_CHANNEL, false);
    }

    public void setBetaChannelEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BETA_CHANNEL, enabled).apply();
    }

    /**
     * Drops shortcut assignments past the current tile count, left behind by users who
     * filled the twelve-tile grid in 1.5. Without this they stay in storage unseen and
     * would reappear if the grid ever grew again.
     */
    public void pruneGridFavorites() {
        SharedPreferences.Editor editor = prefs.edit();
        for (int slot = GRID_FAVORITE_COUNT; slot < LEGACY_GRID_FAVORITE_COUNT; slot++) {
            editor.remove(KEY_GRID_FAVORITE_PREFIX + slot);
        }
        editor.apply();
    }
}
