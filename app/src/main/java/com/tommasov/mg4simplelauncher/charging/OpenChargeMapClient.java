package com.tommasov.mg4simplelauncher.charging;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import com.tommasov.mg4simplelauncher.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Queries the Open Charge Map registry for stations around a position.
 *
 * <p>Open Charge Map bans callers that hammer the API, so this is deliberately built for
 * on-demand use: one request when the user opens the screen or asks to refresh, never on a
 * timer. Results are meant to be cached by the caller and filtered locally.
 *
 * <p>Data © Open Charge Map contributors; the attribution has to stay visible wherever the
 * results are shown.
 */
public class OpenChargeMapClient {

    private static final String TAG = "OpenChargeMap";
    private static final String ENDPOINT = "https://api.openchargemap.io/v3/poi";
    private static final int TIMEOUT_MS = 15_000;

    public interface Callback {
        void onResult(@NonNull List<ChargePoint> points);

        /** No network, bad response, or no API key configured. */
        void onError(@NonNull Exception e);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    /**
     * Bumped by {@link #cancel()}. A request remembers the value it started under and stays
     * quiet if it no longer matches, which is how a cancelled lookup is silenced without
     * shutting the executor down — a shut-down executor rejects every later request, and this
     * client is reused every time the driver asks for a refresh.
     */
    private final AtomicInteger generation = new AtomicInteger();

    /** True when the build carries an Open Charge Map key; without one, queries fail fast. */
    public static boolean hasApiKey() {
        return !TextUtils.isEmpty(BuildConfig.OCM_API_KEY);
    }

    /**
     * Fetches stations around ({@code latitude}, {@code longitude}) matching {@code filter}.
     *
     * <p>The filter is applied by the server, not to the response: see {@link ChargingFilter}
     * for why narrowing afterwards would usually return an empty list.
     *
     * @param maxResults upper bound on returned stations, ordered by distance.
     */
    public void nearby(double latitude, double longitude, @NonNull ChargingFilter filter,
                       int maxResults, @NonNull Callback callback) {
        if (!hasApiKey()) {
            mainHandler.post(() -> callback.onError(
                    new IllegalStateException("No Open Charge Map API key configured")));
            return;
        }
        final int startedUnder = generation.get();
        executor.execute(() -> {
            try {
                Uri.Builder query = Uri.parse(ENDPOINT).buildUpon()
                        .appendQueryParameter("output", "json")
                        // No country filter: latitude, longitude and distance already bound
                        // the search, and a country would cut it at the border — exactly
                        // where a driver is most interested in what is on the other side.
                        .appendQueryParameter("latitude", String.valueOf(latitude))
                        .appendQueryParameter("longitude", String.valueOf(longitude))
                        .appendQueryParameter("distance", String.valueOf(filter.radiusKm))
                        .appendQueryParameter("distanceunit", "KM")
                        .appendQueryParameter("maxresults", String.valueOf(maxResults))
                        // Skip stations OCM knows to be decommissioned or not yet live.
                        .appendQueryParameter("statustypeid", "50,75");
                if (filter.operatorId != null) {
                    query.appendQueryParameter("operatorid", String.valueOf(filter.operatorId));
                }
                if (filter.minPowerKw != null) {
                    query.appendQueryParameter("minpowerkw", String.valueOf(filter.minPowerKw));
                }
                // Left verbose on purpose: the compact form returns bare numeric ids, so the
                // operator and connector names the list shows would come back empty.
                String url = query.build().toString();
                List<ChargePoint> points = parse(download(url));
                mainHandler.post(() -> {
                    if (startedUnder == generation.get()) {
                        callback.onResult(points);
                    }
                });
            } catch (Exception e) {
                Log.w(TAG, "charging point lookup failed", e);
                mainHandler.post(() -> {
                    if (startedUnder == generation.get()) {
                        callback.onError(e);
                    }
                });
            }
        });
    }

    @NonNull
    private static List<ChargePoint> parse(@NonNull String body) throws Exception {
        JSONArray array = new JSONArray(body);
        List<ChargePoint> points = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            if (json == null) {
                continue;
            }
            ChargePoint point = ChargePoint.fromJson(json);
            if (point != null) {
                points.add(point);
            }
        }
        return points;
    }

    @NonNull
    private static String download(@NonNull String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-API-Key", BuildConfig.OCM_API_KEY);
            // OCM asks callers to identify themselves so it can contact abusive clients.
            conn.setRequestProperty("User-Agent",
                    "MG4SimpleLauncher/" + BuildConfig.VERSION_NAME);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + code + " for " + ENDPOINT);
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Stops any in-flight lookup; its callback will not run afterwards. The client stays
     * usable — a request started after this one will be served normally.
     *
     * <p>The download itself is left to finish into the void rather than interrupted: it is
     * a blocking socket read that cannot be stopped cleanly, and its result is discarded by
     * the generation check.
     */
    public void cancel() {
        generation.incrementAndGet();
        mainHandler.removeCallbacksAndMessages(null);
    }
}
