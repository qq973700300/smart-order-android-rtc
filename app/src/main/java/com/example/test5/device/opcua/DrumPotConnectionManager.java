package com.example.test5.device.opcua;

import android.content.Context;
import android.util.Log;

import com.example.test5.device.settings.DeviceSettingsStore;
import com.example.test5.net.NetworkDiagnostics;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 滚筒 OPC UA 长连接：App 启动后连接并加载节点映射，语音控制时直接写节点。 */
public final class DrumPotConnectionManager {

    private static final String TAG = "DrumPotVoice";
    private static final long OPC_TIMEOUT_MS = 10_000L;

    private static volatile DrumPotConnectionManager instance;

    private final Context appContext;
    private final OpcUaClientHelper client = new OpcUaClientHelper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DrumPotOpcConn");
        t.setDaemon(true);
        return t;
    });
    private volatile boolean nodeMapReady;

    private DrumPotConnectionManager(Context context) {
        appContext = context.getApplicationContext();
    }

    public static DrumPotConnectionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (DrumPotConnectionManager.class) {
                if (instance == null) {
                    instance = new DrumPotConnectionManager(context);
                }
            }
        }
        return instance;
    }

    public void connectAsync() {
        executor.execute(this::connectInternal);
    }

    public void reconnectAsync() {
        executor.execute(() -> {
            disconnectInternal();
            connectInternal();
        });
    }

    public boolean isReady() {
        return client.isConnected() && nodeMapReady;
    }

    public DrumPotVoiceController.Result control(DrumPotVoiceController.Action action, int gear) {
        if (action == null) {
            return DrumPotVoiceController.Result.fail("未知滚筒动作");
        }
        int rotateGear = Math.max(0, Math.min(3, gear));
        synchronized (this) {
            try {
                return controlLocked(action, rotateGear);
            } catch (Exception e) {
                Log.e(TAG, "control failed action=" + action + ", retry once", e);
                disconnectInternal();
                try {
                    connectInternal();
                    return controlLocked(action, rotateGear);
                } catch (Exception retryEx) {
                    Log.e(TAG, "control retry failed action=" + action, retryEx);
                    return DrumPotVoiceController.Result.fail("滚筒操作失败: " + retryEx.getMessage());
                }
            }
        }
    }

    /** 写出料时间并脉冲启动（与上位机 SetIncorporatingMaterials 一致）。 */
    public DrumPotVoiceController.Result dischargeMaterial(
            String timeBrowseName,
            String startBrowseName,
            int amountMs,
            String label
    ) {
        if (amountMs <= 0) {
            return DrumPotVoiceController.Result.ok("跳过" + label);
        }
        synchronized (this) {
            try {
                return dischargeMaterialLocked(timeBrowseName, startBrowseName, amountMs, label);
            } catch (Exception e) {
                Log.e(TAG, "discharge failed " + label + ", retry once", e);
                disconnectInternal();
                try {
                    connectInternal();
                    return dischargeMaterialLocked(timeBrowseName, startBrowseName, amountMs, label);
                } catch (Exception retryEx) {
                    return DrumPotVoiceController.Result.fail(label + "失败: " + retryEx.getMessage());
                }
            }
        }
    }

    public DrumPotVoiceController.Result heatStart(int gear) {
        int heatGear = Math.max(0, Math.min(3, gear));
        synchronized (this) {
            try {
                return heatStartLocked(heatGear);
            } catch (Exception e) {
                Log.e(TAG, "heatStart failed, retry once", e);
                disconnectInternal();
                try {
                    connectInternal();
                    return heatStartLocked(heatGear);
                } catch (Exception retryEx) {
                    return DrumPotVoiceController.Result.fail("加热启动失败: " + retryEx.getMessage());
                }
            }
        }
    }

    public DrumPotVoiceController.Result heatStop() {
        synchronized (this) {
            try {
                return heatStopLocked();
            } catch (Exception e) {
                Log.e(TAG, "heatStop failed, retry once", e);
                disconnectInternal();
                try {
                    connectInternal();
                    return heatStopLocked();
                } catch (Exception retryEx) {
                    return DrumPotVoiceController.Result.fail("加热停止失败: " + retryEx.getMessage());
                }
            }
        }
    }

    /** 写绝对定位位置 (ns=4;i=276) 并脉冲绝对定位 (ns=4;i=287)。 */
    public DrumPotVoiceController.Result moveToAbsolutePosition(float position) {
        synchronized (this) {
            try {
                return moveToAbsolutePositionLocked(position);
            } catch (Exception e) {
                Log.e(TAG, "abs move failed position=" + position + ", retry once", e);
                disconnectInternal();
                try {
                    connectInternal();
                    return moveToAbsolutePositionLocked(position);
                } catch (Exception retryEx) {
                    return DrumPotVoiceController.Result.fail(
                            "滚筒绝对定位失败: " + retryEx.getMessage());
                }
            }
        }
    }

    /** 仅写绝对定位位置 Float（ns=4;i=276）。 */
    public DrumPotVoiceController.Result writeAbsolutePosition(float position) {
        synchronized (this) {
            try {
                return writeAbsolutePositionLocked(position);
            } catch (Exception e) {
                Log.e(TAG, "abs write failed position=" + position + ", retry once", e);
                disconnectInternal();
                try {
                    connectInternal();
                    return writeAbsolutePositionLocked(position);
                } catch (Exception retryEx) {
                    return DrumPotVoiceController.Result.fail(
                            "写绝对定位位置失败: " + retryEx.getMessage());
                }
            }
        }
    }

    /** 仅脉冲绝对定位 Boolean（ns=4;i=287）。 */
    public DrumPotVoiceController.Result pulseAbsoluteMove() {
        synchronized (this) {
            try {
                return pulseAbsoluteMoveLocked();
            } catch (Exception e) {
                Log.e(TAG, "abs pulse failed, retry once", e);
                disconnectInternal();
                try {
                    connectInternal();
                    return pulseAbsoluteMoveLocked();
                } catch (Exception retryEx) {
                    return DrumPotVoiceController.Result.fail(
                            "触发绝对定位失败: " + retryEx.getMessage());
                }
            }
        }
    }

    private DrumPotVoiceController.Result dischargeMaterialLocked(
            String timeBrowseName,
            String startBrowseName,
            int amountMs,
            String label
    ) throws Exception {
        ensureReadyLocked();
        int namespace = DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX;
        client.write(resolve(timeBrowseName, namespace), String.valueOf(amountMs));
        client.pulseTrue(resolve(startBrowseName, namespace));
        Thread.sleep(amountMs);
        return DrumPotVoiceController.Result.ok(label + " " + amountMs + "ms");
    }

    private DrumPotVoiceController.Result heatStartLocked(int gear) throws Exception {
        ensureReadyLocked();
        int namespace = DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX;
        client.write(resolve("加热档位", namespace), String.valueOf(gear));
        client.pulseTrue(resolve("加热启动", namespace));
        return DrumPotVoiceController.Result.ok("加热已启动，档位 " + gear);
    }

    private DrumPotVoiceController.Result heatStopLocked() throws Exception {
        ensureReadyLocked();
        int namespace = DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX;
        client.pulseTrue(resolve("加热停止", namespace));
        return DrumPotVoiceController.Result.ok("加热已停止");
    }

    private DrumPotVoiceController.Result moveToAbsolutePositionLocked(float position) throws Exception {
        DrumPotVoiceController.Result writeResult = writeAbsolutePositionLocked(position);
        if (!writeResult.ok) {
            return writeResult;
        }
        Thread.sleep(1000L);
        DrumPotVoiceController.Result pulseResult = pulseAbsoluteMoveLocked();
        if (!pulseResult.ok) {
            return pulseResult;
        }
        WaitResult waitResult = waitForAbsoluteMoveDone(position);
        if (waitResult.reached) {
            return DrumPotVoiceController.Result.ok(
                    "已绝对定位到 " + position + "（当前 " + waitResult.lastCurrent + "）");
        }
        return DrumPotVoiceController.Result.fail(
                "绝对定位超时：目标 " + position
                        + "，当前 " + waitResult.lastCurrent
                        + "，判定=" + waitResult.method);
    }

    private DrumPotVoiceController.Result writeAbsolutePositionLocked(float position) throws Exception {
        ensureReadyLocked();
        String nodeId = absPositionValueNode();
        client.write(nodeId, formatPositionValue(position));
        Log.i(TAG, "abs position write " + position + " -> " + nodeId);
        return DrumPotVoiceController.Result.ok("已写绝对定位位置 " + position);
    }

    private static String formatPositionValue(float position) {
        if (Math.rint(position) == position) {
            return String.valueOf((long) position);
        }
        return String.valueOf(position);
    }

    private DrumPotVoiceController.Result pulseAbsoluteMoveLocked() throws Exception {
        ensureReadyLocked();
        String nodeId = absPositionTriggerNode();
        client.pulseTrue(nodeId);
        Log.i(TAG, "abs position pulse -> " + nodeId);
        return DrumPotVoiceController.Result.ok("已触发绝对定位");
    }

    private static final class WaitResult {
        final boolean reached;
        final float lastCurrent;
        final String method;

        WaitResult(boolean reached, float lastCurrent, String method) {
            this.reached = reached;
            this.lastCurrent = lastCurrent;
            this.method = method != null ? method : "";
        }
    }

    /** 读取滚筒轴当前位置（BrowseName「当前位置」）。 */
    public DrumPotVoiceController.Result readCurrentPosition() {
        synchronized (this) {
            try {
                ensureReadyLocked();
                String nodeId = currentPositionNode();
                if (nodeId == null) {
                    return DrumPotVoiceController.Result.fail("PLC 中未找到「当前位置」节点");
                }
                float value = client.readFloat(nodeId);
                return DrumPotVoiceController.Result.ok("当前位置 " + value);
            } catch (Exception e) {
                Log.e(TAG, "readCurrentPosition failed", e);
                return DrumPotVoiceController.Result.fail("读当前位置失败: " + e.getMessage());
            }
        }
    }

    /**
     * 等待绝对定位完成。优先级（与 C# SetDischargeCharge 一致思路）：
     * 1. 「绝对定位完成」/「绝度定位完成」Boolean 为 true
     * 2. 「当前位置」Float 接近目标值
     * 3. 兜底：「绝对定位」触发位回到 false
     */
    private WaitResult waitForAbsoluteMoveDone(float targetPosition) throws Exception {
        String currentNode = currentPositionNode();
        String doneNode = absPositionDoneNode();
        String triggerNode = absPositionTriggerNode();
        long deadline = System.currentTimeMillis() + DrumPotOpcConfig.ABS_MOVE_TIMEOUT_MS;
        float lastCurrent = Float.NaN;
        Thread.sleep(DrumPotOpcConfig.ABS_MOVE_POLL_MS);
        while (System.currentTimeMillis() < deadline) {
            if (doneNode != null) {
                try {
                    if (client.readBoolean(doneNode)) {
                        lastCurrent = tryReadCurrentPosition(currentNode);
                        Log.i(TAG, "abs move done flag=true target=" + targetPosition
                                + " current=" + lastCurrent);
                        return new WaitResult(true, lastCurrent, "定位完成标志");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "abs move poll done flag failed", e);
                }
            }
            if (currentNode != null) {
                try {
                    lastCurrent = client.readFloat(currentNode);
                    Log.d(TAG, "abs move poll current=" + lastCurrent + " target=" + targetPosition);
                    if (Math.abs(lastCurrent - targetPosition) <= DrumPotOpcConfig.POSITION_TOLERANCE) {
                        Log.i(TAG, "abs move reached by current position");
                        return new WaitResult(true, lastCurrent, "当前位置");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "abs move poll current position failed", e);
                }
            }
            if (currentNode == null && doneNode == null) {
                try {
                    if (!client.readBoolean(triggerNode)) {
                        Log.i(TAG, "abs move trigger cleared (fallback)");
                        return new WaitResult(true, lastCurrent, "触发位复位");
                    }
                } catch (Exception e) {
                    Log.w(TAG, "abs move poll trigger failed", e);
                }
            }
            Thread.sleep(DrumPotOpcConfig.ABS_MOVE_POLL_MS);
        }
        if (Float.isNaN(lastCurrent)) {
            lastCurrent = tryReadCurrentPosition(currentNode);
        }
        Log.w(TAG, "abs move wait timeout target=" + targetPosition + " current=" + lastCurrent);
        return new WaitResult(false, lastCurrent, "超时");
    }

    private float tryReadCurrentPosition(String currentNode) {
        if (currentNode == null) {
            return Float.NaN;
        }
        try {
            return client.readFloat(currentNode);
        } catch (Exception e) {
            Log.w(TAG, "tryReadCurrentPosition failed", e);
            return Float.NaN;
        }
    }

    private String currentPositionNode() {
        return client.tryResolveBrowseName(DrumPotOpcConfig.BROWSE_CURRENT_POSITION);
    }

    private String absPositionDoneNode() {
        String node = client.tryResolveBrowseName(DrumPotOpcConfig.BROWSE_ABS_POSITION_DONE);
        if (node != null) {
            return node;
        }
        return client.tryResolveBrowseName(DrumPotOpcConfig.BROWSE_ABS_POSITION_DONE_ALT);
    }

    private String absPositionValueNode() {
        return client.resolveNodeIdOrFallback(
                DrumPotOpcConfig.BROWSE_ABS_POSITION_VALUE,
                DrumPotOpcConfig.NODE_ABS_POSITION_VALUE);
    }

    private String absPositionTriggerNode() {
        return client.resolveNodeIdOrFallback(
                DrumPotOpcConfig.BROWSE_ABS_POSITION_TRIGGER,
                DrumPotOpcConfig.NODE_ABS_POSITION_TRIGGER);
    }

    private DrumPotVoiceController.Result controlLocked(DrumPotVoiceController.Action action, int rotateGear)
            throws Exception {
        ensureReadyLocked();
        int namespace = DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX;
        long t0 = System.currentTimeMillis();
        switch (action) {
            case START:
                client.pulseTrue(resolve("启动", namespace));
                return ok("滚筒已启动", action, t0);
            case STOP:
                client.pulseTrue(resolve("停止", namespace));
                return ok("滚筒已停止", action, t0);
            case RESET:
                client.pulseTrue(resolve("复位", namespace));
                return ok("滚筒已复位", action, t0);
            case ROTATE_START:
                client.write(resolve("转速控制档位", namespace),
                        String.valueOf(rotateGear));
                client.pulseTrue(resolve("锅旋转启动", namespace));
                return ok("滚筒已开始旋转，档位 " + rotateGear, action, t0);
            case ROTATE_STOP:
                client.pulseTrue(resolve("锅旋转停止", namespace));
                return ok("滚筒旋转已停止", action, t0);
            default:
                return DrumPotVoiceController.Result.fail("未知滚筒动作");
        }
    }

    private DrumPotVoiceController.Result ok(String message, DrumPotVoiceController.Action action, long t0) {
        Log.i(TAG, "control OK action=" + action + " elapsedMs=" + (System.currentTimeMillis() - t0));
        return DrumPotVoiceController.Result.ok(message);
    }

    private void ensureReadyLocked() throws Exception {
        if (client.isConnected() && nodeMapReady) {
            return;
        }
        connectInternal();
        if (!client.isConnected() || !nodeMapReady) {
            throw new IllegalStateException("滚筒 OPC 未就绪");
        }
    }

    private synchronized void connectInternal() {
        if (client.isConnected() && nodeMapReady) {
            return;
        }
        String endpoint = DeviceSettingsStore.getDrumPotEndpointUrl(appContext);
        String host = DeviceSettingsStore.getDrumPotHost(appContext);
        int port = DeviceSettingsStore.getDrumPotPort(appContext);
        try {
            if (client.isConnected()) {
                client.disconnect();
            }
            nodeMapReady = false;
            Log.i(TAG, "connecting " + endpoint);
            NetworkDiagnostics.logBeforeDeviceTcp(TAG, host, port);
            client.connect(endpoint, OPC_TIMEOUT_MS);
            client.loadInterfaceNodeMap(DrumPotOpcConfig.SERVER_INTERFACE_NODE_ID);
            nodeMapReady = client.hasNodeMap();
            if (nodeMapReady) {
                Log.i(TAG, "connected OK nodeMap loaded " + endpoint);
            } else {
                Log.e(TAG, "connected but nodeMap empty " + endpoint);
            }
        } catch (Exception e) {
            nodeMapReady = false;
            Log.e(TAG, "connect failed " + endpoint, e);
        }
    }

    private void disconnectInternal() {
        nodeMapReady = false;
        client.disconnect();
    }

    private String resolve(String browseName, int namespace) {
        return client.resolveNodeId(browseName, namespace);
    }
}
