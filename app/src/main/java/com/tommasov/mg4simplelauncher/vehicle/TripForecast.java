package com.tommasov.mg4simplelauncher.vehicle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;

import androidx.annotation.NonNull;

import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;

/**
 * What the car's navigator expects of the journey ahead.
 *
 * <p>The range the vehicle reports looks backwards: it is the recent consumption projected
 * forward, which is right while you carry on doing what you were doing and wrong the moment
 * the driving changes. Leaving home after a week of city traffic and joining a motorway is
 * exactly that moment, and the error falls on the dangerous side — a charging stop that reads
 * as comfortably in reach at 12 kWh/100 km is not in reach at 20.
 *
 * <p>The navigator knows better, because it has the route. Remaining distance divided by
 * remaining time is the average speed it is predicting for what is left of the journey, and
 * average speed is the single best predictor of consumption there is — better than any rule
 * about motorways, since it already accounts for traffic, junctions and the climb over a
 * pass.
 *
 * <p>Read from {@code IGeneralService}, a different service of the same SAIC adapter that
 * supplies charge and range: those live on {@code MapService} behind the {@code IMapService}
 * token, these on {@code GeneralService} behind its own. Both are exported.
 */
public final class TripForecast {

    private static final String TAG = "TripForecast";

    private static final String ADAPTER_PACKAGE = "com.saicmotor.adapterservice";
    private static final String GENERAL_SERVICE =
            "com.saicmotor.adapterservice.services.GeneralService";
    private static final String INTERFACE_TOKEN =
            "com.saicmotor.adapterservice.IGeneralService";

    /**
     * Transaction numbers, read from the {@code TRANSACTION_*} constants in the adapter's own
     * {@code IGeneralService$Stub} — not counted off the method list, which a dex file prints
     * in alphabetical order and which does not match the order the AIDL declared them in.
     */
    private static final int TX_IS_MAP_NAVIGATING = 18;
    private static final int TX_REMAINING_TIMES = 33;
    private static final int TX_REMAINING_DISTANCE = 34;

    /** Below this there is no journey worth correcting a consumption estimate for. */
    private static final double MIN_DISTANCE_KM = 5;
    /** Outside this band the two raw readings were not what we took them for. */
    private static final double MIN_SPEED_KMH = 5;
    private static final double MAX_SPEED_KMH = 200;

    /** The route ahead, as the navigator sees it. */
    public static final class Trip {
        /** Kilometres still to drive to the destination. */
        public final double distanceKm;
        /** Average speed the navigator implies for them, in km/h. */
        public final double averageSpeedKmh;

        Trip(double distanceKm, double averageSpeedKmh) {
            this.distanceKm = distanceKm;
            this.averageSpeedKmh = averageSpeedKmh;
        }
    }

    public interface Callback {
        void onTrip(@NonNull Trip trip);

        /** No navigation running, no adapter, or numbers that made no sense. */
        void onNoTrip();
    }

    private TripForecast() {
    }

    /** Takes one reading and releases the connection immediately afterwards. */
    public static void read(@NonNull Context context, @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(ADAPTER_PACKAGE, GENERAL_SERVICE));

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName component, IBinder binder) {
                int navigating = readInt(binder, TX_IS_MAP_NAVIGATING);
                int rawDistance = readInt(binder, TX_REMAINING_DISTANCE);
                int rawTime = readInt(binder, TX_REMAINING_TIMES);
                release(appContext, this);
                // Raw, because the units are not documented anywhere and the car is the only
                // place they can be confirmed. Whatever the driver reports back, these three
                // numbers explain what the estimate did.
                DiagnosticsLog.log(appContext, TAG, "navigating=" + navigating
                        + " distance=" + rawDistance + " time=" + rawTime);

                Trip trip = interpret(navigating, rawDistance, rawTime);
                if (trip == null) {
                    callback.onNoTrip();
                } else {
                    callback.onTrip(trip);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName component) {
            }

            @Override
            public void onNullBinding(ComponentName component) {
                release(appContext, this);
                callback.onNoTrip();
            }
        };

        try {
            if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                DiagnosticsLog.log(appContext, TAG, "general service not present");
                callback.onNoTrip();
            }
        } catch (SecurityException e) {
            DiagnosticsLog.log(appContext, TAG, "binding refused", e);
            callback.onNoTrip();
        }
    }

    /**
     * Makes sense of two numbers whose units nobody wrote down.
     *
     * <p>Distance could be metres or kilometres, time seconds or minutes, and the adapter's
     * own sources disagree across firmware versions. Rather than guess once and be wrong on
     * somebody's car, every combination is tried and the one that yields a speed a car could
     * actually hold is taken. When two combinations both look plausible the reading is
     * discarded: a silent wrong answer here would quietly poison every arrival estimate.
     */
    static Trip interpret(int navigating, int rawDistance, int rawTime) {
        if (navigating <= 0 || rawDistance <= 0 || rawTime <= 0) {
            return null;
        }
        double[] distances = {rawDistance / 1000d, rawDistance};      // metres, kilometres
        double[] hours = {rawTime / 3600d, rawTime / 60d};            // seconds, minutes

        Trip only = null;
        for (double km : distances) {
            if (km < MIN_DISTANCE_KM) {
                continue;
            }
            for (double h : hours) {
                double speed = km / h;
                if (speed < MIN_SPEED_KMH || speed > MAX_SPEED_KMH) {
                    continue;
                }
                if (only != null) {
                    return null;    // ambiguous: two readings of the same pair both work
                }
                only = new Trip(km, speed);
            }
        }
        return only;
    }

    private static void release(@NonNull Context context, @NonNull ServiceConnection conn) {
        try {
            context.unbindService(conn);
        } catch (IllegalArgumentException ignored) {
            // Already released.
        }
    }

    /** One no-argument call returning an int, or -1 when it does not land. */
    private static int readInt(@NonNull IBinder binder, int transaction) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(INTERFACE_TOKEN);
            binder.transact(transaction, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            return -1;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
