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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Carousel page 2: a grid of assignable shortcuts, a wider complement to the three home
 * cards. Tap a tile to launch its app, long-press to change or clear it; an empty tile
 * opens the picker straight away.
 */
public class FavoritesGridFragment extends Fragment {

    private static final int SPAN_COUNT = 6;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private PreferencesManager preferencesManager;
    private FavoriteGridAdapter adapter;

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
        adapter = new FavoriteGridAdapter(this::onSlotClick, this::onSlotLongClick);

        RecyclerView grid = view.findViewById(R.id.favorites_grid);
        grid.setLayoutManager(new GridLayoutManager(requireContext(), SPAN_COUNT));
        grid.setAdapter(adapter);
    }

    @Override
    public void onResume() {
        super.onResume();
        // A slot may have been (re)assigned in the picker, so rebind every time.
        reload();
    }

    /**
     * Resolves every slot's label and icon off the main thread: twelve package lookups are
     * enough to stutter the carousel swipe on the head unit if done inline.
     */
    private void reload() {
        final Context ctx = requireContext().getApplicationContext();
        executor.execute(() -> {
            PackageManager pm = ctx.getPackageManager();
            List<FavoriteGridAdapter.Slot> slots = new ArrayList<>();
            for (int i = 0; i < PreferencesManager.GRID_FAVORITE_COUNT; i++) {
                String pkg = preferencesManager.getGridFavorite(i);
                String label = null;
                Drawable icon = null;
                if (pkg != null) {
                    try {
                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                        label = pm.getApplicationLabel(ai).toString();
                        icon = AppIcons.highRes(ctx, pkg);
                    } catch (PackageManager.NameNotFoundException e) {
                        // App was uninstalled; free the slot and show it as empty.
                        preferencesManager.clearGridFavorite(i);
                        pkg = null;
                    }
                }
                slots.add(new FavoriteGridAdapter.Slot(i, pkg, label, icon));
            }
            mainHandler.post(() -> {
                if (!isAdded()) {
                    return;
                }
                adapter.submit(slots);
            });
        });
    }

    private void onSlotClick(FavoriteGridAdapter.Slot slot) {
        if (slot.packageName == null) {
            openPicker(slot.index);
            return;
        }
        if (!AppLauncher.launch(requireContext(), slot.packageName)) {
            // Not launchable anymore: let the user reassign the slot.
            Toast.makeText(requireContext(), slot.packageName, Toast.LENGTH_SHORT).show();
            openPicker(slot.index);
        }
    }

    private void onSlotLongClick(FavoriteGridAdapter.Slot slot) {
        if (slot.packageName == null) {
            openPicker(slot.index);
            return;
        }
        // Unlike the three home cards, a grid tile can also be emptied again.
        new AlertDialog.Builder(requireContext())
                .setTitle(slot.label)
                .setItems(new CharSequence[]{
                        getString(R.string.grid_slot_change),
                        getString(R.string.grid_slot_remove)}, (dialog, which) -> {
                    if (which == 0) {
                        openPicker(slot.index);
                    } else {
                        preferencesManager.clearGridFavorite(slot.index);
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
