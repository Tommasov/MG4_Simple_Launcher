package com.tommasov.mg4simplelauncher.charging;

import android.Manifest;
import android.app.AlertDialog;
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

import com.tommasov.mg4simplelauncher.PreferencesManager;
import com.tommasov.mg4simplelauncher.R;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drives the charging card on carousel page 3: the nearest motorway and Supercharger
 * sites, and a tap that opens {@link ChargingMapActivity}.
 *
 * <p>One network at a time, chosen by the driver from the gear on the card. A plain "nearest
 * stations" search answers with the wrong thing on a motorway — around Florence the closest
 * few dozen are all city chargers — and which network matters depends on the journey, which
 * is not a decision we can make once for everyone.
 *
 * <p>Deliberately separate from the fragment that owns the page. That page refreshes its
 * readings every few seconds, and Open Charge Map bans callers who poll; keeping this out
 * of the fragment makes it hard to accidentally attach a network call to that ticker. The
 * card loads once per visit and then leaves the API alone.
 */
public class ChargingCardBinder {

    /** What the card lists. Six fills its height at the size the entries are set in. */
    private static final int SUMMARY_COUNT = 6;
    /** The networks the gear offers, in the order the dialog lists them. */
    private static final ChargingFilter[] CHOICES = {
            ChargingFilter.ALL, ChargingFilter.MOTORWAY,
            ChargingFilter.SUPERCHARGER, ChargingFilter.FAST};

    private final OpenChargeMapClient client = new OpenChargeMapClient();
    private final LocationResolver locationResolver = new LocationResolver();
    private final View card;
    private final View results;
    private final TextView status;
    private final TextView groupLabel;
    private final TextView[] names = new TextView[SUMMARY_COUNT];
    private final TextView[] details = new TextView[SUMMARY_COUNT];

    /** Set once the card has shown results, so returning to the page costs no request. */
    private boolean loaded;

    public ChargingCardBinder(@NonNull View page) {
        card = page.findViewById(R.id.charging_card);
        results = page.findViewById(R.id.charging_card_results);
        status = page.findViewById(R.id.charging_card_status);
        groupLabel = page.findViewById(R.id.charge_group_label);

        int[] rowIds = {R.id.charge_row_0, R.id.charge_row_1, R.id.charge_row_2,
                R.id.charge_row_3, R.id.charge_row_4, R.id.charge_row_5};
        for (int i = 0; i < SUMMARY_COUNT; i++) {
            View row = page.findViewById(rowIds[i]);
            names[i] = row.findViewById(R.id.summary_name);
            details[i] = row.findViewById(R.id.summary_detail);
        }

        card.setOnClickListener(v -> {
            Context context = v.getContext();
            // The full screen owns the permission prompt, so the card never has to ask.
            // It opens on the network the card is showing: the tap is a request to see more
            // of what is on the card, not to start the search over.
            Intent intent = new Intent(context, ChargingMapActivity.class)
                    .putExtra(ChargingMapActivity.EXTRA_FILTER,
                            new PreferencesManager(context).getChargingCardFilter().name());
            context.startActivity(intent);
        });
        page.findViewById(R.id.charging_card_refresh).setOnClickListener(v -> reload());
        page.findViewById(R.id.charging_card_options).setOnClickListener(v -> chooseNetwork());
    }

    /** Loads the summary the first time the page becomes visible; a no-op afterwards. */
    public void loadOnce() {
        if (loaded) {
            return;
        }
        load();
    }

    /**
     * Reads the list again now, because someone asked. The once-per-visit rule exists to keep
     * the launcher from polling Open Charge Map on a timer, and a button somebody pressed is
     * not polling; waiting for the next visit to see a station that has just come into range
     * is the frustration this removes.
     */
    public void reload() {
        client.cancel();
        loaded = false;
        load();
    }

    /** Lets the driver pick which network the card lists, and reloads it on the spot. */
    private void chooseNetwork() {
        Context context = card.getContext();
        PreferencesManager preferences = new PreferencesManager(context);
        ChargingFilter current = preferences.getChargingCardFilter();
        CharSequence[] labels = new CharSequence[CHOICES.length];
        int checked = 0;
        for (int i = 0; i < CHOICES.length; i++) {
            labels[i] = context.getString(CHOICES[i].labelRes);
            if (CHOICES[i] == current) {
                checked = i;
            }
        }
        new AlertDialog.Builder(context)
                .setTitle(R.string.charging_card_options)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    preferences.setChargingCardFilter(CHOICES[which]);
                    dialog.dismiss();
                    reload();
                })
                .show();
    }

    private void load() {
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
        ChargingFilter filter = new PreferencesManager(context).getChargingCardFilter();
        fetch(origin, filter, points -> {
            if (points.isEmpty()) {
                showStatus(R.string.charging_empty);
                return;
            }
            groupLabel.setText(filter.labelRes);
            bind(points);
            loaded = true;
        });
    }

    /** Runs one query, handing back an empty list rather than failing the whole card. */
    private void fetch(@NonNull Location origin, @NonNull ChargingFilter filter,
                       @NonNull Consumer<List<ChargePoint>> then) {
        client.nearby(origin.getLatitude(), origin.getLongitude(), filter, SUMMARY_COUNT,
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

    /** "7.6 km · 300 kW". The operator is omitted: the heading above already says it. */
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
