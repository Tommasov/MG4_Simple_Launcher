package com.tommasov.mg4simplelauncher;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    /** Which favorite set MODE_PICK writes into; defaults to {@link #TARGET_HOME}. */
    public static final String EXTRA_TARGET = "target";
    public static final String MODE_ALL = "all";
    public static final String MODE_SYSTEM = "system";
    /**
     * The picker, showing screens instead of apps: Android's settings pages and the
     * launcher's own. Kept a separate list rather than sitting on top of the apps — they are
     * a different kind of thing, and mixed in they read as nine odd-looking apps.
     */
    public static final String MODE_PICK_SCREENS = "pick_screens";
    public static final String MODE_PICK = "pick";
    public static final String TARGET_HOME = "home";
    public static final String TARGET_GRID = "grid";
    /** The two small shortcuts under "All apps" on the home page. */
    public static final String TARGET_DOCK = "dock";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String mode;
    private String target;
    private int slot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_drawer);

        mode = getIntent().getStringExtra(EXTRA_MODE);
        if (mode == null) {
            mode = MODE_ALL;
        }
        target = getIntent().getStringExtra(EXTRA_TARGET);
        if (target == null) {
            target = TARGET_HOME;
        }
        slot = getIntent().getIntExtra(EXTRA_SLOT, -1);

        TextView title = findViewById(R.id.drawer_title);
        title.setText(titleForMode());

        // Explicit back affordance for the head unit, mirroring the system back gesture.
        findViewById(R.id.drawer_back_button).setOnClickListener(v -> finish());

        // One button, three jobs depending on where we are: reach the system apps from the
        // "all apps" drawer, and swap between apps and screens while picking.
        TextView headerButton = findViewById(R.id.system_apps_button);
        if (MODE_ALL.equals(mode)) {
            headerButton.setText(R.string.system_apps);
            headerButton.setOnClickListener(v -> startActivity(
                    drawerIntent(MODE_SYSTEM, slot, target)));
        } else if (MODE_PICK.equals(mode)) {
            headerButton.setText(R.string.target_screens);
            headerButton.setOnClickListener(v -> {
                startActivity(drawerIntent(MODE_PICK_SCREENS, slot, target));
                finish();
            });
        } else if (MODE_PICK_SCREENS.equals(mode)) {
            headerButton.setText(R.string.all_apps);
            headerButton.setOnClickListener(v -> {
                startActivity(drawerIntent(MODE_PICK, slot, target));
                finish();
            });
        } else {
            headerButton.setVisibility(View.GONE);
        }

        RecyclerView grid = findViewById(R.id.app_grid);
        int span = Math.max(4, getResources().getConfiguration().screenWidthDp / 130);
        grid.setLayoutManager(new GridLayoutManager(this, span));

        loadApps(grid);
    }

    /** The same screen again, in another mode, carrying the slot it is filling. */
    private Intent drawerIntent(String newMode, int slot, String target) {
        Intent intent = new Intent(this, AppDrawerActivity.class);
        intent.putExtra(EXTRA_MODE, newMode);
        intent.putExtra(EXTRA_SLOT, slot);
        intent.putExtra(EXTRA_TARGET, target);
        return intent;
    }

    private String titleForMode() {
        switch (mode) {
            case MODE_SYSTEM:
                return getString(R.string.system_apps);
            case MODE_PICK:
                return getString(R.string.pick_favorite_title);
            case MODE_PICK_SCREENS:
                return getString(R.string.target_screens);
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
                // In the picker a long-press would fight the tap-to-assign gesture, so the
                // app-details shortcut only exists in the browsing drawers.
                AppListAdapter.OnAppClickListener longClick =
                        MODE_PICK.equals(mode) || MODE_PICK_SCREENS.equals(mode)
                                ? null : this::onAppLongClick;
                grid.setAdapter(new AppListAdapter(apps, this::onAppClick, longClick));
            });
        });
    }

    private List<AppInfo> queryApps() {
        if (MODE_PICK_SCREENS.equals(mode)) {
            return screenTargets();
        }
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

    /**
     * The things a tile can hold besides an app, dressed as apps so the same grid, the same
     * adapter and the same tap-to-assign carry them.
     */
    private List<AppInfo> screenTargets() {
        List<AppInfo> targets = new ArrayList<>();
        for (LaunchTargets.Target target : LaunchTargets.all()) {
            targets.add(new AppInfo(target.label(this), target.id, target.icon(this), false));
        }
        return targets;
    }

    private void onAppClick(AppInfo app) {
        if (MODE_PICK.equals(mode) || MODE_PICK_SCREENS.equals(mode)) {
            if (slot >= 0) {
                PreferencesManager prefs = new PreferencesManager(this);
                if (TARGET_GRID.equals(target)) {
                    prefs.setGridFavorite(slot, app.packageName);
                } else if (TARGET_DOCK.equals(target)) {
                    prefs.setDockShortcut(slot, app.packageName);
                } else {
                    prefs.setFavorite(slot, app.packageName);
                }
            }
            finish();
            return;
        }
        launch(app.packageName);
    }

    /** Long-press opens Android's app-details page (permissions, storage, uninstall). */
    private void onAppLongClick(AppInfo app) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", app.packageName, null));
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            // Some head-unit builds strip the Settings details screen.
            Toast.makeText(this, R.string.app_info_unavailable, Toast.LENGTH_SHORT).show();
        }
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
    }
}