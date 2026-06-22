package com.tommasov.mg4simplelauncher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tommasov.mg4simplelauncher.update.UpdateManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Full-screen drawer that lists launchable apps. Three modes:
 *  - ALL: every launchable app, tap to launch.
 *  - SYSTEM: only system apps, tap to launch.
 *  - PICK: every launchable app, tap to assign it to a favorite slot, then return.
 */
public class AppDrawerActivity extends AppCompatActivity {

    public static final String EXTRA_MODE = "mode";
    public static final String EXTRA_SLOT = "slot";
    public static final String MODE_ALL = "all";
    public static final String MODE_SYSTEM = "system";
    public static final String MODE_PICK = "pick";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String mode;
    private int slot;
    private UpdateManager updateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_drawer);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) {
            mode = MODE_ALL;
        }
        slot = getIntent().getIntExtra(EXTRA_SLOT, -1);

        TextView title = findViewById(R.id.drawer_title);
        title.setText(titleForMode());

        // Explicit back affordance for the head unit, mirroring the system back gesture.
        findViewById(R.id.drawer_back_button).setOnClickListener(v -> finish());

        // The manual update check belongs in the browsing drawers, not the favorite picker.
        View checkUpdates = findViewById(R.id.check_updates_button);
        if (MODE_PICK.equals(mode)) {
            checkUpdates.setVisibility(View.GONE);
        } else {
            updateManager = new UpdateManager(this);
            checkUpdates.setOnClickListener(v -> updateManager.checkForUpdates(true));
        }

        // System apps are reached from the "all apps" drawer header; redundant elsewhere.
        View systemApps = findViewById(R.id.system_apps_button);
        if (MODE_ALL.equals(mode)) {
            systemApps.setOnClickListener(v -> {
                Intent intent = new Intent(this, AppDrawerActivity.class);
                intent.putExtra(EXTRA_MODE, MODE_SYSTEM);
                startActivity(intent);
            });
        } else {
            systemApps.setVisibility(View.GONE);
        }

        RecyclerView grid = findViewById(R.id.app_grid);
        int span = Math.max(4, getResources().getConfiguration().screenWidthDp / 130);
        grid.setLayoutManager(new GridLayoutManager(this, span));

        loadApps(grid);
    }

    private String titleForMode() {
        switch (mode) {
            case MODE_SYSTEM:
                return getString(R.string.system_apps);
            case MODE_PICK:
                return getString(R.string.pick_favorite_title);
            default:
                return getString(R.string.all_apps);
        }
    }

    private void loadApps(RecyclerView grid) {
        executor.execute(() -> {
            List<AppInfo> apps = queryApps();
            mainHandler.post(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                grid.setAdapter(new AppListAdapter(apps, this::onAppClick));
            });
        });
    }

    private List<AppInfo> queryApps() {
        PackageManager pm = getPackageManager();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(intent, 0);

        String ownPackage = getPackageName();
        List<AppInfo> apps = new ArrayList<>();
        for (ResolveInfo ri : resolveInfos) {
            ApplicationInfo ai = ri.activityInfo.applicationInfo;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ownPackage)) {
                continue;
            }
            // An updated system app (e.g. preinstalled Maps the user updated) counts as
            // a user app, so it shows up in "all apps" rather than the system drawer.
            boolean system = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                    && (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0;
            if (MODE_SYSTEM.equals(mode)) {
                if (!system) {
                    continue;
                }
            } else if (MODE_ALL.equals(mode)) {
                if (system) {
                    continue;
                }
            }
            // MODE_PICK keeps every app so any can be assigned as a favorite.
            String label = ri.loadLabel(pm).toString();
            apps.add(new AppInfo(label, pkg, ri.loadIcon(pm), system));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return apps;
    }

    private void onAppClick(AppInfo app) {
        if (MODE_PICK.equals(mode)) {
            if (slot >= 0) {
                new PreferencesManager(this).setFavorite(slot, app.packageName);
            }
            finish();
            return;
        }
        launch(app.packageName);
    }

    private void launch(String packageName) {
        if (!AppLauncher.launch(this, packageName)) {
            Toast.makeText(this, packageName, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
        // Tear down any in-flight download/dialog started from the update button.
        if (updateManager != null) {
            updateManager.cancel();
        }
    }
}