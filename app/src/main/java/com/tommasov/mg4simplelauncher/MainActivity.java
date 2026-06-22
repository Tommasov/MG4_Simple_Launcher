package com.tommasov.mg4simplelauncher;

import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.tommasov.mg4simplelauncher.update.UpdateManager;

/**
 * Home screen. Three vertical cards each launch one chosen favorite app; a long press
 * re-assigns the slot. The fourth column opens the "all apps" and "system apps" drawers.
 */
public class MainActivity extends AppCompatActivity {

    private PreferencesManager preferencesManager;

    private View[] favoriteCards;
    private ImageView[] favoriteIcons;
    private TextView[] favoriteLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        preferencesManager = new PreferencesManager(this);

        favoriteCards = new View[]{
                findViewById(R.id.card_fav_1),
                findViewById(R.id.card_fav_2),
                findViewById(R.id.card_fav_3)};
        favoriteIcons = new ImageView[]{
                findViewById(R.id.icon_fav_1),
                findViewById(R.id.icon_fav_2),
                findViewById(R.id.icon_fav_3)};
        favoriteLabels = new TextView[]{
                findViewById(R.id.label_fav_1),
                findViewById(R.id.label_fav_2),
                findViewById(R.id.label_fav_3)};

        for (int i = 0; i < PreferencesManager.FAVORITE_COUNT; i++) {
            final int slot = i;
            favoriteCards[i].setOnClickListener(v -> onFavoriteClick(slot));
            favoriteCards[i].setOnLongClickListener(v -> {
                openDrawer(AppDrawerActivity.MODE_PICK, slot);
                return true;
            });
        }

        findViewById(R.id.card_all_apps).setOnClickListener(
                v -> openDrawer(AppDrawerActivity.MODE_ALL, -1));
        findViewById(R.id.card_system_apps).setOnClickListener(
                v -> openDrawer(AppDrawerActivity.MODE_SYSTEM, -1));

        // Silently check for a newer build on launch; prompts the user only if one exists.
        new UpdateManager(this).checkForUpdates(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // A favorite may have been (re)assigned in the picker, so rebind every time.
        for (int i = 0; i < PreferencesManager.FAVORITE_COUNT; i++) {
            bindFavorite(i);
        }
    }

    private void bindFavorite(int slot) {
        String pkg = preferencesManager.getFavorite(slot);
        PackageManager pm = getPackageManager();
        if (pkg != null) {
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                Drawable icon = pm.getApplicationIcon(ai);
                CharSequence label = pm.getApplicationLabel(ai);
                favoriteIcons[slot].setImageDrawable(icon);
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
        Intent intent = getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else {
            // Not launchable anymore: let the user reassign the slot.
            Toast.makeText(this, pkg, Toast.LENGTH_SHORT).show();
            openDrawer(AppDrawerActivity.MODE_PICK, slot);
        }
    }

    private void openDrawer(String mode, int slot) {
        Intent intent = new Intent(this, AppDrawerActivity.class);
        intent.putExtra(AppDrawerActivity.EXTRA_MODE, mode);
        intent.putExtra(AppDrawerActivity.EXTRA_SLOT, slot);
        startActivity(intent);
    }
}