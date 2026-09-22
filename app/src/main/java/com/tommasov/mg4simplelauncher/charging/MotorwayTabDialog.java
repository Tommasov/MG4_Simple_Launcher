package com.tommasov.mg4simplelauncher.charging;

import android.app.Activity;
import android.content.Context;
import android.location.Location;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.tommasov.mg4simplelauncher.Dialogs;
import com.tommasov.mg4simplelauncher.PreferencesManager;
import com.tommasov.mg4simplelauncher.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Defines what the motorway tab looks for: a power floor, and optionally the networks that
 * line the motorways where this car is driven.
 *
 * <p>The networks are not a list this launcher carries. Open Charge Map's own operator
 * reference runs to thousands of entries worldwide, and a hard-coded table per country would
 * be wrong the first time a network was bought or renamed. Instead the choices are whatever
 * has powerful stations around the car right now, commonest first — which in Italy offers
 * Free To X, Ewiva and Ionity, and in France offers French networks, with nothing to maintain.
 *
 * <p>Picking none is a real answer and the default one: the tab then means "the most powerful
 * ones nearby", which works in every country. That matters more than it sounds, because the
 * version of this tab that was tied to a single operator returned an empty list the moment
 * the car crossed a border.
 */
public final class MotorwayTabDialog {

    /** How far to look when asking which networks are around. */
    private static final int OPERATOR_SCAN_KM = 120;

    private MotorwayTabDialog() {
    }

    /** Shows the dialogue; {@code onChanged} runs if anything was saved. */
    public static void show(@NonNull Activity activity, @NonNull Runnable onChanged) {
        PreferencesManager preferences = new PreferencesManager(activity);
        Context scaled = Dialogs.scaled(activity);
        int pad = activity.getResources().getDimensionPixelSize(R.dimen.card_gap);

        LinearLayout body = new LinearLayout(scaled);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(pad, pad, pad, 0);

        body.addView(caption(scaled, activity.getString(R.string.motorway_power)));
        RadioGroup powers = new RadioGroup(scaled);
        powers.setOrientation(RadioGroup.HORIZONTAL);
        int[] choices = PreferencesManager.MOTORWAY_POWERS;
        int current = preferences.getMotorwayMinPowerKw();
        for (int kw : choices) {
            RadioButton button = (RadioButton) activity.getLayoutInflater()
                    .inflate(R.layout.part_choice_item, powers, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    activity.getResources().getDimensionPixelSize(R.dimen.settings_tab_height),
                    1f);
            button.setLayoutParams(params);
            button.setId(View.generateViewId());
            button.setText(activity.getString(R.string.motorway_power_value, kw));
            button.setTag(kw);
            powers.addView(button);
            if (kw == current) {
                powers.check(button.getId());
            }
        }
        body.addView(powers);

        body.addView(caption(scaled, activity.getString(R.string.motorway_operators)));
        LinearLayout operators = new LinearLayout(scaled);
        operators.setOrientation(LinearLayout.VERTICAL);
        body.addView(operators);

        Set<String> chosen = new LinkedHashSet<>(preferences.getMotorwayOperators());
        loadOperators(activity, current, operators, chosen);
        // The list answers to the threshold above it, not to the saved one: raising the bar
        // while the dialogue is open is exactly when a driver wants to see which networks
        // survive it. What is already ticked is carried across, so changing your mind about
        // the power does not silently drop the networks you had chosen.
        powers.setOnCheckedChangeListener((group, checkedId) -> {
            View button = group.findViewById(checkedId);
            if (button == null) {
                return;
            }
            chosen.clear();
            for (OpenChargeMapClient.Operator operator : ticked(operators)) {
                chosen.add(operator.id);
            }
            loadOperators(activity, (Integer) button.getTag(), operators, chosen);
        });

        Dialogs.builder(activity)
                .setTitle(R.string.settings_motorway)
                .setView(Dialogs.scroll(scaled, body))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.motorway_save, (dialog, which) -> {
                    View checked = powers.findViewById(powers.getCheckedRadioButtonId());
                    if (checked != null) {
                        preferences.setMotorwayMinPowerKw((Integer) checked.getTag());
                    }
                    List<OpenChargeMapClient.Operator> picked = ticked(operators);
                    Set<String> ids = new LinkedHashSet<>();
                    StringBuilder names = new StringBuilder();
                    for (OpenChargeMapClient.Operator operator : picked) {
                        ids.add(operator.id);
                        if (names.length() > 0) {
                            names.append(", ");
                        }
                        names.append(operator.name);
                    }
                    preferences.setMotorwayOperators(ids, names.toString());
                    onChanged.run();
                })
                .show();
    }

    /**
     * Fills the network list once the lookup answers.
     *
     * <p>Without a position there is nothing to ask about, and rather than pretend, the line
     * says so: the tab still works on power alone, which is exactly what an unconfigured one
     * does.
     */
    private static void loadOperators(@NonNull Activity activity, int minPowerKw,
                                      @NonNull LinearLayout container,
                                      @NonNull Set<String> chosen) {
        container.removeAllViews();
        TextView loading = caption(container.getContext(),
                activity.getString(R.string.motorway_looking));
        container.addView(loading);
        Location origin = LocationResolver.lastKnown(activity);
        if (origin == null) {
            loading.setText(R.string.motorway_no_position);
            return;
        }
        new OpenChargeMapClient().operatorsNear(origin.getLatitude(), origin.getLongitude(),
                OPERATOR_SCAN_KM, minPowerKw,
                new OpenChargeMapClient.OperatorCallback() {
                    @Override
                    public void onResult(@NonNull List<OpenChargeMapClient.Operator> found) {
                        if (activity.isFinishing() || activity.isDestroyed()) {
                            return;
                        }
                        container.removeAllViews();
                        if (found.isEmpty()) {
                            container.addView(caption(container.getContext(),
                                    activity.getString(R.string.motorway_none_found)));
                            return;
                        }
                        for (OpenChargeMapClient.Operator operator : found) {
                            container.addView(row(activity, container, operator,
                                    chosen.contains(operator.id)));
                        }
                    }

                    @Override
                    public void onError(@NonNull Exception e) {
                        if (!activity.isFinishing() && !activity.isDestroyed()) {
                            loading.setText(R.string.motorway_lookup_failed);
                        }
                    }
                });
    }

    @NonNull
    private static CheckBox row(@NonNull Activity activity, @NonNull LinearLayout parent,
                                @NonNull OpenChargeMapClient.Operator operator, boolean on) {
        CheckBox box = (CheckBox) activity.getLayoutInflater()
                .inflate(R.layout.part_choice_check, parent, false);
        box.setText(activity.getString(R.string.motorway_operator_row, operator.name,
                operator.stations));
        box.setChecked(on);
        box.setTag(operator);
        return box;
    }

    @NonNull
    private static List<OpenChargeMapClient.Operator> ticked(@NonNull LinearLayout container) {
        List<OpenChargeMapClient.Operator> picked = new ArrayList<>();
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof CheckBox && ((CheckBox) child).isChecked()) {
                picked.add((OpenChargeMapClient.Operator) child.getTag());
            }
        }
        return picked;
    }

    @NonNull
    private static TextView caption(@NonNull Context context, @NonNull String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setGravity(Gravity.START);
        view.setTextColor(ContextCompat.getColor(context, R.color.text_secondary));
        int pad = context.getResources().getDimensionPixelSize(R.dimen.card_gap) / 2;
        view.setPadding(0, pad, 0, pad);
        return view;
    }
}
