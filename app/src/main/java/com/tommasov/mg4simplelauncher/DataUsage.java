package com.tommasov.mg4simplelauncher;

import android.app.AppOpsManager;
import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.TrafficStats;
import android.os.Process;
import android.provider.Settings;

import androidx.annotation.NonNull;

import java.util.Calendar;

/**
 * How much of the car's mobile data has gone this billing cycle.
 *
 * <p>MG includes one gigabyte a month with the vehicle, and nothing on the head unit says how
 * much of it is left — the allowance is spent by the car itself as much as by the driver, on
 * map updates and telemetry, so the first warning that it has run out is usually the moment
 * something stops working.
 *
 * <p>Counts the car's own connection and not a phone hotspot: the question is what went over
 * MG's allowance, not what the head unit transferred. On this vehicle that means adding the
 * cellular and Ethernet totals together, because the modem sits in the TBOX and reaches
 * Android over an internal wired interface — a cellular-only figure reads zero here.
 *
 * <p>Two sources, because the good one needs the driver's consent. {@link NetworkStatsManager}
 * gives real totals for an arbitrary period, and asks for the "usage access" permission that
 * is granted by hand in Android's settings rather than by a dialog. Without it there is only
 * {@link TrafficStats}, which counts from boot: on a car that shuts down at every stop that
 * figure says nothing about a month, so it is offered as "since last start" and never dressed
 * up as a monthly total.
 *
 * <p>Neither will agree to the byte with MG's own accounting. Operators count differently and
 * bill on their own clock; this is a gauge, not a statement.
 */
final class DataUsage {

    /** What MG includes. Not configurable yet: every car on this firmware has the same one. */
    static final long ALLOWANCE_BYTES = 1024L * 1024L * 1024L;

    /** Where the figure came from, so the screen can say so honestly. */
    enum Source {
        /** Real total for the billing cycle. */
        CYCLE,
        /** Everything since the head unit last booted. */
        SINCE_BOOT,
        /** Nothing readable at all. */
        NONE
    }

    static final class Reading {
        final long bytes;
        final Source source;

        Reading(long bytes, Source source) {
            this.bytes = bytes;
            this.source = source;
        }
    }

    private DataUsage() {
    }

    /**
     * Whether the driver has granted usage access. Checked through {@link AppOpsManager}
     * rather than the package manager: the permission is declared in the manifest either way,
     * and what matters is whether the switch in Android's settings is on.
     */
    static boolean hasUsageAccess(@NonNull Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) {
            return false;
        }
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /** The screen where that switch lives. */
    @NonNull
    static Intent usageAccessSettings() {
        return new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
    }

    /**
     * Mobile bytes used in the cycle that began on {@code cycleDay} of this month or last.
     *
     * @param cycleDay day of the month the allowance renews, 1-31. A month that is too short
     *                 for the chosen day renews on its last day rather than skipping.
     */
    @NonNull
    static Reading read(@NonNull Context context, int cycleDay) {
        if (hasUsageAccess(context)) {
            try {
                NetworkStatsManager stats = (NetworkStatsManager)
                        context.getSystemService(Context.NETWORK_STATS_SERVICE);
                if (stats != null) {
                    long start = cycleStart(cycleDay);
                    long now = System.currentTimeMillis();
                    // Both transports, added together. The vehicle's modem lives in the TBOX
                    // and reaches Android over an internal Ethernet interface, so its traffic
                    // is filed as wired and a mobile-only total reads zero on the car that
                    // most needs the figure. Nothing else is plugged into an MG4, so whatever
                    // Ethernet carries here went over MG's allowance too.
                    long total = deviceBytes(stats, ConnectivityManager.TYPE_MOBILE, start, now)
                            + deviceBytes(stats, ConnectivityManager.TYPE_ETHERNET, start, now);
                    if (total > 0) {
                        return new Reading(total, Source.CYCLE);
                    }
                }
            } catch (Exception ignored) {
                // Fall through: a firmware that refuses the query is not worth an error.
            }
        }
        long sinceBoot = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes();
        if (sinceBoot > 0) {
            return new Reading(sinceBoot, Source.SINCE_BOOT);
        }
        return new Reading(0, Source.NONE);
    }

    /**
     * Bytes over one transport, or zero when this firmware will not report it. Asked
     * separately so that one refusal does not take the other total with it.
     */
    private static long deviceBytes(@NonNull NetworkStatsManager stats, int networkType,
                                    long start, long end) {
        try {
            // A null subscriber id means "every subscriber": asking for the real one would
            // need READ_PHONE_STATE, and this head unit has a single SIM.
            NetworkStats.Bucket bucket =
                    stats.querySummaryForDevice(networkType, null, start, end);
            return bucket == null ? 0 : bucket.getRxBytes() + bucket.getTxBytes();
        } catch (Exception e) {
            return 0;
        }
    }

    /** Midnight on the most recent {@code cycleDay}. */
    static long cycleStart(int cycleDay) {
        Calendar now = Calendar.getInstance();
        Calendar start = (Calendar) now.clone();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        start.set(Calendar.DAY_OF_MONTH,
                Math.min(cycleDay, start.getActualMaximum(Calendar.DAY_OF_MONTH)));
        if (start.after(now)) {
            // The renewal day has not come round yet this month, so the cycle began last month.
            start.add(Calendar.MONTH, -1);
            start.set(Calendar.DAY_OF_MONTH,
                    Math.min(cycleDay, start.getActualMaximum(Calendar.DAY_OF_MONTH)));
        }
        return start.getTimeInMillis();
    }
}
