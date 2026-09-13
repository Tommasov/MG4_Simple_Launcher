package com.tommasov.mg4simplelauncher.charging;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.tommasov.mg4simplelauncher.R;

import java.util.List;

/**
 * Drives the charging card on carousel page 3: the nearest few stations, and a tap that
 * opens {@link ChargingMapActivity}.
 *
 * <p>Deliberately separate from the fragment that owns the page. That page refreshes its
 * readings every few seconds, and Open Charge Map bans callers who poll; keeping this out
 * of the fragment makes it hard to accidentally attach a network call to that ticker. The
 * card loads once per visit and then leaves the API alone.
 */
public class ChargingCardBinder {

    private static final int SUMMARY_COUNT = 3;

    private final OpenChargeMapClient client = new OpenChargeMapClient();
    private final View card;
    private final View results;
    private final TextView status;
    private final TextView[] names = new TextView[SUMMARY_COUNT];
    private final TextView[] details = new TextView[SUMMARY_COUNT];

    /** Set once the card has shown results, so returning to the page costs no request. */
    private boolean loaded;

    public ChargingCardBinder(@NonNull View page) {
        card = page.findViewById(R.id.charging_card);
        results = page.findViewById(R.id.charging_card_results);
        status = page.findViewById(R.id.charging_card_status);

        int[] rowIds = {R.id.charge_row_0, R.id.charge_row_1, R.id.charge_row_2};
        for (int i = 0; i < SUMMARY_COUNT; i++) {
            View row = page.findViewById(rowIds[i]);
            names[i] = row.findViewById(R.id.summary_name);
            details[i] = row.findViewById(R.id.summary_detail);
        }

        card.setOnClickListener(v -> {
            Context context = v.getContext();
            // The full screen owns the permission prompt, so the card never has to ask.
            context.startActivity(new Intent(context, ChargingMapActivity.class));
        });
    }

    /** Loads the summary the first time the page becomes visible; a no-op afterwards. */
    public void loadOnce() {
        if (loaded) {
            return;
        }
        Context context = card.getContext();
        if (!OpenChargeMapClient.hasApiKey()) {
            showStatus(R.string.charging_no_key);
            return;
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            showStatus(R.string.charging_permission_needed);
            return;
        }
        Location origin = lastKnownLocation(context);
        if (origin == null) {
            showStatus(R.string.charging_no_location);
            return;
        }
        showStatus(R.string.charging_loading);
        client.nearby(origin.getLatitude(), origin.getLongitude(), ChargingFilter.ALL,
                SUMMARY_COUNT, new OpenChargeMapClient.Callback() {
                    @Override
                    public void onResult(@NonNull List<ChargePoint> points) {
                        if (points.isEmpty()) {
                            showStatus(R.string.charging_empty);
                            return;
                        }
                        bind(points);
                        loaded = true;
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        showStatus(R.string.charging_error);
                    }
                });
    }

    private void bind(@NonNull List<ChargePoint> points) {
        Context context = card.getContext();
        for (int i = 0; i < SUMMARY_COUNT; i++) {
            boolean present = i < points.size();
            names[i].setVisibility(present ? View.VISIBLE : View.GONE);
            details[i].setVisibility(present ? View.VISIBLE : View.GONE);
            if (!present) {
                continue;
            }
            ChargePoint point = points.get(i);
            names[i].setText(point.title);
            details[i].setText(summarise(context, point));
        }
        status.setVisibility(View.GONE);
        results.setVisibility(View.VISIBLE);
    }

    /** "7.6 km · 300 kW · Free To X", skipping whatever OCM does not know. */
    private static String summarise(@NonNull Context context, @NonNull ChargePoint point) {
        StringBuilder sb = new StringBuilder();
        if (point.hasDistance()) {
            sb.append(context.getString(R.string.charging_distance_km, point.distanceKm));
        }
        if (point.maxPowerKw > 0) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(context.getString(R.string.charging_power_kw, point.maxPowerKw));
        }
        if (!point.operator.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(point.operator);
        }
        return sb.toString();
    }

    @Nullable
    private static Location lastKnownLocation(@NonNull Context context) {
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

    private void showStatus(@StringRes int messageRes) {
        status.setText(messageRes);
        status.setVisibility(View.VISIBLE);
        results.setVisibility(View.GONE);
    }

    /** Drops any in-flight lookup when the page goes away. */
    public void cancel() {
        client.cancel();
    }
}
