package com.tommasov.mg4simplelauncher.diag;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.tommasov.mg4simplelauncher.BuildConfig;
import com.tommasov.mg4simplelauncher.TechnicalDetails;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends the diagnostics log to the author's probe, because on this head unit there is
 * nowhere else for it to go.
 *
 * <p>Copying the log to the clipboard looked like a way out and was not: there is no text
 * field on board to paste into, and nothing on the car accepts a share either — the firmware
 * ships the Bluetooth stack with {@code profile_supported_opp} set to false, which disables
 * the one activity that handles {@code ACTION_SEND}. Without adb and without a browser, a
 * report that cannot be uploaded can only be photographed, which is what testers were
 * reduced to.
 *
 * <p>The receiving end is {@code probe.php}, which accepts a report and does nothing else:
 * post the text with the write key and which app it came from, and the reply is one line,
 * {@code OK <app>/<name>} or {@code ERR <reason>}. There is no way to read a report back
 * over HTTP, by anyone, which is what makes it safe to ship the key to a hundred cars.
 */
public final class ProbeReport {

    private static final int TIMEOUT_MS = 20_000;

    /** The probe refuses an empty or wrong key with a 403 and no explanation. */
    private static final int HTTP_FORBIDDEN = 403;

    public interface Callback {
        /** Stored, under the name the probe gave it. */
        void onSent(@NonNull String reportName);

        /** Not stored. The reason is meant to be shown: it is the only clue the driver has. */
        void onFailed(@NonNull String reason);
    }

    private static final ExecutorService SENDER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ProbeReport() {
    }

    /**
     * Whether this build can send at all. The key lives in the git-ignored
     * {@code apikeys.properties}, so a fresh clone builds without one — and then the button
     * is simply not offered, the same way the charging card steps aside without its key.
     */
    public static boolean isConfigured() {
        return !BuildConfig.PROBE_KEY.isEmpty() && !BuildConfig.PROBE_URL.isEmpty();
    }

    /**
     * Uploads the current log off the main thread; the callback always lands back on it.
     *
     * <p>The log is sent with a fresh header in front of it, and the device card behind that.
     * The stored header is the first thing the trimmer drops when the file grows past its
     * cap, and a report that does not say which build and which car it came from is worth
     * very little on the other end. The readings come along for the same reason: the person
     * reading this is at a desk, and cannot ask the screen which WebView is installed or what
     * is carrying the connection.
     */
    public static void send(@NonNull Context context, @NonNull String note,
                            @NonNull Callback callback) {
        Context appContext = context.getApplicationContext();
        SENDER.execute(() -> {
            try {
                String body = "k=" + encode(BuildConfig.PROBE_KEY)
                        + "&app=" + encode(BuildConfig.PROBE_APP)
                        + "&note=" + encode(note(note))
                        + "&text=" + encode(DiagnosticsLog.sessionHeader() + "\n\n"
                                + TechnicalDetails.report(appContext)
                                + DiagnosticsLog.read(appContext));
                String answer = post(BuildConfig.PROBE_URL, body);
                if (answer.startsWith("OK")) {
                    String name = answer.substring(2).trim();
                    MAIN.post(() -> callback.onSent(name));
                } else {
                    String reason = answer.startsWith("ERR") ? answer.substring(3).trim() : answer;
                    MAIN.post(() -> callback.onFailed(reason));
                }
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailed(describe(e)));
            }
        });
    }

    /**
     * One line, so a report can be told apart in the list without opening it: the build and
     * the car, then whatever the driver typed.
     *
     * <p>What they typed is what makes the log usable. A stack trace says what broke; only
     * the sentence in front of it says what they were trying to do at the time.
     */
    @NonNull
    private static String note(@NonNull String typed) {
        String head = BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") — "
                + Build.MANUFACTURER + " " + Build.MODEL;
        String trimmed = typed.trim();
        return trimmed.isEmpty() ? head : head + " — " + trimmed;
    }

    @NonNull
    private static String post(@NonNull String urlString, @NonNull String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlString).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type",
                    "application/x-www-form-urlencoded; charset=utf-8");
            byte[] payload = body.getBytes(StandardCharsets.UTF_8);
            // Without this the whole log is buffered to work out a Content-Length, which on a
            // patchy mobile connection is exactly when memory is worth not wasting.
            conn.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload);
            }
            int code = conn.getResponseCode();
            if (code == HTTP_FORBIDDEN) {
                throw new IllegalStateException("key refused (HTTP 403)");
            }
            // The probe answers a rejected report with 400 and an ERR line, so the error
            // stream is worth reading rather than discarding.
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (in == null) {
                throw new IllegalStateException("HTTP " + code + ", empty answer");
            }
            try (InputStream stream = in) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = stream.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8.name()).trim();
            }
        } finally {
            conn.disconnect();
        }
    }

    @NonNull
    private static String encode(@NonNull String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    /** A cause short enough to fit in a dialog on a car screen. */
    @NonNull
    private static String describe(@NonNull Exception e) {
        String message = e.getMessage();
        return message == null || message.isEmpty() ? e.getClass().getSimpleName() : message;
    }
}
