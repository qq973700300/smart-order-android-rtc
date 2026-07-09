package com.example.test5.order.mq;

import android.content.Context;
import android.util.Log;

import com.example.test5.device.tashi.StockBinVoiceController;
import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigStore;
import com.example.test5.recipe.flow.RecipeFlow;
import com.example.test5.recipe.flow.RecipeFlowExecutor;
import com.example.test5.recipe.flow.RecipeFlowStore;
import com.google.gson.Gson;

/** 收到 MQ 订单后匹配菜谱并驱动料仓（后续可扩展机械臂/炒菜机）。 */
public final class OrderProductionExecutor {

    private static final String TAG = "OrderProduce";

    private final Context appContext;
    private final Gson gson = new Gson();

    public OrderProductionExecutor(Context context) {
        appContext = context.getApplicationContext();
    }

    public boolean process(
            String orderNumber,
            String topic,
            String dishNameRaw,
            String tableNumber,
            String cookNumber,
            String source,
            String rawJson
    ) {
        OrderInbox.Entry entry = OrderInbox.getInstance().add(
                orderNumber,
                topic,
                dishNameRaw,
                tableNumber,
                cookNumber,
                source,
                rawJson
        );
        return runProduction(entry, dishNameRaw);
    }

    boolean runProduction(OrderInbox.Entry entry, String dishNameRaw) {
        if (!MqSettingsStore.isAutoProduce(appContext)) {
            OrderInbox.getInstance().updateStatus(
                    entry,
                    OrderInbox.Status.RECEIVED,
                    "已接收，暂不动设备");
            return true;
        }

        String dishName = normalizeDishName(dishNameRaw);
        OrderInbox.getInstance().updateStatus(entry, OrderInbox.Status.PROCESSING, "匹配菜谱…");

        DishsConfig config = DishsConfigStore.findByDishName(appContext, dishName);
        if (config == null) {
            OrderInbox.getInstance().updateStatus(
                    entry,
                    OrderInbox.Status.FAILED,
                    "未找到菜谱: " + dishName);
            Log.w(TAG, "no recipe for " + dishName);
            return false;
        }

        RecipeFlow flow = RecipeFlowStore.getByRecipeId(appContext, config.id);
        if (flow != null && !flow.nodes.isEmpty()) {
            return runCustomFlow(entry, dishName, config, flow);
        }

        String location = config.dishLocation;
        OrderInbox.getInstance().updateStatus(
                entry,
                OrderInbox.Status.PROCESSING,
                "料仓取料 " + location + "…");

        StockBinVoiceController.Result pickResult =
                StockBinVoiceController.pick(appContext, location);
        if (!pickResult.ok) {
            OrderInbox.getInstance().updateStatus(
                    entry,
                    OrderInbox.Status.FAILED,
                    pickResult.message);
            Log.w(TAG, "pick failed " + dishName + ": " + pickResult.message);
            return false;
        }

        OrderInbox.getInstance().updateStatus(
                entry,
                OrderInbox.Status.DONE,
                "料仓已取料 " + location + "（" + dishName + "）");
        Log.i(TAG, "pick OK " + dishName + " location=" + location);
        return true;
    }

    private boolean runCustomFlow(
            OrderInbox.Entry entry,
            String dishName,
            DishsConfig config,
            RecipeFlow flow
    ) {
        OrderInbox.getInstance().updateStatus(
                entry,
                OrderInbox.Status.PROCESSING,
                "执行自定义流程…");
        RecipeFlowExecutor executor = new RecipeFlowExecutor(appContext);
        RecipeFlowExecutor.Result result = executor.execute(flow, config, (index, total, node, message) ->
                OrderInbox.getInstance().updateStatus(
                        entry,
                        OrderInbox.Status.PROCESSING,
                        "流程 " + index + "/" + total + "：" + message));
        if (!result.ok) {
            OrderInbox.getInstance().updateStatus(
                    entry,
                    OrderInbox.Status.FAILED,
                    result.message);
            Log.w(TAG, "flow failed " + dishName + ": " + result.message);
            return false;
        }
        OrderInbox.getInstance().updateStatus(
                entry,
                OrderInbox.Status.DONE,
                result.message + "（" + dishName + "）");
        Log.i(TAG, "flow OK " + dishName);
        return true;
    }

    public OrderMqModels.DataItem parseDataItem(String json) {
        try {
            return gson.fromJson(json, OrderMqModels.DataItem.class);
        } catch (Exception e) {
            return null;
        }
    }

    public OrderMqModels.MessageDetail parseMessageDetail(String messageJson) {
        if (messageJson == null || messageJson.isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(messageJson, OrderMqModels.MessageDetail.class);
        } catch (Exception e) {
            return null;
        }
    }

    public OrderMqModels.VoiceItem parseVoiceItem(String messageJson) {
        if (messageJson == null || messageJson.isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(messageJson, OrderMqModels.VoiceItem.class);
        } catch (Exception e) {
            return null;
        }
    }

    static String normalizeDishName(String dishName) {
        if (dishName == null) {
            return "";
        }
        return dishName.replace("(机器)", "").trim();
    }
}
