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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drives the charging card on carousel page 3: the nearest motorway and Supercharger
 * sites, and a tap that opens {@link ChargingMapActivity}.
 *
 * <p>One query per column rather than one for the lot, because a plain "nearest stations"
 * search answers with the wrong thing: around Florence the closest few dozen are all city
 * chargers, and neither the motorway network nor the Superchargers — the two you plan a long
 * drive around — appear at all. They run one after the other, not in parallel, to keep a
 * single connection open at a time.
 *
 * <p>Deliberately separate from the fragment that owns the page. That page refreshes its
 * readings every few seconds, and Open Charge Map bans callers who poll; keeping this out
 * of the fragment makes it hard to accidentally attach a network call to that ticker. The
 * card loads once per visit and then leaves the API alone.
 */
public class ChargingCardBinder {

    /** The three columns, in the order they are drawn. */
    private static final ChargingFilter[] GROUPS = {
            ChargingFilter.MOTORWAY, ChargingFilter.SUPERCHARGER, ChargingFilter.FAST};
    /** Four stations under each heading. */
    private static final int PER_GROUP = 4;
    private static final int SUMMARY_COUNT = PER_GROUP * GROUPS.length;

    private final OpenChargeMapClient client = new OpenChargeMapClient();
    private final LocationResolver locationResolver = new LocationResolver();
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

        int[] rowIds = {R.id.charge_row_0, R.id.charge_row_1, R.id.charge_row_2,
                R.id.charge_row_3, R.id.charge_row_4, R.id.charge_row_5,
                R.id.charge_row_6, R.id.charge_row_7, R.id.charge_row_8,
                R.id.charge_row_9, R.id.charge_row_10, R.id.charge_row_11};
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
            // Worded as an instruction, not a diagnosis: tapping the card opens the screen
            // that asks for the permission, so the way out is the card itself.
            showStatus(R.string.charging_permission_card);
            return;
        }
        showStatus(R.string.charging_no_location);
        // Waiting for a real fix, not just reading the cache: with the cache empty the card
        // used to sit on "waiting for a position" for good, even once the map screen had
        // found one. The resolver is cancelled when the page goes away, so nothing keeps
        // listening behind the driver's back.
        locationResolver.resolve(context, new LocationResolver.Callback() {
            @Override
            public void onLocation(@NonNull Location origin) {
                loadAround(context, origin);
            }

            @Override
            public void onUnavailable() {
                showStatus(R.string.charging_location_failed);
            }
        });
    }

    private void loadAround(@NonNull Context context, @NonNull Location origin) {
        showStatus(R.string.charging_loading);
        fetchGroup(origin, 0, new ArrayList<>());
    }

    /**
     * Walks the three queries one after another, carrying the results collected so far.
     *
     * <p>One at a time rather than three at once: this runs on a head unit sharing a phone's
     * hotspot as often as not, and Open Charge Map is being asked a favour, not paid for a
     * service. Recursion rather than a loop because each call only starts when the one
     * before it has answered.
     */
    private void fetchGroup(@NonNull Location origin, int index,
                            @NonNull List<List<ChargePoint>> collected) {
        if (index == GROUPS.length) {
            boolean anything = false;
            for (List<ChargePoint> group : collected) {
                anything |= !group.isEmpty();
            }
            if (!anything) {
                showStatus(R.string.charging_empty);
                return;
            }
            bind(collected);
            loaded = true;
            return;
        }
        fetch(origin, GROUPS[index], points -> {
            collected.add(points);
            fetchGroup(origin, index + 1, collected);
        });
    }

    /** Runs one query, handing back an empty list rather than failing the whole card. */
    private void fetch(@NonNull Location origin, @NonNull ChargingFilter filter,
                       @NonNull Consumer<List<ChargePoint>> then) {
        client.nearby(origin.getLatitude(), origin.getLongitude(), filter, PER_GROUP,
                new OpenChargeMapClient.Callback() {
                    @Override
                    public void onResult(@NonNull List<ChargePoint> points) {
                        then.accept(points);
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        // One network missing is not worth blanking the other.
                        then.accept(Collections.emptyList());
                    }
                });
    }

    private void bind(@NonNull List<List<ChargePoint>> groups) {
        Context context = card.getContext();
        List<ChargePoint> slots = new ArrayList<>();
        // Fixed slots: each heading owns its own block of four, so a short group leaves a
        // gap at the bottom of its column instead of pulling the next one up into it.
        for (List<ChargePoint> group : groups) {
            for (int i = 0; i < PER_GROUP; i++) {
                slots.add(i < group.size() ? group.get(i) : null);
            }
        }
        for (int i = 0; i < SUMMARY_COUNT; i++) {
            boolean present = slots.get(i) != null;
            names[i].setVisibility(present ? View.VISIBLE : View.GONE);
            details[i].setVisibility(present ? View.VISIBLE : View.GONE);
            if (!present) {
                continue;
            }
            ChargePoint point = slots.get(i);
            names[i].setText(point.title);
            details[i].setText(summarise(context, point));
        }
        status.setVisibility(View.GONE);
        results.setVisibility(View.VISIBLE);
    }

    /** "7.6 km · 300 kW". The operator is omitted: the group heading already says it. */
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
        return sb.toString();
    }

    private void showStatus(@StringRes int messageRes) {
        status.setText(messageRes);
        status.setVisibility(View.VISIBLE);
        results.setVisibility(View.GONE);
    }

    /** Drops any in-flight lookup and stops listening for a position. */
    public void cancel() {
        locationResolver.cancel();
        client.cancel();
    }
}
