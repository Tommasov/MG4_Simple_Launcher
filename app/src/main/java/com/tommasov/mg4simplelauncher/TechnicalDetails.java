package com.tommasov.mg4simplelauncher;

import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fills the technical details screen: what this machine is, and what it is connected to.
 *
 * <p>Every reading is taken defensively. Half of these come from system services that a
 * vendor firmware is free to stub out — and on this head unit several are — so a value that
 * cannot be had shows a dash rather than blanking the screen or, worse, crashing a launcher
 * the driver cannot then get out of.
 */
class TechnicalDetails {

    private static final long REFRESH_MS = 3_000;
    private static final double GB = 1024d * 1024d * 1024d;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final LinearLayout left;
    private final LinearLayout middle;
    private final LinearLayout right;
    /** One value view per row, kept so a refresh writes into the rows instead of rebuilding. */
    private final Map<String, TextView> values = new LinkedHashMap<>();
    /** The technology reading is explained to the log once, not every three seconds. */
    private boolean technologyExplained;
    private ProgressBar gauge;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    TechnicalDetails(@NonNull View root) {
        this.context = root.getContext();
        this.left = root.findViewById(R.id.details_left);
        this.middle = root.findViewById(R.id.details_middle);
        this.right = root.findViewById(R.id.details_right);
        buildRows();
    }

    /** Begins refreshing. Call from {@code onResume}. */
    void start() {
        handler.post(ticker);
    }

    /** Stops refreshing. Call from {@code onPause}. */
    void stop() {
        handler.removeCallbacks(ticker);
    }

    // --- rows -------------------------------------------------------------

    private void buildRows() {
        heading(left, R.string.sys_device);
        row(left, "model", R.string.sys_model);
        row(left, "android", R.string.sys_android_label);
        row(left, "launcher", R.string.sys_launcher_label);
        row(left, "uptime", R.string.sys_uptime_label);
        row(left, "memory", R.string.sys_memory);
        row(left, "storage", R.string.sys_storage);

        heading(middle, R.string.sys_network);
        row(middle, "connection", R.string.net_connection);
        row(middle, "link", R.string.net_link_speed);
        row(middle, "operator", R.string.net_operator);
        row(middle, "technology", R.string.net_technology);
        row(middle, "signal", R.string.net_signal);
        row(middle, "roaming", R.string.net_roaming);

        heading(right, R.string.data_usage);
        row(right, "data", R.string.data_this_cycle);
        right.addView(buildGauge());
        row(right, "cycle", R.string.data_cycle_day);
        // Both rows lead somewhere: one to Android's permission screen, one to the day the
        // allowance renews. Nothing else on this screen is touchable, so they say so by
        // doing something when touched rather than by looking like buttons.
        clickable("data", v -> onDataRowTapped());
        clickable("cycle", v -> {
            if (DataUsage.hasUsageAccess(context)) {
                chooseCycleDay();
            } else {
                onDataRowTapped();
            }
        });
    }

    /** The bar under the figure: a gauge is read faster than "412 MB of 1.0 GB". */
    private View buildGauge() {
        gauge = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        gauge.setMax(100);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(10));
        params.topMargin = dp(12);
        params.bottomMargin = dp(4);
        gauge.setLayoutParams(params);
        return gauge;
    }

    /** Makes the row holding {@code key} react to a touch, label and value together. */
    private void clickable(@NonNull String key, @NonNull View.OnClickListener listener) {
        TextView value = values.get(key);
        if (value == null || !(value.getParent() instanceof View)) {
            return;
        }
        View row = (View) value.getParent();
        row.setOnClickListener(listener);
        row.setBackgroundResource(R.drawable.icon_press_selector);
    }

    private void heading(@NonNull LinearLayout column, @StringRes int titleRes) {
        TextView view = new TextView(context);
        view.setText(titleRes);
        view.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        view.setTextSize(complexUnit(R.dimen.info_card_title_size));
        view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(10);
        // Headings after the first need air above them; the first sits under the title bar.
        if (column.getChildCount() > 0) {
            params.topMargin = dp(22);
        }
        view.setLayoutParams(params);
        column.addView(view);
    }

    /**
     * One reading: label on the left, value on the right, a hairline underneath.
     *
     * <p>The label column is fixed so the values line up down the page — a column of numbers
     * that starts in a different place on every line is read one line at a time.
     */
    private void row(@NonNull LinearLayout column, @NonNull String key, @StringRes int labelRes) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));

        TextView label = new TextView(context);
        label.setText(labelRes);
        label.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        label.setTextSize(complexUnit(R.dimen.info_card_body_size));
        label.setLayoutParams(new LinearLayout.LayoutParams(dp(190),
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView value = new TextView(context);
        value.setText(R.string.sys_unknown);
        value.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        value.setTextSize(complexUnit(R.dimen.info_card_body_size));
        value.setMaxLines(1);
        value.setEllipsize(android.text.TextUtils.TruncateAt.END);
        value.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.addView(label);
        row.addView(value);
        column.addView(row);
        column.addView(hairline());
        values.put(key, value);
    }

    private View hairline() {
        View line = new View(context);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
        line.setLayoutParams(params);
        line.setBackgroundColor(ContextCompat.getColor(context, R.color.hairline));
        return line;
    }

    private void set(@NonNull String key, @Nullable String text) {
        TextView view = values.get(key);
        if (view != null) {
            view.setText(text == null || text.isEmpty()
                    ? context.getString(R.string.sys_unknown) : text);
        }
    }

    // --- readings ---------------------------------------------------------

    private void refresh() {
        // System services and filesystem stats can throw transiently (e.g. /data remounting
        // during an OTA); a refresh tick must never crash the launcher.
        try {
            bindDevice();
            bindMemory();
            bindStorage();
            bindNetwork();
            bindCellular();
            bindData();
        } catch (Exception ignored) {
            // Keep the last good values until the next tick.
        }
    }

    private void bindDevice() {
        set("model", capitalize(Build.MANUFACTURER) + " " + Build.MODEL);
        set("android", Build.VERSION.RELEASE + " · API " + Build.VERSION.SDK_INT);
        set("uptime", formatUptime(SystemClock.elapsedRealtime()));
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            set("launcher", pi.versionName + " (" + pi.getLongVersionCode() + ")");
        } catch (PackageManager.NameNotFoundException e) {
            set("launcher", null);
        }
    }

    private void bindMemory() {
        ActivityManager am =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        set("memory", formatGb(used) + " / " + formatGb(mi.totalMem) + " GB "
                + context.getString(R.string.sys_used_total));
    }

    private void bindStorage() {
        try {
            // Matches the figures the user sees in system Settings (whole primary volume).
            StorageStatsManager stats =
                    (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
            long total = stats.getTotalBytes(StorageManager.UUID_DEFAULT);
            long free = stats.getFreeBytes(StorageManager.UUID_DEFAULT);
            set("storage", formatGb(free) + " / " + formatGb(total) + " GB "
                    + context.getString(R.string.sys_free_total));
        } catch (Exception e) {
            // Fall back to the data partition figures if storage stats are unavailable.
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            set("storage", formatGb(fs.getAvailableBytes()) + " / "
                    + formatGb(fs.getTotalBytes()) + " GB "
                    + context.getString(R.string.sys_free_total));
        }
    }

    private void bindNetwork() {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        String type = context.getString(R.string.net_offline);
        String link = null;
        if (cm != null) {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    type = context.getString(R.string.net_wifi);
                    link = wifiLinkSpeed(context);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    type = context.getString(R.string.net_mobile);
                    // The figure the modem negotiated for the link now in use.
                    int down = caps.getLinkDownstreamBandwidthKbps();
                    link = down > 0 ? (down / 1000) + " Mbps" : null;
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    // On this vehicle the TBOX hangs off an internal Ethernet interface, so
                    // the car's own SIM reaches Android as a wired connection. "Ethernet" is
                    // technically right and useless: nobody plugged a cable into their MG4.
                    type = context.getString(hasVehicleSim(context)
                            ? R.string.net_onboard : R.string.net_ethernet);
                    int down = caps.getLinkDownstreamBandwidthKbps();
                    link = down > 0 ? (down / 1000) + " Mbps" : null;
                }
            }
        }
        set("connection", type);
        set("link", link);
    }

    /** Whether the car has a SIM of its own, ready and registered. */
    private static boolean hasVehicleSim(@NonNull Context context) {
        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        return tm != null && tm.getSimState() == TelephonyManager.SIM_STATE_READY;
    }

    /**
     * The car's own SIM: who it is on, over what, and how well.
     *
     * <p>Only what Android 9 hands out without a telephony permission. Operator name, network
     * type and roaming are free; the identifiers that would need {@code READ_PHONE_STATE} —
     * IMEI, the subscriber id, the number — are not here, because a launcher asking for those
     * would be reasonable grounds for suspicion and none of them tells the driver anything.
     * Each call is wrapped separately: a firmware that refuses one should still show the rest.
     */
    private void bindCellular() {
        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm == null || tm.getSimState() != TelephonyManager.SIM_STATE_READY) {
            set("operator", null);
            set("technology", null);
            set("signal", null);
            set("roaming", null);
            return;
        }

        String operator = tm.getNetworkOperatorName();
        if (operator == null || operator.isEmpty()) {
            operator = tm.getSimOperatorName();
        }
        set("operator", operator);

        set("technology", technologyName(tm));

        try {
            SignalStrength strength = tm.getSignalStrength();
            set("signal", strength == null ? null : signalText(strength.getLevel()));
        } catch (Exception e) {
            set("signal", null);
        }

        try {
            set("roaming", context.getString(
                    tm.isNetworkRoaming() ? R.string.yes : R.string.no));
        } catch (Exception e) {
            set("roaming", null);
        }
    }

    /**
     * Mobile data used against MG's included gigabyte.
     *
     * <p>Says which figure it is showing. A total for the cycle is what the driver wants; the
     * since-boot fallback is labelled as such, because presenting it as a month's usage would
     * be worse than showing nothing — it would read as "plenty left" on a car that restarts
     * several times a day.
     */
    private void bindData() {
        int cycleDay = new PreferencesManager(context).getDataCycleDay();
        boolean allowed = DataUsage.hasUsageAccess(context);
        DataUsage.Reading reading = DataUsage.read(context, cycleDay);

        TextView label = labelOf("data");
        if (label != null) {
            label.setText(reading.source == DataUsage.Source.CYCLE
                    ? R.string.data_this_cycle : R.string.data_since_boot);
        }
        set("data", context.getString(R.string.data_of,
                formatBytes(reading.bytes), formatBytes(DataUsage.ALLOWANCE_BYTES)));
        gauge.setProgress((int) Math.min(100,
                reading.bytes * 100 / DataUsage.ALLOWANCE_BYTES));

        // The second row carries whichever of the two things is worth saying. With usage
        // access granted, when the allowance renews; without it, the way to get the real
        // figure — which otherwise never gets offered, because the since-boot fallback
        // always has some bytes in it and looks like an answer.
        TextView cycleLabel = labelOf("cycle");
        if (allowed) {
            if (cycleLabel != null) {
                cycleLabel.setText(R.string.data_cycle_day);
            }
            // The day alone. A full date here was both noise and wrong: it printed the day
            // the current cycle started, under a label promising the next renewal, when the
            // only fact that matters is which day of the month the allowance comes back.
            set("cycle", String.valueOf(cycleDay));
        } else {
            if (cycleLabel != null) {
                // Named after what granting it buys, not after the permission.
                cycleLabel.setText(R.string.data_this_cycle);
            }
            set("cycle", context.getString(R.string.data_allow));
        }
    }

    /** Opens Android's usage-access screen, where the driver grants (or refuses) the reading. */
    private void onDataRowTapped() {
        if (DataUsage.hasUsageAccess(context)) {
            return;
        }
        try {
            context.startActivity(DataUsage.usageAccessSettings()
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Toast.makeText(context, R.string.data_allow_hint, Toast.LENGTH_LONG).show();
        }
    }

    /** Which day of the month the allowance renews: MG does not start everyone's on the 1st. */
    private void chooseCycleDay() {
        PreferencesManager preferences = new PreferencesManager(context);
        CharSequence[] days = new CharSequence[31];
        for (int i = 0; i < days.length; i++) {
            days[i] = String.valueOf(i + 1);
        }
        Dialogs.builder(context)
                .setTitle(R.string.data_cycle_day_title)
                .setSingleChoiceItems(days, preferences.getDataCycleDay() - 1,
                        (dialog, which) -> {
                            preferences.setDataCycleDay(which + 1);
                            dialog.dismiss();
                            refresh();
                        })
                .show();
    }

    @Nullable
    private TextView labelOf(@NonNull String key) {
        TextView value = values.get(key);
        if (value == null || !(value.getParent() instanceof LinearLayout)) {
            return null;
        }
        View first = ((LinearLayout) value.getParent()).getChildAt(0);
        return first instanceof TextView ? (TextView) first : null;
    }

    /** "412 MB", "1.4 GB", "1 GB" — no decimals where they say nothing. */
    private static String formatBytes(long bytes) {
        double mb = bytes / (1024d * 1024d);
        if (mb < 1024) {
            return String.format(Locale.getDefault(), "%.0f MB", mb);
        }
        double gb = mb / 1024d;
        return String.format(Locale.getDefault(),
                gb == Math.floor(gb) ? "%.0f GB" : "%.1f GB", gb);
    }

    /**
     * The radio technology, or null when this firmware will not say.
     *
     * <p>Two ways of asking, because they are gated differently across builds:
     * {@code getDataNetworkType} is the current one and some firmwares put it behind
     * {@code READ_PHONE_STATE}, while the deprecated {@code getNetworkType} is often still
     * open. Whichever answers, the reason for a dash is written to the diagnostics log once
     * per screen — on the vehicle this is the only way to tell "not permitted" from "the
     * modem is not registered", and the two want different fixes.
     */
    @Nullable
    private String technologyName(@NonNull TelephonyManager tm) {
        String reason;
        try {
            String name = networkTypeName(tm.getDataNetworkType());
            if (name != null) {
                return name;
            }
            reason = "data network type unknown";
        } catch (SecurityException e) {
            try {
                String name = networkTypeName(tm.getNetworkType());
                if (name != null) {
                    return name;
                }
                reason = "network type unknown (data type needs READ_PHONE_STATE)";
            } catch (SecurityException second) {
                reason = "network type refused: " + second.getMessage();
            }
        }
        if (!technologyExplained) {
            technologyExplained = true;
            DiagnosticsLog.log(context, "TechnicalDetails", reason);
        }
        return null;
    }

    /** "●●●○○" — bars, because dBm means nothing to most people. */
    private String signalText(int level) {
        StringBuilder bars = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            bars.append(i < level ? '●' : '○');
        }
        return bars.toString();
    }

    /** The radio technology, named the way the status bar names it. */
    private static String networkTypeName(int type) {
        switch (type) {
            case TelephonyManager.NETWORK_TYPE_LTE:
                return "LTE";
            case TelephonyManager.NETWORK_TYPE_HSPAP:
                return "HSPA+";
            case TelephonyManager.NETWORK_TYPE_HSPA:
            case TelephonyManager.NETWORK_TYPE_HSDPA:
            case TelephonyManager.NETWORK_TYPE_HSUPA:
                return "HSPA";
            case TelephonyManager.NETWORK_TYPE_UMTS:
                return "UMTS";
            case TelephonyManager.NETWORK_TYPE_EDGE:
                return "EDGE";
            case TelephonyManager.NETWORK_TYPE_GPRS:
                return "GPRS";
            case TelephonyManager.NETWORK_TYPE_UNKNOWN:
                return null;
            default:
                return "type " + type;
        }
    }

    /** Wi-Fi negotiated link speed (e.g. "120 Mbps"), or null when unavailable. */
    @Nullable
    private static String wifiLinkSpeed(Context ctx) {
        WifiManager wm = (WifiManager)
                ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            WifiInfo info = wm.getConnectionInfo();
            if (info != null && info.getLinkSpeed() >= 0) {
                return info.getLinkSpeed() + " Mbps";
            }
        }
        return null;
    }

    // --- formatting -------------------------------------------------------

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density);
    }

    /** A dimen in sp, as setTextSize's default unit expects it. */
    private float complexUnit(int dimenRes) {
        return context.getResources().getDimension(dimenRes)
                / context.getResources().getDisplayMetrics().scaledDensity;
    }

    private static String formatGb(long bytes) {
        return String.format(Locale.getDefault(), "%.1f", bytes / GB);
    }

    /** Human-readable uptime, e.g. "1d 3h 12m" (days dropped when zero). */
    private static String formatUptime(long elapsedMs) {
        long totalSeconds = elapsedMs / 1000;
        long days = totalSeconds / 86_400;
        long hours = (totalSeconds % 86_400) / 3_600;
        long minutes = (totalSeconds % 3_600) / 60;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        return sb.append(hours).append("h ").append(minutes).append("m").toString();
    }

    private static String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
