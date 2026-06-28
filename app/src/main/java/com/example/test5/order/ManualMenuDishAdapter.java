package com.example.test5.order;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.R;
import com.google.android.material.button.MaterialButton;

import java.util.List;

/** 手动点餐菜单：+/- 调整份数 */
public final class ManualMenuDishAdapter extends RecyclerView.Adapter<ManualMenuDishAdapter.DishViewHolder> {

    public interface Listener {
        void onQuantityChanged();
    }

    private final List<MenuItem> items;
    private final OrderCart cart;
    private final Listener listener;

    public ManualMenuDishAdapter(List<MenuItem> items, OrderCart cart, Listener listener) {
        this.items = items;
        this.cart = cart;
        this.listener = listener;
    }

    @NonNull
    @Override
    public DishViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_menu_dish_manual, parent, false);
        return new DishViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DishViewHolder holder, int position) {
        MenuItem item = items.get(position);
        int quantity = cart.getQuantity(item.name);

        holder.dishImage.setImageResource(item.imageResId);
        holder.dishName.setText(item.name);
        holder.dishSubtitle.setText(item.subtitle);
        holder.quantityText.setText(String.valueOf(quantity));
        holder.minusButton.setEnabled(quantity > 0);

        holder.plusButton.setOnClickListener(v -> {
            cart.adjustQuantity(item.name, 1);
            listener.onQuantityChanged();
            notifyItemChanged(holder.getBindingAdapterPosition());
        });
        holder.minusButton.setOnClickListener(v -> {
            cart.adjustQuantity(item.name, -1);
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

    static final class DishViewHolder extends RecyclerView.ViewHolder {
        final ImageView dishImage;
        final TextView dishName;
        final TextView dishSubtitle;
        final TextView quantityText;
        final MaterialButton minusButton;
        final MaterialButton plusButton;

        DishViewHolder(@NonNull View itemView) {
            super(itemView);
            dishImage = itemView.findViewById(R.id.dish_image);
            dishName = itemView.findViewById(R.id.dish_name);
            dishSubtitle = itemView.findViewById(R.id.dish_subtitle);
            quantityText = itemView.findViewById(R.id.quantity_text);
            minusButton = itemView.findViewById(R.id.minus_button);
            plusButton = itemView.findViewById(R.id.plus_button);
        }
    }
}
