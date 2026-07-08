package com.example.test5;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.order.OrderCart;
import com.example.test5.order.OrderSubmitDialogs;
import com.example.test5.order.RestaurantFunctionHandler;
import com.example.test5.recipe.CustomMenuAdapter;
import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 自定义菜单：从 DishsConfig.xml 加载菜谱，加入购物车送厨。 */
public class CustomMenuActivity extends AppCompatActivity {

    private OrderCart cart;
    private CustomMenuAdapter adapter;
    private TextView cartSummaryView;
    private MaterialButton checkoutButton;
    private RestaurantFunctionHandler orderHandler;
    private final List<DishsConfig> displayItems = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_custom_menu);
        orderHandler = new RestaurantFunctionHandler(this);

        MaterialToolbar toolbar = findViewById(R.id.custom_menu_toolbar);
        RecyclerView recycler = findViewById(R.id.custom_menu_recycler);
        TextInputEditText searchInput = findViewById(R.id.custom_menu_search_input);
        cartSummaryView = findViewById(R.id.cart_summary_text);
        checkoutButton = findViewById(R.id.checkout_button);

        toolbar.setNavigationOnClickListener(v -> finish());

        cart = OrderCart.getInstance();
        orderHandler.setSubmitListener((success, message, summary) -> runOnUiThread(() -> {
            updateCartSummary();
            if (adapter != null) {
                adapter.refreshAll();
            }
            OrderSubmitDialogs.show(this, success, message, summary);
        }));

        adapter = new CustomMenuAdapter(displayItems, cart, this::updateCartSummary);
        recycler.setLayoutManager(new GridLayoutManager(this, 2));
        recycler.setAdapter(adapter);
        int bottomPad = (int) (96 * getResources().getDisplayMetrics().density);
        recycler.setPadding(0, 0, 0, bottomPad);
        recycler.setClipToPadding(false);

        checkoutButton.setOnClickListener(v -> submitOrder());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                loadDishes(s != null ? s.toString() : "");
            }
        });

        loadDishes("");
        updateCartSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cart.setChangeListener(this::updateCartSummaryFromCart);
        updateCartSummary();
    }

    @Override
    protected void onPause() {
        cart.setChangeListener(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        orderHandler.shutdown();
        executor.shutdownNow();
    }

    private void loadDishes(String keyword) {
        executor.execute(() -> {
            List<DishsConfig> list = DishsConfigStore.search(this, keyword);
            runOnUiThread(() -> {
                displayItems.clear();
                displayItems.addAll(list);
                adapter.setItems(displayItems);
            });
        });
    }

    private void submitOrder() {
        if (cart.countActive() == 0) {
            Toast.makeText(this, R.string.cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        checkoutButton.setEnabled(false);
        checkoutButton.setText(R.string.order_subscribe_requesting);
        executor.execute(() -> {
            String result = orderHandler.execute(
                    RestaurantFunctionHandler.TOOL_SUBMIT_ORDER,
                    "{}"
            );
            boolean success = isSuccess(result);
            if (!success) {
                String message = extractMessage(result);
                runOnUiThread(() -> {
                    checkoutButton.setEnabled(cart.countActive() > 0);
                    updateCartSummary();
                    OrderSubmitDialogs.show(this, false, message, "");
                });
            } else {
                runOnUiThread(() -> {
                    checkoutButton.setEnabled(cart.countActive() > 0);
                    updateCartSummary();
                });
            }
        });
    }

    private void updateCartSummaryFromCart(String cartText) {
        runOnUiThread(() -> updateCartSummary(cartText));
    }

    private void updateCartSummary() {
        updateCartSummary(cart.buildCartText());
    }

    private void updateCartSummary(String cartText) {
        int total = cart.countActive();
        if (total == 0) {
            cartSummaryView.setText(R.string.cart_empty);
            checkoutButton.setEnabled(false);
            checkoutButton.setText(R.string.checkout);
        } else {
            cartSummaryView.setText(cartText);
            checkoutButton.setEnabled(true);
            checkoutButton.setText(getString(R.string.checkout_with_count, total));
        }
        if (adapter != null) {
            adapter.refreshAll();
        }
    }

    private static boolean isSuccess(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            return root.has("ok") && root.get("ok").getAsBoolean();
        } catch (Exception ignored) {
        }
        return false;
    }

    private static String extractMessage(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("message")) {
                return root.get("message").getAsString();
            }
        } catch (Exception ignored) {
        }
        return json;
    }
}
