package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.tommasov.mg4simplelauncher.apps.DownloadsActivity;
import com.tommasov.mg4simplelauncher.charging.ChargingMapActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What a tile can hold besides an app.
 *
 * <p>A launcher that can only start apps wastes most of what Android can be asked to do. Two
 * families are worth a tile here. Android's own settings screens, because the three that
 * matter in a car — Wi-Fi, Bluetooth, mobile data — are four taps deep inside Settings and
 * are opened while something is already going wrong. And the launcher's own screens, which
 * until now could only be reached from the one place each was wired to.
 *
 * <p>Deliberately not included: the app shortcuts Android apps publish for a long-press menu.
 * Reading those needs {@code LauncherApps}, which the system grants only to the default
 * launcher — and this one is deliberately not it, so the driver's head unit keeps looking
 * standard. That is a refusal by the system, not something to work around.
 *
 * <p>Stored as a string in the same preference an app package used to occupy, distinguished
 * by a prefix. A stored value with no prefix is a package name, which is what every existing
 * installation has: nothing to migrate.
 */
public final class LaunchTargets {

    /** An Android settings screen: the rest of the id is the {@code android.settings} action. */
    private static final String SYSTEM_PREFIX = "sys:";
    /** One of this launcher's own screens. */
    private static final String OWN_PREFIX = "own:";

    /** One thing a tile can be pointed at. */
    public static final class Target {
        public final String id;
        @StringRes
        public final int labelRes;
        @DrawableRes
        public final int iconRes;

        Target(String id, @StringRes int labelRes, @DrawableRes int iconRes) {
            this.id = id;
            this.labelRes = labelRes;
            this.iconRes = iconRes;
        }

        @NonNull
        public String label(@NonNull Context context) {
            return context.getString(labelRes);
        }

        @Nullable
        public Drawable icon(@NonNull Context context) {
            return ContextCompat.getDrawable(context, iconRes);
        }
    }

    /**
     * Android's settings screens. Only the ones a driver opens on the move: the list stops
     * where "useful in the car" stops, because a tile chooser with forty entries is a worse
     * answer than the drawer it replaces.
     */
    private static final Target[] SYSTEM = {
            new Target(SYSTEM_PREFIX + Settings.ACTION_WIFI_SETTINGS,
                    R.string.target_wifi, R.drawable.ic_target_wifi),
            new Target(SYSTEM_PREFIX + Settings.ACTION_BLUETOOTH_SETTINGS,
                    R.string.target_bluetooth, R.drawable.ic_target_bluetooth),
            new Target(SYSTEM_PREFIX + Settings.ACTION_DATA_ROAMING_SETTINGS,
                    R.string.target_mobile_data, R.drawable.ic_target_cellular),
            new Target(SYSTEM_PREFIX + Settings.ACTION_DISPLAY_SETTINGS,
                    R.string.target_display, R.drawable.ic_target_display),
            new Target(SYSTEM_PREFIX + Settings.ACTION_APPLICATION_SETTINGS,
                    R.string.target_apps, R.drawable.ic_target_apps),
    };

    /** This launcher's screens, each of which had exactly one way in until now. */
    private static final Target[] OWN = {
            new Target(OWN_PREFIX + "charging",
                    R.string.charging_title, R.drawable.ic_target_charge),
            new Target(OWN_PREFIX + "downloads",
                    R.string.downloads_title, R.drawable.ic_download),
            new Target(OWN_PREFIX + "settings",
                    R.string.settings_title, R.drawable.ic_gear),
            new Target(OWN_PREFIX + "technical",
                    R.string.sys_technical, R.drawable.ic_target_info),
    };

    private LaunchTargets() {
    }

    /** Everything that is not an app, in the order the chooser should show it. */
    @NonNull
    public static List<Target> all() {
        List<Target> targets = new ArrayList<>(SYSTEM.length + OWN.length);
        targets.addAll(Arrays.asList(OWN));
        targets.addAll(Arrays.asList(SYSTEM));
        return targets;
    }

    /** True when this stored value is one of these rather than a package name. */
    public static boolean isTarget(@Nullable String id) {
        return id != null && (id.startsWith(SYSTEM_PREFIX) || id.startsWith(OWN_PREFIX));
    }

    /** The target for a stored id, or null when it names one this build does not know. */
    @Nullable
    public static Target find(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (Target target : all()) {
            if (target.id.equals(id)) {
                return target;
            }
        }
        return null;
    }

    /**
     * The icon for whatever a tile holds, app or screen; null when it can no longer be
     * resolved — an app since uninstalled, or an id this build does not know.
     */
    @Nullable
    public static Drawable iconFor(@NonNull Context context, @NonNull String id) {
        Target target = find(id);
        if (target != null) {
            return target.icon(context);
        }
        return isTarget(id) ? null : AppIcons.highRes(context, id);
    }

    /** The label for whatever a tile holds, or null when it cannot be resolved. */
    @Nullable
    public static CharSequence labelFor(@NonNull Context context, @NonNull String id) {
        Target target = find(id);
        if (target != null) {
            return target.label(context);
        }
        if (isTarget(id)) {
            return null;
        }
        try {
            PackageManager pm = context.getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(id, 0));
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Starts what {@code id} points at. Returns false when it cannot be started — an unknown
     * id from a newer build, or a settings screen this firmware does not carry, which is a
     * real possibility on a vendor Android.
     */
    public static boolean launch(@NonNull Context context, @NonNull String id) {
        Intent intent = intentFor(context, id);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        context.startActivity(intent);
        return true;
    }

    @Nullable
    private static Intent intentFor(@NonNull Context context, @NonNull String id) {
        if (id.startsWith(SYSTEM_PREFIX)) {
            return new Intent(id.substring(SYSTEM_PREFIX.length()));
        }
        if (!id.startsWith(OWN_PREFIX)) {
            return null;
        }
        switch (id.substring(OWN_PREFIX.length())) {
            case "charging":
                return new Intent(context, ChargingMapActivity.class);
            case "downloads":
                return new Intent(context, DownloadsActivity.class);
            case "settings":
                return new Intent(context, SettingsActivity.class);
            case "technical":
                return new Intent(context, TechnicalDetailsActivity.class);
            default:
                return null;
        }
    }
}
