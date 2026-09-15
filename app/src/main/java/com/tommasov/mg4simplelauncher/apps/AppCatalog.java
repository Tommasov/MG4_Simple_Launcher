package com.tommasov.mg4simplelauncher.apps;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

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

/**
 * Reads the catalogue of the author's other apps for the MG4.
 *
 * <p>A single JSON file on the same server the launcher already takes its updates from, so
 * adding an app means editing that file rather than releasing a new launcher. The APKs are
 * hosted alongside it: the catalogue could point anywhere, but keeping the binaries under the
 * same roof means one place to trust and one place to fix.
 *
 * <p>Entries that do not parse are skipped rather than failing the screen — a catalogue with
 * one bad line should still show the rest.
 */
public class AppCatalog {

    private static final String TAG = "AppCatalog";
    private static final int TIMEOUT_MS = 15_000;
    /**
     * The car is often on a phone hotspot that has not finished waking up when this screen
     * opens, so the first attempt fails for no lasting reason. Three tries a second and a half
     * apart cost nothing and save the driver pressing refresh until it catches.
     */
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 1_500;

    public interface Callback {
        void onCatalog(@NonNull List<CatalogApp> apps);

        /** No network, bad response, or a file that is not a catalogue at all. */
        void onError(@NonNull Exception e);
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String catalogUrl;

    public AppCatalog(@NonNull String catalogUrl) {
        this.catalogUrl = catalogUrl;
    }

    /** Fetches off the main thread; the callback runs on it. */
    public void load(@NonNull Callback callback) {
        executor.execute(() -> {
            Exception last = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    deliver(callback);
                    return;
                } catch (Exception e) {
                    last = e;
                    Log.w(TAG, "catalogue attempt " + attempt + " of " + MAX_ATTEMPTS
                            + " failed", e);
                    if (attempt < MAX_ATTEMPTS) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }
            Exception failure = last == null ? new IllegalStateException("catalogue") : last;
            mainHandler.post(() -> callback.onError(failure));
        });
    }

    /** One attempt: fetch, parse, and hand the result over on the main thread. */
    private void deliver(@NonNull Callback callback) throws Exception {
        JSONObject json = new JSONObject(download(catalogUrl));
        JSONArray array = json.getJSONArray("apps");
        List<CatalogApp> apps = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            try {
                apps.add(CatalogApp.fromJson(entry, catalogUrl));
            } catch (Exception e) {
                // One malformed entry is not worth blanking the others.
                Log.w(TAG, "skipping catalogue entry " + i, e);
            }
        }
        mainHandler.post(() -> callback.onCatalog(apps));
    }

    @NonNull
    private static String download(@NonNull String urlString) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                throw new IllegalStateException("HTTP " + code + " for " + urlString);
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
}
