package com.tommasov.mg4simplelauncher;

import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Binds the fixed set of assignable shortcut slots shown on carousel page 2. */
public class FavoriteGridAdapter
        extends RecyclerView.Adapter<FavoriteGridAdapter.SlotViewHolder> {

    /** One shortcut tile; {@code packageName} is null while the slot is still empty. */
    public static class Slot {
        public final int index;
        @Nullable
        public final String packageName;
        @Nullable
        public final String label;
        @Nullable
        public final Drawable icon;

        public Slot(int index, @Nullable String packageName, @Nullable String label,
                    @Nullable Drawable icon) {
            this.index = index;
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
        }
    }

    public interface OnSlotListener {
        void onSlot(Slot slot);
    }

    private final List<Slot> slots = new ArrayList<>();
    private final OnSlotListener clickListener;
    private final OnSlotListener longClickListener;

    public FavoriteGridAdapter(OnSlotListener clickListener, OnSlotListener longClickListener) {
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    /** Replaces every tile at once; the slot count is fixed, so a full rebind is cheapest. */
    public void submit(List<Slot> newSlots) {
        slots.clear();
        slots.addAll(newSlots);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SlotViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_favorite_slot, parent, false);
        return new SlotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlotViewHolder holder, int position) {
        Slot slot = slots.get(position);
        if (slot.packageName == null) {
            holder.icon.setImageResource(R.drawable.ic_add);
            holder.label.setText(R.string.add_favorite);
        } else {
            holder.icon.setImageDrawable(slot.icon);
            holder.label.setText(slot.label);
        }
        holder.itemView.setOnClickListener(v -> clickListener.onSlot(slot));
        holder.itemView.setOnLongClickListener(v -> {
            longClickListener.onSlot(slot);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return slots.size();
    }

    static class SlotViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        SlotViewHolder(@NonNull View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.slot_icon);
            label = itemView.findViewById(R.id.slot_label);
        }
    }
}
