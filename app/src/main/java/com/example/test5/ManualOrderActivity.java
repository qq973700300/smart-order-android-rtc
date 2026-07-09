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

/** 手动点餐：从本地自定义菜谱选菜送厨。 */
public class ManualOrderActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";

    private OrderCart cart;
    private CustomMenuAdapter adapter;
    private TextView cartSummaryView;
    private MaterialButton checkoutButton;
    private TextInputEditText searchInput;
    private RestaurantFunctionHandler orderHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_order);
        orderHandler = new RestaurantFunctionHandler(this);

        MaterialToolbar toolbar = findViewById(R.id.manual_toolbar);
        RecyclerView menuRecycler = findViewById(R.id.menu_recycler);
        searchInput = findViewById(R.id.manual_search_input);
        cartSummaryView = findViewById(R.id.cart_summary_text);
        checkoutButton = findViewById(R.id.checkout_button);

        toolbar.setNavigationOnClickListener(v -> finish());
        String customTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (customTitle != null && !customTitle.isEmpty()) {
            toolbar.setTitle(customTitle);
        }

        cart = OrderCart.getInstance();
        orderHandler.setSubmitListener((success, message, summary) -> runOnUiThread(() -> {
            updateCartSummary();
            if (adapter != null) {
                adapter.refreshAll();
            }
            OrderSubmitDialogs.show(this, success, message, summary);
        }));

        adapter = new CustomMenuAdapter(new ArrayList<>(), cart, this::updateCartSummary);
        menuRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        menuRecycler.setAdapter(adapter);
        int bottomPad = (int) (96 * getResources().getDisplayMetrics().density);
        menuRecycler.setPadding(0, 0, 0, bottomPad);
        menuRecycler.setClipToPadding(false);

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
        loadDishes(currentKeyword());
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

    private String currentKeyword() {
        return searchInput.getText() != null ? searchInput.getText().toString() : "";
    }

    private void loadDishes(String keyword) {
        executor.execute(() -> {
            List<DishsConfig> list = DishsConfigStore.search(this, keyword);
            runOnUiThread(() -> adapter.setItems(list));
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
