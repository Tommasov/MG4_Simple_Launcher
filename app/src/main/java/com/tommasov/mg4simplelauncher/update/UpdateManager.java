package com.tommasov.mg4simplelauncher.update;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.tommasov.mg4simplelauncher.BuildConfig;
import com.tommasov.mg4simplelauncher.R;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point for the in-app updater. Ties together {@link UpdateChecker},
 * {@link ApkDownloader} and {@link ApkInstaller} and owns the user-facing dialogs.
 */
public class UpdateManager {

    private final Activity activity;
    private final UpdateChecker checker;
    private final ExecutorService verifyExecutor = Executors.newSingleThreadExecutor();

    private ApkDownloader downloader;
    private AlertDialog progressDialog;
    private ProgressBar progressBar;
    private TextView progressText;

    public UpdateManager(@NonNull Activity activity) {
        this.activity = activity;
        this.checker = new UpdateChecker(activity, BuildConfig.UPDATE_BASE_URL);
    }

    /**
     * @param userInitiated when false (e.g. an automatic check on launch), stays silent
     *                      unless an update is actually found.
     */
    public void checkForUpdates(boolean userInitiated) {
        checker.check(new UpdateChecker.Callback() {
            @Override
            public void onUpdateAvailable(@NonNull UpdateInfo info) {
                if (!activity.isFinishing()) {
                    showUpdateDialog(info);
                }
            }

            @Override
            public void onUpToDate() {
                if (userInitiated) {
                    toast(R.string.update_up_to_date);
                }
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (userInitiated) {
                    toast(R.string.update_check_failed);
                }
            }
        });
    }

    private void showUpdateDialog(@NonNull UpdateInfo info) {
        String message = activity.getString(R.string.update_available_message, info.versionName);
        if (!TextUtils.isEmpty(info.changelog)) {
            message += "\n\n" + info.changelog;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_available_title)
                .setMessage(message)
                .setCancelable(!info.mandatory)
                .setPositiveButton(R.string.update_action_install,
                        (d, w) -> onInstallChosen(info));
        if (!info.mandatory) {
            builder.setNegativeButton(R.string.update_action_later, null);
        }
        builder.show();
    }

    private void onInstallChosen(@NonNull UpdateInfo info) {
        // Android won't install from this app until the user allows "unknown apps".
        if (!ApkInstaller.canInstall(activity)) {
            new AlertDialog.Builder(activity)
                    .setTitle(R.string.update_permission_title)
                    .setMessage(R.string.update_permission_message)
                    .setPositiveButton(R.string.update_permission_open_settings,
                            (d, w) -> ApkInstaller.requestInstallPermission(activity))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        startDownload(info);
    }

    private void startDownload(@NonNull UpdateInfo info) {
        showProgressDialog();
        downloader = new ApkDownloader(activity);
        downloader.start(info, new ApkDownloader.Callback() {
            @Override
            public void onProgress(int percent) {
                updateProgress(percent);
            }

            @Override
            public void onComplete(@NonNull File apk) {
                verifyAndInstall(apk, info);
            }

            @Override
            public void onFailed(@NonNull String reason) {
                dismissProgressDialog();
                toast(R.string.update_download_failed);
            }
        });
    }

    private void verifyAndInstall(@NonNull File apk, @NonNull UpdateInfo info) {
        // Hashing can take a moment for a large APK; keep it off the main thread.
        verifyExecutor.execute(() -> {
            boolean ok = ApkInstaller.verify(apk, info.sha256);
            activity.runOnUiThread(() -> {
                dismissProgressDialog();
                if (ok) {
                    ApkInstaller.install(activity, apk);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                    toast(R.string.update_verify_failed);
                }
            });
        });
    }

    // --- progress dialog --------------------------------------------------

    private void showProgressDialog() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        progressText = new TextView(activity);
        progressText.setText(activity.getString(R.string.update_downloading, 0));
        progressText.setGravity(Gravity.CENTER);

        progressBar = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true);

        layout.addView(progressText);
        layout.addView(progressBar);

        progressDialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.update_downloading_title)
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    if (downloader != null) {
                        downloader.cancel();
                    }
                })
                .create();
        progressDialog.show();
    }

    private void updateProgress(int percent) {
        if (progressBar != null) {
            progressBar.setIndeterminate(percent <= 0);
            progressBar.setProgress(percent);
        }
        if (progressText != null) {
            progressText.setText(activity.getString(R.string.update_downloading, percent));
        }
    }

    private void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing() && !activity.isFinishing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }

    private void toast(int resId) {
        if (!activity.isFinishing()) {
            Toast.makeText(activity, resId, Toast.LENGTH_SHORT).show();
        }
    }
}