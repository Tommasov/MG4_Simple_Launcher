package com.tommasov.mg4simplelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tommasov.mg4simplelauncher.charging.ChargingCardBinder;

/**
 * Carousel page 3, the tools page: the charging points card and the settings card.
 *
 * <p>Nothing here refreshes on a timer any more. The readings that needed one — memory,
 * storage, network — moved into the settings screen with the technical details; what is
 * left is a summary that only changes when the driver changes it, and a charging card that
 * deliberately loads once per visit because Open Charge Map bans callers who poll.
 */
public class SystemInfoFragment extends Fragment {

    private ChargingCardBinder chargingCard;
    private TextView settingsSummary;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_system, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        chargingCard = new ChargingCardBinder(view);
        settingsSummary = view.findViewById(R.id.settings_card_summary);

        // The round button is the visible affordance, but the whole card answers too: one
        // more place to hit is worth more than the tidiness of a single target here.
        View.OnClickListener openSettings =
                v -> startActivity(new Intent(requireContext(), SettingsActivity.class));
        view.findViewById(R.id.settings_card).setOnClickListener(openSettings);
        view.findViewById(R.id.settings_card_button).setOnClickListener(openSettings);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Deliberately not on a ticker: Open Charge Map bans callers that poll it.
        chargingCard.loadOnce();
        bindSettingsSummary();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        chargingCard.cancel();
    }

    /** Restated on every resume, so returning from settings shows the new choices. */
    private void bindSettingsSummary() {
        PreferencesManager preferences = new PreferencesManager(requireContext());
        int launchPage = preferences.getHomePage();
        int launchName;
        if (launchPage == HomePagerAdapter.PAGE_SHORTCUTS) {
            launchName = R.string.settings_page_shortcuts;
        } else if (launchPage == HomePagerAdapter.PAGE_SYSTEM) {
            launchName = R.string.settings_page_system;
        } else {
            launchName = R.string.settings_page_home;
        }
        String shortcuts = getString(preferences.isShortcutsPageEnabled()
                ? R.string.settings_card_shortcuts_on
                : R.string.settings_card_shortcuts_off);
        settingsSummary.setText(
                getString(R.string.settings_card_launch, getString(launchName))
                        + System.lineSeparator() + shortcuts);
    }
}
