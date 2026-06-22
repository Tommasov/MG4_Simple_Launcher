package com.tommasov.mg4simplelauncher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Process;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.List;

/**
 * Carousel page 1: the launcher home. Three vertical cards each launch one chosen favorite
 * app (long-press to re-assign); the fourth column opens the drawers and the fixed shortcuts.
 */
public class HomeFragment extends Fragment {

    // Android 9 default Settings and Files packages, launched by the two fixed shortcuts.
    private static final String PKG_SETTINGS = "com.android.settings";
    private static final String PKG_FILES = "com.android.documentsui";

    private PreferencesManager preferencesManager;

    private View[] favoriteCards;
    private ImageView[] favoriteIcons;
    private TextView[] favoriteLabels;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferencesManager = new PreferencesManager(requireContext());

        favoriteCards = new View[]{
                view.findViewById(R.id.card_fav_1),
                view.findViewById(R.id.card_fav_2),
                view.findViewById(R.id.card_fav_3)};
        favoriteIcons = new ImageView[]{
                view.findViewById(R.id.icon_fav_1),
                view.findViewById(R.id.icon_fav_2),
                view.findViewById(R.id.icon_fav_3)};
        favoriteLabels = new TextView[]{
                view.findViewById(R.id.label_fav_1),
                view.findViewById(R.id.label_fav_2),
                view.findViewById(R.id.label_fav_3)};

        for (int i = 0; i < PreferencesManager.FAVORITE_COUNT; i++) {
            final int slot = i;
            favoriteCards[i].setOnClickListener(v -> onFavoriteClick(slot));
            favoriteCards[i].setOnLongClickListener(v -> {
                openDrawer(AppDrawerActivity.MODE_PICK, slot);
                return true;
            });
        }

        view.findViewById(R.id.card_all_apps).setOnClickListener(
                v -> openDrawer(AppDrawerActivity.MODE_ALL, -1));

        // Two fixed shortcuts to the Android 9 default Settings and Files apps.
        ImageView settingsIcon = view.findViewById(R.id.icon_settings);
        ImageView filesIcon = view.findViewById(R.id.icon_files);
        bindFixedApp(settingsIcon, PKG_SETTINGS);
        bindFixedApp(filesIcon, PKG_FILES);
        settingsIcon.setOnClickListener(v -> launch(PKG_SETTINGS));
        filesIcon.setOnClickListener(v -> launch(PKG_FILES));
    }

    @Override
    public void onResume() {
        super.onResume();
        // A favorite may have been (re)assigned in the picker, so rebind every time.
        for (int i = 0; i < PreferencesManager.FAVORITE_COUNT; i++) {
            bindFavorite(i);
        }
    }

    private void bindFavorite(int slot) {
        String pkg = preferencesManager.getFavorite(slot);
        PackageManager pm = requireContext().getPackageManager();
        if (pkg != null) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                favoriteIcons[slot].setImageDrawable(highResIcon(pkg));
                favoriteLabels[slot].setText(label);
                return;
            } catch (PackageManager.NameNotFoundException e) {
                // App was uninstalled; fall through to the empty state.
                preferencesManager.clearFavorite(slot);
            }
        }
        favoriteIcons[slot].setImageResource(R.drawable.ic_add);
        favoriteLabels[slot].setText(R.string.add_favorite);
    }

    private void onFavoriteClick(int slot) {
        String pkg = preferencesManager.getFavorite(slot);
        if (pkg == null) {
            openDrawer(AppDrawerActivity.MODE_PICK, slot);
            return;
        }
        Intent intent = requireContext().getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            // Not launchable anymore: let the user reassign the slot.
            Toast.makeText(requireContext(), pkg, Toast.LENGTH_SHORT).show();
            openDrawer(AppDrawerActivity.MODE_PICK, slot);
        }
    }

    /** Shows the app's launcher icon, or a placeholder if it isn't installed on this build. */
    private void bindFixedApp(ImageView view, String pkg) {
        Drawable icon = highResIcon(pkg);
        if (icon != null) {
            view.setImageDrawable(icon);
        } else {
            view.setImageResource(R.drawable.ic_add);
        }
    }

    /**
     * Loads the launcher icon at a high density bucket so it stays sharp when scaled up to
     * the large card size, instead of upscaling the device-density icon. Falls back to the
     * package manager's default icon, or null if the package isn't installed.
     */
    private Drawable highResIcon(String pkg) {
        LauncherApps launcherApps = (LauncherApps)
                requireContext().getSystemService(android.content.Context.LAUNCHER_APPS_SERVICE);
        if (launcherApps != null) {
            try {
                List<LauncherActivityInfo> activities =
                        launcherApps.getActivityList(pkg, Process.myUserHandle());
                if (!activities.isEmpty()) {
                    Drawable icon = activities.get(0).getIcon(DisplayMetrics.DENSITY_XXXHIGH);
                    if (icon != null) {
                        return icon;
                    }
                }
            } catch (Exception ignored) {
                // Fall back to the default-density icon below.
            }
        }
        try {
            return requireContext().getPackageManager().getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private void launch(String pkg) {
        Intent intent = requireContext().getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            Toast.makeText(requireContext(), pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openDrawer(String mode, int slot) {
        Intent intent = new Intent(requireContext(), AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_MODE, mode);
        intent.putExtra(AppDrawerActivity.EXTRA_SLOT, slot);
        startActivity(intent);
    }
}
