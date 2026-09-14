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
 * Reads sensor values from the vehicle through SAIC's adapter service.
 *
 * <p>Same door as {@link com.tommasov.mg4simplelauncher.charging.FactoryNavigator}, different
 * room: the adapter serves {@code IMapService} so the factory navigator can ask the car about
 * itself, and that service is exported without a permission, so the launcher can ask too.
 *
 * <p>Descriptor and transaction codes were read out of the firmware with dexdump. These calls
 * are far simpler than sending a destination — no arguments, an int back — so the parcel is
 * written by hand rather than regenerating the whole interface.
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

    private static final int TX_IN_CAR_PM25 = 62;
    private static final int TX_OUTSIDE_PM25 = 61;
    private static final int TX_TEMPERATURE = 67;

    /** A reading the car could not supply. */
    public static final int UNKNOWN = Integer.MIN_VALUE;

    /** One snapshot. Any field may be {@link #UNKNOWN} on its own. */
    public static final class Air {
        public final int inCarPm25;
        public final int outsidePm25;
        public final int temperature;

        Air(int inCarPm25, int outsidePm25, int temperature) {
            this.inCarPm25 = inCarPm25;
            this.outsidePm25 = outsidePm25;
            this.temperature = temperature;
        }

        /** False when the car answered but every value was missing — nothing worth showing. */
        public boolean hasAnything() {
            return inCarPm25 != UNKNOWN || outsidePm25 != UNKNOWN || temperature != UNKNOWN;
        }
    }

    public interface Callback {
        void onAir(@NonNull Air air);

        /** No adapter service, binding refused, or the car rejected the calls. */
        void onUnavailable();
    }

    private VehicleData() {
    }

    /** Takes one snapshot and releases the connection immediately afterwards. */
    public static void readAir(@NonNull Context context, @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(ADAPTER_PACKAGE, ADAPTER_SERVICE));

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName component, IBinder binder) {
                Air air = new Air(
                        readInt(binder, TX_IN_CAR_PM25),
                        readInt(binder, TX_OUTSIDE_PM25),
                        readInt(binder, TX_TEMPERATURE));
                release(appContext, this);
                // Logged raw on purpose: the units are not documented anywhere, and the first
                // reading from a real car is what tells us how to label them.
                DiagnosticsLog.log(appContext, TAG, "air raw: inCar=" + air.inCarPm25
                        + " outside=" + air.outsidePm25 + " temp=" + air.temperature);
                if (air.hasAnything()) {
                    callback.onAir(air);
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
