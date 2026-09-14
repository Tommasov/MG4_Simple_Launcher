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
 * Reads the vehicle's own state through SAIC's adapter service.
 *
 * <p>Same door as {@link com.tommasov.mg4simplelauncher.charging.FactoryNavigator}, different
 * room: the adapter serves {@code IMapService} so the factory navigator can ask the car about
 * itself, and that service is exported without a permission, so the launcher can ask too.
 *
 * <p>Descriptors and transaction codes were read out of the firmware with dexdump, and so was
 * the choice of which values to ask for. The air-quality getters this class used to call —
 * {@code getInCarPM25Val} and its siblings — turned out to be {@code return 0;} in
 * {@code AQIManager}: two instructions, a hardcoded zero. Nothing on this vehicle was failing;
 * SAIC shipped the stub empty. {@code getSensorTemperature} is real but reads
 * {@code CarInfoManager.getIMUTemprature()} — the temperature of the inertial measurement
 * board, not of the cabin — and answers -1 when it cannot. The four values below are the ones
 * with a genuine implementation behind them: battery and range come from {@code CarBMSManager},
 * the odometer from {@code CarSensorManager}.
 *
 * <p>Nothing here is a published API. Every failure resolves to {@link Callback#onUnavailable()}
 * so a car that answers differently, or no car at all, simply shows nothing.
 */
public final class VehicleData {

    private static final String TAG = "VehicleData";

    private static final String ADAPTER_PACKAGE = "com.saicmotor.adapterservice";
    private static final String ADAPTER_SERVICE =
            "com.saicmotor.adapterservice.services.MapService";
    private static final String INTERFACE_TOKEN =
            "com.saicmotor.adapterservice.IMapService";

    private static final int TX_TOTAL_MILEAGE = 11;
    private static final int TX_ENDURANCE_MILEAGE = 43;
    private static final int TX_BATTERY_PERCENTAGE = 51;
    private static final int TX_CHARGING_STATUS = 52;

    /** A reading the car could not supply. */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /** One snapshot. Any field may be {@link #UNKNOWN} on its own. */
    public static final class State {
        /** State of charge, 0-100. */
        public final int batteryPercent;
        /** Remaining range in kilometres. */
        public final int rangeKm;
        /** Odometer in kilometres. */
        public final int odometerKm;
        public final boolean charging;

        State(int batteryPercent, int rangeKm, int odometerKm, boolean charging) {
            this.batteryPercent = batteryPercent;
            this.rangeKm = rangeKm;
            this.odometerKm = odometerKm;
            this.charging = charging;
        }

        /**
         * False when the car answered but every value was missing — nothing worth showing.
         *
         * <p>{@code charging} is left out of this test on purpose: false is what the adapter
         * returns both for "not charging" and for "could not ask", so on its own it proves
         * nothing about whether the car is listening.
         */
        public boolean hasAnything() {
            return batteryPercent != UNKNOWN || rangeKm != UNKNOWN || odometerKm != UNKNOWN;
        }
    }

    public interface Callback {
        void onState(@NonNull State state);

        /** No adapter service, binding refused, or the car rejected the calls. */
        void onUnavailable();
    }

    private VehicleData() {
    }

    /** Takes one snapshot and releases the connection immediately afterwards. */
    public static void read(@NonNull Context context, @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(ADAPTER_PACKAGE, ADAPTER_SERVICE));

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName component, IBinder binder) {
                State state = new State(
                        readMeasurement(binder, TX_BATTERY_PERCENTAGE),
                        readMeasurement(binder, TX_ENDURANCE_MILEAGE),
                        readMeasurement(binder, TX_TOTAL_MILEAGE),
                        readInt(binder, TX_CHARGING_STATUS) > 0);
                release(appContext, this);
                // Logged raw on purpose: with no adb access to the car, the log is the only
                // way to tell a value the car declined from one it genuinely reported.
                DiagnosticsLog.log(appContext, TAG, "vehicle raw: battery="
                        + state.batteryPercent + " range=" + state.rangeKm
                        + " odometer=" + state.odometerKm + " charging=" + state.charging);
                if (state.hasAnything()) {
                    callback.onState(state);
                } else {
                    callback.onUnavailable();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName component) {
            }

            @Override
            public void onNullBinding(ComponentName component) {
                release(appContext, this);
                callback.onUnavailable();
            }
        };

        try {
            if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                DiagnosticsLog.log(appContext, TAG, "adapter service not present");
                callback.onUnavailable();
            }
        } catch (SecurityException e) {
            DiagnosticsLog.log(appContext, TAG, "binding refused", e);
            callback.onUnavailable();
        }
    }

    private static void release(@NonNull Context context, @NonNull ServiceConnection conn) {
        try {
            context.unbindService(conn);
        } catch (IllegalArgumentException ignored) {
            // Already released.
        }
    }

    /**
     * A reading that cannot legitimately be zero, so zero is read as "the car did not say".
     *
     * <p>The adapter's own getters return 0 when the BMS is not connected — it is their
     * initial value, returned untouched down the exception path — and a car whose head unit
     * is running has neither zero charge, zero range nor zero kilometres on the clock.
     */
    private static int readMeasurement(@NonNull IBinder binder, int transaction) {
        int value = readInt(binder, transaction);
        return value <= 0 ? UNKNOWN : value;
    }

    /** One no-argument call returning an int, or {@link #UNKNOWN} when it does not land. */
    private static int readInt(@NonNull IBinder binder, int transaction) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(INTERFACE_TOKEN);
            binder.transact(transaction, data, reply, 0);
            reply.readException();
            return reply.readInt();
        } catch (Exception e) {
            return UNKNOWN;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
