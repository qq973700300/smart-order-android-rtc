package com.example.test5.recipe.flow;

import android.content.Context;
import android.util.Log;

import com.example.test5.device.opcua.DrumPotConnectionManager;
import com.example.test5.device.opcua.DrumPotOpcConfig;
import com.example.test5.device.opcua.DrumPotOpcMaterials;
import com.example.test5.device.opcua.DrumPotVoiceController;
import com.example.test5.device.tashi.StockBinVoiceController;
import com.example.test5.device.yuejiang.YuejiangRobotController;
import com.example.test5.recipe.DishsConfig;

import androidx.annotation.Nullable;

import com.example.test5.ui.flow.FlowStepErrorHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** 按 DAG 驱动设备：支持并行分支与汇合等待。 */
public final class RecipeFlowExecutor {

    private static final String TAG = "RecipeFlowExec";

    public interface ProgressListener {
        /** 步骤开始执行前回调。 */
        void onStep(int index, int total, FlowNode node, String message);

        /** 步骤执行结束后回调。 */
        void onStepFinished(int index, int total, FlowNode node, boolean success, String message);

        /** 等待类步骤倒计时（每秒）。 */
        default void onWaitTick(FlowNode node, int remainingSeconds, int totalSeconds) {
        }
    }

    public static final class ExecuteOptions {
        @Nullable
        public final FlowStepErrorHandler errorHandler;

        public ExecuteOptions(@Nullable FlowStepErrorHandler errorHandler) {
            this.errorHandler = errorHandler;
        }
    }

    private final Context appContext;

    public RecipeFlowExecutor(Context context) {
        appContext = context.getApplicationContext();
    }

    public Result execute(RecipeFlow flow, DishsConfig recipe, ProgressListener listener) {
        return execute(flow, recipe, listener, null);
    }

    public Result execute(
            RecipeFlow flow,
            DishsConfig recipe,
            ProgressListener listener,
            @Nullable FlowStepErrorHandler errorHandler
    ) {
        ExecuteOptions options = new ExecuteOptions(errorHandler);
        if (flow == null || flow.nodes.isEmpty()) {
            return Result.fail("流程为空");
        }
        if (flow.edges.isEmpty()) {
            return executeSequential(flow, recipe, listener, options);
        }
        return executeParallelDag(flow, recipe, listener, options);
    }

    private Result executeSequential(
            RecipeFlow flow,
            DishsConfig recipe,
            ProgressListener listener,
            ExecuteOptions options
    ) {
        List<FlowNode> order = RecipeFlowStore.executionOrder(flow);
        int total = order.size();
        for (int i = 0; i < total; i++) {
            FlowNode node = order.get(i);
            notify(listener, i + 1, total, node, node.label);
            StepResult stepResult = executeNode(node, recipe, listener);
            if (!stepResult.ok) {
                Result failure = resolveStepFailure(node, stepResult.message, listener, options, i + 1, total);
                if (failure == null) {
                    continue;
                }
                notifyFinished(listener, i + 1, total, node, false, stepResult.message);
                Log.w(TAG, "step failed: " + node.displayTitle() + " -> " + stepResult.message);
                return failure;
            }
            notifyFinished(listener, i + 1, total, node, true, stepResult.message);
        }
        return Result.ok("流程执行完成，共 " + total + " 步");
    }

    private Result executeParallelDag(
            RecipeFlow flow,
            DishsConfig recipe,
            ProgressListener listener,
            ExecuteOptions options
    ) {
        Map<String, FlowNode> nodeMap = new HashMap<>();
        for (FlowNode node : flow.nodes) {
            nodeMap.put(node.id, node);
        }

        Map<String, List<String>> successors = new HashMap<>();
        Map<String, AtomicInteger> remainingPreds = new HashMap<>();
        for (FlowNode node : flow.nodes) {
            successors.put(node.id, new ArrayList<>());
            remainingPreds.put(node.id, new AtomicInteger(0));
        }
        for (FlowEdge edge : flow.edges) {
            if (!nodeMap.containsKey(edge.fromNodeId) || !nodeMap.containsKey(edge.toNodeId)) {
                continue;
            }
            successors.get(edge.fromNodeId).add(edge.toNodeId);
            remainingPreds.get(edge.toNodeId).incrementAndGet();
        }

        Set<String> completed = new HashSet<>();
        AtomicReference<String> failMessage = new AtomicReference<>();
        AtomicReference<Boolean> userStopped = new AtomicReference<>(false);
        AtomicInteger finishedCount = new AtomicInteger(0);
        int total = flow.nodes.size();
        ExecutorService pool = Executors.newCachedThreadPool();

        try {
            while (failMessage.get() == null) {
                List<String> batch = new ArrayList<>();
                for (FlowNode node : flow.nodes) {
                    String id = node.id;
                    if (!completed.contains(id) && remainingPreds.get(id).get() == 0) {
                        batch.add(id);
                    }
                }
                if (batch.isEmpty()) {
                    break;
                }

                CountDownLatch latch = new CountDownLatch(batch.size());
                boolean parallel = batch.size() > 1;

                for (String nodeId : batch) {
                    pool.execute(() -> {
                        try {
                            if (failMessage.get() != null) {
                                return;
                            }
                            FlowNode node = nodeMap.get(nodeId);
                            if (node == null) {
                                failMessage.compareAndSet(null, "节点丢失: " + nodeId);
                                return;
                            }
                            int index = finishedCount.incrementAndGet();
                            notify(listener, index, total, node,
                                    (parallel ? "并行 " : "") + node.label);
                            StepResult stepResult = executeNode(node, recipe, listener);
                            if (!stepResult.ok) {
                                synchronized (completed) {
                                    if (failMessage.get() != null) {
                                        return;
                                    }
                                    Result failure = resolveStepFailure(
                                            node, stepResult.message, listener, options, index, total);
                                    if (failure == null) {
                                        completed.add(nodeId);
                                        for (String nextId : successors.get(nodeId)) {
                                            remainingPreds.get(nextId).decrementAndGet();
                                        }
                                        return;
                                    }
                                    notifyFinished(listener, index, total, node, false, stepResult.message);
                                    userStopped.set(failure.userStopped);
                                    failMessage.compareAndSet(null, failure.message);
                                }
                                return;
                            }
                            notifyFinished(listener, index, total, node, true, stepResult.message);
                            synchronized (completed) {
                                completed.add(nodeId);
                            }
                            for (String nextId : successors.get(nodeId)) {
                                remainingPreds.get(nextId).decrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }

                try {
                    latch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return Result.fail("流程被中断");
                }
            }

            if (failMessage.get() != null) {
                return Result.fail(failMessage.get(), Boolean.TRUE.equals(userStopped.get()));
            }
            if (completed.size() < flow.nodes.size()) {
                return Result.fail("部分步骤未执行，请检查连线是否断层");
            }
            return Result.ok("流程执行完成，共 " + total + " 步（支持并行）");
        } finally {
            pool.shutdownNow();
        }
    }

    private static void notify(ProgressListener listener, int index, int total, FlowNode node, String message) {
        if (listener != null) {
            listener.onStep(index, total, node, message);
        }
    }

    private static void notifyFinished(
            ProgressListener listener,
            int index,
            int total,
            FlowNode node,
            boolean success,
            String message
    ) {
        if (listener != null) {
            listener.onStepFinished(index, total, node, success, message);
        }
    }

    @Nullable
    private Result resolveStepFailure(
            FlowNode node,
            String message,
            ProgressListener listener,
            ExecuteOptions options,
            int index,
            int total
    ) {
        if (options.errorHandler == null) {
            return Result.fail("步骤失败（" + node.label + "）：" + message);
        }
        FlowStepErrorHandler.Decision decision = options.errorHandler.onStepFailed(node, message);
        if (decision == FlowStepErrorHandler.Decision.SKIP) {
            notifyFinished(listener, index, total, node, false, "已跳过：" + message);
            return null;
        }
        return Result.fail("已停止：" + message, true);
    }

    private static void notifyWaitTick(
            ProgressListener listener,
            FlowNode node,
            int remainingSeconds,
            int totalSeconds
    ) {
        if (listener != null) {
            listener.onWaitTick(node, remainingSeconds, totalSeconds);
        }
    }

    private StepResult executeNode(FlowNode node, DishsConfig recipe, ProgressListener listener) {
        FlowDeviceType device = node.deviceType();
        if (device == null) {
            return StepResult.fail("未知设备: " + node.deviceId);
        }
        Map<String, String> params = node.params;
        switch (device) {
            case STOCK_BIN:
                return executeStockBin(node.actionId, params, recipe);
            case YUEJIANG:
                return executeYuejiang(node.actionId, params);
            case DRUM_POT:
                return executeDrumPot(node.actionId, params, listener, node);
            case FLOW_CONTROL:
                return executeFlowControl(node.actionId, params, listener, node);
            default:
                return StepResult.fail("不支持的设备");
        }
    }

    private StepResult executeFlowControl(
            String actionId,
            Map<String, String> params,
            ProgressListener listener,
            FlowNode node
    ) {
        if ("join".equals(actionId)) {
            return StepResult.ok("分支已汇合");
        }
        if ("delay".equals(actionId)) {
            int seconds = parseInt(param(params, "seconds"), 0);
            return waitSeconds(listener, node, seconds, "已等待 " + seconds + " 秒");
        }
        return StepResult.fail("未知流程控制: " + actionId);
    }

    private StepResult waitSeconds(
            ProgressListener listener,
            FlowNode node,
            int seconds,
            String doneMessage
    ) {
        if (seconds <= 0) {
            return StepResult.ok("跳过等待");
        }
        try {
            for (int remaining = seconds; remaining > 0; remaining--) {
                notifyWaitTick(listener, node, remaining, seconds);
                Thread.sleep(1000L);
            }
            return StepResult.ok(doneMessage);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return StepResult.fail("等待被中断");
        }
    }

    private StepResult executeStockBin(String actionId, Map<String, String> params, DishsConfig recipe) {
        if (!"pick".equals(actionId)) {
            return StepResult.fail("未知料仓动作: " + actionId);
        }
        String slot = param(params, "slot_code");
        if (slot.isEmpty() && recipe != null && recipe.dishLocation != null) {
            slot = recipe.dishLocation;
        }
        if (slot.isEmpty()) {
            return StepResult.fail("料仓位置码为空");
        }
        StockBinVoiceController.Result result = StockBinVoiceController.pick(appContext, slot);
        return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
    }

    private StepResult executeYuejiang(String actionId, Map<String, String> params) {
        if (!"run_script".equals(actionId)) {
            return StepResult.fail("未知机械臂动作: " + actionId);
        }
        String script = param(params, "script");
        if (script.isEmpty()) {
            return StepResult.fail("脚本名为空");
        }
        YuejiangRobotController.Result result = YuejiangRobotController.runScript(appContext, script);
        return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
    }

    private StepResult executeDrumPot(
            String actionId,
            Map<String, String> params,
            ProgressListener listener,
            FlowNode node
    ) {
        if ("wait".equals(actionId)) {
            int seconds = parseInt(param(params, "seconds"), 0);
            return waitSeconds(listener, node, seconds, "已等待 " + seconds + " 秒");
        }
        DrumPotOpcMaterials.Def material = DrumPotOpcMaterials.findByActionId(actionId);
        if (material != null) {
            int amountMs = parseInt(param(params, "amount_ms"), 0);
            DrumPotConnectionManager manager = DrumPotConnectionManager.getInstance(appContext);
            DrumPotVoiceController.Result result = manager.dischargeMaterial(
                    material.timeBrowseName,
                    material.startBrowseName,
                    amountMs,
                    material.label);
            return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
        }
        if ("heat_start".equals(actionId)) {
            int gear = parseInt(param(params, "gear"), 2);
            DrumPotVoiceController.Result result =
                    DrumPotConnectionManager.getInstance(appContext).heatStart(gear);
            return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
        }
        if ("heat_stop".equals(actionId)) {
            DrumPotVoiceController.Result result =
                    DrumPotConnectionManager.getInstance(appContext).heatStop();
            return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
        }
        if ("abs_write_position".equals(actionId)) {
            float position = parseFloat(param(params, "position"), 0f);
            DrumPotVoiceController.Result result =
                    DrumPotConnectionManager.getInstance(appContext).writeAbsolutePosition(position);
            return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
        }
        if ("abs_pulse_move".equals(actionId)) {
            DrumPotVoiceController.Result result =
                    DrumPotConnectionManager.getInstance(appContext).pulseAbsoluteMove();
            return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
        }
        if ("abs_move".equals(actionId)) {
            float position = parseFloat(param(params, "position"), DrumPotOpcConfig.AXIS_POS_SERVE);
            DrumPotVoiceController.Result result =
                    DrumPotConnectionManager.getInstance(appContext).moveToAbsolutePosition(position);
            return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
        }
        DrumPotVoiceController.Action action = mapDrumAction(actionId);
        if (action == null) {
            return StepResult.fail("未知滚筒动作: " + actionId);
        }
        int gear = parseInt(param(params, "gear"), 1);
        DrumPotVoiceController.Result result = DrumPotVoiceController.control(appContext, action, gear);
        return result.ok ? StepResult.ok(result.message) : StepResult.fail(result.message);
    }

    private static DrumPotVoiceController.Action mapDrumAction(String actionId) {
        if (actionId == null) {
            return null;
        }
        switch (actionId) {
            case "start":
                return DrumPotVoiceController.Action.START;
            case "stop":
                return DrumPotVoiceController.Action.STOP;
            case "reset":
                return DrumPotVoiceController.Action.RESET;
            case "rotate_start":
                return DrumPotVoiceController.Action.ROTATE_START;
            case "rotate_stop":
                return DrumPotVoiceController.Action.ROTATE_STOP;
            default:
                return null;
        }
    }

    private static String param(Map<String, String> params, String key) {
        if (params == null || !params.containsKey(key) || params.get(key) == null) {
            return "";
        }
        return params.get(key).trim();
    }

    private static int parseInt(String text, int defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static float parseFloat(String text, float defaultValue) {
        if (text == null || text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Float.parseFloat(text.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final class StepResult {
        final boolean ok;
        final String message;

        private StepResult(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        static StepResult ok(String message) {
            return new StepResult(true, message);
        }

        static StepResult fail(String message) {
            return new StepResult(false, message);
        }
    }

    public static final class Result {
        public final boolean ok;
        public final String message;
        public final boolean userStopped;

        private Result(boolean ok, String message, boolean userStopped) {
            this.ok = ok;
            this.message = message;
            this.userStopped = userStopped;
        }

        public static Result ok(String message) {
            return new Result(true, message, false);
        }

        public static Result fail(String message) {
            return fail(message, false);
        }

        public static Result fail(String message, boolean userStopped) {
            return new Result(false, message, userStopped);
        }
    }

    public static void applyRecipeFields(DishsConfig config, RecipeFlow flow) {
        if (config == null || flow == null) {
            return;
        }
        boolean hasHeat = false;
        for (FlowNode node : flow.nodes) {
            if (FlowDeviceType.STOCK_BIN.id.equals(node.deviceId) && "pick".equals(node.actionId)) {
                String slot = node.params != null ? node.params.get("slot_code") : null;
                if (slot != null && !slot.trim().isEmpty()) {
                    config.dishLocation = slot.trim();
                }
            }
            if (FlowDeviceType.DRUM_POT.id.equals(node.deviceId) && "wait".equals(node.actionId)) {
                String seconds = node.params != null ? node.params.get("seconds") : null;
                if (seconds != null && !seconds.trim().isEmpty()) {
                    config.friedTime = parseInt(seconds, config.friedTime);
                }
            }
            if (FlowDeviceType.FLOW_CONTROL.id.equals(node.deviceId) && "delay".equals(node.actionId)) {
                String seconds = node.params != null ? node.params.get("seconds") : null;
                if (seconds != null && !seconds.trim().isEmpty()) {
                    config.friedTime = parseInt(seconds, config.friedTime);
                }
            }
            if (FlowDeviceType.DRUM_POT.id.equals(node.deviceId) && "heat_start".equals(node.actionId)) {
                hasHeat = true;
            }
            DrumPotOpcMaterials.Def material = DrumPotOpcMaterials.findByActionId(node.actionId);
            if (material != null && material.recipeField != null) {
                int amount = parseInt(node.params != null ? node.params.get("amount_ms") : null, 0);
                applyRecipeAmount(config, material.recipeField, amount);
            }
        }
        if (config.dishLocation == null || config.dishLocation.isEmpty()) {
            config.dishLocation = "150";
        }
        config.statusType = hasHeat;
        if (config.friedTime <= 0) {
            config.friedTime = 65;
        }
    }

    private static void applyRecipeAmount(DishsConfig config, String field, int amount) {
        switch (field) {
            case "outWater":
                config.outWater = amount;
                break;
            case "outOil":
                config.outOil = amount;
                break;
            case "outSalt":
                config.outSalt = amount;
                break;
            case "outShengSauce":
                config.outShengSauce = amount;
                break;
            case "outLaoSauce":
                config.outLaoSauce = amount;
                break;
            case "outVinegar":
                config.outVinegar = amount;
                break;
            case "whiteSugar":
                config.whiteSugar = amount;
                break;
            case "oldsoySauce":
                config.oldsoySauce = amount;
                break;
            default:
                break;
        }
    }
}
