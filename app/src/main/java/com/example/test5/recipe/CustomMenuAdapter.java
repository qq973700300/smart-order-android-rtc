package com.example.test5.recipe;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.R;
import com.example.test5.order.OrderCart;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** 自定义菜谱网格：+/- 加入购物车。 */
public final class CustomMenuAdapter extends RecyclerView.Adapter<CustomMenuAdapter.Holder> {

    public interface Listener {
        void onQuantityChanged();
    }

    private final List<DishsConfig> items;
    private final OrderCart cart;
    private final Listener listener;

    public CustomMenuAdapter(List<DishsConfig> items, OrderCart cart, Listener listener) {
        this.items = items;
        this.cart = cart;
        this.listener = listener;
    }

    public void setItems(List<DishsConfig> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_custom_menu_dish, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        DishsConfig item = items.get(position);
        String name = item.dishName != null ? item.dishName : "";
        int quantity = cart.getQuantity(name);

        holder.dishName.setText(name);
        holder.dishMeta.setText(holder.itemView.getContext().getString(
                R.string.dishs_item_meta,
                safe(item.dishLocation),
                item.friedTime,
                item.statusType
                        ? holder.itemView.getContext().getString(R.string.dishs_heat_on)
                        : holder.itemView.getContext().getString(R.string.dishs_heat_off)));
        holder.quantityText.setText(String.valueOf(quantity));
        holder.minusButton.setEnabled(quantity > 0);

        holder.plusButton.setOnClickListener(v -> {
            cart.adjustQuantity(name, 1);
            listener.onQuantityChanged();
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
        holder.minusButton.setOnClickListener(v -> {
            cart.adjustQuantity(name, -1);
            listener.onQuantityChanged();
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public void refreshAll() {
        notifyDataSetChanged();
    }

    private static String safe(String value) {
        return value != null ? value : "-";
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView dishName;
        final TextView dishMeta;
        final TextView quantityText;
        final MaterialButton minusButton;
        final MaterialButton plusButton;

        Holder(@NonNull View itemView) {
            super(itemView);
            dishName = itemView.findViewById(R.id.dish_name);
            dishMeta = itemView.findViewById(R.id.dish_meta);
            quantityText = itemView.findViewById(R.id.quantity_text);
            minusButton = itemView.findViewById(R.id.minus_button);
            plusButton = itemView.findViewById(R.id.plus_button);
        }
    }
}
