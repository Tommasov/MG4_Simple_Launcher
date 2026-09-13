package com.tommasov.mg4simplelauncher.charging;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;

/**
 * Sends a destination to the vehicle's factory navigator.
 *
 * <p>The stock Telenav app accepts no {@code geo:} intent — its manifest declares only its
 * own private actions — so the supported route is the one the car's own EV route planner
 * uses: bind SAIC's adapter service and hand it the stop over Binder.
 *
 * <p>The wire format below was read out of the firmware's own binaries rather than guessed:
 * {@code IGeneralService$Stub$Proxy.startNavFromEVRout} writes the interface token, then two
 * typed lists of {@code EVRoutPoiInfo}, and transacts on code 48; {@code
 * EVRoutPoiInfo.writeToParcel} writes latitude, longitude and name in that order and
 * nothing else. The parcel is written by hand instead of through a regenerated AIDL: only
 * these three values and one transaction matter, and reproducing the whole interface would
 * mean mirroring several dozen methods in their exact declaration order just to keep the
 * transaction numbering aligned.
 *
 * <p>None of this is a published API. It can disappear with a firmware update, so every
 * failure path falls back to the caller rather than surfacing an error.
 */
final class FactoryNavigator {

    private static final String TAG = "FactoryNavigator";

    private static final String ADAPTER_PACKAGE = "com.saicmotor.adapterservice";
    private static final String ADAPTER_SERVICE =
            "com.saicmotor.adapterservice.services.GeneralService";
    private static final String INTERFACE_TOKEN =
            "com.saicmotor.adapterservice.IGeneralService";
    /** {@code startNavFromEVRout(List, List)} sits at this transaction code. */
    private static final int TRANSACTION_START_NAV_FROM_EV_ROUTE = 48;

    interface Callback {
        /** The stop reached the navigator. */
        void onSent();

        /** The service is absent, refused the binding, or rejected the call. */
        void onUnavailable();
    }

    private FactoryNavigator() {
    }

    /**
     * Asks the factory navigator to route to a point. Binding is asynchronous, so the result
     * arrives through {@code callback}; the connection is dropped as soon as the call lands.
     */
    static void sendDestination(@NonNull Context context, double latitude, double longitude,
                                @NonNull String name, @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(ADAPTER_PACKAGE, ADAPTER_SERVICE));

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName component, IBinder binder) {
                boolean sent = transact(binder, latitude, longitude, name);
                try {
                    appContext.unbindService(this);
                } catch (IllegalArgumentException ignored) {
                    // Already gone; nothing to release.
                }
                if (sent) {
                    callback.onSent();
                } else {
                    callback.onUnavailable();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName component) {
                // Nothing to do: the call is made and released inside onServiceConnected.
            }

            @Override
            public void onNullBinding(ComponentName component) {
                try {
                    appContext.unbindService(this);
                } catch (IllegalArgumentException ignored) {
                    // Already gone.
                }
                callback.onUnavailable();
            }
        };

        try {
            if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                // No such service: any head unit that is not this vehicle's, emulator included.
                callback.onUnavailable();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "adapter service refused the binding", e);
            callback.onUnavailable();
        }
    }

    private static boolean transact(@NonNull IBinder binder, double latitude, double longitude,
                                    @NonNull String name) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(INTERFACE_TOKEN);

            // First typed list: the stops. Parcel.writeTypedList's format is the element
            // count, then per element a 1 marker followed by the element's own fields.
            data.writeInt(1);
            data.writeInt(1);
            data.writeDouble(latitude);
            data.writeDouble(longitude);
            data.writeString(name);

            // Second list: the planner passes two, and which is which is not recoverable
            // from the binaries. An empty list is the harmless choice — worst case the
            // navigator ignores it.
            data.writeInt(0);

            binder.transact(TRANSACTION_START_NAV_FROM_EV_ROUTE, data, reply, 0);
            // Throws if the far side reported an exception, so a rejected call is not
            // mistaken for a delivered one.
            reply.readException();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "sending the destination failed", e);
            return false;
        } finally {
            reply.recycle();
            data.recycle();
        }
    }
}
