package com.tommasov.mg4simplelauncher.charging;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.tommasov.mg4simplelauncher.R;

/**
 * Fills in {@code part_charge_extras}: bays, price and restricted access.
 *
 * <p>Shared by the map list and the card on the carousel page so the two cannot drift apart,
 * and because the rules for what to leave out are the interesting part. None of these three
 * fields is guaranteed — Open Charge Map has whatever whoever added the station typed in —
 * so each pair is hidden on its own, and the whole row disappears when there is nothing.
 */
final class ChargeExtras {

    private ChargeExtras() {
    }

    /** Binds the pairs. Returns false when the station had nothing worth a line. */
    static boolean bind(@NonNull View row, @NonNull ChargePoint point) {
        return bind(row, point, true);
    }

    /**
     * @param showBays false where the connector breakdown is already on show: it says how
     *                 many bays of each kind there are, which is the same count said better.
     */
    static boolean bind(@NonNull View row, @NonNull ChargePoint point, boolean showBays) {
        Context context = row.getContext();

        // A single bay is what Open Charge Map records when nobody counted, so it is as
        // likely to be a default as a fact; only a real count says anything.
        boolean hasBays = showBays && point.pointCount > 1;
        TextView bays = row.findViewById(R.id.charge_bays);
        bays.setText(hasBays ? String.valueOf(point.pointCount) : "");
        show(row, R.id.charge_bays_icon, hasBays);
        show(row, R.id.charge_bays, hasBays);

        int access = accessLabel(point.usageTypeId);
        TextView accessView = row.findViewById(R.id.charge_access);
        accessView.setText(access == 0 ? "" : context.getString(access));
        show(row, R.id.charge_access_icon, access != 0);
        show(row, R.id.charge_access, access != 0);

        return hasBays || access != 0;
    }

    private static void show(@NonNull View row, int id, boolean visible) {
        row.findViewById(id).setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    /**
     * Open Charge Map usage type ids, reduced to the one thing that matters on a journey:
     * whether you can turn up and plug in, or whether something stands in the way.
     *
     * <p>Public stations and those you pay for on the spot say nothing at all — that is what
     * a driver already expects, and a padlock next to every row would be noise.
     *
     * <p>Type 4, "membership required", says nothing either, though it once did. On the
     * Supercharger network it is how the cheaper subscriber tariff is recorded, not a closed
     * door: these were being marked with a padlock while the owner of this launcher had
     * charged at them repeatedly without a Tesla subscription. A wrong padlock costs a
     * usable stop; a missing one costs a surprise at the till.
     *
     * <p>The ids are stable reference data; an unrecognised one stays quiet rather than
     * guessing. The titles are not taken from the API either, since it only returns them in
     * English.
     */
    @StringRes
    private static int accessLabel(int usageTypeId) {
        switch (usageTypeId) {
            case 7:
                return R.string.charging_access_notice;
            case 2:
            case 3:
            case 6:
                return R.string.charging_access_private;
            default:
                return 0;
        }
    }
}
