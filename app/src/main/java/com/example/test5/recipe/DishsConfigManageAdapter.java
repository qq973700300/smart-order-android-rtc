package com.example.test5.recipe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.R;
import com.google.android.material.card.MaterialCardView;
import com.example.test5.recipe.flow.RecipeFlowStore;

import java.util.ArrayList;
import java.util.List;

/** 自定义菜谱管理列表：单击选中高亮。 */
public final class DishsConfigManageAdapter extends RecyclerView.Adapter<DishsConfigManageAdapter.Holder> {

    public interface Listener {
        void onItemClick(DishsConfig item);
    }

    private final List<DishsConfig> items = new ArrayList<>();
    private final Listener listener;
    private int selectedPosition = RecyclerView.NO_POSITION;
    private final int selectedStrokePx;

    public DishsConfigManageAdapter(android.content.Context context, Listener listener) {
        this.listener = listener;
        selectedStrokePx = (int) (2 * context.getResources().getDisplayMetrics().density);
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
        boolean selected = position == selectedPosition;

        holder.nameView.setText(item.dishName != null ? item.dishName : "");
        holder.metaView.setText(holder.itemView.getContext().getString(
                R.string.dishs_manage_item_flow_meta,
                item.dishLocation != null ? item.dishLocation : "-",
                RecipeFlowStore.stepCount(holder.itemView.getContext(), item.id),
                item.friedTime));
        applySelectedStyle(holder, selected);

        holder.card.setOnClickListener(v -> {
            int clicked = holder.getBindingAdapterPosition();
            if (clicked == RecyclerView.NO_POSITION) {
                return;
            }
            int old = selectedPosition;
            selectedPosition = clicked;
            if (old != RecyclerView.NO_POSITION) {
                notifyItemChanged(old);
            }
            notifyItemChanged(selectedPosition);
            listener.onItemClick(items.get(clicked));
        });
    }

    private void applySelectedStyle(Holder holder, boolean selected) {
        holder.card.setChecked(selected);
        holder.card.setStrokeWidth(selected ? selectedStrokePx : 0);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView nameView;
        final TextView metaView;

        Holder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.manage_item_card);
            nameView = itemView.findViewById(R.id.manage_dish_name);
            metaView = itemView.findViewById(R.id.manage_dish_meta);
        }
    }
}
