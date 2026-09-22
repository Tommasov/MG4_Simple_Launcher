package com.tommasov.mg4simplelauncher;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.tommasov.mg4simplelauncher.apps.DownloadsActivity;
import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;
import com.tommasov.mg4simplelauncher.charging.ChargingMapActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

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
    /** A specific activity, as {@code act:package/class}: the vehicle's own screens. */
    private static final String ACTIVITY_PREFIX = "act:";
    /** A page inside one of the vehicle's own apps: see {@link VehicleShortcuts}. */
    private static final String OEM_PREFIX = "oem:";

    /** The two the dock is set to out of the box, named so nobody has to spell the prefix. */
    public static final String OWN_DOWNLOADS = OWN_PREFIX + "downloads";
    public static final String OWN_SETTINGS = OWN_PREFIX + "settings";

    /**
     * Package name fragments that mark software belonging to the car rather than to Android.
     *
     * <p>A guess, and knowingly so: there is no flag that says "this belongs to the vehicle".
     * These are the vendors that ship on this head unit, matched on the package name because
     * the labels are inconsistent and often absent. A few unrelated packages slipping in
     * costs nothing — they appear in a chooser and get ignored — whereas missing the climate
     * or camera screens would cost the whole feature.
     */
    private static final String[] VEHICLE_HINTS = {
            "saic", "roewe", "aroundview", "avm", "hmi", "vehicle", "carservice",
    };

    /**
     * Screens that are never offered as a tile, whatever they are called on a given car.
     *
     * <p>These are the emergency-call screens. Opening one from here was tried on a real
     * vehicle and it is bad in two different ways.
     *
     * <p>The first is mechanical: {@code com.saicmotor.hmi.btcall.ECallActivity} is declared
     * {@code singleInstance} with its own theme, so it takes over its own task and sits there
     * — the head unit had to be restarted to get out of it.
     *
     * <p>The second is worse and is the reason this list exists rather than a bug report.
     * That activity is only the display for a call somebody else has placed: its package
     * holds no calling permission at all, just {@code READ_CALL_LOG} and {@code READ_CONTACTS}.
     * Started on its own it shows an emergency call in progress, with the avatar and the
     * running timer, while nothing whatever has been dialled. A driver who reaches that screen
     * in the seconds after a crash would believe help is on its way. No tile is worth that.
     *
     * <p>The neighbouring {@code com.saicmotor.rescuecall} is excluded for the opposite
     * reason: that one does hold {@code CALL_PHONE} and {@code BIND_INCALL_SERVICE}, and is
     * what the SOS button uses to dial a real number. It is reachable from the drawer, which
     * is the vehicle's own decision; it does not also need to be one tap away on the home
     * screen, where it can be hit by mistake.
     */
    private static final String[] OFF_LIMITS = {
            "ecall", "bcall", "rescuecall", "emergency", "sos",
    };

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
            new Target(OWN_DOWNLOADS,
                    R.string.downloads_title, R.drawable.ic_download),
            new Target(OWN_SETTINGS,
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
        return id != null && (id.startsWith(SYSTEM_PREFIX) || id.startsWith(OWN_PREFIX)
                || id.startsWith(ACTIVITY_PREFIX) || id.startsWith(OEM_PREFIX));
    }

    /** The id under which a page inside one of the vehicle's apps is stored. */
    @NonNull
    public static String shortcutId(@NonNull VehicleShortcuts.Shortcut shortcut) {
        return OEM_PREFIX + shortcut.id;
    }

    @Nullable
    private static VehicleShortcuts.Shortcut shortcutOf(@NonNull String id) {
        return id.startsWith(OEM_PREFIX)
                ? VehicleShortcuts.find(id.substring(OEM_PREFIX.length())) : null;
    }

    /** The id under which a vehicle screen is stored. */
    @NonNull
    public static String activityId(@NonNull ComponentName component) {
        return ACTIVITY_PREFIX + component.getPackageName() + "/" + component.getClassName();
    }

    @Nullable
    private static ComponentName componentOf(@NonNull String id) {
        if (!id.startsWith(ACTIVITY_PREFIX)) {
            return null;
        }
        String[] parts = id.substring(ACTIVITY_PREFIX.length()).split("/", 2);
        return parts.length == 2 ? new ComponentName(parts[0], parts[1]) : null;
    }

    /**
     * Every exported activity belonging to the car's own software.
     *
     * <p>Found by asking the package manager rather than by carrying a list: these screens
     * differ between trims and firmware versions, and a hard-coded name that is wrong on
     * someone's car is worse than no entry at all. What comes back is what this vehicle
     * actually has.
     *
     * <p>Only exported and enabled activities are listed — the rest would refuse to start
     * anyway — and the ones that already have an icon in the drawer are left out, since those
     * are apps and belong in the other list. Their siblings are not: an app with an icon can
     * still hold screens worth reaching directly.
     */
    @NonNull
    public static List<ActivityTarget> vehicleScreens(@NonNull Context context) {
        PackageManager pm = context.getPackageManager();
        // The activities that already have an icon in the drawer, named one by one.
        Set<String> inDrawer = new HashSet<>();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(launcher, 0)) {
            inDrawer.add(ri.activityInfo.packageName + "/" + ri.activityInfo.name);
        }

        List<ActivityTarget> screens = new ArrayList<>();
        for (PackageInfo info : pm.getInstalledPackages(PackageManager.GET_ACTIVITIES)) {
            if (info.activities == null || !looksLikeVehicle(info.packageName)) {
                continue;
            }
            for (ActivityInfo activity : info.activities) {
                if (!activity.exported || !activity.enabled) {
                    continue;
                }
                // Skipped one activity at a time, not one package at a time. Dropping the
                // whole package because it appears in the drawer also dropped everything
                // else inside it: on this car that hid the charging management screen of
                // com.saicmotor.hmi.vehiclesettings behind the fact that the app it belongs
                // to has an icon — and that screen is the most useful of the lot.
                if (inDrawer.contains(activity.packageName + "/" + activity.name)) {
                    continue;
                }
                if (isOffLimits(activity.packageName, activity.name)) {
                    continue;
                }
                screens.add(new ActivityTarget(
                        new ComponentName(activity.packageName, activity.name),
                        readableName(pm, activity)));
            }
        }
        Collections.sort(screens, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return screens;
    }

    /** One of the car's screens, found at runtime. */
    public static final class ActivityTarget {
        public final ComponentName component;
        public final String label;

        ActivityTarget(ComponentName component, String label) {
            this.component = component;
            this.label = label;
        }

        @NonNull
        public String id() {
            return activityId(component);
        }
    }

    /**
     * Whether this is one of the screens that must never be offered. Matched on fragments of
     * the name rather than on exact components, because the names differ between trims and
     * firmware versions and a list that is right on one car and wrong on another would be
     * worse than useless here. A screen wrongly excluded costs one entry in a chooser.
     */
    private static boolean isOffLimits(@NonNull String packageName, @NonNull String className) {
        String lower = (packageName + "/" + className).toLowerCase(Locale.ROOT);
        for (String fragment : OFF_LIMITS) {
            if (lower.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static boolean looksLikeVehicle(@NonNull String packageName) {
        String lower = packageName.toLowerCase(Locale.ROOT);
        for (String hint : VEHICLE_HINTS) {
            if (lower.contains(hint)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Something a person can read. Vendor activities usually carry no label of their own, in
     * which case the class name is all there is: "AroundViewActivity" becomes "Around View",
     * which is not a title but beats a package path in a chooser.
     */
    @NonNull
    private static String readableName(@NonNull PackageManager pm, @NonNull ActivityInfo info) {
        CharSequence label = info.loadLabel(pm);
        String appLabel = String.valueOf(info.applicationInfo.loadLabel(pm));
        if (label != null && label.length() > 0 && !label.toString().equals(appLabel)) {
            return label.toString();
        }
        String simple = info.name.substring(info.name.lastIndexOf('.') + 1);
        if (simple.endsWith("Activity")) {
            simple = simple.substring(0, simple.length() - "Activity".length());
        }
        // CamelCase into words, keeping runs of capitals together so AVM stays AVM.
        String spaced = simple.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ")
                .replaceAll("(?<=[A-Z])(?=[A-Z][a-z])", " ")
                .replace('_', ' ')
                .trim();
        return spaced.isEmpty() ? info.name : spaced;
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
        VehicleShortcuts.Shortcut shortcut = shortcutOf(id);
        if (shortcut != null) {
            // The icon of the app the page lives in: it is the one the driver associates with
            // that screen, and we have no artwork of our own for eleven vendor pages.
            return AppIcons.highRes(context, shortcut.packageName());
        }
        Target target = find(id);
        if (target != null) {
            return target.icon(context);
        }
        ComponentName component = componentOf(id);
        if (component != null) {
            PackageManager pm = context.getPackageManager();
            try {
                return pm.getActivityInfo(component, 0).loadIcon(pm);
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
        }
        return isTarget(id) ? null : AppIcons.highRes(context, id);
    }

    /** The label for whatever a tile holds, or null when it cannot be resolved. */
    @Nullable
    public static CharSequence labelFor(@NonNull Context context, @NonNull String id) {
        VehicleShortcuts.Shortcut shortcut = shortcutOf(id);
        if (shortcut != null) {
            return shortcut.label(context);
        }
        Target target = find(id);
        if (target != null) {
            return target.label(context);
        }
        ComponentName component = componentOf(id);
        if (component != null) {
            PackageManager pm = context.getPackageManager();
            try {
                return readableName(pm, pm.getActivityInfo(component, 0));
            } catch (PackageManager.NameNotFoundException e) {
                return null;
            }
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
        VehicleShortcuts.Shortcut shortcut = shortcutOf(id);
        if (shortcut != null) {
            try {
                context.startActivity(shortcut.intent());
                return true;
            } catch (SecurityException | ActivityNotFoundException e) {
                // Some of these pages are reachable and some are not, and the manifest does
                // not always say which: the music app declares itself unexported and may or
                // may not honour that. The tile reports the refusal rather than pretending.
                DiagnosticsLog.log(context, "LaunchTargets", "refused " + id + ": " + e);
                return false;
            }
        }
        ComponentName stored = componentOf(id);
        if (stored != null && isOffLimits(stored.getPackageName(), stored.getClassName())) {
            // Checked again here and not only where the list is built: a tile assigned before
            // this build existed still holds its id, and the emergency screens are exactly
            // the ones that must not open because somebody picked one last week.
            DiagnosticsLog.log(context, "LaunchTargets", "refused off-limits " + id);
            return false;
        }
        Intent intent = intentFor(context, id);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (intent.resolveActivity(context.getPackageManager()) == null) {
            return false;
        }
        try {
            context.startActivity(intent);
            return true;
        } catch (SecurityException | ActivityNotFoundException e) {
            // Vehicle activities are declared exported and still refuse to start: some want a
            // vendor permission, others only run when the car itself asks. The tile reports
            // the refusal instead of taking the launcher down with it.
            DiagnosticsLog.log(context, "LaunchTargets", "refused " + id + ": " + e);
            return false;
        }
    }

    @Nullable
    private static Intent intentFor(@NonNull Context context, @NonNull String id) {
        if (id.startsWith(SYSTEM_PREFIX)) {
            return new Intent(id.substring(SYSTEM_PREFIX.length()));
        }
        ComponentName activity = componentOf(id);
        if (activity != null) {
            return new Intent().setComponent(activity);
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
