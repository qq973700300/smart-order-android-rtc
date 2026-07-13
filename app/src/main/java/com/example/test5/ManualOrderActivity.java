package com.example.test5;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.order.FlowProductionLauncher;
import com.example.test5.order.OrderCart;
import com.example.test5.order.RestaurantFunctionHandler;
import com.example.test5.recipe.CustomMenuAdapter;
import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 手动点餐：从本地自定义菜谱选菜，本机直接驱动产线。 */
public class ManualOrderActivity extends AppCompatActivity {

    public static final String EXTRA_TITLE = "extra_title";

    private OrderCart cart;
    private CustomMenuAdapter adapter;
    private TextView cartSummaryView;
    private TextView cartExpandHintView;
    private View cartInfoArea;
    private MaterialButton checkoutButton;
    private TextInputEditText searchInput;
    private RestaurantFunctionHandler orderHandler;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private BottomSheetDialog cartSheet;
    private View cartSheetRoot;
    private LinearLayout cartSheetItems;
    private ScrollView cartSheetScroll;
    private TextView cartSheetSummaryView;
    private TextView cartSheetEmptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manual_order);
        orderHandler = new RestaurantFunctionHandler(this);

        MaterialToolbar toolbar = findViewById(R.id.manual_toolbar);
        RecyclerView menuRecycler = findViewById(R.id.menu_recycler);
        searchInput = findViewById(R.id.manual_search_input);
        cartSummaryView = findViewById(R.id.cart_summary_text);
        cartExpandHintView = findViewById(R.id.cart_expand_hint);
        cartInfoArea = findViewById(R.id.cart_info_area);
        checkoutButton = findViewById(R.id.checkout_button);

        toolbar.setNavigationOnClickListener(v -> finish());
        String customTitle = getIntent().getStringExtra(EXTRA_TITLE);
        if (customTitle != null && !customTitle.isEmpty()) {
            toolbar.setTitle(customTitle);
        }

        cart = OrderCart.getInstance();

        adapter = new CustomMenuAdapter(new ArrayList<>(), cart, this::updateCartSummary);
        menuRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        menuRecycler.setAdapter(adapter);
        int bottomPad = (int) (96 * getResources().getDisplayMetrics().density);
        menuRecycler.setPadding(0, 0, 0, bottomPad);
        menuRecycler.setClipToPadding(false);

        setupCartSheet();
        cartInfoArea.setOnClickListener(v -> openCartSheet());
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
        if (cartSheet != null) {
            cartSheet.dismiss();
        }
        super.onDestroy();
        orderHandler.shutdown();
        executor.shutdownNow();
    }

    private void setupCartSheet() {
        cartSheet = new BottomSheetDialog(this);
        cartSheetRoot = getLayoutInflater().inflate(R.layout.bottom_sheet_cart, null);
        cartSheet.setContentView(cartSheetRoot);
        cartSheetSummaryView = cartSheetRoot.findViewById(R.id.cart_sheet_summary);
        cartSheetEmptyView = cartSheetRoot.findViewById(R.id.cart_sheet_empty);
        cartSheetItems = cartSheetRoot.findViewById(R.id.cart_sheet_items);
        cartSheetScroll = (ScrollView) cartSheetItems.getParent();
        cartSheet.setOnShowListener(dialog -> expandCartSheetFully());
    }

    private void expandCartSheetFully() {
        if (cartSheet == null) {
            return;
        }
        FrameLayout bottomSheet = cartSheet.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet == null) {
            return;
        }
        ViewGroup.LayoutParams params = bottomSheet.getLayoutParams();
        if (params != null) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            bottomSheet.setLayoutParams(params);
        }
        BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(bottomSheet);
        behavior.setSkipCollapsed(true);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    private void bindCartSheetItems() {
        if (cartSheetItems == null) {
            return;
        }
        cartSheetItems.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int maxScrollPx = (int) (420 * getResources().getDisplayMetrics().density);
        for (OrderCart.Item item : cart.getActiveItems()) {
            View row = inflater.inflate(R.layout.item_cart_line, cartSheetItems, false);
            TextView nameView = row.findViewById(R.id.cart_item_name);
            TextView quantityView = row.findViewById(R.id.cart_item_quantity);
            MaterialButton minusButton = row.findViewById(R.id.cart_item_minus);
            MaterialButton plusButton = row.findViewById(R.id.cart_item_plus);
            nameView.setText(item.dishName);
            quantityView.setText(String.valueOf(item.quantity));
            minusButton.setEnabled(item.quantity > 0);
            plusButton.setOnClickListener(v -> {
                cart.adjustQuantity(item.dishName, 1);
                updateCartSummary();
            });
            minusButton.setOnClickListener(v -> {
                cart.adjustQuantity(item.dishName, -1);
                updateCartSummary();
            });
            cartSheetItems.addView(row);
        }
        if (cartSheetScroll != null) {
            cartSheetScroll.post(() -> {
                int contentH = cartSheetItems.getHeight();
                ViewGroup.LayoutParams lp = cartSheetScroll.getLayoutParams();
                lp.height = contentH > maxScrollPx ? maxScrollPx : ViewGroup.LayoutParams.WRAP_CONTENT;
                cartSheetScroll.setLayoutParams(lp);
            });
        }
    }

    private void openCartSheet() {
        if (cart.countActive() == 0) {
            Toast.makeText(this, R.string.cart_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        refreshCartSheet();
        cartSheet.show();
    }

    private void refreshCartSheet() {
        int total = cart.countActive();
        if (cartSheetSummaryView != null) {
            cartSheetSummaryView.setText(getString(R.string.cart_sheet_summary, total));
        }
        boolean empty = total == 0;
        if (cartSheetEmptyView != null) {
            cartSheetEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        }
        if (cartSheetScroll != null) {
            cartSheetScroll.setVisibility(empty ? View.GONE : View.VISIBLE);
        }
        if (!empty) {
            bindCartSheetItems();
        } else if (cartSheetItems != null) {
            cartSheetItems.removeAllViews();
        }
        if (empty && cartSheet != null && cartSheet.isShowing()) {
            cartSheet.dismiss();
        }
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
        if (cartSheet != null && cartSheet.isShowing()) {
            cartSheet.dismiss();
        }
        FlowProductionLauncher.launchManualSubmit(this, orderHandler);
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
            cartExpandHintView.setVisibility(View.GONE);
            checkoutButton.setEnabled(false);
            checkoutButton.setText(R.string.checkout);
        } else {
            cartSummaryView.setText(cartText);
            cartExpandHintView.setVisibility(View.VISIBLE);
            checkoutButton.setEnabled(true);
            checkoutButton.setText(getString(R.string.checkout_with_count, total));
        }
        refreshCartSheet();
        if (adapter != null) {
            adapter.refreshAll();
        }
    }
}
