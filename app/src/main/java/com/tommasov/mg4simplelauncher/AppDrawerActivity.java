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

import androidx.annotation.StringRes;
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
    /**
     * The picker, showing the car's own screens: every exported activity belonging to the
     * vehicle's software, found on this vehicle rather than read from a list.
     */
    public static final String MODE_PICK_VEHICLE = "pick_vehicle";
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

        // Where each of the two header buttons goes depends on which list is on screen: the
        // two it is not showing. In the browsing drawer the second one has nothing to do.
        TextView primary = findViewById(R.id.system_apps_button);
        TextView secondary = findViewById(R.id.secondary_header_button);
        if (MODE_ALL.equals(mode)) {
            switchTo(primary, R.string.system_apps, MODE_SYSTEM, false);
        } else if (MODE_PICK.equals(mode)) {
            switchTo(primary, R.string.target_screens, MODE_PICK_SCREENS, true);
            switchTo(secondary, R.string.target_vehicle, MODE_PICK_VEHICLE, true);
        } else if (MODE_PICK_SCREENS.equals(mode)) {
            switchTo(primary, R.string.all_apps, MODE_PICK, true);
            switchTo(secondary, R.string.target_vehicle, MODE_PICK_VEHICLE, true);
        } else if (MODE_PICK_VEHICLE.equals(mode)) {
            switchTo(primary, R.string.all_apps, MODE_PICK, true);
            switchTo(secondary, R.string.target_screens, MODE_PICK_SCREENS, true);
        } else {
            primary.setVisibility(View.GONE);
        }

        RecyclerView grid = findViewById(R.id.app_grid);
        int span = Math.max(4, getResources().getConfiguration().screenWidthDp / 130);
        grid.setLayoutManager(new GridLayoutManager(this, span));

        loadApps(grid);
    }

    /**
     * Points a header button at another list. {@code replace} closes this one on the way, so
     * the three picker lists stay siblings instead of stacking up behind each other.
     */
    private void switchTo(TextView button, @StringRes int labelRes, String newMode,
                          boolean replace) {
        button.setText(labelRes);
        button.setVisibility(View.VISIBLE);
        button.setOnClickListener(v -> {
            startActivity(drawerIntent(newMode, slot, target));
            if (replace) {
                finish();
            }
        });
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
            case MODE_PICK_VEHICLE:
                return getString(R.string.target_vehicle);
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
                        mode != null && mode.startsWith(MODE_PICK)
                                ? null : this::onAppLongClick;
                grid.setAdapter(new AppListAdapter(apps, this::onAppClick, longClick));

                TextView empty = findViewById(R.id.drawer_empty);
                empty.setText(MODE_PICK_VEHICLE.equals(mode)
                        ? R.string.drawer_empty_vehicle : R.string.drawer_empty);
                empty.setVisibility(apps.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private List<AppInfo> queryApps() {
        if (MODE_PICK_SCREENS.equals(mode)) {
            return screenTargets();
        }
        if (MODE_PICK_VEHICLE.equals(mode)) {
            return vehicleTargets();
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

    /**
     * The car's screens, as found on this vehicle. Each keeps the icon of the package it
     * belongs to: they are unlabelled system components, and the icon is the only thing that
     * tells the climate screens from the camera ones at a glance.
     */
    private List<AppInfo> vehicleTargets() {
        List<AppInfo> targets = new ArrayList<>();
        for (LaunchTargets.ActivityTarget screen : LaunchTargets.vehicleScreens(this)) {
            targets.add(new AppInfo(screen.label, screen.id(),
                    LaunchTargets.iconFor(this, screen.id()), true));
        }
        return targets;
    }

    private void onAppClick(AppInfo app) {
        if (MODE_PICK.equals(mode) || MODE_PICK_SCREENS.equals(mode)
                || MODE_PICK_VEHICLE.equals(mode)) {
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
            Dialogs.toast(this, R.string.app_info_unavailable, Toast.LENGTH_SHORT);
        }
    }

    private void launch(String packageName) {
        if (!AppLauncher.launch(this, packageName)) {
            Dialogs.toast(this, packageName, Toast.LENGTH_SHORT);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}