package com.tommasov.mg4simplelauncher.charging;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.tommasov.mg4simplelauncher.PreferencesManager;
import com.tommasov.mg4simplelauncher.Dialogs;
import com.tommasov.mg4simplelauncher.R;
import com.tommasov.mg4simplelauncher.vehicle.VehicleData;

import java.util.ArrayList;
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

    /**
     * How far the car must travel before the card asks Open Charge Map again.
     *
     * <p>Ten kilometres, not a stopwatch: the list only changes because the car moved, and
     * Open Charge Map asks callers not to poll — a launcher that refreshed on a timer would
     * be doing exactly that, sitting still in a car park. At motorway speed this works out
     * at a request every four or five minutes, and in town at almost none.
     */
    private static final float SEARCH_AGAIN_METRES = 10_000f;
    /** Floor under the search rate, for the stretch of motorway that covers 10 km quickly. */
    private static final long SEARCH_AGAIN_MIN_INTERVAL_MS = 120_000;
    /**
     * How far the car must travel before the distances on screen are redrawn. This costs
     * nothing but arithmetic — the stations are already in hand, and their coordinates do
     * not change — so it happens far more often than a search, and it is what makes the
     * kilometres actually count down as you drive rather than jump every few minutes.
     */
    private static final float REDRAW_METRES = 200f;
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
    private final View[] extras = new View[SUMMARY_COUNT];
    private final ImageView[] arrivalIcons = new ImageView[SUMMARY_COUNT];
    private final TextView[] arrivals = new TextView[SUMMARY_COUNT];
    private final View arrivalNote;

    /** Set once the card has shown results, so returning to the page costs no request. */
    private boolean loaded;
    /** The last list drawn, kept so the vehicle reading can redraw it when it lands. */
    private List<ChargePoint> lastPoints = Collections.emptyList();
    @Nullable
    private ArrivalCharge arrival;
    /** Where the car was when Open Charge Map was last asked. */
    @Nullable
    private Location searchOrigin;
    /** Where the car is now: the distances on screen are measured from here. */
    @Nullable
    private Location currentOrigin;
    /** Where the card last redrew, so small movements do not redraw six rows a second. */
    @Nullable
    private Location redrawOrigin;
    private long lastSearchAt;

    public ChargingCardBinder(@NonNull View page) {
        card = page.findViewById(R.id.charging_card);
        results = page.findViewById(R.id.charging_card_results);
        status = page.findViewById(R.id.charging_card_status);
        groupLabel = page.findViewById(R.id.charge_group_label);
        arrivalNote = page.findViewById(R.id.charging_arrival_note);

        int[] rowIds = {R.id.charge_row_0, R.id.charge_row_1, R.id.charge_row_2,
                R.id.charge_row_3, R.id.charge_row_4, R.id.charge_row_5};
        for (int i = 0; i < SUMMARY_COUNT; i++) {
            View row = page.findViewById(rowIds[i]);
            names[i] = row.findViewById(R.id.summary_name);
            arrivalIcons[i] = row.findViewById(R.id.summary_arrival_icon);
            arrivals[i] = row.findViewById(R.id.summary_arrival);
            details[i] = row.findViewById(R.id.summary_detail);
            extras[i] = row.findViewById(R.id.summary_extras);
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

    /**
     * Called whenever the page comes to the front. The first time it loads the card; after
     * that it only picks the position watch back up, so the driver does not watch the list
     * blank and refill every time they swipe past. The watch decides on its own whether the
     * car has moved far enough to be worth a new search.
     */
    public void start() {
        if (!loaded) {
            load();
            return;
        }
        // Already has a list on screen: pick the watch back up and take a fresh vehicle
        // reading, but leave the stations showing. Blanking a card the driver can already
        // read, to refill it with almost the same six names, is a worse answer than a list
        // that is a few kilometres stale for the second it takes the first fix to arrive.
        readVehicle();
        follow(card.getContext());
    }

    /** Page no longer in front: stop listening for positions. */
    public void stop() {
        locationResolver.cancel();
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
        // Forget where the last search was taken from, or the movement test would swallow
        // the very request the driver just asked for: standing still, the car has not
        // travelled the ten kilometres that normally earn a new one.
        searchOrigin = null;
        redrawOrigin = null;
        load();
    }

    /**
     * Asks the car for charge and range, and redraws when it answers.
     *
     * <p>Runs alongside the station search rather than before it: the list is worth showing
     * whether or not the car is talking, and on a trim without the adapter this simply never
     * comes back. Each pass takes a fresh reading — between one visit to this page and the
     * next the driver has been driving, which is precisely what changes the answer.
     */
    private void readVehicle() {
        VehicleData.read(card.getContext(), new VehicleData.Callback() {
            @Override
            public void onState(@NonNull VehicleData.State state) {
                arrival = ArrivalCharge.from(state);
                arrivalNote.setVisibility(arrival == null ? View.GONE : View.VISIBLE);
                if (arrival != null && !lastPoints.isEmpty()) {
                    bind(lastPoints);
                }
            }

            @Override
            public void onUnavailable() {
                arrival = null;
                arrivalNote.setVisibility(View.GONE);
            }
        });
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
        Dialogs.builder(context)
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
        readVehicle();
        showStatus(R.string.charging_no_location);
        follow(context);
    }

    /**
     * Follows the car, rather than taking one reading.
     *
     * <p>With the cache empty the card used to sit on "waiting for a position" for good,
     * even once the map screen had found one; and a single fix is only right for the moment
     * the page opened. The resolver is cancelled when the page goes away, so nothing keeps
     * listening behind the driver's back.
     */
    private void follow(@NonNull Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationResolver.watch(context, new LocationResolver.Callback() {
            @Override
            public void onLocation(@NonNull Location fix) {
                onMoved(context, fix);
            }

            @Override
            public void onUnavailable() {
                // Only worth saying on a card that has nothing else to show.
                if (lastPoints.isEmpty()) {
                    showStatus(R.string.charging_location_failed);
                }
            }
        });
    }

    /**
     * Every fix while the page is in front of the driver, which is several a second on a
     * good signal. Three outcomes, cheapest first: usually nothing, sometimes a redraw with
     * the new distances, and occasionally a fresh search.
     */
    private void onMoved(@NonNull Context context, @NonNull Location fix) {
        currentOrigin = fix;
        if (searchOrigin == null) {
            search(context, fix);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (fix.distanceTo(searchOrigin) >= SEARCH_AGAIN_METRES
                && now - lastSearchAt >= SEARCH_AGAIN_MIN_INTERVAL_MS) {
            search(context, fix);
            return;
        }
        if (redrawOrigin == null || fix.distanceTo(redrawOrigin) >= REDRAW_METRES) {
            redrawOrigin = fix;
            if (!lastPoints.isEmpty()) {
                bind(lastPoints);
            }
        }
    }

    /** Asks Open Charge Map from here, and remembers where "here" was. */
    private void search(@NonNull Context context, @NonNull Location origin) {
        searchOrigin = origin;
        redrawOrigin = origin;
        lastSearchAt = SystemClock.elapsedRealtime();
        loadAround(context, origin);
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

    /**
     * Distance to a station from where the car is now, falling back to the figure Open
     * Charge Map returned when it was asked. Between two searches these are the numbers that
     * move: the stations stand still, the car does not.
     */
    private double distanceKm(@NonNull ChargePoint point) {
        if (currentOrigin == null) {
            return point.distanceKm;
        }
        float[] out = new float[1];
        Location.distanceBetween(currentOrigin.getLatitude(), currentOrigin.getLongitude(),
                point.latitude, point.longitude, out);
        return out[0] / 1000d;
    }

    private void bind(@NonNull List<ChargePoint> points) {
        lastPoints = points;
        Context context = card.getContext();
        // Re-sorted rather than kept in the order Open Charge Map sent: after a few
        // kilometres two stations that were neighbours have swapped places, and a list that
        // claims to be the nearest six should say so in order.
        List<ChargePoint> ordered = new ArrayList<>(points);
        Collections.sort(ordered, (a, b) -> Double.compare(distanceKm(a), distanceKm(b)));
        points = ordered;
        for (int i = 0; i < SUMMARY_COUNT; i++) {
            boolean present = i < points.size();
            names[i].setVisibility(present ? View.VISIBLE : View.GONE);
            details[i].setVisibility(present ? View.VISIBLE : View.GONE);
            extras[i].setVisibility(View.GONE);
            if (!present) {
                continue;
            }
            ChargePoint point = points.get(i);
            names[i].setText(point.title);
            double distanceKm = distanceKm(point);
            details[i].setText(summarise(context, point, distanceKm));
            bindArrival(i, point, distanceKm);
            extras[i].setVisibility(
                    ChargeExtras.bind(extras[i], point) ? View.VISIBLE : View.GONE);
        }
        status.setVisibility(View.GONE);
        results.setVisibility(View.VISIBLE);
    }

    /**
     * Charge expected on arrival, at the end of the line. Last on purpose: the figures before
     * it describe the station, this one describes whether the driver can get there.
     */
    private void bindArrival(int slot, @NonNull ChargePoint point, double distanceKm) {
        boolean show = arrival != null && (currentOrigin != null || point.hasDistance());
        arrivalIcons[slot].setVisibility(show ? View.VISIBLE : View.GONE);
        arrivals[slot].setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            return;
        }
        Context context = card.getContext();
        int percent = arrival.percentOnArrival(distanceKm);
        int colour = ContextCompat.getColor(context, ChargePointAdapter.colourFor(percent));
        arrivals[slot].setText(context.getString(R.string.charging_arrival_summary, percent));
        arrivals[slot].setTextColor(colour);
        arrivalIcons[slot].setImageTintList(ColorStateList.valueOf(colour));
    }

    /** "7.6 km · 300 kW". The operator is omitted: the heading above already says it. */
    private static String summarise(@NonNull Context context, @NonNull ChargePoint point,
                                    double distanceKm) {
        StringBuilder sb = new StringBuilder();
        if (distanceKm > 0 || point.hasDistance()) {
            sb.append(context.getString(R.string.charging_distance_km, distanceKm));
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
