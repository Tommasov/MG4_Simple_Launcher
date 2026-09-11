package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;
import android.util.DisplayMetrics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/** Shared helper for loading the launcher icons shown on the large home tiles. */
final class AppIcons {

    private AppIcons() {
    }

    /**
     * Loads the launcher icon at a high density bucket so it stays sharp when scaled up to
     * the large card size, instead of upscaling the device-density icon. Falls back to the
     * package manager's default icon, or null if the package isn't installed.
     */
    @Nullable
    static Drawable highRes(@NonNull Context context, @NonNull String pkg) {
        LauncherApps launcherApps =
                (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        if (launcherApps != null) {
            try {
                List<LauncherActivityInfo> activities =
                        launcherApps.getActivityList(pkg, Process.myUserHandle());
                if (!activities.isEmpty()) {
                    Drawable icon = activities.get(0).getIcon(DisplayMetrics.DENSITY_XXXHIGH);
                    if (icon != null) {
                        return icon;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to the default-density icon below.
            }
        }
        try {
            return context.getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
