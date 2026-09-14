package com.tommasov.mg4simplelauncher.diag;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import com.tommasov.mg4simplelauncher.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An on-device log the driver can read without a computer.
 *
 * <p>The head unit cannot be reached over adb, so anything that goes wrong in the car has to
 * leave a trace the app itself can show later. Entries are appended to a single file in the
 * app's own storage and read back by {@link DiagnosticsActivity}.
 *
 * <p>Writes go through a single background thread so logging never stalls the UI — except
 * for crashes, which are written on the dying thread because there is no later.
 */
public final class DiagnosticsLog {

    private static final String TAG = "DiagnosticsLog";
    private static final String FILE_NAME = "diagnostics.log";
    /** Kept small: this is read on a car screen, and old lines stop being useful fast. */
    private static final long MAX_BYTES = 128 * 1024;
    private static final long TRIM_TO_BYTES = 64 * 1024;

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor();
    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US);

    private DiagnosticsLog() {
    }

    public static File file(@NonNull Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    /** Records one line. Also mirrors to logcat, which still helps on the emulator. */
    public static void log(@NonNull Context context, @NonNull String tag,
                           @NonNull String message) {
        Log.i(tag, message);
        File target = file(context.getApplicationContext());
        String line = STAMP.format(new Date()) + "  " + tag + ": " + message;
        WRITER.execute(() -> append(target, line));
    }

    /** Records a failure with its stack trace, which is what makes a crash diagnosable. */
    public static void log(@NonNull Context context, @NonNull String tag,
                           @NonNull String message, @NonNull Throwable error) {
        log(context, tag, message + "\n" + stackTrace(error));
    }

    /**
     * Writes straight away on the calling thread. Only for the crash handler: the process is
     * about to die and a queued write would never run.
     */
    static void logNow(@NonNull File target, @NonNull String tag, @NonNull String message) {
        append(target, STAMP.format(new Date()) + "  " + tag + ": " + message);
    }

    /** A header worth having at the top of every session, to tell builds apart. */
    public static String sessionHeader() {
        return "--- " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") on "
                + Build.MANUFACTURER + " " + Build.MODEL
                + ", Android " + Build.VERSION.RELEASE + " ---";
    }

    public static String stackTrace(@NonNull Throwable error) {
        StringWriter out = new StringWriter();
        error.printStackTrace(new PrintWriter(out));
        return out.toString();
    }

    @NonNull
    public static String read(@NonNull Context context) {
        File target = file(context);
        if (!target.exists()) {
            return "";
        }
        try {
            byte[] data = new byte[(int) target.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(target)) {
                int read = in.read(data);
                return read <= 0 ? "" : new String(data, 0, read, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return "Could not read the log: " + e;
        }
    }

    /** Number of recorded lines, used to say how much there is before opening it. */
    public static int lineCount(@NonNull String log) {
        int count = 0;
        for (int i = 0; i < log.length(); i++) {
            if (log.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    public static void clear(@NonNull Context context) {
        File target = file(context);
        WRITER.execute(() -> {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
        });
    }

    private static synchronized void append(@NonNull File target, @NonNull String line) {
        try {
            trimIfHuge(target);
            try (Writer writer = new OutputStreamWriter(
                    new FileOutputStream(target, true), StandardCharsets.UTF_8)) {
                writer.write(line);
                writer.write("\n");
            }
        } catch (IOException e) {
            Log.w(TAG, "could not append to the diagnostics log", e);
        }
    }

    /** Drops the oldest half once the file grows past the cap, so it can never run away. */
    private static void trimIfHuge(@NonNull File target) throws IOException {
        if (!target.exists() || target.length() <= MAX_BYTES) {
            return;
        }
        byte[] data = new byte[(int) TRIM_TO_BYTES];
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(target, "r")) {
            raf.seek(target.length() - TRIM_TO_BYTES);
            raf.readFully(data);
        }
        try (FileOutputStream out = new FileOutputStream(target, false)) {
            out.write("--- older entries dropped ---\n".getBytes(StandardCharsets.UTF_8));
            out.write(data);
        }
    }
}
