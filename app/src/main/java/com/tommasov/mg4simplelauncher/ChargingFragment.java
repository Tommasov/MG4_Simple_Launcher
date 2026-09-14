package com.tommasov.mg4simplelauncher;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.tommasov.mg4simplelauncher.charging.ChargingCardBinder;

/**
 * Carousel page 3: the charging points, and nothing else.
 *
 * <p>The page arrived carrying four cards of live system readings and left with one. What
 * went is what nobody reads at 130 km/h; what stayed is where the next charger is. Settings
 * keep a button in the corner because they have to live somewhere, not because they belong
 * on this page.
 *
 * <p>Nothing here refreshes on a timer: the card loads once per visit, because Open Charge
 * Map bans callers who poll it.
 */
public class ChargingFragment extends Fragment {

    private ChargingCardBinder chargingCard;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_charging, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        chargingCard = new ChargingCardBinder(view);
        view.findViewById(R.id.settings_card_button).setOnClickListener(
                v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        // Deliberately not on a ticker: Open Charge Map bans callers that poll it.
        chargingCard.loadOnce();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        chargingCard.cancel();
    }
}
