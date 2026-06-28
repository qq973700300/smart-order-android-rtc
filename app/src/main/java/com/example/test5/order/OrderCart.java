package com.example.test5.order;

import java.util.LinkedHashMap;
import java.util.Map;

/** 共享购物车：语音 Function Calling 与手动点餐共用 */
public final class OrderCart {

    public interface ChangeListener {
        void onCartChanged(String cartText);
    }

    private static final OrderCart INSTANCE = new OrderCart();

    private final Map<String, Integer> items = new LinkedHashMap<>();
    private ChangeListener changeListener;
    private String lastSubmittedSummary = "";

    private OrderCart() {
    }

    public static OrderCart getInstance() {
        return INSTANCE;
    }

    public void setChangeListener(ChangeListener listener) {
        this.changeListener = listener;
    }

    public String getLastSubmittedSummary() {
        return lastSubmittedSummary;
    }

    void setLastSubmittedSummary(String summary) {
        lastSubmittedSummary = summary == null ? "" : summary;
    }

    public void clear() {
        items.clear();
        notifyChange();
    }

    public int getQuantity(String dishName) {
        if (dishName == null) {
            return 0;
        }
        return items.getOrDefault(dishName, 0);
    }

    /** 手动点餐 +/- */
    public void adjustQuantity(String dishName, int delta) {
        if (!MenuCatalog.isValidDish(dishName)) {
            return;
        }
        int next = Math.max(0, getQuantity(dishName) + delta);
        if (next == 0) {
            items.put(dishName, 0);
        } else {
            items.put(dishName, next);
        }
        notifyChange();
    }

    void addDish(String dishName, int quantity) {
        items.put(dishName, getQuantity(dishName) + quantity);
    }

    void removeDish(String dishName, int quantity) {
        int remove = Math.max(1, quantity);
        items.put(dishName, Math.max(0, getQuantity(dishName) - remove));
    }

    Map<String, Integer> snapshot() {
        return new LinkedHashMap<>(items);
    }

    void setQuantity(String dishName, int quantity) {
        items.put(dishName, Math.max(0, quantity));
    }

    public int countActive() {
        int total = 0;
        for (int q : items.values()) {
            if (q > 0) {
                total += q;
            }
        }
        return total;
    }

    public String buildCartText() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (text.length() > 0) {
                text.append('，');
            }
            text.append(entry.getKey()).append(" x").append(entry.getValue());
        }
        return text.toString();
    }

    void notifyChange() {
        if (changeListener != null) {
            changeListener.onCartChanged(buildCartText());
        }
    }
}
