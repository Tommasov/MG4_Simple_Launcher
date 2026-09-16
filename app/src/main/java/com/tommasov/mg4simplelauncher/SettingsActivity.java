package com.tommasov.mg4simplelauncher;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.SwitchCompat;

import com.tommasov.mg4simplelauncher.diag.DiagnosticsActivity;
import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;
import com.tommasov.mg4simplelauncher.update.UpdateManager;

import java.util.function.Consumer;

/**
 * Launcher settings: which carousel page opens on launch, whether the shortcuts page
 * exists at all, and a manual update check.
 *
 * <p>Changes are saved as they are made rather than behind an OK button — there is no
 * cancel to honour, and a driver should be able to leave at any point without losing what
 * they just set. {@link MainActivity} picks the new shape up when it resumes.
 */
public class SettingsActivity extends AppCompatActivity {

    private PreferencesManager preferences;
    private UpdateManager updateManager;
    private RadioGroup homePageGroup;
    private View shortcutsHomeOption;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = new PreferencesManager(this);
        updateManager = new UpdateManager(this);

        findViewById(R.id.settings_back_button).setOnClickListener(v -> finish());

        bindHomePage();
        bindFeatures();
        bindSixTileHome();
        bindUpdateOnLaunch();
        bindBetaChannel();
        bindUpdates();
        bindDiagnostics();
    }

    private void bindHomePage() {
        homePageGroup = findViewById(R.id.settings_home_page_group);
        shortcutsHomeOption = findViewById(R.id.home_page_shortcuts);

        homePageGroup.check(buttonFor(preferences.getHomePage()));
        homePageGroup.setOnCheckedChangeListener(
                (group, checkedId) -> preferences.setHomePage(pageFor(checkedId)));
        updateShortcutsOptionVisibility(preferences.isShortcutsPageEnabled());
    }

    private void bindFeatures() {
        bindToggle(R.id.toggle_shortcuts_page,
                R.string.settings_shortcuts_page,
                R.string.settings_shortcuts_page_hint,
                preferences.isShortcutsPageEnabled(),
                checked -> {
                    preferences.setShortcutsPageEnabled(checked);
                    // Turning the page off while it was the launch page would leave the
                    // launcher opening on a screen that no longer exists, so fall back.
                    if (!checked
                            && preferences.getHomePage() == HomePagerAdapter.PAGE_SHORTCUTS) {
                        preferences.setHomePage(HomePagerAdapter.PAGE_HOME);
                        homePageGroup.check(R.id.home_page_main);
                    }
                    updateShortcutsOptionVisibility(checked);
                });
    }

    private void bindSixTileHome() {
        bindToggle(R.id.toggle_six_tile_home,
                R.string.settings_six_tile_home,
                R.string.settings_six_tile_home_hint,
                preferences.isSixTileHomeEnabled(),
                preferences::setSixTileHomeEnabled);
    }

    private void bindUpdateOnLaunch() {
        bindToggle(R.id.toggle_update_on_launch,
                R.string.settings_update_on_launch,
                R.string.settings_update_on_launch_hint,
                preferences.isUpdateCheckOnLaunchEnabled(),
                preferences::setUpdateCheckOnLaunchEnabled);
    }

    private void bindBetaChannel() {
        bindToggle(R.id.toggle_beta_channel,
                R.string.beta_channel_title,
                R.string.beta_channel_hint,
                preferences.isBetaChannelEnabled(),
                checked -> {
                    if (!checked) {
                        preferences.setBetaChannelEnabled(false);
                        return;
                    }
                    // Warn on the way in, never on the way out: joining is what has a
                    // one-way consequence, since Android will not install the older stable
                    // build over a newer beta.
                    Dialogs.builder(this)
                            .setTitle(R.string.beta_channel_title)
                            .setMessage(R.string.beta_channel_warning)
                            .setPositiveButton(R.string.beta_channel_join,
                                    (dialog, which) -> preferences.setBetaChannelEnabled(true))
                            .setNegativeButton(R.string.update_action_later,
                                    (dialog, which) -> revertToggle(R.id.toggle_beta_channel))
                            .setOnCancelListener(
                                    dialog -> revertToggle(R.id.toggle_beta_channel))
                            .show();
                });
    }

    /** Puts a switch back after the user declined the dialog it opened. */
    private void revertToggle(int rowId) {
        SwitchCompat toggle = findViewById(rowId).findViewById(R.id.toggle_switch);
        toggle.setChecked(false);
    }

    /**
     * Wires one row of the feature list. Adding a feature is an {@code <include>} in the
     * layout plus one call here.
     *
     * <p>The row carries the click, not the switch: the switch itself is a small target to
     * hit from the driver's seat, and a row-sized one is far easier.
     */
    private void bindToggle(int rowId, @StringRes int labelRes, @StringRes int hintRes,
                            boolean initial, @NonNull Consumer<Boolean> onChanged) {
        View row = findViewById(rowId);
        ((TextView) row.findViewById(R.id.toggle_label)).setText(labelRes);
        ((TextView) row.findViewById(R.id.toggle_hint)).setText(hintRes);

        SwitchCompat toggle = row.findViewById(R.id.toggle_switch);
        toggle.setChecked(initial);
        row.setOnClickListener(v -> {
            boolean checked = !toggle.isChecked();
            toggle.setChecked(checked);
            onChanged.accept(checked);
        });
    }

    /** The shortcuts page cannot be the launch page while it is switched off. */
    private void updateShortcutsOptionVisibility(boolean shortcutsEnabled) {
        shortcutsHomeOption.setVisibility(shortcutsEnabled ? View.VISIBLE : View.GONE);
    }

    private void bindUpdates() {
        TextView version = findViewById(R.id.settings_version);
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText(getString(R.string.settings_version,
                    info.versionName, info.getLongVersionCode()));
        } catch (PackageManager.NameNotFoundException e) {
            version.setText("");
        }
        findViewById(R.id.settings_check_updates)
                .setOnClickListener(v -> updateManager.checkForUpdates(true));
    }

    private void bindDiagnostics() {
        findViewById(R.id.settings_open_technical).setOnClickListener(
                v -> startActivity(new Intent(this, TechnicalDetailsActivity.class)));
        TextView summary = findViewById(R.id.settings_diagnostics_summary);
        int lines = DiagnosticsLog.lineCount(DiagnosticsLog.read(this));
        summary.setText(getResources().getQuantityString(
                R.plurals.diagnostics_lines, lines, lines));
        findViewById(R.id.settings_open_diagnostics).setOnClickListener(
                v -> startActivity(new Intent(this, DiagnosticsActivity.class)));
    }

    private static int buttonFor(int pageKind) {
        switch (pageKind) {
            case HomePagerAdapter.PAGE_SHORTCUTS:
                return R.id.home_page_shortcuts;
            case HomePagerAdapter.PAGE_CHARGING:
                return R.id.home_page_system;
            default:
                return R.id.home_page_main;
        }
    }

    private static int pageFor(int checkedId) {
        if (checkedId == R.id.home_page_shortcuts) {
            return HomePagerAdapter.PAGE_SHORTCUTS;
        }
        if (checkedId == R.id.home_page_system) {
            return HomePagerAdapter.PAGE_CHARGING;
        }
        return HomePagerAdapter.PAGE_HOME;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Tear down any in-flight download/dialog so it can't leak the window or a receiver.
        updateManager.cancel();
    }
}
