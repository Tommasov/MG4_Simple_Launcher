package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/** Shared helper for launching an installed package from its launcher intent. */
final class AppLauncher {

    private AppLauncher() {
    }

    /**
     * Launches {@code pkg}'s main activity in a new task. Returns false if the package has no
     * launch intent (not installed / not launchable), leaving the fallback to the caller.
     */
    static boolean launch(@NonNull Context context, @NonNull String pkg) {
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            return false;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }
}
