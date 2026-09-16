package com.tommasov.mg4simplelauncher.charging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tommasov.mg4simplelauncher.vehicle.VehicleData;

/**
 * How much charge is likely left on arrival at a station.
 *
 * <p>The car already does the hard part. {@code getEnduranceMileage} is the range the vehicle
 * itself predicts, computed from how this car has actually been driven — weather, load, right
 * foot and all. Dividing it by the state of charge gives kilometres per percentage point
 * without the launcher having to model consumption, which it has no business doing.
 *
 * <p>What the launcher must correct for is the distance. Open Charge Map returns the straight
 * line between two coordinates, and roads do not go straight: {@link #ROAD_FACTOR} turns the
 * crow's flight into a plausible drive. It is one number standing in for terrain, motorway
 * junctions and one-way systems, so the result is an estimate and is shown as one — never as
 * a promise the driver could plan a marginal leg around.
 *
 * <p>A sharper figure is possible once a station has been sent to the navigator, which knows
 * the real route; that is a separate source for the same arithmetic and is why this class
 * takes a distance rather than a station.
 */
public final class ArrivalCharge {

    /**
     * Straight line to road distance. European road networks run roughly 20-40% longer than
     * the direct line depending on terrain; 1.3 sits in the middle of that and errs on the
     * cautious side for the flat motorway driving where these estimates matter most.
     *
     * <p>Deliberately a single constant rather than something clever: it is the one number in
     * here that wants tuning against real drives, and it should be easy to find and change.
     */
    private static final float ROAD_FACTOR = 1.3f;

    private final int batteryPercent;
    private final float kmPerPercent;

    private ArrivalCharge(int batteryPercent, float kmPerPercent) {
        this.batteryPercent = batteryPercent;
        this.kmPerPercent = kmPerPercent;
    }

    /**
     * Builds an estimator from one vehicle reading, or returns null when the car did not
     * supply both figures — on a trim without the adapter, or before the first reading lands.
     * Callers show distances without an estimate in that case rather than guessing.
     */
    @Nullable
    public static ArrivalCharge from(@NonNull VehicleData.State state) {
        if (state.batteryPercent == VehicleData.UNKNOWN || state.batteryPercent <= 0) {
            return null;
        }
        if (state.rangeKm == VehicleData.UNKNOWN || state.rangeKm <= 0) {
            return null;
        }
        return new ArrivalCharge(state.batteryPercent, state.rangeKm / (float) state.batteryPercent);
    }

    /**
     * State of charge expected on arrival, as a percentage.
     *
     * <p>Clamped at zero: a station beyond the remaining range reads 0%, which says "not on
     * this charge" plainly. A negative number would be arithmetically honest and practically
     * useless — the driver needs to know the leg does not work, not by how much.
     *
     * @param straightLineKm distance as Open Charge Map gives it, in kilometres.
     */
    public int percentOnArrival(double straightLineKm) {
        if (straightLineKm <= 0) {
            return batteryPercent;
        }
        double used = (straightLineKm * ROAD_FACTOR) / kmPerPercent;
        return (int) Math.max(0, Math.round(batteryPercent - used));
    }
}
