package com.example.test5.order.mq;

import android.content.Context;
import android.util.Log;

import com.example.test5.device.tashi.StockBinVoiceController;
import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigStore;
import com.example.test5.recipe.flow.FlowNode;
import com.example.test5.recipe.flow.RecipeFlow;
import com.example.test5.recipe.flow.RecipeFlowExecutor;
import com.example.test5.recipe.flow.RecipeFlowStore;
import com.google.gson.Gson;

import com.example.test5.ui.flow.FlowStepErrorHandler;

import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/** 收到 MQ 订单或本机下单后匹配菜谱并驱动设备。 */
public final class OrderProductionExecutor {

    private static final String TAG = "OrderProduce";

    public static final class ProduceResult {
        public final boolean ok;
        public final String message;
        public final boolean userStopped;

        ProduceResult(boolean ok, String message, boolean userStopped) {
            this.ok = ok;
            this.message = message != null ? message : "";
            this.userStopped = userStopped;
        }
    }

    @Nullable
    private FlowStepErrorHandler stepErrorHandler;

    public void setStepErrorHandler(@Nullable FlowStepErrorHandler handler) {
        this.stepErrorHandler = handler;
    }

    private final Context appContext;
    private final Gson gson = new Gson();

    public OrderProductionExecutor(Context context) {
        appContext = context.getApplicationContext();
    }

    /** 本机手动/语音下单：直接驱动设备，不受「自动取料」开关限制。 */
    public ProduceResult produceLocal(String dishName, String source) {
        return produceLocal(dishName, source, null);
    }

    public ProduceResult produceLocal(
            String dishName,
            String source,
            @Nullable RecipeFlowExecutor.ProgressListener progressListener
    ) {
        String orderNumber = "LOCAL_" + System.currentTimeMillis();
        OrderInbox.Entry entry = OrderInbox.getInstance().add(
                orderNumber,
                MqSettingsStore.getStoreTopic(appContext),
                dishName,
                "",
                "",
                source,
                "{\"local\":true,\"dish\":\"" + dishName + "\"}"
        );
        AtomicBoolean userStopped = new AtomicBoolean(false);
        boolean ok = runProduction(entry, dishName, true, progressListener, userStopped);
        return new ProduceResult(ok, entry.statusMessage, userStopped.get());
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
        return runProduction(entry, dishNameRaw, false);
    }

    boolean runProduction(OrderInbox.Entry entry, String dishNameRaw) {
        return runProduction(entry, dishNameRaw, false, null);
    }

    boolean runProduction(OrderInbox.Entry entry, String dishNameRaw, boolean forceProduce) {
        return runProduction(entry, dishNameRaw, forceProduce, null);
    }

    boolean runProduction(
            OrderInbox.Entry entry,
            String dishNameRaw,
            boolean forceProduce,
            @Nullable RecipeFlowExecutor.ProgressListener progressListener
    ) {
        return runProduction(entry, dishNameRaw, forceProduce, progressListener, null);
    }

    boolean runProduction(
            OrderInbox.Entry entry,
            String dishNameRaw,
            boolean forceProduce,
            @Nullable RecipeFlowExecutor.ProgressListener progressListener,
            @Nullable AtomicBoolean userStoppedOut
    ) {
        if (!forceProduce && !MqSettingsStore.isAutoProduce(appContext)) {
            OrderInbox.getInstance().updateStatus(
                    entry,
                    OrderInbox.Status.RECEIVED,
                    "已接收，暂不动设备");
            return true;
        }

        String dishName = normalizeDishName(dishNameRaw);
        DishsConfigStore.reload(appContext);
        RecipeFlowStore.reload(appContext);
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
            Log.i(TAG, "run custom flow for " + dishName + ", steps=" + flow.nodes.size());
            return runCustomFlow(entry, dishName, config, flow, progressListener, userStoppedOut);
        }

        Log.i(TAG, "no custom flow for " + dishName + ", fallback stock bin");

        String location = config.dishLocation;
        OrderInbox.getInstance().updateStatus(
                entry,
                OrderInbox.Status.PROCESSING,
                "料仓取料 " + location + "…");
        notifyProgress(progressListener, 1, 1, null, "料仓取料 " + location);

        StockBinVoiceController.Result pickResult =
                StockBinVoiceController.pick(appContext, location);
        notifyProgressFinished(progressListener, 1, 1, null, pickResult.ok, pickResult.message);
        if (!pickResult.ok) {
            if (handleStepFailure(null, pickResult.message, progressListener, 1, 1, userStoppedOut)) {
                OrderInbox.getInstance().updateStatus(
                        entry,
                        OrderInbox.Status.DONE,
                        "已跳过料仓取料（" + dishName + "）");
                return true;
            }
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
            RecipeFlow flow,
            @Nullable RecipeFlowExecutor.ProgressListener progressListener,
            @Nullable AtomicBoolean userStoppedOut
    ) {
        OrderInbox.getInstance().updateStatus(
                entry,
                OrderInbox.Status.PROCESSING,
                "执行自定义流程…");
        RecipeFlowExecutor executor = new RecipeFlowExecutor(appContext);
        RecipeFlowExecutor.Result result = executor.execute(flow, config, new RecipeFlowExecutor.ProgressListener() {
            @Override
            public void onStep(int index, int total, FlowNode node, String message) {
                OrderInbox.getInstance().updateStatus(
                        entry,
                        OrderInbox.Status.PROCESSING,
                        "流程 " + index + "/" + total + "：" + message);
                notifyProgress(progressListener, index, total, node, message);
            }

            @Override
            public void onStepFinished(
                    int index,
                    int total,
                    FlowNode node,
                    boolean success,
                    String message
            ) {
                notifyProgressFinished(progressListener, index, total, node, success, message);
            }

            @Override
            public void onWaitTick(FlowNode node, int remainingSeconds, int totalSeconds) {
                if (progressListener != null) {
                    progressListener.onWaitTick(node, remainingSeconds, totalSeconds);
                }
            }
        }, stepErrorHandler);
        if (!result.ok) {
            OrderInbox.getInstance().updateStatus(
                    entry,
                    OrderInbox.Status.FAILED,
                    result.message);
            Log.w(TAG, "flow failed " + dishName + ": " + result.message);
            if (result.userStopped && userStoppedOut != null) {
                userStoppedOut.set(true);
                entry.statusMessage = result.message;
            }
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

    private boolean handleStepFailure(
            @Nullable FlowNode node,
            String message,
            @Nullable RecipeFlowExecutor.ProgressListener progressListener,
            int index,
            int total,
            @Nullable AtomicBoolean userStoppedOut
    ) {
        if (stepErrorHandler == null) {
            return false;
        }
        FlowStepErrorHandler.Decision decision = stepErrorHandler.onStepFailed(node, message);
        if (decision == FlowStepErrorHandler.Decision.SKIP) {
            notifyProgressFinished(progressListener, index, total, node, false, "已跳过：" + message);
            return true;
        }
        if (userStoppedOut != null) {
            userStoppedOut.set(true);
        }
        return false;
    }

    private static void notifyProgress(
            @Nullable RecipeFlowExecutor.ProgressListener listener,
            int index,
            int total,
            @Nullable FlowNode node,
            String message
    ) {
        if (listener != null) {
            listener.onStep(index, total, node, message);
        }
    }

    private static void notifyProgressFinished(
            @Nullable RecipeFlowExecutor.ProgressListener listener,
            int index,
            int total,
            @Nullable FlowNode node,
            boolean success,
            String message
    ) {
        if (listener != null) {
            listener.onStepFinished(index, total, node, success, message);
        }
    }
}
