package com.tommasov.mg4simplelauncher.charging;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.tommasov.mg4simplelauncher.R;

import java.util.ArrayList;
import java.util.List;

/** Binds charging stations into the left-hand list of the charging screen. */
public class ChargePointAdapter
        extends RecyclerView.Adapter<ChargePointAdapter.ChargePointViewHolder> {

    public interface Listener {
        /** A row was tapped: centre the map on it. */
        void onSelect(@NonNull ChargePoint point);

        /** The arrow on a row was tapped: hand the location to a navigation app. */
        void onNavigate(@NonNull ChargePoint point);
    }

    private final List<ChargePoint> points = new ArrayList<>();
    private final Listener listener;
    /** False on vehicles with no navigator: a shortcut to nowhere is worse than none. */
    private final boolean showNavigate;
    /** Id of the row matching the highlighted map pin; -1 when nothing is selected. */
    private long selectedId = -1;
    /** Null until the car answers, and on trims that never will. */
    @Nullable
    private ArrivalCharge arrival;

    public ChargePointAdapter(@NonNull Listener listener, boolean showNavigate) {
        this.listener = listener;
        this.showNavigate = showNavigate;
    }

    public void submit(@NonNull List<ChargePoint> newPoints) {
        points.clear();
        points.addAll(newPoints);
        selectedId = -1;
        notifyDataSetChanged();
    }

    /** Lights the row for {@code point} so list and map agree on what is selected. */
    /**
     * Supplies (or withdraws) the estimate of charge left on arrival. The list is built from
     * Open Charge Map before the car has answered, so this lands afterwards and redraws.
     */
    public void setArrivalCharge(@Nullable ArrivalCharge estimate) {
        this.arrival = estimate;
        notifyDataSetChanged();
    }

    public void setSelected(@NonNull ChargePoint point) {
        if (selectedId == point.id) {
            return;
        }
        selectedId = point.id;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChargePointViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_charge_point, parent, false);
        return new ChargePointViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChargePointViewHolder holder, int position) {
        ChargePoint point = points.get(position);
        holder.title.setText(point.title);
        holder.operator.setText(describe(point));

        holder.distance.setText(point.hasDistance()
                ? holder.itemView.getContext()
                        .getString(R.string.charging_distance_km, point.distanceKm)
                : "");
        // OCM leaves the power out for plenty of stations; show nothing rather than "0 kW".
        holder.power.setText(point.maxPowerKw > 0
                ? holder.itemView.getContext()
                        .getString(R.string.charging_power_kw, point.maxPowerKw)
                : "");

        bindArrival(holder.arrival, point);

        holder.navigate.setVisibility(showNavigate ? View.VISIBLE : View.GONE);
        holder.itemView.setActivated(point.id == selectedId);
        holder.itemView.setOnClickListener(v -> listener.onSelect(point));
        holder.navigate.setOnClickListener(v -> listener.onNavigate(point));
    }

    @Override
    public int getItemCount() {
        return points.size();
    }

    /**
     * Charge expected on arrival, when both the car and Open Charge Map have supplied their
     * half of it. Colour carries the warning: the number alone is easy to read past on a
     * screen the driver glances at.
     */
    private void bindArrival(@NonNull TextView view, @NonNull ChargePoint point) {
        if (arrival == null || !point.hasDistance()) {
            view.setVisibility(View.GONE);
            return;
        }
        int percent = arrival.percentOnArrival(point.distanceKm);
        Context context = view.getContext();
        int colour = ContextCompat.getColor(context, colourFor(percent));
        view.setText(context.getString(R.string.charging_arrival_percent, percent));
        view.setTextColor(colour);
        // The glyph is a compound drawable, so it needs tinting alongside the text rather
        // than inheriting the colour the way a child ImageView would.
        TextViewCompat.setCompoundDrawableTintList(view, ColorStateList.valueOf(colour));
        view.setVisibility(View.VISIBLE);
    }

    /**
     * Thresholds, not a gradient: 15% is roughly the point at which an MG4 driver starts
     * planning the next stop, and 5% is where the car itself begins to nag.
     */
    @ColorRes
    static int colourFor(int percent) {
        if (percent <= 5) {
            return R.color.charge_none;
        }
        return percent <= 15 ? R.color.charge_low : R.color.text_secondary;
    }

    /**
     * "Enel X · Type 2 (Socket Only)". The operator is dropped when it only repeats the
     * station name, which OCM data does often.
     */
    private static String describe(@NonNull ChargePoint point) {
        boolean operatorAddsNothing = point.operator.isEmpty()
                || point.title.equalsIgnoreCase(point.operator);
        if (operatorAddsNothing) {
            return point.connectors;
        }
        if (point.connectors.isEmpty()) {
            return point.operator;
        }
        return point.operator + " · " + point.connectors;
    }

    static class ChargePointViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView operator;
        final TextView distance;
        final TextView arrival;
        final TextView power;
        final ImageView navigate;

        ChargePointViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.charge_title);
            operator = itemView.findViewById(R.id.charge_operator);
            distance = itemView.findViewById(R.id.charge_distance);
            arrival = itemView.findViewById(R.id.charge_arrival);
            power = itemView.findViewById(R.id.charge_power);
            navigate = itemView.findViewById(R.id.charge_navigate);
        }
    }
}
