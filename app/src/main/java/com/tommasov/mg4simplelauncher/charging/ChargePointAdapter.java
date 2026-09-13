package com.tommasov.mg4simplelauncher.charging;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
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
    /** Id of the row matching the highlighted map pin; -1 when nothing is selected. */
    private long selectedId = -1;

    public ChargePointAdapter(@NonNull Listener listener) {
        this.listener = listener;
    }

    public void submit(@NonNull List<ChargePoint> newPoints) {
        points.clear();
        points.addAll(newPoints);
        selectedId = -1;
        notifyDataSetChanged();
    }

    /** Lights the row for {@code point} so list and map agree on what is selected. */
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

        holder.itemView.setActivated(point.id == selectedId);
        holder.itemView.setOnClickListener(v -> listener.onSelect(point));
        holder.navigate.setOnClickListener(v -> listener.onNavigate(point));
    }

    @Override
    public int getItemCount() {
        return points.size();
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
        final TextView power;
        final ImageView navigate;

        ChargePointViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.charge_title);
            operator = itemView.findViewById(R.id.charge_operator);
            distance = itemView.findViewById(R.id.charge_distance);
            power = itemView.findViewById(R.id.charge_power);
            navigate = itemView.findViewById(R.id.charge_navigate);
        }
    }
}
