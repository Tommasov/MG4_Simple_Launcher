package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carousel page 2: eight assignable shortcuts, laid out as half cards matching the home
 * page's fourth column. Tap a tile to launch its app, long-press to change or clear it;
 * an empty tile opens the picker straight away.
 */
public class FavoritesGridFragment extends Fragment {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PreferencesManager preferencesManager;
    private View[] slotCards;
    private ImageView[] slotIcons;
    private TextView[] slotLabels;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        preferencesManager = new PreferencesManager(requireContext());

        slotCards = new View[]{
                view.findViewById(R.id.slot_0),
                view.findViewById(R.id.slot_1),
                view.findViewById(R.id.slot_2),
                view.findViewById(R.id.slot_3),
                view.findViewById(R.id.slot_4),
                view.findViewById(R.id.slot_5),
                view.findViewById(R.id.slot_6),
                view.findViewById(R.id.slot_7)};
        slotIcons = new ImageView[]{
                view.findViewById(R.id.slot_icon_0),
                view.findViewById(R.id.slot_icon_1),
                view.findViewById(R.id.slot_icon_2),
                view.findViewById(R.id.slot_icon_3),
                view.findViewById(R.id.slot_icon_4),
                view.findViewById(R.id.slot_icon_5),
                view.findViewById(R.id.slot_icon_6),
                view.findViewById(R.id.slot_icon_7)};
        slotLabels = new TextView[]{
                view.findViewById(R.id.slot_label_0),
                view.findViewById(R.id.slot_label_1),
                view.findViewById(R.id.slot_label_2),
                view.findViewById(R.id.slot_label_3),
                view.findViewById(R.id.slot_label_4),
                view.findViewById(R.id.slot_label_5),
                view.findViewById(R.id.slot_label_6),
                view.findViewById(R.id.slot_label_7)};

        for (int i = 0; i < PreferencesManager.GRID_FAVORITE_COUNT; i++) {
            final int slot = i;
            slotCards[i].setOnClickListener(v -> onSlotClick(slot));
            slotCards[i].setOnLongClickListener(v -> {
                onSlotLongClick(slot);
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // A slot may have been (re)assigned in the picker, so rebind every time.
        reload();
    }

    /**
     * Resolves every slot's label and icon off the main thread: eight package lookups are
     * enough to stutter the carousel swipe on the head unit if done inline.
     */
    private void reload() {
        final Context ctx = requireContext().getApplicationContext();
        executor.execute(() -> {
            PackageManager pm = ctx.getPackageManager();
            int count = PreferencesManager.GRID_FAVORITE_COUNT;
            String[] labels = new String[count];
            Drawable[] icons = new Drawable[count];
            for (int i = 0; i < count; i++) {
                String pkg = preferencesManager.getGridFavorite(i);
                if (pkg == null) {
                    continue;
                }
                CharSequence label = LaunchTargets.labelFor(ctx, pkg);
                Drawable icon = LaunchTargets.iconFor(ctx, pkg);
                if (label != null && icon != null) {
                    labels[i] = label.toString();
                    icons[i] = icon;
                } else {
                    // App uninstalled, or a screen this build no longer knows: free the slot.
                    preferencesManager.clearGridFavorite(i);
                }
            }
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                bind(labels, icons);
            });
        });
    }

    private void bind(String[] labels, Drawable[] icons) {
        for (int i = 0; i < labels.length; i++) {
            if (labels[i] == null) {
                slotIcons[i].setImageResource(R.drawable.ic_add);
                slotLabels[i].setText(R.string.add_favorite);
            } else {
                // Keep the placeholder if the icon failed to load but the app is installed.
                if (icons[i] != null) {
                    slotIcons[i].setImageDrawable(icons[i]);
                } else {
                    slotIcons[i].setImageResource(R.drawable.ic_add);
                }
                slotLabels[i].setText(labels[i]);
            }
        }
    }

    private void onSlotClick(int slot) {
        String pkg = preferencesManager.getGridFavorite(slot);
        if (pkg == null) {
            openPicker(slot);
            return;
        }
        if (!AppLauncher.launch(requireContext(), pkg)) {
            // Not launchable anymore: say so plainly and let the user reassign the slot.
            Dialogs.toast(requireContext(), R.string.target_unavailable, Toast.LENGTH_SHORT);
            openPicker(slot);
        }
    }

    private void onSlotLongClick(int slot) {
        String pkg = preferencesManager.getGridFavorite(slot);
        if (pkg == null) {
            openPicker(slot);
            return;
        }
        // Unlike the three home cards, a shortcut tile can also be emptied again.
        Dialogs.builder(requireContext())
                .setTitle(slotLabels[slot].getText())
                .setItems(new CharSequence[]{
                        getString(R.string.grid_slot_change),
                        getString(R.string.grid_slot_remove)}, (dialog, which) -> {
                    if (which == 0) {
                        openPicker(slot);
                    } else {
                        preferencesManager.clearGridFavorite(slot);
                        reload();
                    }
                })
                .show();
    }

    private void openPicker(int slot) {
        Intent intent = new Intent(requireContext(), AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_MODE, AppDrawerActivity.MODE_PICK);
        intent.putExtra(AppDrawerActivity.EXTRA_TARGET, AppDrawerActivity.TARGET_GRID);
        intent.putExtra(AppDrawerActivity.EXTRA_SLOT, slot);
        startActivity(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
