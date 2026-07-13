package com.example.test5.order;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.R;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

/** 购物车展开面板：已选菜品 +/-。 */
public final class CartSheetAdapter extends RecyclerView.Adapter<CartSheetAdapter.Holder> {

    public interface Listener {
        void onQuantityChanged();
    }

    private final OrderCart cart;
    private final Listener listener;
    private final List<OrderCart.Item> items = new ArrayList<>();

    public CartSheetAdapter(OrderCart cart, Listener listener) {
        this.cart = cart;
        this.listener = listener;
    }

    public void refresh() {
        items.clear();
        items.addAll(cart.getActiveItems());
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart_line, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        OrderCart.Item item = items.get(position);
        holder.nameView.setText(item.dishName);
        holder.quantityView.setText(String.valueOf(item.quantity));
        holder.minusButton.setEnabled(item.quantity > 0);
        holder.plusButton.setOnClickListener(v -> {
            cart.adjustQuantity(item.dishName, 1);
            listener.onQuantityChanged();
            refresh();
        });
        holder.minusButton.setOnClickListener(v -> {
            cart.adjustQuantity(item.dishName, -1);
            listener.onQuantityChanged();
            refresh();
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView nameView;
        final TextView quantityView;
        final MaterialButton minusButton;
        final MaterialButton plusButton;

        Holder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.cart_item_name);
            quantityView = itemView.findViewById(R.id.cart_item_quantity);
            minusButton = itemView.findViewById(R.id.cart_item_minus);
            plusButton = itemView.findViewById(R.id.cart_item_plus);
        }
    }
}
