package com.tommasov.mg4simplelauncher;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tommasov.mg4simplelauncher.charging.ChargingFilter;
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
    private static final String KEY_DOCK_PREFIX = "dock_";
    private static final String KEY_BATTERY_KWH = "battery_kwh";
    private static final String KEY_HOME_PAGE = "home_page";
    private static final String KEY_SHORTCUTS_ENABLED = "shortcuts_enabled";
    private static final String KEY_BETA_CHANNEL = "beta_channel";
    private static final String KEY_UPDATE_ON_LAUNCH = "update_on_launch";
    private static final String KEY_CHARGING_FILTER = "charging_card_filter";
    private static final String KEY_SIX_TILE_HOME = "six_tile_home";
    private static final String KEY_PREFS_VERSION = "prefs_version";
    /** Bumped whenever {@link #migrate()} gains a step. */
    private static final int PREFS_VERSION = 1;
    /** Favourites on the classic home: three large cards. */
    public static final int FAVORITE_COUNT = 3;
    /** Favourites on the six-tile home. The first three share their slots with the classic
     *  arrangement, so switching between the two never moves an app the driver placed. */
    public static final int FAVORITE_COUNT_SIX = 6;
    /** Four columns of two half cards, matching the 1920x720 head unit. */
    public static final int GRID_FAVORITE_COUNT = 8;

    /**
     * The two small shortcuts under "All apps", in both home arrangements.
     *
     * <p>They were the Android Files and Settings apps, fixed. That made them the only two
     * places on the home the driver could not change, and redundant besides: both are one tap
     * away in the drawer, and can sit on a favourite card or a shortcut tile like anything
     * else. They keep those two as their factory setting — a launcher set as the home screen
     * should not bury the way back to Android's settings — but nothing stops the driver from
     * putting something else there.
     */
    public static final int DOCK_COUNT = 2;
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
    /**
     * Whether the shortcuts page is in the carousel. Off unless asked for: eight tiles are
     * more than most people fill, and every app is a tap away on the all-apps button
     * regardless. Installations that predate this default keep the page — see
     * {@link #migrate()}.
     */
    public boolean isShortcutsPageEnabled() {
        return prefs.getBoolean(KEY_SHORTCUTS_ENABLED, false);
    }

    public void setShortcutsPageEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHORTCUTS_ENABLED, enabled).apply();
    }

    /**
     * Whether this install follows the beta channel. Opt-in and off by default: a beta can
     * be broken, and Android will not install an older versionCode over a newer one, so a
     * tester cannot simply step back to the stable build.
     */
    /**
     * Whether the launcher looks for a new build when it starts. On by default: this is how
     * it behaved before the setting existed, and it is how most people get the update at all.
     */
    /**
     * Which network the charging card lists, by {@link ChargingFilter} name.
     *
     * <p>Stored as the enum's name rather than its ordinal: the order of the constants is a
     * detail of the source file, and reordering them must not silently change what a car
     * already set up is showing. An unknown name falls back to the default.
     */
    @NonNull
    public ChargingFilter getChargingCardFilter() {
        String stored = prefs.getString(KEY_CHARGING_FILTER, null);
        if (stored != null) {
            try {
                return ChargingFilter.valueOf(stored);
            } catch (IllegalArgumentException ignored) {
                // Written by an older or newer build; fall through to the default.
            }
        }
        return ChargingFilter.MOTORWAY;
    }

    public void setChargingCardFilter(@NonNull ChargingFilter filter) {
        prefs.edit().putString(KEY_CHARGING_FILTER, filter.name()).apply();
    }

    /** Whether the home shows six half tiles instead of three large cards. */
    /** The app in one of the two small slots, or null when the driver has never chosen. */
    @Nullable
    public String getDockShortcut(int slot) {
        return prefs.getString(KEY_DOCK_PREFIX + slot, null);
    }

    public void setDockShortcut(int slot, @NonNull String packageName) {
        prefs.edit().putString(KEY_DOCK_PREFIX + slot, packageName).apply();
    }

    /** Puts a slot back to its factory app. */
    public void clearDockShortcut(int slot) {
        prefs.edit().remove(KEY_DOCK_PREFIX + slot).apply();
    }

    /**
     * The batteries this generation of MG4 was sold with, by the name on the spec sheet.
     *
     * <p>These are the gross figures the car is sold under, which is what an owner recognises
     * — a driver looking for their own pack finds "64 kWh", not "61.7". Some later Standard
     * Range builds are quoted at 49 kWh: that is the same pack measured as usable rather than
     * gross, so it belongs to the 51 entry and does not need one of its own.
     */
    public static final int[] BATTERY_SIZES = {51, 64, 77};

    /**
     * What is actually in there, in kWh, for each of the above.
     *
     * <p>A pack never gives up all of its nameplate: the BMS keeps a buffer at both ends. On
     * these three it is 50.8, 61.7 and 74.4, so using the gross figure would inflate the
     * available energy by three to four percent — and inflate the arrival estimate with it,
     * in the optimistic direction that the whole feature exists to avoid.
     */
    private static final double[] BATTERY_USABLE_KWH = {50.8, 61.7, 74.4};

    /**
     * Usable battery capacity, needed to turn "kWh per 100 km" into kilometres. The car does
     * not report it, so it is asked once; the larger pack is the default because it is the
     * one most of these cars carry.
     */
    public int getBatteryCapacityKwh() {
        return prefs.getInt(KEY_BATTERY_KWH, 64);
    }

    public void setBatteryCapacityKwh(int kwh) {
        prefs.edit().putInt(KEY_BATTERY_KWH, kwh).apply();
    }

    /**
     * Usable energy of the chosen pack, which is what every calculation wants. Falls back to
     * the nameplate figure for a value this build does not know, which is still closer than
     * refusing to answer.
     */
    public double getUsableBatteryKwh() {
        int chosen = getBatteryCapacityKwh();
        for (int i = 0; i < BATTERY_SIZES.length; i++) {
            if (BATTERY_SIZES[i] == chosen) {
                return BATTERY_USABLE_KWH[i];
            }
        }
        return chosen;
    }

    public boolean isSixTileHomeEnabled() {
        return prefs.getBoolean(KEY_SIX_TILE_HOME, false);
    }

    public void setSixTileHomeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SIX_TILE_HOME, enabled).apply();
    }

    public boolean isUpdateCheckOnLaunchEnabled() {
        return prefs.getBoolean(KEY_UPDATE_ON_LAUNCH, true);
    }

    public void setUpdateCheckOnLaunchEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_UPDATE_ON_LAUNCH, enabled).apply();
    }

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
    /**
     * Brings an existing installation up to the current expectations, once.
     *
     * <p>Today it has one job: the shortcuts page used to be on for everybody and is now off
     * unless asked for. A stored preference only exists for people who opened settings and
     * touched the switch, so simply flipping the default would take the page away from
     * everyone else — including those using it happily, who never had a reason to visit
     * settings at all. So the old value is written down for them before the default changes.
     *
     * <p>"Them" is narrowed to installations with at least one shortcut actually assigned.
     * A page nobody ever put an app on is a page nobody loses anything by closing, and
     * leaving it open would mean those cars carry an empty page for ever.
     *
     * <p>Must run before anything reads the preferences — the carousel is built from them.
     */
    public void migrate() {
        if (prefs.getInt(KEY_PREFS_VERSION, 0) >= PREFS_VERSION) {
            return;
        }
        // Empty on a fresh install, and only then: any key at all means someone was here
        // before this build. Read before writing the version marker, which would fill it.
        boolean existingInstall = !prefs.getAll().isEmpty();
        SharedPreferences.Editor editor = prefs.edit();
        if (existingInstall
                && !prefs.contains(KEY_SHORTCUTS_ENABLED)
                && hasAnyGridFavorite()) {
            editor.putBoolean(KEY_SHORTCUTS_ENABLED, true);
        }
        editor.putInt(KEY_PREFS_VERSION, PREFS_VERSION).apply();
    }

    /** True when any tile of the shortcuts grid holds an app. */
    private boolean hasAnyGridFavorite() {
        for (int slot = 0; slot < GRID_FAVORITE_COUNT; slot++) {
            if (prefs.getString(KEY_GRID_FAVORITE_PREFIX + slot, null) != null) {
                return true;
            }
        }
        return false;
    }

    public void pruneGridFavorites() {
        SharedPreferences.Editor editor = prefs.edit();
        for (int slot = GRID_FAVORITE_COUNT; slot < LEGACY_GRID_FAVORITE_COUNT; slot++) {
            editor.remove(KEY_GRID_FAVORITE_PREFIX + slot);
        }
        editor.apply();
    }
}
