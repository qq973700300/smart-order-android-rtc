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
