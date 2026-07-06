package com.example.test5.order;



import android.util.Log;

import com.google.gson.Gson;

import com.google.gson.JsonObject;



import java.io.IOException;

import java.util.Map;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;



/**

 * Function Calling 工具执行：add_dish / remove_dish / get_cart / submit_order。

 * submit_order 会调用 OrderSubscribe 送厨。

 */

public final class RestaurantFunctionHandler {

    private static final String TAG = "OrderSubmit";

    public static final String TOOL_ADD_DISH = "add_dish";

    public static final String TOOL_REMOVE_DISH = "remove_dish";

    public static final String TOOL_GET_CART = "get_cart";

    public static final String TOOL_SUBMIT_ORDER = "submit_order";
    public static final String TOOL_END_CONVERSATION = "end_conversation";
    public static final String TOOL_SING_SONG = "sing_song";

    public interface CartListener {

        void onCartUpdated(String cartText);

    }

    public interface SubmitListener {

        void onOrderSubmitted(boolean success, String message, String submittedSummary);

    }

    public interface SingListener {

        void onSingRequested();
    }

    public interface EndConversationListener {

        void onEndConversationRequested();
    }

    private final Gson gson = new Gson();

    private final OrderCart cart = OrderCart.getInstance();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private CartListener cartListener;

    private SubmitListener submitListener;
    private SingListener singListener;
    private EndConversationListener endConversationListener;

    public void setCartListener(CartListener cartListener) {

        this.cartListener = cartListener;

    }

    public void setSubmitListener(SubmitListener submitListener) {

        this.submitListener = submitListener;

    }

    public void setSingListener(SingListener singListener) {
        this.singListener = singListener;
    }

    public void setEndConversationListener(EndConversationListener listener) {
        this.endConversationListener = listener;
    }

    public String execute(String toolName, String argumentsJson) {

        try {

            JsonObject args = argumentsJson == null || argumentsJson.isEmpty()

                    ? new JsonObject()

                    : gson.fromJson(argumentsJson, JsonObject.class);



            String result;

            switch (toolName) {

                case TOOL_ADD_DISH:

                    result = addDish(

                            getString(args, "dish_name"),

                            Math.max(1, getInt(args, "quantity", 1))

                    );

                    break;

                case TOOL_REMOVE_DISH:

                    result = removeDish(

                            getString(args, "dish_name"),

                            Math.max(1, getInt(args, "quantity", 1))

                    );

                    break;

                case TOOL_GET_CART:

                    result = formatCart("当前订单");

                    break;

                case TOOL_SUBMIT_ORDER:

                    result = submitOrderSync();

                    break;

                case TOOL_END_CONVERSATION:
                    result = endConversation();
                    break;

                case TOOL_SING_SONG:
                    result = singSong();
                    break;

                default:

                    result = error("未知工具: " + toolName);

                    break;

            }

            notifyCart();

            return result;

        } catch (Exception e) {

            return error(e.getMessage());

        }

    }



    public int getQuantity(String dishName) {

        return cart.getQuantity(dishName);

    }



    public void shutdown() {

        executor.shutdownNow();

    }



    private String addDish(String dishName, int quantity) {

        if (dishName.isEmpty()) {

            return error("dish_name 不能为空");

        }

        if (!MenuCatalog.isValidDish(dishName)) {

            return error("无效菜名: " + dishName);

        }

        cart.addDish(dishName, quantity);

        return formatCart("已添加 " + dishName + " x" + quantity);

    }



    private String removeDish(String dishName, int quantity) {

        if (dishName.isEmpty()) {

            return error("dish_name 不能为空");

        }

        if (!MenuCatalog.isValidDish(dishName)) {

            return error("无效菜名: " + dishName);

        }

        int before = cart.getQuantity(dishName);

        if (before <= 0) {

            return error("订单里没有 " + dishName);

        }

        int removed = Math.min(quantity, before);

        cart.removeDish(dishName, removed);

        return formatCart("已减 " + dishName + " x" + removed);

    }



    private String submitOrderSync() throws IOException, InterruptedException {

        int total = cart.countActive();

        if (total == 0) {

            return error("购物车为空，无法送厨");

        }



        StringBuilder summary = new StringBuilder();

        int submitted = 0;

        String lastError = "";



        for (Map.Entry<String, Integer> entry : cart.snapshot().entrySet()) {

            int quantity = entry.getValue();

            if (quantity <= 0) {

                continue;

            }

            String dishName = entry.getKey();

            int dishSubmitted = 0;

            for (int i = 0; i < quantity; i++) {

                String message = "{" + dishName + ":" + (i + 1) + "}";

                HttpHelper.OrderSubscribeResult result = HttpHelper.submitOrder(

                        StoreConfig.STORE_ID,

                        StoreConfig.EQUIPMENT_NUM,

                        StoreConfig.STORE_NAME,

                        message

                );

                if (result.success) {

                    dishSubmitted++;

                    submitted++;

                } else {

                    lastError = result.msg;

                }

                Thread.sleep(300);

            }

            cart.setQuantity(dishName, Math.max(0, quantity - dishSubmitted));

            if (dishSubmitted > 0) {

                if (summary.length() > 0) {

                    summary.append('，');

                }

                summary.append(dishName).append(" x").append(dishSubmitted);

            }

        }



        if (submitted == 0) {

            String msg = lastError.isEmpty() ? "送厨失败" : lastError;

            notifySubmit(false, msg, "");

            return error(msg);

        }

        String summaryText = summary.toString();

        cart.setLastSubmittedSummary(summaryText);

        String message = "已送厨：" + summaryText + "，机器开始做了";

        Log.i(TAG, message);

        notifySubmit(true, message, summaryText);

        JsonObject ok = new JsonObject();

        ok.addProperty("ok", true);

        ok.addProperty("message", message);

        ok.addProperty("submitted_summary", summaryText);

        return gson.toJson(ok);

    }



    private void notifySubmit(boolean success, String message, String submittedSummary) {

        if (submitListener != null) {

            submitListener.onOrderSubmitted(success, message, submittedSummary);

        }

    }



    private String singSong() {
        if (singListener != null) {
            singListener.onSingRequested();
        }
        JsonObject ok = new JsonObject();
        ok.addProperty("ok", true);
        ok.addProperty("message", "正在播放《鹅企的说唱》");
        ok.addProperty("song", "eqi_qiye_rap");
        return gson.toJson(ok);
    }

    private String endConversation() {
        if (endConversationListener != null) {
            endConversationListener.onEndConversationRequested();
        }
        JsonObject ok = new JsonObject();
        ok.addProperty("ok", true);
        ok.addProperty("message", "已确认结束");
        return gson.toJson(ok);
    }

    private String formatCart(String prefix) {

        JsonObject result = new JsonObject();

        result.addProperty("ok", true);

        result.addProperty("message", prefix);

        result.addProperty("cart_text", cart.buildCartText());

        return gson.toJson(result);

    }



    private void notifyCart() {

        cart.notifyChange();

        if (cartListener != null) {

            cartListener.onCartUpdated(cart.buildCartText());

        }

    }



    private static String getString(JsonObject args, String key) {

        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString() : "";

    }



    private static int getInt(JsonObject args, String key, int defaultValue) {

        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsInt() : defaultValue;

    }



    private String error(String message) {

        JsonObject node = new JsonObject();

        node.addProperty("ok", false);

        node.addProperty("message", message == null ? "" : message);

        return gson.toJson(node);

    }

}


