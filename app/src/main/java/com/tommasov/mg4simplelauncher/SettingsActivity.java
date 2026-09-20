package com.tommasov.mg4simplelauncher;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.tommasov.mg4simplelauncher.charging.FactoryNavigator;
import com.tommasov.mg4simplelauncher.diag.DiagnosticsActivity;
import com.tommasov.mg4simplelauncher.diag.DiagnosticsLog;
import com.tommasov.mg4simplelauncher.update.UpdateManager;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SettingsActivity extends AppCompatActivity {

    private PreferencesManager preferences;
    private UpdateManager updateManager;
    private RadioGroup homePageGroup;
    private View shortcutsHomeOption;
    private View chargingHomeOption;

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
        bindBattery();
        bindNavigator();
        bindBetaBadge();
        bindUpdateOnLaunch();
        bindBetaChannel();
        bindUpdates();
        bindDiagnostics();
    }

    private void bindHomePage() {
        homePageGroup = findViewById(R.id.settings_home_page_group);
        shortcutsHomeOption = findViewById(R.id.home_page_shortcuts);
        chargingHomeOption = findViewById(R.id.home_page_system);

        homePageGroup.check(buttonFor(preferences.getHomePage()));
        homePageGroup.setOnCheckedChangeListener(
                (group, checkedId) -> preferences.setHomePage(pageFor(checkedId)));
        updateShortcutsOptionVisibility(preferences.isShortcutsPageEnabled());
        updateChargingOptionVisibility(preferences.isChargingPageEnabled());
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
        bindToggle(R.id.toggle_charging_page,
                R.string.settings_charging_page,
                R.string.settings_charging_page_hint,
                preferences.isChargingPageEnabled(),
                checked -> {
                    preferences.setChargingPageEnabled(checked);
                    // Same fallback as the shortcuts page: the launcher cannot open on a
                    // screen that is no longer in the carousel.
                    if (!checked
                            && preferences.getHomePage() == HomePagerAdapter.PAGE_CHARGING) {
                        preferences.setHomePage(HomePagerAdapter.PAGE_HOME);
                        homePageGroup.check(R.id.home_page_main);
                    }
                    updateChargingOptionVisibility(checked);
                });
    }

    /** The beta mark, on only while the driver is actually on that channel. */
    private void bindBetaBadge() {
        findViewById(R.id.settings_beta_badge).setVisibility(
                preferences.isBetaChannelEnabled() ? View.VISIBLE : View.GONE);
    }

    /**
     * Which battery this car has. Asked rather than detected because no adapter call reports
     * it, and without it the launcher cannot turn a consumption figure into kilometres — the
     * whole correction for the route ahead rests on this one number.
     */
    private void bindBattery() {
        int[] sizes = PreferencesManager.BATTERY_SIZES;
        int[] ids = {R.id.battery_51, R.id.battery_64, R.id.battery_77};
        RadioGroup group = findViewById(R.id.settings_battery_group);
        for (int i = 0; i < ids.length && i < sizes.length; i++) {
            RadioButton button = findViewById(ids[i]);
            button.setText(getString(R.string.settings_battery_kwh, sizes[i]));
            if (sizes[i] == preferences.getBatteryCapacityKwh()) {
                group.check(ids[i]);
            }
        }
        group.setOnCheckedChangeListener((g, checkedId) -> {
            for (int i = 0; i < ids.length && i < sizes.length; i++) {
                if (ids[i] == checkedId) {
                    preferences.setBatteryCapacityKwh(sizes[i]);
                }
            }
        });
    }

    /**
     * Where destinations go, offered only when this vehicle has more than one answer.
     *
     * <p>A car with the factory navigator and nothing else has nothing to choose, and neither
     * has a car with one map app and no factory navigator: the row would be a control with a
     * single position, which is worse than no control at all.
     *
     * <p>The first entry is always "no app of my own": on a car that has the factory
     * navigator that means sending the stop to it, and on a car that does not it means
     * letting Android ask, which is what already happens today. One stored value, two honest
     * readings, and a driver who picked an app can always get back to neither.
     */
    private void bindNavigator() {
        View row = findViewById(R.id.settings_navigator_row);
        boolean factory = FactoryNavigator.hasFactoryNavigator(this);
        List<ResolveInfo> apps = FactoryNavigator.geoHandlers(this);
        if ((factory ? 1 : 0) + apps.size() < 2) {
            row.setVisibility(View.GONE);
            return;
        }

        List<String> values = new ArrayList<>();
        List<CharSequence> labels = new ArrayList<>();
        values.add(PreferencesManager.NAVIGATOR_FACTORY);
        labels.add(getString(factory ? R.string.settings_navigator_factory
                : R.string.settings_navigator_ask));
        PackageManager packages = getPackageManager();
        for (ResolveInfo info : apps) {
            values.add(info.activityInfo.packageName);
            labels.add(info.loadLabel(packages));
        }

        row.setVisibility(View.VISIBLE);
        showNavigator(values, labels);
        row.setOnClickListener(v -> askNavigator(values, labels));
    }

    /** The current choice, on the row, so it can be read without opening anything. */
    private void showNavigator(@NonNull List<String> values, @NonNull List<CharSequence> labels) {
        int at = values.indexOf(preferences.getNavigatorTarget());
        TextView value = findViewById(R.id.settings_navigator_value);
        value.setText(labels.get(at < 0 ? 0 : at));
    }

    /**
     * The picker itself. Built by hand rather than with {@code setSingleChoiceItems} because
     * the list needs the sentence above it: what a driver gives up by choosing an app of
     * their own is the part they cannot work out from the names.
     */
    private void askNavigator(@NonNull List<String> values, @NonNull List<CharSequence> labels) {
        Context scaled = Dialogs.scaled(this);
        int pad = getResources().getDimensionPixelSize(R.dimen.card_gap);

        TextView hint = new TextView(scaled);
        hint.setText(R.string.settings_navigator_hint);
        hint.setTextColor(getColor(R.color.text_secondary));
        hint.setPadding(0, 0, 0, pad);

        RadioGroup group = new RadioGroup(scaled);
        group.setOrientation(RadioGroup.VERTICAL);

        LinearLayout body = new LinearLayout(scaled);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, 0);
        body.addView(hint);
        body.addView(group);

        int chosen = values.indexOf(preferences.getNavigatorTarget());
        for (int i = 0; i < values.size(); i++) {
            RadioButton button = (RadioButton) getLayoutInflater()
                    .inflate(R.layout.part_choice_item, group, false);
            button.setId(View.generateViewId());
            button.setText(labels.get(i));
            group.addView(button);
            if (i == (chosen < 0 ? 0 : chosen)) {
                group.check(button.getId());
            }
        }

        AlertDialog dialog = Dialogs.builder(this)
                .setTitle(R.string.settings_navigator)
                .setView(body)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        group.setOnCheckedChangeListener((g, id) -> {
            int index = g.indexOfChild(g.findViewById(id));
            if (index >= 0) {
                preferences.setNavigatorTarget(values.get(index));
                showNavigator(values, labels);
            }
            dialog.dismiss();
        });
        dialog.show();
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
                        bindBetaBadge();
                        return;
                    }
                    // Warn on the way in, never on the way out: joining is what has a
                    // one-way consequence, since Android will not install the older stable
                    // build over a newer beta.
                    Dialogs.builder(this)
                            .setTitle(R.string.beta_channel_title)
                            .setMessage(R.string.beta_channel_warning)
                            .setPositiveButton(R.string.beta_channel_join, (dialog, which) -> {
                                preferences.setBetaChannelEnabled(true);
                                // The mark follows the switch at once: leaving it until the
                                // screen is reopened would make the driver doubt the toggle.
                                bindBetaBadge();
                            })
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

    private void updateChargingOptionVisibility(boolean chargingEnabled) {
        chargingHomeOption.setVisibility(chargingEnabled ? View.VISIBLE : View.GONE);
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
