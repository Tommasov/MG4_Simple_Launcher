package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/** Shared helper for launching whatever a tile holds: an installed app, or a screen. */
public final class AppLauncher {

    private AppLauncher() {
    }

    /**
     * Launches {@code pkg}'s main activity in a new task, or the screen {@code pkg} names.
     * Returns false when there is nothing to launch — package not installed, or a screen this
     * firmware does not carry — leaving the fallback to the caller.
     */
    public static boolean launch(@NonNull Context context, @NonNull String pkg) {
        // Tiles used to hold package names only, so anything with a prefix is one of the
        // settings or launcher screens added later; everything else is still a package.
        if (LaunchTargets.isTarget(pkg)) {
            return LaunchTargets.launch(context, pkg);
        }
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }
}
