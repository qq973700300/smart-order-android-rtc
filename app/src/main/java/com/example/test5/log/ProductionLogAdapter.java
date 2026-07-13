package com.example.test5.log;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.R;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class ProductionLogAdapter extends RecyclerView.Adapter<ProductionLogAdapter.Holder> {

    public interface OnItemClickListener {
        void onItemClick(ProductionLogStore.Entry entry);
    }

    private final List<ProductionLogStore.Entry> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
    private OnItemClickListener itemClickListener;

    public void setItems(List<ProductionLogStore.Entry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        itemClickListener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_production_log, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ProductionLogStore.Entry entry = items.get(position);
        holder.titleView.setText(entry.title);
        holder.timeView.setText(timeFormat.format(new Date(entry.timestampMs)));

        String meta = categoryLabel(holder, entry.category);
        if (!entry.dishName.isEmpty()) {
            meta = meta + " · " + entry.dishName;
        }
        if (!entry.orderNumber.isEmpty()) {
            meta = meta + " · " + entry.orderNumber;
        }
        holder.metaView.setText(meta);

        String body = entry.message.isEmpty() ? entry.title : entry.message;
        holder.messageView.setText(body);

        int accent = levelColor(holder, entry.level);
        holder.cardView.setStrokeColor(accent);
        holder.levelView.setText(levelLabel(holder, entry.level));
        holder.levelView.setTextColor(accent);

        holder.itemView.setOnClickListener(v -> {
            if (itemClickListener != null) {
                itemClickListener.onItemClick(entry);
            }
        });
    }

    private static String categoryLabel(Holder holder, String category) {
        if (category == null) {
            return holder.itemView.getContext().getString(R.string.production_log_category_system);
        }
        switch (category) {
            case "ORDER":
                return holder.itemView.getContext().getString(R.string.production_log_category_order);
            case "FLOW":
                return holder.itemView.getContext().getString(R.string.production_log_category_flow);
            case "MQ":
                return holder.itemView.getContext().getString(R.string.production_log_category_mq);
            case "DEVICE":
                return holder.itemView.getContext().getString(R.string.production_log_category_device);
            case "SYSTEM":
            default:
                return holder.itemView.getContext().getString(R.string.production_log_category_system);
        }
    }

    private static String levelLabel(Holder holder, String level) {
        if ("WARN".equals(level)) {
            return holder.itemView.getContext().getString(R.string.production_log_level_warn);
        }
        if ("ERROR".equals(level)) {
            return holder.itemView.getContext().getString(R.string.production_log_level_error);
        }
        return holder.itemView.getContext().getString(R.string.production_log_level_info);
    }

    private static int levelColor(Holder holder, String level) {
        if ("WARN".equals(level)) {
            return Color.parseColor("#F57C00");
        }
        if ("ERROR".equals(level)) {
            return ContextCompat.getColor(holder.itemView.getContext(), android.R.color.holo_red_dark);
        }
        return Color.parseColor("#2E7D32");
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView cardView;
        final TextView titleView;
        final TextView timeView;
        final TextView metaView;
        final TextView messageView;
        final TextView levelView;

        Holder(@NonNull View itemView) {
            super(itemView);
            cardView = (MaterialCardView) itemView;
            titleView = itemView.findViewById(R.id.log_item_title);
            timeView = itemView.findViewById(R.id.log_item_time);
            metaView = itemView.findViewById(R.id.log_item_meta);
            messageView = itemView.findViewById(R.id.log_item_message);
            levelView = itemView.findViewById(R.id.log_item_level);
        }
    }
}
