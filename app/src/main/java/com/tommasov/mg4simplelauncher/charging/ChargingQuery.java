package com.tommasov.mg4simplelauncher.charging;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tommasov.mg4simplelauncher.PreferencesManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * What a filter actually asks Open Charge Map for, once the driver's own settings are folded
 * in.
 *
 * <p>Three of the four tabs are fixed: they mean the same thing in every country, so the
 * {@link ChargingFilter} constants say all there is to say. The motorway tab cannot be, and
 * that is the whole reason this class exists. Open Charge Map publishes no field for "this
 * station is on a motorway" — there is no road classification in the data at all — so the tab
 * has always been an approximation. In Italy the approximation was one operator id, because
 * the motorway network there is a single company; across the border that same id returns an
 * empty list, on the trip where a driver most wants the tab.
 *
 * <p>So the tab is defined by the driver instead: a power floor that always applies, and
 * optionally the networks that line the motorways where they drive. With no networks chosen
 * it degrades to "the most powerful ones nearby", which is a fair guess anywhere.
 *
 * <p>The client stays a plain HTTP client and knows nothing about preferences; this is where
 * the two meet.
 */
public final class ChargingQuery {

    /** OCM {@code operatorid}: one id, several comma separated, or null for any. */
    @Nullable
    public final String operatorIds;
    /** OCM {@code minpowerkw}, or null when any power qualifies. */
    @Nullable
    public final Integer minPowerKw;
    public final int radiusKm;

    private ChargingQuery(@Nullable String operatorIds, @Nullable Integer minPowerKw,
                          int radiusKm) {
        this.operatorIds = operatorIds;
        this.minPowerKw = minPowerKw;
        this.radiusKm = radiusKm;
    }

    @NonNull
    public static ChargingQuery of(@NonNull Context context, @NonNull ChargingFilter filter) {
        if (filter != ChargingFilter.MOTORWAY) {
            return new ChargingQuery(
                    filter.operatorId == null ? null : String.valueOf(filter.operatorId),
                    filter.minPowerKw, filter.radiusKm);
        }
        PreferencesManager preferences = new PreferencesManager(context);
        Set<String> chosen = preferences.getMotorwayOperators();
        // A narrow tab can afford to look a long way; an open one cannot, or the answer is a
        // wall of stations the driver has to read past.
        int radius = chosen.isEmpty() ? 100 : filter.radiusKm;
        return new ChargingQuery(chosen.isEmpty() ? null : TextUtils.join(",", chosen),
                preferences.getMotorwayMinPowerKw(), radius);
    }

    /** The bare list of operators to ask for, for building a picker. */
    @NonNull
    public static List<String> operatorList(@Nullable String operatorIds) {
        List<String> ids = new ArrayList<>();
        if (operatorIds != null && !operatorIds.isEmpty()) {
            for (String id : operatorIds.split(",")) {
                if (!id.trim().isEmpty()) {
                    ids.add(id.trim());
                }
            }
        }
        return ids;
    }
}
