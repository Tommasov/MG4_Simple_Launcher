package com.tommasov.mg4simplelauncher.apps;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tommasov.mg4simplelauncher.BuildConfig;
import com.tommasov.mg4simplelauncher.R;
import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;
import com.tommasov.mg4simplelauncher.update.ApkDownloader;
import com.tommasov.mg4simplelauncher.update.ApkInstaller;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The author's other apps for this car: what exists, what is installed, and a button to put
 * that right.
 *
 * <p>Not a shop. The catalogue is one JSON file the author maintains, the binaries sit beside
 * it, and every download is checked against the hash published there before Android is asked
 * to install it — the same path the launcher's own updates take, for the same reason: a head
 * unit has no Play Store to vouch for anything.
 */
public class DownloadsActivity extends AppCompatActivity implements CatalogAdapter.Listener {

    private static final String TAG_DIAG = "Downloads";

    private final AppCatalog catalog = new AppCatalog(BuildConfig.CATALOG_URL);
    private CatalogAdapter adapter;
    private RecyclerView list;
    private TextView status;
    @Nullable
    private ApkDownloader downloader;
    @Nullable
    private AlertDialog progress;
    @Nullable
    private ProgressBar progressBar;
    @Nullable
    private TextView progressText;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        findViewById(R.id.downloads_back_button).setOnClickListener(v -> finish());
        findViewById(R.id.downloads_refresh).setOnClickListener(v -> load());

        // Opening this screen is as good a moment as any to throw away the APKs of installs
        // already done: they are megabytes each and serve no purpose once installed.
        ApkDownloader.clearDownloads(this);

        status = findViewById(R.id.downloads_status);
        adapter = new CatalogAdapter(this);
        list = findViewById(R.id.downloads_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        load();
    }

    private void load() {
        showStatus(R.string.downloads_loading);
        catalog.load(new AppCatalog.Callback() {
            @Override
            public void onCatalog(@NonNull List<CatalogApp> apps) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (apps.isEmpty()) {
                    showStatus(R.string.downloads_empty);
                    return;
                }
                adapter.submit(apps);
                status.setVisibility(View.GONE);
                list.setVisibility(View.VISIBLE);
            }

            @Override
            public void onError(@NonNull Exception e) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                DiagnosticsLog.log(DownloadsActivity.this, TAG_DIAG, "catalogue failed", e);
                showStatus(R.string.downloads_failed);
            }
        });
    }

    @Override
    public void onAction(@NonNull CatalogApp app) {
        // Android refuses installs from an app without this permission, and the refusal is
        // silent from here: better to ask now than to download ten megabytes for nothing.
        if (!ApkInstaller.canInstall(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.update_permission_title)
                    .setMessage(R.string.update_permission_message)
                    .setPositiveButton(R.string.update_permission_open_settings,
                            (d, w) -> ApkInstaller.requestInstallPermission(this))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            return;
        }
        download(app);
    }

    private void download(@NonNull CatalogApp app) {
        DiagnosticsLog.log(this, TAG_DIAG, "installing " + app.packageName
                + " " + app.versionName + " (" + app.versionCode + ")");
        showProgress(app.name);
        downloader = new ApkDownloader(this);
        downloader.start(app.apkUrl, fileNameFor(app), new ApkDownloader.Callback() {
            @Override
            public void onProgress(int percent) {
                if (progressBar != null) {
                    progressBar.setIndeterminate(percent <= 0);
                    progressBar.setProgress(percent);
                }
                if (progressText != null) {
                    progressText.setText(getString(R.string.update_downloading, percent));
                }
            }

            @Override
            public void onComplete(@NonNull File apk) {
                verifyAndInstall(apk, app);
            }

            @Override
            public void onFailed(@NonNull String reason) {
                dismissProgress();
                toast(R.string.update_download_failed);
            }
        });
    }

    /**
     * Named after the package and build rather than after whatever the URL ends in: the two
     * apps published so far disagree on whether the version belongs in the file name, and a
     * stale download must never be mistaken for a fresh one.
     */
    @NonNull
    private static String fileNameFor(@NonNull CatalogApp app) {
        return app.packageName + "-" + app.versionCode + ".apk";
    }

    private void verifyAndInstall(@NonNull File apk, @NonNull CatalogApp app) {
        // Hashing megabytes is not main-thread work; the executor is single-use.
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            boolean ok = ApkInstaller.verify(apk, app.sha256);
            runOnUiThread(() -> {
                dismissProgress();
                if (ok) {
                    ApkInstaller.install(this, apk);
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    apk.delete();
                    DiagnosticsLog.log(this, TAG_DIAG,
                            "hash mismatch for " + app.packageName);
                    toast(R.string.update_verify_failed);
                }
            });
        });
        executor.shutdown();
    }

    /**
     * Built by hand on an AppCompat dialog rather than with ProgressDialog.
     *
     * <p>ProgressDialog draws itself from the platform theme, not the app's, so in a car set
     * to dark mode it came up in the wrong colours while every other dialog in the launcher
     * followed along. This is the same construction the update flow uses, which is why that
     * one has always looked right.
     */
    private void showProgress(@NonNull String appName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        progressText = new TextView(this);
        progressText.setText(getString(R.string.update_downloading, 0));
        progressText.setGravity(Gravity.CENTER);

        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(100);
        progressBar.setIndeterminate(true);

        layout.addView(progressText);
        layout.addView(progressBar);

        progress = new AlertDialog.Builder(this)
                .setTitle(appName)
                .setView(layout)
                .setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> {
                    if (downloader != null) {
                        downloader.cancel();
                    }
                })
                .create();
        progress.show();
    }

    private void dismissProgress() {
        if (progress != null && progress.isShowing()) {
            progress.dismiss();
        }
        progress = null;
        progressBar = null;
        progressText = null;
    }

    private void showStatus(@StringRes int message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        list.setVisibility(View.GONE);
    }

    private void toast(@StringRes int message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // An install that just finished changes what every row should say.
        adapter.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (downloader != null) {
            downloader.cancel();
        }
        dismissProgress();
    }
}
