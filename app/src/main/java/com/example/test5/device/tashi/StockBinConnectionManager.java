package com.example.test5.device.tashi;

import android.content.Context;
import android.util.Log;

import com.example.test5.device.TcpTextClient;
import com.example.test5.device.settings.DeviceSettingsStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 料仓 TCP 长连接：App 启动后保持连接，语音取料时直接发指令。
 * 协议与上位机 MainSubControl.StockBinCommand 一致：先 CLEAR，再 SHIP，等待后 AOTT 轮询状态。
 */
public final class StockBinConnectionManager {

    private static final String TAG = "StockBinVoice";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** 发 SHIP 后先等待设备启动（上位机默认 5 秒）。 */
    private static final int SHIP_SETTLE_MS = 5000;
    private static final int STATUS_POLL_MS = 1000;
    private static final int STATUS_TIMEOUT_MS = 30000;
    private static final Pattern STATUS_PATTERN = Pattern.compile("S0\\d+");

    private static volatile StockBinConnectionManager instance;

    private final Context appContext;
    private final TcpTextClient client = new TcpTextClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "StockBinConn");
        t.setDaemon(true);
        return t;
    });

    private StockBinConnectionManager(Context context) {
        appContext = context.getApplicationContext();
        client.addListener(new TcpTextClient.Listener() {
            @Override
            public void onMessage(String line) {
                Log.i(TAG, "RX: " + line);
            }

            @Override
            public void onDisconnected(String reason) {
                Log.w(TAG, "disconnected: " + reason + ", will reconnect on next pick or connectAsync");
            }
        });
    }

    public static StockBinConnectionManager getInstance(Context context) {
        if (instance == null) {
            synchronized (StockBinConnectionManager.class) {
                if (instance == null) {
                    instance = new StockBinConnectionManager(context);
                }
            }
        }
        return instance;
    }

    /** App 启动时在后台连接料仓。 */
    public void connectAsync() {
        executor.execute(this::connectInternal);
    }

    /** 设置页改 IP 后重连。 */
    public void reconnectAsync() {
        executor.execute(() -> {
            client.disconnect();
            connectInternal();
        });
    }

    public boolean isConnected() {
        return client.isConnected();
    }

    public StockBinVoiceController.Result pick(String slotCode) {
        String code = slotCode == null ? "" : slotCode.trim();
        if (!code.matches("\\d{3}")) {
            return StockBinVoiceController.Result.fail("出货码必须为 3 位数字，例如 110、120、210");
        }
        synchronized (this) {
            try {
                return pickLocked(code);
            } catch (Exception e) {
                Log.e(TAG, "pick failed code=" + code + ", retry connect once", e);
                client.disconnect();
                try {
                    connectInternal();
                    return pickLocked(code);
                } catch (Exception retryEx) {
                    Log.e(TAG, "pick retry failed code=" + code, retryEx);
                    return StockBinVoiceController.Result.fail("料仓操作失败: " + retryEx.getMessage());
                }
            }
        }
    }

    private StockBinVoiceController.Result pickLocked(String code) throws Exception {
        ensureConnectedLocked();
        String successMarker = code.substring(0, 2) + "1";
        String failureMarker = code.substring(0, 2) + "8";
        long t0 = System.currentTimeMillis();

        client.drainReceivedText();
        client.send(TashiConfig.CMD_CLEAR);
        Log.i(TAG, "pick CLEAR before SHIP code=" + code);
        Thread.sleep(200);

        String shipCmd = TashiConfig.buildShipCommand(code);
        client.send(shipCmd);
        Log.i(TAG, "pick SHIP sent " + shipCmd);

        Thread.sleep(SHIP_SETTLE_MS);

        PickStatus status = waitForPickStatus(successMarker, failureMarker, t0);
        long elapsedMs = System.currentTimeMillis() - t0;
        switch (status) {
            case SUCCESS:
                Log.i(TAG, "pick OK code=" + code + " elapsedMs=" + elapsedMs);
                return StockBinVoiceController.Result.ok("已从料仓取出位置 " + code);
            case FAILURE:
                Log.w(TAG, "pick device failure code=" + code + " elapsedMs=" + elapsedMs);
                return StockBinVoiceController.Result.fail("料仓出货失败，位置 " + code);
            case BUSY:
                Log.w(TAG, "pick device busy code=" + code + " elapsedMs=" + elapsedMs);
                return StockBinVoiceController.Result.fail("料仓正在忙，请稍后再试");
            case TIMEOUT:
                Log.w(TAG, "pick timeout code=" + code + " elapsedMs=" + elapsedMs);
                return StockBinVoiceController.Result.fail("料仓出货超时，位置 " + code);
            default:
                return StockBinVoiceController.Result.fail("料仓出货状态未知，位置 " + code);
        }
    }

    private PickStatus waitForPickStatus(String successMarker, String failureMarker, long startedAt)
            throws Exception {
        long deadline = startedAt + STATUS_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            client.send(TashiConfig.CMD_AOTT);
            Thread.sleep(STATUS_POLL_MS);
            PickStatus status = parseReceivedStatus(client.drainReceivedText(), successMarker, failureMarker);
            if (status != PickStatus.PENDING) {
                return status;
            }
        }
        return PickStatus.TIMEOUT;
    }

    private static PickStatus parseReceivedStatus(String data, String successMarker, String failureMarker) {
        if (data == null || data.isEmpty()) {
            return PickStatus.PENDING;
        }
        if (data.contains(TashiConfig.NACK)) {
            return PickStatus.BUSY;
        }
        Matcher matcher = STATUS_PATTERN.matcher(data);
        while (matcher.find()) {
            String statusCode = matcher.group();
            if (statusCode.contains(successMarker)) {
                return PickStatus.SUCCESS;
            }
            if (statusCode.contains(failureMarker)) {
                return PickStatus.FAILURE;
            }
        }
        if (data.contains(successMarker)) {
            return PickStatus.SUCCESS;
        }
        if (data.contains(failureMarker)) {
            return PickStatus.FAILURE;
        }
        return PickStatus.PENDING;
    }

    private void ensureConnectedLocked() throws Exception {
        if (client.isConnected()) {
            return;
        }
        connectInternal();
        if (!client.isConnected()) {
            throw new IllegalStateException("料仓未连接");
        }
    }

    private synchronized void connectInternal() {
        if (client.isConnected()) {
            return;
        }
        String host = DeviceSettingsStore.getStockBinHost(appContext);
        int port = DeviceSettingsStore.getStockBinPort(appContext);
        try {
            Log.i(TAG, "connecting " + host + ":" + port);
            client.connect(host, port, CONNECT_TIMEOUT_MS);
            Log.i(TAG, "connected OK " + host + ":" + port);
        } catch (Exception e) {
            Log.e(TAG, "connect failed " + host + ":" + port, e);
        }
    }

    private enum PickStatus {
        PENDING,
        SUCCESS,
        FAILURE,
        BUSY,
        TIMEOUT
    }
}
