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
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Locale;
import java.util.UUID;

/**
 * Carousel page 2: live system information (device, memory, storage, network, uptime).
 * Every value is read without dangerous permissions; the view refreshes while visible.
 */
public class SystemInfoFragment extends Fragment {

    private static final long REFRESH_MS = 3_000;
    private static final double GB = 1024d * 1024d * 1024d;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private TextView deviceBody;
    private TextView memoryValue;
    private TextView storageValue;
    private TextView networkValue;
    private TextView networkDetail;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_system, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        deviceBody = view.findViewById(R.id.tv_device_body);
        memoryValue = view.findViewById(R.id.tv_memory_value);
        storageValue = view.findViewById(R.id.tv_storage_value);
        networkValue = view.findViewById(R.id.tv_network_value);
        networkDetail = view.findViewById(R.id.tv_network_detail);
    }

    @Override
    public void onResume() {
        super.onResume();
        handler.post(ticker);
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacks(ticker);
    }

    private void refresh() {
        Context ctx = getContext();
        if (ctx == null) {
            return;
        }
        // System services and filesystem stats can throw transiently (e.g. /data remounting
        // during an OTA); a refresh tick must never crash the launcher.
        try {
            deviceBody.setText(buildDeviceText(ctx));
            bindMemory(ctx);
            bindStorage(ctx);
            bindNetwork(ctx);
        } catch (Exception ignored) {
            // Keep the last good values until the next tick.
        }
    }

    private String buildDeviceText(Context ctx) {
        String model = capitalize(Build.MANUFACTURER) + " " + Build.MODEL;
        String android = getString(R.string.sys_android,
                Build.VERSION.RELEASE, Build.VERSION.SDK_INT);
        String uptime = getString(R.string.sys_uptime,
                formatUptime(SystemClock.elapsedRealtime()));
        String launcher;
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            launcher = getString(R.string.sys_launcher, pi.versionName, pi.getLongVersionCode());
        } catch (PackageManager.NameNotFoundException e) {
            launcher = "";
        }
        return model + "\n" + android + "\n" + uptime + "\n" + launcher;
    }

    private void bindMemory(Context ctx) {
        ActivityManager am = (ActivityManager) ctx.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            return;
        }
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        long used = mi.totalMem - mi.availMem;
        memoryValue.setText(formatGb(used) + " / " + formatGb(mi.totalMem) + " GB");
    }

    private void bindStorage(Context ctx) {
        try {
            // Matches the figures the user sees in system Settings (whole primary volume).
            StorageStatsManager stats =
                    (StorageStatsManager) ctx.getSystemService(Context.STORAGE_STATS_SERVICE);
            long total = stats.getTotalBytes(StorageManager.UUID_DEFAULT);
            long free = stats.getFreeBytes(StorageManager.UUID_DEFAULT);
            storageValue.setText(formatGb(free) + " / " + formatGb(total) + " GB");
        } catch (Exception e) {
            // Fall back to the data partition figures if storage stats are unavailable.
            StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
            storageValue.setText(
                    formatGb(fs.getAvailableBytes()) + " / " + formatGb(fs.getTotalBytes()) + " GB");
        }
    }

    private void bindNetwork(Context ctx) {
        ConnectivityManager cm =
                (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        String type = getString(R.string.net_offline);
        String detail = "";
        if (cm != null) {
            Network active = cm.getActiveNetwork();
            NetworkCapabilities caps = active == null ? null : cm.getNetworkCapabilities(active);
            if (caps != null) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    type = getString(R.string.net_wifi);
                    detail = wifiLinkSpeed(ctx);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                    type = getString(R.string.net_mobile);
                } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    type = getString(R.string.net_ethernet);
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
