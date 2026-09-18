package com.tommasov.mg4simplelauncher.charging;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import androidx.annotation.NonNull;

import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;

/**
 * Sends a destination to the vehicle's factory navigator.
 *
 * <p>The stock Telenav app accepts no {@code geo:} intent — its manifest declares only its
 * own private actions — so the supported route is the one the car's own EV route planner
 * uses: bind SAIC's adapter service and hand it the stop over Binder.
 *
 * <p>The call used is the one behind the voice assistant's "take me to" — {@code
 * IVoiceVuiService.goToPoi} — rather than the EV planner's {@code startNavFromEVRout}. The
 * planner's entry point accepts the call without complaint and then does nothing, which is
 * worse than an error: it reports success while the driver sees no route. goToPoi is the
 * path that actually starts one.
 *
 * <p>The wire format was read out of the firmware with dexdump: interface token, three
 * strings, then latitude and longitude, on transaction 17. Written by hand rather than
 * through a regenerated AIDL, because reproducing the whole interface would mean mirroring
 * dozens of methods in their exact declaration order just to keep transaction numbers
 * aligned, for the sake of one call.
 *
 * <p>The adapter hands the call to whichever navigator registered as its listener, so what
 * the three strings mean is settled on the far side. In Telenav's handler they fill an
 * {@code Entity}: an id looked up in its own database, a name, and an address. The id is
 * left empty because one we invent matches nothing in that database — an earlier attempt
 * that put the station name there had the destination filed under "work".
 *
 * <p>Confirmed on the vehicle: the stop reaches Telenav with its name and address, and is
 * accepted both as a waypoint on a running route and as a fresh destination. Trims without
 * the factory navigator have nothing listening, which is what {@link #isNavigationAvailable}
 * is for.
 *
 * <p>None of this is a published API. It can disappear with a firmware update, so every
 * failure path falls back to the caller rather than surfacing an error.
 */
final class FactoryNavigator {

    private static final String TAG = "FactoryNavigator";

    private static final String ADAPTER_PACKAGE = "com.saicmotor.adapterservice";
    private static final String ADAPTER_SERVICE =
            "com.saicmotor.adapterservice.services.VoiceVuiService";
    private static final String INTERFACE_TOKEN =
            "com.saicmotor.adapterservice.IVoiceVuiService";
    /**
     * Navigation apps shipped on SAIC head units, by market: Telenav here in Europe, iGO in
     * Hong Kong, SAIC's own in Israel. Only the trims with the full infotainment package
     * carry one at all, which is why presence is checked rather than assumed.
     */
    static final String[] FACTORY_NAVIGATORS = {
            "com.telenav.app.arp",
            "com.nng.igo.primong",
            "com.saicmotor.navigation",
    };

    /** {@code goToPoi(String, String, String, double, double)} sits at this code. */
    private static final int TRANSACTION_GO_TO_POI = 17;

    interface Callback {
        /** The stop reached the navigator. */
        void onSent();

        /** The service is absent, refused the binding, or rejected the call. */
        void onUnavailable();
    }

    private FactoryNavigator() {
    }

    /**
     * Whether anything on this vehicle can accept a destination.
     *
     * <p>Asked as a capability rather than derived from the model: the base MG4 ships without
     * a navigator, but {@code build.prop} names the head unit, not the trim, so the model
     * cannot answer this. Presence can. It also gets the case a trim check would get wrong —
     * an owner who installed a map app of their own.
     */
    public static boolean isNavigationAvailable(@NonNull Context context) {
        if (hasFactoryNavigator(context)) {
            return true;
        }
        Intent geo = new Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=0,0"));
        return geo.resolveActivity(context.getPackageManager()) != null;
    }

    /**
     * Whether one of the vehicle's own navigators is installed.
     *
     * <p>Asked before the adapter service is, because the adapter is no evidence either way:
     * it ships on every trim, including the ones sold without a navigator. Binding to it
     * succeeds there and {@code goToPoi} returns without complaint — it simply has nothing to
     * hand the destination to. Treating that as success swallowed the stop on those cars:
     * the launcher reported the route as sent, never reached the fallback, and the driver saw
     * a tap that did nothing at all.
     */
    public static boolean hasFactoryNavigator(@NonNull Context context) {
        PackageManager packages = context.getPackageManager();
        for (String navigator : FACTORY_NAVIGATORS) {
            if (packages.getLaunchIntentForPackage(navigator) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Asks the factory navigator to route to a point. Binding is asynchronous, so the result
     * arrives through {@code callback}; the connection is dropped as soon as the call lands.
     */
    static void sendDestination(@NonNull Context context, double latitude, double longitude,
                                @NonNull String name, @NonNull String address,
                                @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(ADAPTER_PACKAGE, ADAPTER_SERVICE));

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName component, IBinder binder) {
                boolean sent = transact(binder, latitude, longitude, name, address);
                try {
                    appContext.unbindService(this);
                } catch (IllegalArgumentException ignored) {
                    // Already gone; nothing to release.
                }
                // Worded for what it actually proves: the call landed without an exception.
                // The previous entry point, startNavFromEVRout, accepted it just as quietly
                // and started no route, so "sent" is not the same as "routing".
                DiagnosticsLog.log(appContext, TAG,
                        sent ? "goToPoi accepted the destination"
                             : "goToPoi refused the destination");
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
            DiagnosticsLog.log(appContext, TAG, "binding " + ADAPTER_SERVICE);
            if (!appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)) {
                DiagnosticsLog.log(appContext, TAG, "adapter service not present");
                // No such service: any head unit that is not this vehicle's, emulator included.
                callback.onUnavailable();
            }
        } catch (SecurityException e) {
            Log.w(TAG, "adapter service refused the binding", e);
            callback.onUnavailable();
        }
    }

    private static boolean transact(@NonNull IBinder binder, double latitude, double longitude,
                                    @NonNull String name, @NonNull String address) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(INTERFACE_TOKEN);

            // goToPoi(entity_id, addressName, addressDesc, lat, lon). The names come from
            // the adapter's own log line, and Telenav's handler shows what it does with
            // them: entity_id is looked up as an id in its own POI database, addressName
            // becomes the destination's name, addressDesc its address. An id we invent is
            // not an id it knows, so that field goes empty — the coordinates are what the
            // route is built from, and Telenav skips every string that is empty.
            data.writeString("");
            data.writeString(name);
            data.writeString(address);
            data.writeDouble(latitude);
            data.writeDouble(longitude);

            binder.transact(TRANSACTION_GO_TO_POI, data, reply, 0);
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
