package com.tommasov.mg4simplelauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.ArrayList;
import java.util.List;

/**
 * Pages inside the vehicle's own apps that can be opened directly.
 *
 * <p>The car's software is not built the way Android apps usually are. Its settings are one
 * activity with every page inside it, so there is no {@code BluetoothActivity} to start and
 * nothing for {@link LaunchTargets#vehicleScreens} to find: asking the package manager what
 * exists returns one entry called "Settings" and leaves the driver four taps from the page
 * they wanted.
 *
 * <p>Those apps do however read an intent extra and open on the page it names. Nothing
 * documents this; the keys and values below were read out of the firmware's own bytecode,
 * where {@code SettingsActivity.onResume} does {@code getStringExtra("module")} and compares
 * it with {@code "bluetooth"}, and {@code ChargeManagementActivity.switchFragment} accepts
 * seven fragment names. That is why this is a hand-written table while everything else in the
 * vehicle list is discovered at runtime — and why every entry is checked against the package
 * manager before it is offered, so a car that does not have it never sees a dead row.
 *
 * <p>The firmware these were read from is frozen: this vehicle receives no more updates, so
 * the keys cannot change underneath. On a trim or market with different software the check
 * simply hides them.
 */
public final class VehicleShortcuts {

    /** One page, and how to ask for it. */
    public static final class Shortcut {
        public final String id;
        @StringRes
        public final int labelRes;
        final String packageName;
        final String activity;
        @Nullable
        final String extraKey;
        @Nullable
        final String extraValue;

        Shortcut(String id, @StringRes int labelRes, String packageName, String activity,
                 @Nullable String extraKey, @Nullable String extraValue) {
            this.id = id;
            this.labelRes = labelRes;
            this.packageName = packageName;
            this.activity = activity;
            this.extraKey = extraKey;
            this.extraValue = extraValue;
        }

        @NonNull
        public String label(@NonNull Context context) {
            return context.getString(labelRes);
        }

        /** The app the page lives in, for borrowing its icon. */
        @NonNull
        public String packageName() {
            return packageName;
        }

        @NonNull
        Intent intent() {
            Intent intent = new Intent()
                    .setComponent(new ComponentName(packageName, activity))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (extraKey != null) {
                intent.putExtra(extraKey, extraValue);
            }
            return intent;
        }
    }

    private static final String SETTINGS = "com.saicmotor.hmi.systemsettings";
    private static final String SETTINGS_ACTIVITY = SETTINGS + ".SettingsActivity";
    private static final String VEHICLE = "com.saicmotor.hmi.vehiclesettings";
    private static final String CHARGE_ACTIVITY =
            VEHICLE + ".chargemanagement.ui.ChargeManagementActivity";
    private static final String VEHICLE_ACTIVITY =
            VEHICLE + ".vehicleconfig.ui.VehicleSettingsActivity";
    private static final String MUSIC = "com.saicmotor.hmi.music";
    private static final String MUSIC_ACTIVITY = MUSIC + ".ui.activity.MusicActivity";

    /**
     * Every page worth a tile, in the order the picker shows them.
     *
     * <p>The music entries are included knowing they may refuse: that activity declares
     * {@code exported="false"}, so the system may well turn us away. Offering them and
     * letting the launcher report the refusal is more useful than deciding for the driver
     * that they cannot have them — the same firmware has surprised us in both directions.
     */
    private static final Shortcut[] ALL = {
            new Shortcut("bluetooth", R.string.oem_bluetooth,
                    SETTINGS, SETTINGS_ACTIVITY, "module", "bluetooth"),
            new Shortcut("charge", R.string.oem_charge,
                    VEHICLE, CHARGE_ACTIVITY, "module", "ChargeManagementFragment"),
            new Shortcut("discharge", R.string.oem_discharge,
                    VEHICLE, CHARGE_ACTIVITY, "module", "DischargeManagementFragment"),
            new Shortcut("energy", R.string.oem_energy,
                    VEHICLE, CHARGE_ACTIVITY, "module", "EnergyConsumptionFragment"),
            new Shortcut("safety", R.string.oem_safety,
                    VEHICLE, VEHICLE_ACTIVITY, "flag_tag", "SafeSettings"),
            new Shortcut("music_bt", R.string.oem_music_bluetooth,
                    MUSIC, MUSIC_ACTIVITY, "module", "bt"),
            new Shortcut("music_usb", R.string.oem_music_usb,
                    MUSIC, MUSIC_ACTIVITY, "module", "usb"),
            new Shortcut("music_online", R.string.oem_music_online,
                    MUSIC, MUSIC_ACTIVITY, "module", "online"),
    };

    private VehicleShortcuts() {
    }

    /** The ones this particular car actually carries. */
    @NonNull
    public static List<Shortcut> available(@NonNull Context context) {
        PackageManager packages = context.getPackageManager();
        List<Shortcut> found = new ArrayList<>();
        for (Shortcut shortcut : ALL) {
            try {
                packages.getActivityInfo(
                        new ComponentName(shortcut.packageName, shortcut.activity), 0);
                found.add(shortcut);
            } catch (PackageManager.NameNotFoundException absent) {
                // Another trim, another market, or an app this car was not sold with.
            }
        }
        return found;
    }

    @Nullable
    static Shortcut find(@NonNull String id) {
        for (Shortcut shortcut : ALL) {
            if (shortcut.id.equals(id)) {
                return shortcut;
            }
        }
        return null;
    }
}
