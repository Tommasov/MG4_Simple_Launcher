package com.tommasov.mg4simplelauncher.charging;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.tommasov.mg4simplelauncher.R;

/**
 * Quick filters offered above the charging list. Every filter narrows the query itself
 * rather than the results.
 *
 * <p>That matters: asking for the nearest stations and keeping only one operator returns
 * nothing useful, because the nearest few dozen are all urban. Around Florence, 58 of the
 * 60 closest stations belong to a single city operator, while the motorway chargers this
 * filter is for start 8 km out and continue for a hundred. Letting Open Charge Map do the
 * filtering returns the nearest stations <em>of that operator</em>, which is the question
 * the driver is actually asking.
 *
 * <p>Operator ids come from the OCM reference data and are verified against live queries.
 */
public enum ChargingFilter {

    /** Everything nearby: a city-sized radius is plenty. */
    ALL(R.string.charging_filter_all, null, null, 25),

    /** In Italy the motorway network is Free To X (300 kW service-area chargers). */
    MOTORWAY(R.string.charging_filter_motorway, 3503, null, 200),

    /**
     * Tesla operator id 3534, "including non-Tesla" — the sites an MG4 can actually use.
     * Id 23, "Tesla-only charging", is deliberately excluded: those bays cannot charge it.
     */
    SUPERCHARGER(R.string.charging_filter_supercharger, 3534, null, 200),

    /** Anything that refills the car during a stop rather than overnight. */
    FAST(R.string.charging_filter_fast, null, 50, 60);

    @StringRes
    public final int labelRes;
    /** OCM {@code operatorid}, or null when the filter does not narrow by operator. */
    @Nullable
    public final Integer operatorId;
    /** OCM {@code minpowerkw}, or null when any power qualifies. */
    @Nullable
    public final Integer minPowerKw;
    /** Search radius in km: operator filters need a long reach to find anything at all. */
    public final int radiusKm;

    ChargingFilter(@StringRes int labelRes, @Nullable Integer operatorId,
                   @Nullable Integer minPowerKw, int radiusKm) {
        this.labelRes = labelRes;
        this.operatorId = operatorId;
        this.minPowerKw = minPowerKw;
        this.radiusKm = radiusKm;
    }
}
