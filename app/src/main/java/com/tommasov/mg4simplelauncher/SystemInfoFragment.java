package com.tommasov.mg4simplelauncher;

import android.app.ActivityManager;
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Locale;

/**
 * Carousel page 2: live system information (device, memory, storage, battery, uptime).
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
        deviceBody.setText(buildDeviceText(ctx));
        bindMemory(ctx);
        bindStorage();
        bindNetwork(ctx);
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
        String body = model + "\n" + android + "\n" + uptime + "\n" + launcher;
        // Temperature isn't exposed by a public API on a normal app, so this is best-effort:
        // shown only when a thermal zone is actually readable on this device.
        String temp = readDeviceTemperature();
        if (temp != null) {
            body += "\n" + getString(R.string.sys_temp, temp);
        }
        return body;
    }

    /**
     * Best-effort device temperature read from {@code /sys/class/thermal}. Prefers a CPU/SoC
     * zone and falls back to the hottest plausible zone. Returns null if nothing is readable
     * (e.g. SELinux blocks access), so the caller can simply omit the line.
     */
    @Nullable
    private static String readDeviceTemperature() {
        File[] zones = new File("/sys/class/thermal").listFiles(
                (dir, name) -> name.startsWith("thermal_zone"));
        if (zones == null) {
            return null;
        }
        double preferred = Double.NaN;
        double hottest = Double.NaN;
        for (File zone : zones) {
            Double celsius = parseTemp(new File(zone, "temp"));
            if (celsius == null || celsius < 0 || celsius > 150) {
                continue;
            }
            if (Double.isNaN(hottest) || celsius > hottest) {
                hottest = celsius;
            }
            String type = readFirstLine(new File(zone, "type"));
            if (type != null) {
                String t = type.toLowerCase(Locale.US);
                if (t.contains("cpu") || t.contains("soc") || t.contains("tsens")
                        || t.contains("ap")) {
                    if (Double.isNaN(preferred) || celsius > preferred) {
                        preferred = celsius;
                    }
                }
            }
        }
        double chosen = !Double.isNaN(preferred) ? preferred : hottest;
        return Double.isNaN(chosen) ? null : Math.round(chosen) + "°C";
    }

    /** Parses a thermal zone temp file, normalising milli-°C (e.g. 47000) to °C. */
    private static Double parseTemp(File file) {
        String line = readFirstLine(file);
        if (line == null) {
            return null;
        }
        try {
            long raw = Long.parseLong(line.trim());
            return raw > 1000 ? raw / 1000.0 : (double) raw;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private static String readFirstLine(File file) {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (Exception e) {
            return null;
        }
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

    private void bindStorage() {
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        storageValue.setText(
                formatGb(fs.getAvailableBytes()) + " / " + formatGb(fs.getTotalBytes()) + " GB");
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
        return String.format(Locale.US, "%.1f", bytes / GB);
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
