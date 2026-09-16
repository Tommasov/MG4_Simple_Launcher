package com.tommasov.mg4simplelauncher.charging;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tommasov.mg4simplelauncher.vehicle.TripForecast;
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

    /**
     * Consumption against average speed, in kWh/100 km: {@code a + b·v²}.
     *
     * <p>Two points of an MG4 fix the curve — about 13 at 50 km/h and about 23 at 130 — and
     * the square term is not a curve-fitting trick but the shape of aerodynamic drag, which
     * is what makes a motorway leg expensive. It predicts ~18 at 100 km/h, which is the
     * figure owners report.
     *
     * <p>Approximate, and only used for the *difference* between two ways of driving: what it
     * has to get right is that 130 costs the better part of twice what 50 does, not the
     * second decimal of either.
     */
    private static final double CONSUMPTION_BASE = 11.3;
    private static final double CONSUMPTION_DRAG = 6.94e-4;

    private final int batteryPercent;
    private final float kmPerPercent;
    /** From the route ahead, when the navigator has one; never larger than the above. */
    private final float routeKmPerPercent;

    private ArrivalCharge(int batteryPercent, float kmPerPercent, float routeKmPerPercent) {
        this.batteryPercent = batteryPercent;
        this.kmPerPercent = kmPerPercent;
        this.routeKmPerPercent = routeKmPerPercent;
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
        float kmPerPercent = state.rangeKm / (float) state.batteryPercent;
        return new ArrivalCharge(state.batteryPercent, kmPerPercent, kmPerPercent);
    }

    /**
     * The same reading, corrected for the journey the navigator has been given.
     *
     * <p>The car's own range assumes you carry on driving as you have been. Set off from home
     * after a week in town and point the navigator at a motorway, and that assumption is
     * generous by half: a stop that reads as comfortably in reach is not. The navigator knows
     * the route, so the average speed it predicts says what the next hundred kilometres will
     * actually cost.
     *
     * <p>Only ever pessimistic. If the route ahead is slower than the recent driving — a
     * motorway run ending in city traffic — the car's own figure is kept, because the battery
     * does not gain range from good news and a driver who runs out because the launcher was
     * optimistic is owed a better excuse than arithmetic.
     *
     * @param batteryKwh usable capacity of this trim, in kWh.
     */
    @NonNull
    public ArrivalCharge onRoute(@NonNull TripForecast.Trip trip, double batteryKwh) {
        double routeConsumption = consumptionAt(trip.averageSpeedKmh);
        if (routeConsumption <= 0) {
            return this;
        }
        // Energy on board now, spent at the rate the route implies.
        double energyKwh = batteryKwh * batteryPercent / 100d;
        double routeKm = energyKwh / routeConsumption * 100d;
        float perPercent = (float) (routeKm / batteryPercent);
        if (perPercent >= kmPerPercent) {
            return this;
        }
        return new ArrivalCharge(batteryPercent, kmPerPercent, perPercent);
    }

    /** kWh per 100 km at a given average speed. */
    static double consumptionAt(double speedKmh) {
        return CONSUMPTION_BASE + CONSUMPTION_DRAG * speedKmh * speedKmh;
    }

    /** True when the route ahead is dearer than the driving behind, so the two differ. */
    public boolean isRouteCorrected() {
        return routeKmPerPercent < kmPerPercent;
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
        double used = (straightLineKm * ROAD_FACTOR) / routeKmPerPercent;
        return (int) Math.max(0, Math.round(batteryPercent - used));
    }
}
