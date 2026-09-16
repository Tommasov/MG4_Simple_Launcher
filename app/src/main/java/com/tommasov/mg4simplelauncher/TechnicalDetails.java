package com.tommasov.mg4simplelauncher;

import android.app.ActivityManager;
import android.app.usage.StorageStatsManager;
import android.content.Context;
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
import android.webkit.WebView;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
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
        row(left, "webview", R.string.sys_webview);

        heading(middle, R.string.sys_network);
        row(middle, "connection", R.string.net_connection);
        row(middle, "link", R.string.net_link_speed);
        row(middle, "operator", R.string.net_operator);
        row(middle, "technology", R.string.net_technology);
        row(middle, "signal", R.string.net_signal);
        row(middle, "roaming", R.string.net_roaming);
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
        set("webview", webViewVersion());
    }

    /**
     * Which Chromium renders web content here, and how old it is.
     *
     * <p>Worth a line of its own on a head unit without Play Services: the system WebView
     * never updates, so it is frozen at whatever the firmware shipped. Everything that
     * displays a web page on this car — the HTML viewer, any app with an embedded view, and
     * anything we might build — runs on that engine and inherits both its abilities and its
     * unpatched holes. The version is the only way to know which.
     */
    @Nullable
    private String webViewVersion() {
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info == null) {
                return null;
            }
            String version = info.versionName;
            // Chromium version strings are four parts; the first is the one that dates it.
            int dot = version == null ? -1 : version.indexOf('.');
            String major = dot > 0 ? version.substring(0, dot) : version;
            return major + " (" + version + ")";
        } catch (Exception e) {
            return null;
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
