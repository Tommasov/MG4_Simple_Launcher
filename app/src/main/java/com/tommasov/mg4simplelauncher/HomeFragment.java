package com.tommasov.mg4simplelauncher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Carousel page 1: the launcher home. Three vertical cards each launch one chosen favorite
 * app (long-press to re-assign); the fourth column opens the drawers and the fixed shortcuts.
 */
public class HomeFragment extends Fragment {

    // Android 9 default Settings and Files packages, launched by the two fixed shortcuts.
    /**
     * What the two small slots hold until the driver says otherwise. Files first, Settings
     * second, reading left to right as they always have.
     */
    private static final String[] DOCK_DEFAULTS = {
            "com.android.documentsui", "com.android.settings"};

    private PreferencesManager preferencesManager;
    /** Which arrangement is currently on screen, so a change in settings can be noticed. */
    private boolean sixTiles;

    /** Layout ids by slot. The first three are shared by both arrangements. */
    private static final int[] CARD_IDS = {
            R.id.card_fav_1, R.id.card_fav_2, R.id.card_fav_3,
            R.id.card_fav_4, R.id.card_fav_5, R.id.card_fav_6};
    private static final int[] ICON_IDS = {
            R.id.icon_fav_1, R.id.icon_fav_2, R.id.icon_fav_3,
            R.id.icon_fav_4, R.id.icon_fav_5, R.id.icon_fav_6};
    private static final int[] LABEL_IDS = {
            R.id.label_fav_1, R.id.label_fav_2, R.id.label_fav_3,
            R.id.label_fav_4, R.id.label_fav_5, R.id.label_fav_6};

    private View[] favoriteCards;
    private ImageView[] favoriteIcons;
    private TextView[] favoriteLabels;
    /** The two small shortcuts under "All apps": index 0 on the left, 1 on the right. */
    private final ImageView[] dockIcons = new ImageView[PreferencesManager.DOCK_COUNT];

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
        inflateArrangement(preferencesManager.isSixTileHomeEnabled());
    }

    /**
     * Puts one of the two arrangements into the page and wires it up.
     *
     * <p>The favourites are either three large cards or six half tiles, and the choice is a
     * setting the driver can change while this page sits in the background. Swapping the
     * contents of the frame is enough: rebuilding the carousel page would be heavier and
     * would lose the scroll position of the pages either side.
     */
    private void inflateArrangement(boolean sixTiles) {
        ViewGroup container = requireView().findViewById(R.id.home_container);
        container.removeAllViews();
        LayoutInflater.from(requireContext()).inflate(
                sixTiles ? R.layout.part_home_six : R.layout.part_home_classic, container, true);
        this.sixTiles = sixTiles;

        int count = sixTiles
                ? PreferencesManager.FAVORITE_COUNT_SIX
                : PreferencesManager.FAVORITE_COUNT;
        favoriteCards = new View[count];
        favoriteIcons = new ImageView[count];
        favoriteLabels = new TextView[count];
        for (int i = 0; i < count; i++) {
            // Ids run from 1 in the layouts, slots from 0 in storage.
            favoriteCards[i] = container.findViewById(CARD_IDS[i]);
            favoriteIcons[i] = container.findViewById(ICON_IDS[i]);
            favoriteLabels[i] = container.findViewById(LABEL_IDS[i]);

            final int slot = i;
            favoriteCards[i].setOnClickListener(v -> onFavoriteClick(slot));
            favoriteCards[i].setOnLongClickListener(v -> {
                openDrawer(AppDrawerActivity.MODE_PICK, slot);
                return true;
            });
        }

        container.findViewById(R.id.card_all_apps).setOnClickListener(
                v -> openDrawer(AppDrawerActivity.MODE_ALL, -1));

        // Two fixed shortcuts to the Android 9 default Settings and Files apps.
        dockIcons[0] = container.findViewById(R.id.icon_files);
        dockIcons[1] = container.findViewById(R.id.icon_settings);
        for (int i = 0; i < dockIcons.length; i++) {
            final int slot = i;
            dockIcons[i].setOnClickListener(v -> launch(dockPackage(slot)));
            dockIcons[i].setOnLongClickListener(v -> {
                onDockLongClick(slot);
                return true;
            });
        }

        bindAll();
    }

    @Override
    public void onResume() {
        super.onResume();
        // The arrangement can have been changed in settings while this page was in the
        // background; rebuilding it here is the only moment the driver cannot see.
        if (preferencesManager.isSixTileHomeEnabled() != sixTiles) {
            inflateArrangement(!sixTiles);
            return;
        }
        bindAll();
    }

    /** Rebinds everything: a favourite may have been assigned, or an app installed. */
    private void bindAll() {
        for (int i = 0; i < favoriteCards.length; i++) {
            bindFavorite(i);
        }
        // Re-resolve the fixed shortcut icons too, in case a target app was installed/updated.
        for (int i = 0; i < dockIcons.length; i++) {
            bindFixedApp(dockIcons[i], dockPackage(i));
        }
    }

    private void bindFavorite(int slot) {
        String pkg = preferencesManager.getFavorite(slot);
        PackageManager pm = requireContext().getPackageManager();
        if (pkg != null) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence label = pm.getApplicationLabel(ai);
                favoriteIcons[slot].setImageDrawable(AppIcons.highRes(requireContext(), pkg));
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
        if (!AppLauncher.launch(requireContext(), pkg)) {
            // Not launchable anymore: let the user reassign the slot.
            Toast.makeText(requireContext(), pkg, Toast.LENGTH_SHORT).show();
            openDrawer(AppDrawerActivity.MODE_PICK, slot);
        }
    }

    /** Shows the app's launcher icon, or a placeholder if it isn't installed on this build. */
    /**
     * Long-press on one of the two small slots. Untouched, it goes straight to the picker,
     * the way the favourite cards do; once the driver has put something of their own there,
     * it offers the way back as well — these are the only slots with a factory app to return
     * to, and without this the only route back to Settings would be to remember its name in
     * a list of forty.
     */
    private void onDockLongClick(int slot) {
        if (preferencesManager.getDockShortcut(slot) == null) {
            openDrawer(AppDrawerActivity.MODE_PICK, slot, AppDrawerActivity.TARGET_DOCK);
            return;
        }
        Dialogs.builder(requireContext())
                .setItems(new CharSequence[]{
                        getString(R.string.grid_slot_change),
                        getString(R.string.dock_slot_reset)}, (dialog, which) -> {
                    if (which == 0) {
                        openDrawer(AppDrawerActivity.MODE_PICK, slot,
                                AppDrawerActivity.TARGET_DOCK);
                    } else {
                        preferencesManager.clearDockShortcut(slot);
                        bindFixedApp(dockIcons[slot], dockPackage(slot));
                    }
                })
                .show();
    }

    /** The driver's choice for one of the two small slots, or the factory app. */
    private String dockPackage(int slot) {
        String chosen = preferencesManager.getDockShortcut(slot);
        return chosen != null ? chosen : DOCK_DEFAULTS[slot];
    }

    private void bindFixedApp(ImageView view, String pkg) {
        Drawable icon = AppIcons.highRes(requireContext(), pkg);
        if (icon != null) {
            view.setImageDrawable(icon);
        } else {
            view.setImageResource(R.drawable.ic_add);
        }
    }

    private void launch(String pkg) {
        if (!AppLauncher.launch(requireContext(), pkg)) {
            Toast.makeText(requireContext(), pkg, Toast.LENGTH_SHORT).show();
        }
    }

    private void openDrawer(String mode, int slot) {
        openDrawer(mode, slot, AppDrawerActivity.TARGET_HOME);
    }

    private void openDrawer(String mode, int slot, String target) {
        Intent intent = new Intent(requireContext(), AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_MODE, mode);
        intent.putExtra(AppDrawerActivity.EXTRA_SLOT, slot);
        intent.putExtra(AppDrawerActivity.EXTRA_TARGET, target);
        startActivity(intent);
    }
}
