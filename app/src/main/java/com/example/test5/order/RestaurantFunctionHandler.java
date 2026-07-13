package com.example.test5.order;



import android.util.Log;

import com.example.test5.order.mq.OrderProductionExecutor;
import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigStore;
import com.example.test5.recipe.flow.FlowNode;
import com.example.test5.recipe.flow.RecipeFlow;
import com.example.test5.recipe.flow.RecipeFlowExecutor;
import com.example.test5.recipe.flow.RecipeFlowStore;
import com.example.test5.ui.flow.FlowProductionUi;
import com.example.test5.ui.flow.FlowStepErrorHandler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.util.Map;

import java.util.concurrent.ExecutorService;

import java.util.concurrent.Executors;



/**

 * Function Calling 工具执行：add_dish / remove_dish / get_cart / submit_order。

 * submit_order 在本机直接驱动产线（料仓/自定义流程）。

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

    public interface ProductionProgressListener {
        void onProductionProgress(String dishName, int index, int total, String message);
    }

    private final Gson gson = new Gson();

    private final OrderCart cart = OrderCart.getInstance();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final DeviceFunctionHandler deviceHandler;

    private final OrderProductionExecutor productionExecutor;

    private final android.content.Context appContext;

    private CartListener cartListener;

    private SubmitListener submitListener;
    private ProductionProgressListener productionProgressListener;
    private FlowProductionUi flowProductionUi;
    private FlowStepErrorHandler stepErrorHandler;
    private SingListener singListener;
    private EndConversationListener endConversationListener;

    public RestaurantFunctionHandler(android.content.Context context) {
        appContext = context.getApplicationContext();
        deviceHandler = new DeviceFunctionHandler(context);
        productionExecutor = new OrderProductionExecutor(context);
    }

    public void setCartListener(CartListener cartListener) {

        this.cartListener = cartListener;

    }

    public void setSubmitListener(SubmitListener submitListener) {

        this.submitListener = submitListener;

    }

    public void setProductionProgressListener(ProductionProgressListener listener) {
        this.productionProgressListener = listener;
    }

    public void setFlowProductionUi(FlowProductionUi ui) {
        this.flowProductionUi = ui;
    }

    public void setFlowStepErrorHandler(FlowStepErrorHandler handler) {
        this.stepErrorHandler = handler;
        productionExecutor.setStepErrorHandler(handler);
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

                    String deviceResult = deviceHandler.execute(toolName, argumentsJson);

                    if (deviceResult != null) {

                        result = deviceResult;

                    } else {

                        result = error("未知工具: " + toolName);

                    }

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

        if (!DishsConfigStore.isValidDishName(appContext, dishName)) {

            return error("无效菜名: " + dishName);

        }

        cart.addDish(dishName, quantity);

        return formatCart("已添加 " + dishName + " x" + quantity);

    }



    private String removeDish(String dishName, int quantity) {

        if (dishName.isEmpty()) {

            return error("dish_name 不能为空");

        }

        if (!DishsConfigStore.isValidDishName(appContext, dishName)) {

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



    private String submitOrderSync() {
        int total = cart.countActive();
        if (total == 0) {
            return error("购物车为空，无法送厨");
        }

        StringBuilder summary = new StringBuilder();
        int submitted = 0;
        String lastError = "";
        boolean userStoppedAll = false;

        for (Map.Entry<String, Integer> entry : cart.snapshot().entrySet()) {
            if (userStoppedAll) {
                break;
            }
            int quantity = entry.getValue();
            if (quantity <= 0) {
                continue;
            }
            String dishName = entry.getKey();
            int dishSubmitted = 0;
            for (int i = 0; i < quantity; i++) {
                if (userStoppedAll) {
                    break;
                }
                final String currentDish = dishName;
                notifyDishStarted(currentDish);
                RecipeFlowExecutor.ProgressListener stepListener = new RecipeFlowExecutor.ProgressListener() {
                    @Override
                    public void onStep(int index, int total, FlowNode node, String message) {
                        notifyStepStarted(currentDish, node, index, total, message);
                    }

                    @Override
                    public void onStepFinished(
                            int index,
                            int total,
                            FlowNode node,
                            boolean success,
                            String message
                    ) {
                        notifyStepFinished(currentDish, node, index, total, success, message);
                    }

                    @Override
                    public void onWaitTick(FlowNode node, int remainingSeconds, int totalSeconds) {
                        notifyWaitTick(currentDish, node, remainingSeconds, totalSeconds);
                    }
                };
                OrderProductionExecutor.ProduceResult result =
                        productionExecutor.produceLocal(dishName, "local", stepListener);
                notifyDishFinished(currentDish, result.ok, result.message);
                if (result.userStopped) {
                    userStoppedAll = true;
                    lastError = result.message.isEmpty() ? "流程已停止" : result.message;
                    break;
                }
                if (result.ok) {
                    dishSubmitted++;
                    submitted++;
                } else {
                    lastError = result.message.isEmpty() ? "生产失败" : result.message;
                }
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
            String msg = lastError.isEmpty() ? "生产失败" : lastError;
            notifySubmit(false, msg, "");
            return error(msg);
        }

        String summaryText = summary.toString();
        cart.setLastSubmittedSummary(summaryText);
        String message = userStoppedAll
                ? ("部分完成：" + summaryText + "（已手动停止）")
                : ("已开始生产：" + summaryText);
        Log.i(TAG, message);
        notifySubmit(true, message, summaryText);

        JsonObject ok = new JsonObject();
        ok.addProperty("ok", true);
        ok.addProperty("message", message);
        ok.addProperty("submitted_summary", summaryText);
        ok.addProperty("user_stopped", userStoppedAll);
        return gson.toJson(ok);
    }

    private void notifyDishStarted(String dishName) {
        if (flowProductionUi != null) {
            flowProductionUi.onDishStarted(dishName);
            DishsConfigStore.reload(appContext);
            RecipeFlowStore.reload(appContext);
            DishsConfig config = DishsConfigStore.findByDishName(appContext, dishName);
            RecipeFlow flow = config != null
                    ? RecipeFlowStore.getByRecipeId(appContext, config.id)
                    : null;
            flowProductionUi.onDishFlowReady(dishName, flow);
        }
    }

    private void notifyStepStarted(
            String dishName,
            FlowNode node,
            int index,
            int total,
            String message
    ) {
        if (flowProductionUi != null) {
            flowProductionUi.onStepStarted(dishName, node, index, total, message);
        }
        if (productionProgressListener != null) {
            productionProgressListener.onProductionProgress(dishName, index, total, message);
        }
    }

    private void notifyStepFinished(
            String dishName,
            FlowNode node,
            int index,
            int total,
            boolean success,
            String message
    ) {
        if (flowProductionUi != null) {
            flowProductionUi.onStepFinished(dishName, node, index, total, success, message);
        }
    }

    private void notifyWaitTick(
            String dishName,
            FlowNode node,
            int remainingSeconds,
            int totalSeconds
    ) {
        if (flowProductionUi != null) {
            flowProductionUi.onWaitTick(dishName, node, remainingSeconds, totalSeconds);
        }
    }

    private void notifyDishFinished(String dishName, boolean success, String message) {
        if (flowProductionUi != null) {
            flowProductionUi.onDishFinished(dishName, success, message);
        }
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


