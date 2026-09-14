package com.tommasov.mg4simplelauncher.charging;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;

import java.util.List;

/**
 * Obtains a position to search around.
 *
 * <p>The cached fix is tried first because it is instant, but it cannot be relied on: right
 * after the user grants the permission there is often nothing cached at all, and a screen
 * that only reads the cache then waits forever. So when the cache is empty this listens for
 * a real fix and gives up after a bounded wait instead of hanging.
 */
final class LocationResolver {

    private static final String TAG = "LocationResolver";
    /** Long enough for a cold GPS fix in the open, short enough not to feel stuck. */
    private static final long TIMEOUT_MS = 30_000;
    /**
     * A fix this accurate is taken immediately. Anything coarser is kept but not trusted
     * yet: the network provider answers in seconds and can be kilometres out, which would
     * list stations from the wrong part of town.
     */
    private static final float GOOD_ENOUGH_METRES = 200f;
    /** How long to keep waiting for something better after a coarse first fix. */
    private static final long SETTLE_MS = 12_000;

    interface Callback {
        void onLocation(@NonNull Location location);

        /** No provider, no permission, or nothing arrived before the timeout. */
        void onUnavailable();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private LocationManager manager;
    @Nullable
    private LocationListener listener;
    private boolean finished;
    @Nullable
    private Location best;
    private boolean settling;

    /** Returns the freshest cached fix across providers, or null when there is none. */
    @Nullable
    static Location lastKnown(@NonNull Context context) {
        LocationManager lm =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            return null;
        }
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location candidate = lm.getLastKnownLocation(provider);
                if (candidate != null && (best == null
                        || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            }
        } catch (SecurityException e) {
            return null;
        }
        return best;
    }

    /**
     * Hands back a position: the cached one when there is one, otherwise the first live fix
     * from any enabled provider. The callback runs once, on the main thread.
     */
    void resolve(@NonNull Context context, @NonNull Callback callback) {
        Location cached = lastKnown(context);
        if (cached != null) {
            callback.onLocation(cached);
            return;
        }

        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            callback.onUnavailable();
            return;
        }
        List<String> providers = manager.getProviders(true);
        if (providers.isEmpty()) {
            callback.onUnavailable();
            return;
        }

        Context appContext = context.getApplicationContext();
        DiagnosticsLog.log(appContext, TAG, "waiting for a fix from " + providers);

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                DiagnosticsLog.log(appContext, TAG, "fix from " + location.getProvider()
                        + ", accuracy " + Math.round(location.getAccuracy()) + " m");
                if (best == null || location.getAccuracy() < best.getAccuracy()) {
                    best = location;
                }
                if (best.getAccuracy() <= GOOD_ENOUGH_METRES) {
                    finish();
                    callback.onLocation(best);
                    return;
                }
                // Coarse so far. Keep it, but give the satellites a little longer before
                // settling for it.
                if (!settling) {
                    settling = true;
                    handler.postDelayed(() -> {
                        if (finished || best == null) {
                            return;
                        }
                        DiagnosticsLog.log(appContext, TAG, "settling for "
                                + best.getProvider() + " at "
                                + Math.round(best.getAccuracy()) + " m");
                        Location settled = best;
                        finish();
                        callback.onLocation(settled);
                    }, SETTLE_MS);
                }
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };

        try {
            // Every enabled provider at once: network answers in seconds where it works,
            // GPS is the one that answers at all in a car park with no data.
            for (String provider : providers) {
                manager.requestLocationUpdates(provider, 0L, 0f, listener,
                        Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            Log.w(TAG, "location updates refused", e);
            finish();
            callback.onUnavailable();
            return;
        }

        handler.postDelayed(() -> {
            if (finished) {
                return;
            }
            // Out of time: a coarse fix still beats telling the driver there is none.
            Location fallback = best;
            finish();
            if (fallback != null) {
                callback.onLocation(fallback);
            } else {
                DiagnosticsLog.log(appContext, TAG, "no fix within " + TIMEOUT_MS + " ms");
                callback.onUnavailable();
            }
        }, TIMEOUT_MS);
    }

    /** Stops listening. Safe to call more than once, and required when the screen goes away. */
    void cancel() {
        finish();
    }

    private void finish() {
        if (finished) {
            return;
        }
        finished = true;
        handler.removeCallbacksAndMessages(null);
        if (manager != null && listener != null) {
            try {
                manager.removeUpdates(listener);
            } catch (SecurityException ignored) {
                // Permission withdrawn while listening; nothing left to release.
            }
        }
        listener = null;
    }
}
