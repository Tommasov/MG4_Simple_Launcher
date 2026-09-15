package com.tommasov.mg4simplelauncher.update;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;

import java.io.File;

/** Downloads the APK with the system {@link DownloadManager} and reports progress. */
public class ApkDownloader {

    private static final String TAG = "ApkDownloader";
    /** Sub-directory of getExternalFilesDir(null); must match res/xml/file_paths.xml. */
    private static final String SUBDIR = "updates";
    private static final long POLL_INTERVAL_MS = 500;

    public interface Callback {
        void onProgress(int percent);

        void onComplete(@NonNull File apk);

        void onFailed(@NonNull String reason);
    }

    private final Context appContext;
    private final DownloadManager downloadManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private long downloadId = -1;
    private File targetFile;
    private Callback callback;
    private BroadcastReceiver completeReceiver;
    private Runnable poller;

    public ApkDownloader(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.downloadManager =
                (DownloadManager) appContext.getSystemService(Context.DOWNLOAD_SERVICE);
    }

    /** Starts downloading {@code info.apkUrl}. Callbacks run on the main thread. */
    public void start(@NonNull UpdateInfo info, @NonNull Callback callback) {
        this.callback = callback;

        String name = info.fileName();
        if (name == null) {
            name = "update.apk";
        }
        File dir = new File(appContext.getExternalFilesDir(null), SUBDIR);
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        targetFile = new File(dir, name);
        // A stale partial/previous download would shadow the fresh one.
        if (targetFile.exists()) {
            //noinspection ResultOfMethodCallIgnored
            targetFile.delete();
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle(name)
                .setDestinationInExternalFilesDir(appContext, null, SUBDIR + "/" + name)
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive");

        registerCompleteReceiver();
        downloadId = downloadManager.enqueue(request);
        DiagnosticsLog.log(appContext, TAG, "downloading " + info.apkUrl + " to "
                + targetFile.getAbsolutePath() + ", free space "
                + (dir.getUsableSpace() / (1024 * 1024)) + " MB");
        startPolling();
    }

    private void registerCompleteReceiver() {
        completeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    onDownloadFinished();
                }
            }
        };
        ContextCompat.registerReceiver(appContext, completeReceiver,
                new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                ContextCompat.RECEIVER_EXPORTED);
    }

    private void startPolling() {
        poller = new Runnable() {
            @Override
            public void run() {
                int percent = queryProgress();
                if (percent >= 0) {
                    callback.onProgress(percent);
                }
                mainHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        mainHandler.post(poller);
    }

    /** Returns 0..100, or -1 if the size is still unknown. */
    private int queryProgress() {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor c = downloadManager.query(query)) {
            if (c == null || !c.moveToFirst()) {
                return -1;
            }
            int total = c.getInt(c.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
            int done = c.getInt(c.getColumnIndexOrThrow(
                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
            if (total <= 0) {
                return -1;
            }
            return (int) (done * 100L / total);
        }
    }

    private void onDownloadFinished() {
        stopPolling();
        int status;
        int reason = 0;
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor c = downloadManager.query(query)) {
            if (c == null || !c.moveToFirst()) {
                fail("download record not found");
                return;
            }
            status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            if (status == DownloadManager.STATUS_FAILED) {
                reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
            }
        }
        unregisterReceiver();

        if (status == DownloadManager.STATUS_SUCCESSFUL && targetFile.exists()) {
            DiagnosticsLog.log(appContext, TAG, "downloaded " + targetFile.length() + " bytes");
            callback.onProgress(100);
            callback.onComplete(targetFile);
        } else {
            // Both halves matter: a successful status with no file at the expected path means
            // DownloadManager renamed it — it appends a suffix rather than overwriting — while
            // a failed status carries a reason code worth reading (1006 is out of space, 1004
            // a truncated transfer, 1009 the destination already existing).
            fail("download status=" + status + " reason=" + reason
                    + ", file " + (targetFile.exists() ? "present" : "missing")
                    + ", free space " + (targetFile.getParentFile() == null ? -1
                            : targetFile.getParentFile().getUsableSpace() / (1024 * 1024))
                    + " MB");
        }
    }

    private void fail(@NonNull String reason) {
        Log.w(TAG, reason);
        // The car has no adb: without this line the driver sees "download failed" and we
        // never learn why.
        DiagnosticsLog.log(appContext, TAG, "failed: " + reason);
        stopPolling();
        unregisterReceiver();
        callback.onFailed(reason);
    }

    private void stopPolling() {
        if (poller != null) {
            mainHandler.removeCallbacks(poller);
            poller = null;
        }
    }

    private void unregisterReceiver() {
        if (completeReceiver != null) {
            try {
                appContext.unregisterReceiver(completeReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered.
            }
            completeReceiver = null;
        }
    }

    /** Downloads younger than this are assumed to belong to an in-progress install. */
    private static final long STALE_AGE_MS = 60 * 60 * 1000L;

    /**
     * Deletes stale APKs from the updates directory to reclaim space. Files modified within the
     * last hour are left untouched: the system installer reads the APK in its own task after we
     * hand it off, so a just-downloaded file may still be in use even across a launcher relaunch.
     * Leftovers from earlier sessions are cleaned on a subsequent startup.
     */
    public static void clearDownloads(@NonNull Context context) {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            return;
        }
        File[] files = new File(base, SUBDIR).listFiles();
        if (files == null) {
            return;
        }
        long cutoff = System.currentTimeMillis() - STALE_AGE_MS;
        for (File file : files) {
            if (file.lastModified() < cutoff) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
    }

    /** Cancels an in-flight download and releases resources. */
    public void cancel() {
        if (downloadId != -1) {
            downloadManager.remove(downloadId);
        }
        stopPolling();
        unregisterReceiver();
    }
}