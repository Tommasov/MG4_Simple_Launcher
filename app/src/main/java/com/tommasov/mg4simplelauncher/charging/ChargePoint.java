package com.tommasov.mg4simplelauncher.charging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One charging location from the Open Charge Map registry, reduced to what the launcher
 * shows. OCM is a mostly static registry: it tells you where a station is and what it can
 * deliver, never whether a bay is free right now.
 */
public class ChargePoint {

    public final long id;
    public final String title;
    /** Operator name, e.g. "Free To X" or "Tesla"; empty when OCM has no operator on file. */
    public final String operator;
    public final String address;
    public final double latitude;
    public final double longitude;
    /** Highest advertised power across this location's connectors, in kW; 0 when unknown. */
    public final double maxPowerKw;
    /** Comma-separated connector names, e.g. "Type 2, CCS". */
    public final String connectors;
    /** Straight-line distance from the search centre in km, filled in by the client. */
    public final double distanceKm;

    private ChargePoint(long id, String title, String operator, String address,
                        double latitude, double longitude, double maxPowerKw,
                        String connectors, double distanceKm) {
        this.id = id;
        this.title = title;
        this.operator = operator;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxPowerKw = maxPowerKw;
        this.connectors = connectors;
        this.distanceKm = distanceKm;
    }

    /**
     * Parses one entry of the {@code /v3/poi} response. Returns null when the entry has no
     * usable coordinates, since a station we cannot place is useless on both map and list.
     */
    @Nullable
    static ChargePoint fromJson(@NonNull JSONObject json) throws JSONException {
        JSONObject address = json.optJSONObject("AddressInfo");
        if (address == null || address.isNull("Latitude") || address.isNull("Longitude")) {
            return null;
        }
        double lat = address.getDouble("Latitude");
        double lon = address.getDouble("Longitude");

        String operator = "";
        JSONObject operatorInfo = json.optJSONObject("OperatorInfo");
        if (operatorInfo != null) {
            operator = operatorInfo.optString("Title", "");
        }

        double maxPower = 0;
        StringBuilder connectors = new StringBuilder();
        JSONArray connections = json.optJSONArray("Connections");
        if (connections != null) {
            for (int i = 0; i < connections.length(); i++) {
                JSONObject connection = connections.optJSONObject(i);
                if (connection == null) {
                    continue;
                }
                maxPower = Math.max(maxPower, connection.optDouble("PowerKW", 0));
                JSONObject type = connection.optJSONObject("ConnectionType");
                if (type != null) {
                    String name = type.optString("Title", "");
                    // The same connector often repeats once per bay; list it only once.
                    if (!name.isEmpty() && connectors.indexOf(name) < 0) {
                        if (connectors.length() > 0) {
                            connectors.append(", ");
                        }
                        connectors.append(name);
                    }
                }
            }
        }

        // OCM puts the distance it computed inside AddressInfo, not at the root.
        double distance = address.optDouble("Distance", Double.NaN);

        return new ChargePoint(
                json.optLong("ID"),
                address.optString("Title", ""),
                operator,
                buildAddress(address),
                lat,
                lon,
                maxPower,
                connectors.toString(),
                distance);
    }

    private static String buildAddress(@NonNull JSONObject address) {
        String line = address.optString("AddressLine1", "");
        String town = address.optString("Town", "");
        if (line.isEmpty()) {
            return town;
        }
        return town.isEmpty() ? line : line + ", " + town;
    }

    /** True when OCM supplied a distance for this point. */
    public boolean hasDistance() {
        return !Double.isNaN(distanceKm);
    }
}
