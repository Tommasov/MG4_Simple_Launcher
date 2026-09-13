package com.tommasov.mg4simplelauncher.charging;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tommasov.mg4simplelauncher.BuildConfig;
import com.tommasov.mg4simplelauncher.R;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Full-screen charging point browser: filters and results on the left, map on the right.
 *
 * <p>The map is OpenStreetMap through osmdroid rather than Google Maps: the head unit is not
 * guaranteed to carry Play Services, and osmdroid needs neither them nor an API key.
 */
public class ChargingMapActivity extends AppCompatActivity
        implements ChargePointAdapter.Listener {

    private static final int REQUEST_LOCATION = 1;
    private static final int MAX_RESULTS = 40;
    private static final double DEFAULT_ZOOM = 11.0;
    private static final double FOCUS_ZOOM = 14.0;
    /** Ceiling for the automatic fit, so a lone result keeps some surrounding context. */
    private static final double MAX_AUTO_ZOOM = 13.5;
    private static final int MAP_PADDING_PX = 80;
    /** Roomier for a selection: the vehicle beacon is tall and would clip at the edge. */
    private static final int SELECTION_PADDING_PX = 150;
    /** Two points a few hundred metres apart would otherwise fill the screen. */
    private static final double MAX_SELECTION_ZOOM = 16.0;

    private final OpenChargeMapClient client = new OpenChargeMapClient();

    private MapView map;
    private RecyclerView list;
    private TextView status;
    private ChargePointAdapter adapter;

    private ChargingFilter filter = ChargingFilter.ALL;
    @Nullable
    private Location origin;

    /** Pins by station id, so the selected one can be swapped for its larger icon. */
    private final Map<Long, Marker> markers = new HashMap<>();
    @Nullable
    private Marker selectedMarker;
    /** Straight line from the car to the selected station. */
    @Nullable
    private Polyline link;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // osmdroid needs its cache path and a real user agent before any MapView inflates;
        // OSM tile servers reject the library's default agent outright.
        Configuration.getInstance().load(
                this, PreferenceManager.getDefaultSharedPreferences(this));
        Configuration.getInstance().setUserAgentValue(BuildConfig.APPLICATION_ID);

        setContentView(R.layout.activity_charging_map);

        map = findViewById(R.id.charging_map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(DEFAULT_ZOOM);

        status = findViewById(R.id.charging_status);
        adapter = new ChargePointAdapter(this);
        list = findViewById(R.id.charging_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        findViewById(R.id.charging_back_button).setOnClickListener(v -> finish());

        RadioGroup filters = findViewById(R.id.charging_filters);
        filters.setOnCheckedChangeListener((group, checkedId) -> {
            filter = filterFor(checkedId);
            // Each filter is a different query, not a different view of the same results.
            load();
        });

        if (!OpenChargeMapClient.hasApiKey()) {
            showStatus(R.string.charging_no_key);
            return;
        }
        requestLocationThenLoad();
    }

    private static ChargingFilter filterFor(int checkedId) {
        if (checkedId == R.id.filter_motorway) {
            return ChargingFilter.MOTORWAY;
        }
        if (checkedId == R.id.filter_supercharger) {
            return ChargingFilter.SUPERCHARGER;
        }
        if (checkedId == R.id.filter_fast) {
            return ChargingFilter.FAST;
        }
        return ChargingFilter.ALL;
    }

    private void requestLocationThenLoad() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_LOCATION);
            return;
        }
        resolveOrigin();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) {
            return;
        }
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            resolveOrigin();
        } else {
            showStatus(R.string.charging_permission_needed);
        }
    }

    /**
     * Takes the freshest cached fix from any provider. A cached position is enough here:
     * the list is ranked by distance, and waiting for a live GPS lock would leave the
     * screen empty for the first minute after a cold start.
     */
    private void resolveOrigin() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            showStatus(R.string.charging_no_location);
            return;
        }
        Location best = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location candidate = lm.getLastKnownLocation(provider);
                if (candidate == null) {
                    continue;
                }
                if (best == null || candidate.getTime() > best.getTime()) {
                    best = candidate;
                }
            }
        } catch (SecurityException e) {
            showStatus(R.string.charging_permission_needed);
            return;
        }
        if (best == null) {
            showStatus(R.string.charging_no_location);
            return;
        }
        origin = best;
        map.getController().setCenter(new GeoPoint(best.getLatitude(), best.getLongitude()));
        load();
    }

    private void load() {
        if (origin == null || !OpenChargeMapClient.hasApiKey()) {
            return;
        }
        showStatus(R.string.charging_loading);
        client.nearby(origin.getLatitude(), origin.getLongitude(), filter, MAX_RESULTS,
                new OpenChargeMapClient.Callback() {
                    @Override
                    public void onResult(@NonNull List<ChargePoint> points) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        adapter.submit(points);
                        showMarkers(points);
                        if (points.isEmpty()) {
                            showStatus(R.string.charging_empty);
                        } else {
                            showList();
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        if (isFinishing() || isDestroyed()) {
                            return;
                        }
                        showStatus(R.string.charging_error);
                    }
                });
    }

    private void showMarkers(@NonNull List<ChargePoint> points) {
        map.getOverlays().clear();
        markers.clear();
        selectedMarker = null;
        link = null;

        Drawable pin = ContextCompat.getDrawable(this, R.drawable.ic_map_marker);
        for (ChargePoint point : points) {
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(point.latitude, point.longitude));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(pin);
            marker.setTitle(point.title);
            marker.setSnippet(point.operator);
            map.getOverlays().add(marker);
            markers.put(point.id, marker);
        }
        addVehicleMarker();
        map.invalidate();
        frame(points);
    }

    /**
     * Puts the car on the map using the native navigation beacon, so every distance in the
     * list has a visible origin. Added last, so it draws on top of the station pins.
     */
    private void addVehicleMarker() {
        if (origin == null) {
            return;
        }
        Marker vehicle = new Marker(map);
        vehicle.setPosition(new GeoPoint(origin.getLatitude(), origin.getLongitude()));
        // The beacon's ellipse sits at 80% of the artwork height; that is the ground point.
        vehicle.setAnchor(Marker.ANCHOR_CENTER, 0.80f);
        vehicle.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_vehicle_position));
        vehicle.setTitle(getString(R.string.charging_you_are_here));
        map.getOverlays().add(vehicle);
    }

    /**
     * Frames the results instead of using one fixed zoom. The filters cover wildly different
     * areas — city stations sit inside a couple of kilometres while motorway chargers span a
     * hundred — so any fixed zoom leaves one of them either blank or piled into a single blob.
     */
    private void frame(@NonNull List<ChargePoint> points) {
        if (points.isEmpty() || origin == null) {
            return;
        }
        double minLat = origin.getLatitude();
        double maxLat = origin.getLatitude();
        double minLon = origin.getLongitude();
        double maxLon = origin.getLongitude();
        for (ChargePoint point : points) {
            minLat = Math.min(minLat, point.latitude);
            maxLat = Math.max(maxLat, point.latitude);
            minLon = Math.min(minLon, point.longitude);
            maxLon = Math.max(maxLon, point.longitude);
        }
        BoundingBox box = new BoundingBox(maxLat, maxLon, minLat, minLon);
        // osmdroid needs a measured view before it can work out the zoom that fits the box.
        final BoundingBox target = box;
        map.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            map.zoomToBoundingBox(target, false, MAP_PADDING_PX);
            // A single result would otherwise zoom to street level and lose all context.
            if (map.getZoomLevelDouble() > MAX_AUTO_ZOOM) {
                map.getController().setZoom(MAX_AUTO_ZOOM);
            }
        });
    }

    private void showStatus(@StringRes int messageRes) {
        status.setText(messageRes);
        status.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
    }

    private void showList() {
        status.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
    }

    /**
     * Highlights a station and ties it back to the car: the pin grows, a line joins the two,
     * the matching row lights up and the map reframes to hold both. Centring on the station
     * alone told the driver nothing about where it is relative to them.
     */
    @Override
    public void onSelect(@NonNull ChargePoint point) {
        Marker marker = markers.get(point.id);
        if (marker == null) {
            return;
        }
        if (selectedMarker != null) {
            selectedMarker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_map_marker));
        }
        marker.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_map_marker_selected));
        selectedMarker = marker;

        GeoPoint target = new GeoPoint(point.latitude, point.longitude);
        if (origin != null) {
            if (link != null) {
                map.getOverlays().remove(link);
            }
            link = new Polyline(map);
            link.setPoints(Arrays.asList(
                    new GeoPoint(origin.getLatitude(), origin.getLongitude()), target));
            link.getOutlinePaint().setColor(ContextCompat.getColor(this, R.color.accent));
            link.getOutlinePaint().setStrokeWidth(6f);
            // Behind the pins, so it never hides the points it connects.
            map.getOverlays().add(0, link);
        }

        adapter.setSelected(point);
        frameSelection(target);
        map.invalidate();
    }

    /** Frames car and station together, falling back to the station when there is no fix. */
    private void frameSelection(@NonNull GeoPoint target) {
        if (origin == null) {
            map.getController().setZoom(FOCUS_ZOOM);
            map.getController().animateTo(target);
            return;
        }
        BoundingBox box = new BoundingBox(
                Math.max(origin.getLatitude(), target.getLatitude()),
                Math.max(origin.getLongitude(), target.getLongitude()),
                Math.min(origin.getLatitude(), target.getLatitude()),
                Math.min(origin.getLongitude(), target.getLongitude()));
        map.post(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            // Not animated: an animated fit applies the zoom later, so reading it back here
            // to clamp it would see the previous value and the clamp would never fire.
            map.zoomToBoundingBox(box, false, SELECTION_PADDING_PX);
            if (map.getZoomLevelDouble() > MAX_SELECTION_ZOOM) {
                map.getController().setZoom(MAX_SELECTION_ZOOM);
            }
        });
    }

    @Override
    public void onNavigate(@NonNull ChargePoint point) {
        // A geo: intent lets whatever navigation app the head unit ships handle the route.
        Uri uri = Uri.parse("geo:" + point.latitude + "," + point.longitude
                + "?q=" + point.latitude + "," + point.longitude
                + "(" + Uri.encode(point.title) + ")");
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.charging_no_navigation, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        map.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.cancel();
        map.onDetach();
    }
}
