package com.tommasov.mg4simplelauncher.charging;

import android.content.Context;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.SystemClock;
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
    /** Past this, a fix while following is noise rather than a position. */
    private static final float COARSE_LIMIT_METRES = 1_000f;
    /** How often a fix earns a line in the on-device log while following. */
    private static final long LOG_INTERVAL_MS = 60_000;

    interface Callback {
        void onLocation(@NonNull Location location);

        /** No provider, no permission, or nothing arrived before the timeout. */
        void onUnavailable();

        /**
         * Called once, only when waiting without a deadline, at the point where a bounded
         * wait would have given up. The search carries on; this exists so the screen can
         * say why it is taking so long instead of looking stuck.
         */
        default void onStillSearching() {
        }
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private LocationManager manager;
    @Nullable
    private LocationListener listener;
    private boolean finished;
    @Nullable
    private Location best;
    /** Whether the caller has had at least one position, in following mode. */
    private boolean delivered;
    private long lastLoggedAt;
    private boolean settling;
    /** When set, the wait has no deadline and ends only when the caller cancels. */
    private boolean untilCancelled;
    /** When set, every fix is handed on instead of the first good one ending the search. */
    private boolean continuous;
    @Nullable
    private GnssStatus.Callback gnssCallback;
    /** Satellites the receiver can see, and how many of them are in the solution. */
    private int satellitesInView;
    private int satellitesUsed;
    private long startedAt;

    /** Returns the freshest cached fix across providers, or null when there is none. */
    @Nullable
    public static Location lastKnown(@NonNull Context context) {
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
     * Keeps looking until the caller cancels, for a screen the driver is sitting in front of.
     *
     * <p>A bounded wait is wrong there. The head unit's assisted GPS rides on the car's own
     * cellular connection, so with that modem off the receiver has to read the satellites'
     * almanac itself, which takes minutes — and a screen that gives up at thirty seconds
     * gives up exactly when it should be waiting. {@link Callback#onStillSearching()} fires
     * at that thirty-second mark so the screen can explain itself, and the fix is delivered
     * whenever it arrives.
     */
    void resolveUntilCancelled(@NonNull Context context, @NonNull Callback callback) {
        untilCancelled = true;
        resolve(context, callback);
    }

    /**
     * Follows the vehicle: {@link Callback#onLocation} runs again for every fix that arrives,
     * until {@link #cancel()}.
     *
     * <p>For a screen that draws where the car is. A single fix is right only for the instant
     * the screen opened — drive away and the marker stays behind, and the heading it was
     * drawn with becomes a memory. The receiver is already running to get the first fix, so
     * this costs keeping it on rather than turning it on.
     */
    void watch(@NonNull Context context, @NonNull Callback callback) {
        untilCancelled = true;
        continuous = true;
        resolve(context, callback);
    }

    /**
     * Hands back a position: the cached one when there is one, otherwise the first live fix
     * from any enabled provider. The callback runs once, on the main thread.
     */
    void resolve(@NonNull Context context, @NonNull Callback callback) {
        // Start clean: the same resolver is used again when the screen comes back after
        // being stopped mid-search, and the flags left behind by the previous attempt would
        // otherwise swallow every callback of the new one.
        finish();
        finished = false;
        best = null;
        settling = false;
        delivered = false;
        satellitesInView = 0;
        satellitesUsed = 0;
        lastLoggedAt = 0;

        Location cached = lastKnown(context);
        if (cached != null) {
            callback.onLocation(cached);
            // In following mode the cached fix is a head start, not an answer: it is often
            // minutes old, and the whole point is to keep up with where the car is now.
            if (!continuous) {
                return;
            }
            delivered = true;
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
        startedAt = SystemClock.elapsedRealtime();
        // The network in use is logged with the wait because assisted GPS is not carried by
        // whichever network happens to be up: Android asks for a cellular connection with
        // the SUPL capability. On Wi-Fi alone the receiver gets no assistance and has to
        // read the satellites' own almanac, which takes minutes rather than seconds. The
        // line below is what tells those two situations apart after the fact.
        DiagnosticsLog.log(appContext, TAG, "waiting for a fix from " + providers
                + ", network " + describeNetwork(appContext));
        watchSatellites(appContext);

        listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (continuous) {
                    // Newest, not best: a car that has moved makes the older, tighter fix the
                    // wrong one. Wildly loose fixes are dropped rather than made to jump.
                    if (location.getAccuracy() > COARSE_LIMIT_METRES && delivered) {
                        return;
                    }
                    // A fix a second, and the log lives in a file on a car that cannot be
                    // reached over adb: written in full it would bury everything else worth
                    // reading. The first one goes in, then one a minute.
                    long now = SystemClock.elapsedRealtime();
                    if (!delivered || now - lastLoggedAt >= LOG_INTERVAL_MS) {
                        lastLoggedAt = now;
                        DiagnosticsLog.log(appContext, TAG, "fix from " + location.getProvider()
                                + ", accuracy " + Math.round(location.getAccuracy()) + " m"
                                + (location.hasBearing()
                                        ? ", bearing " + Math.round(location.getBearing())
                                        : ", no bearing")
                                + (location.hasSpeed()
                                        ? ", speed " + Math.round(location.getSpeed() * 3.6f)
                                                + " km/h"
                                        : ""));
                    }
                    delivered = true;
                    callback.onLocation(location);
                    return;
                }
                DiagnosticsLog.log(appContext, TAG, "fix from " + location.getProvider()
                        + ", accuracy " + Math.round(location.getAccuracy()) + " m, after "
                        + elapsedSeconds() + " s, " + satellites());
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
            if (finished || delivered) {
                return;
            }
            // Out of time: a coarse fix still beats telling the driver there is none.
            Location fallback = best;
            if (fallback != null) {
                finish();
                callback.onLocation(fallback);
                return;
            }
            DiagnosticsLog.log(appContext, TAG, "no fix within " + TIMEOUT_MS + " ms, "
                    + satellites() + ", network " + describeNetwork(appContext));
            if (untilCancelled) {
                // Still listening. Only the wording changes.
                callback.onStillSearching();
            } else {
                finish();
                callback.onUnavailable();
            }
        }, TIMEOUT_MS);
    }

    /**
     * Counts satellites while waiting. This is the measurement that separates a receiver with
     * no sky from a receiver with no assistance: a car park shows few satellites in view,
     * while an unassisted cold start shows plenty in view and none used in the fix.
     */
    private void watchSatellites(@NonNull Context appContext) {
        if (manager == null) {
            return;
        }
        gnssCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                int used = 0;
                for (int i = 0; i < status.getSatelliteCount(); i++) {
                    if (status.usedInFix(i)) {
                        used++;
                    }
                }
                satellitesInView = status.getSatelliteCount();
                satellitesUsed = used;
            }
        };
        try {
            manager.registerGnssStatusCallback(gnssCallback, handler);
        } catch (SecurityException e) {
            gnssCallback = null;
        }
    }

    private String satellites() {
        return satellitesInView + " satellites in view, " + satellitesUsed + " used";
    }

    private long elapsedSeconds() {
        return (SystemClock.elapsedRealtime() - startedAt) / 1000;
    }

    /** Which transport is carrying data right now, in the words the log needs. */
    private static String describeNetwork(@NonNull Context context) {
        ConnectivityManager connectivity =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivity == null) {
            return "unknown";
        }
        NetworkInfo active = connectivity.getActiveNetworkInfo();
        if (active == null || !active.isConnected()) {
            return "none";
        }
        NetworkCapabilities capabilities =
                connectivity.getNetworkCapabilities(connectivity.getActiveNetwork());
        if (capabilities == null) {
            return active.getTypeName();
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return "wifi";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return "cellular";
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return "ethernet";
        }
        return active.getTypeName();
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
        if (manager != null && gnssCallback != null) {
            try {
                manager.unregisterGnssStatusCallback(gnssCallback);
            } catch (SecurityException ignored) {
                // Same.
            }
        }
        gnssCallback = null;
        listener = null;
    }
}
