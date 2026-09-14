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
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * The device, memory, storage and network readings, and the ticker that keeps them current.
 *
 * <p>These used to be a card on the tools page, refreshed while the carousel sat on it. They
 * moved into the settings screen when the charging card took the room: none of it changes
 * the way a charging point or a battery figure does, so it belongs where someone goes to
 * look rather than on a page that is glanced at while driving.
 *
 * <p>Every value is read without a dangerous permission.
 */
final class TechnicalDetails {

    private static final long REFRESH_MS = 3_000;
    private static final double GB = 1024d * 1024d * 1024d;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Context context;
    private final TextView deviceBody;
    private final TextView memoryValue;
    private final TextView storageValue;
    private final TextView networkValue;
    private final TextView networkDetail;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    TechnicalDetails(@NonNull View root) {
        context = root.getContext();
        deviceBody = root.findViewById(R.id.tv_device_body);
        memoryValue = root.findViewById(R.id.tv_memory_value);
        storageValue = root.findViewById(R.id.tv_storage_value);
        networkValue = root.findViewById(R.id.tv_network_value);
        networkDetail = root.findViewById(R.id.tv_network_detail);
    }

    /** Begins refreshing. Call from {@code onResume}. */
    void start() {
        handler.post(ticker);
    }

    /** Stops refreshing. Call from {@code onPause}. */
    void stop() {
        handler.removeCallbacks(ticker);
    }

    private void refresh() {
        // System services and filesystem stats can throw transiently (e.g. /data remounting
        // during an OTA); a refresh tick must never crash the launcher.
        try {
            deviceBody.setText(buildDeviceText());
            bindMemory();
            bindStorage();
            bindNetwork();
        } catch (Exception ignored) {
            // Keep the last good values until the next tick.
        }
    }

    private String buildDeviceText() {
        String model = capitalize(Build.MANUFACTURER) + " " + Build.MODEL;
        String android = context.getString(R.string.sys_android,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
        String uptime = context.getString(R.string.sys_uptime,
                formatUptime(SystemClock.elapsedRealtime()));
        String launcher;
        try {
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            launcher = context.getString(R.string.sys_launcher,
                    pi.versionName, pi.getLongVersionCode());
        } catch (PackageManager.NameNotFoundException e) {
            launcher = "";
        }
        return model + System.lineSeparator() + android + System.lineSeparator()
                + uptime + System.lineSeparator() + launcher;
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
        memoryValue.setText(formatGb(used) + " / " + formatGb(mi.totalMem) + " GB");
    }

    private void bindStorage() {
        try {
            // Matches the figures the user sees in system Settings (whole primary volume).
            StorageStatsManager stats =
                    (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
            long total = stats.getTotalBytes(StorageManager.UUID_DEFAULT);
            long free = stats.getFreeBytes(StorageManager.UUID_DEFAULT);
            storageValue.setText(formatGb(free) + " / " + formatGb(total) + " GB");
        } catch (Exception e) {
            // Fall back to the data partition figures if storage stats are unavailable.
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            storageValue.setText(formatGb(fs.getAvailableBytes()) + " / "
                    + formatGb(fs.getTotalBytes()) + " GB");
        }
    }

    private void bindNetwork() {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        String type = context.getString(R.string.net_offline);
        String detail = "";
        if (cm != null) {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    type = context.getString(R.string.net_wifi);
                    detail = wifiLinkSpeed(context);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    type = context.getString(R.string.net_mobile);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    type = context.getString(R.string.net_ethernet);
                }
            }
        }
        networkValue.setText(type);
        networkDetail.setText(detail);
    }

    /** Wi-Fi negotiated link speed (e.g. "120 Mbps"), or empty when unavailable. */
    private static String wifiLinkSpeed(Context ctx) {
        WifiManager wm = (WifiManager)
                ctx.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            WifiInfo info = wm.getConnectionInfo();
            if (info != null && info.getLinkSpeed() >= 0) {
                return info.getLinkSpeed() + " Mbps";
            }
        }
        return "";
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
