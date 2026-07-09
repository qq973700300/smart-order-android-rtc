package com.example.test5.order.mq;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class OrderInboxAdapter extends RecyclerView.Adapter<OrderInboxAdapter.Holder> {

    private final List<OrderInbox.Entry> items = new ArrayList<>();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    public void setItems(List<OrderInbox.Entry> entries) {
        items.clear();
        items.addAll(entries);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_order_inbox, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OrderInbox.Entry entry = items.get(position);
        String title = entry.dishName;
        if (!entry.tableNumber.isEmpty()) {
            title = title + " · " + entry.tableNumber;
        }
        holder.titleView.setText(title);

        String sourceLabel = "miniprogram".equals(entry.source)
                ? holder.itemView.getContext().getString(R.string.order_source_miniprogram)
                : holder.itemView.getContext().getString(R.string.order_source_voice);
        holder.metaView.setText(holder.itemView.getContext().getString(
                R.string.order_inbox_item_meta,
                sourceLabel,
                entry.orderNumber,
                timeFormat.format(new Date(entry.receivedAtMs))));

        String statusText = statusLabel(holder, entry.status);
        if (!entry.statusMessage.isEmpty()) {
            statusText = statusText + " · " + entry.statusMessage;
        }
        holder.statusView.setText(statusText);
    }

    private static String statusLabel(Holder holder, OrderInbox.Status status) {
        switch (status) {
            case PROCESSING:
                return holder.itemView.getContext().getString(R.string.order_status_processing);
            case DONE:
                return holder.itemView.getContext().getString(R.string.order_status_done);
            case FAILED:
                return holder.itemView.getContext().getString(R.string.order_status_failed);
            case SKIPPED:
                return holder.itemView.getContext().getString(R.string.order_status_skipped);
            case RECEIVED:
            default:
                return holder.itemView.getContext().getString(R.string.order_status_received);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView titleView;
        final TextView metaView;
        final TextView statusView;

        Holder(@NonNull View itemView) {
            super(itemView);
            titleView = itemView.findViewById(R.id.order_item_title);
            metaView = itemView.findViewById(R.id.order_item_meta);
            statusView = itemView.findViewById(R.id.order_item_status);
        }
    }
}
