package com.example.test5.recipe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.R;

import java.util.ArrayList;
import java.util.List;

/** 管理员菜谱列表。 */
public final class DishsConfigManageAdapter extends RecyclerView.Adapter<DishsConfigManageAdapter.Holder> {

    public interface Listener {
        void onItemClick(DishsConfig item);
    }

    private final List<DishsConfig> items = new ArrayList<>();
    private final Listener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;

    public DishsConfigManageAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<DishsConfig> newItems) {
        items.clear();
        items.addAll(newItems);
        selectedPosition = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public DishsConfig getSelectedItem() {
        if (selectedPosition < 0 || selectedPosition >= items.size()) {
            return null;
        }
        return items.get(selectedPosition);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dishs_config_manage, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DishsConfig item = items.get(position);
        holder.nameView.setText(item.dishName != null ? item.dishName : "");
        holder.metaView.setText(holder.itemView.getContext().getString(
                R.string.dishs_manage_item_meta,
                item.dishLocation != null ? item.dishLocation : "-",
                item.statusType
                        ? holder.itemView.getContext().getString(R.string.dishs_heat_on)
                        : holder.itemView.getContext().getString(R.string.dishs_heat_off),
                item.friedTime));
        holder.itemView.setSelected(position == selectedPosition);
        holder.itemView.setOnClickListener(v -> {
            int old = selectedPosition;
            selectedPosition = holder.getBindingAdapterPosition();
            if (old != RecyclerView.NO_POSITION) {
                notifyItemChanged(old);
            }
            notifyItemChanged(selectedPosition);
            listener.onItemClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView nameView;
        final TextView metaView;

        Holder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.manage_dish_name);
            metaView = itemView.findViewById(R.id.manage_dish_meta);
        }
    }
}
