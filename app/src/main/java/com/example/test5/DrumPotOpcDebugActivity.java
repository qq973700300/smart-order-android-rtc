package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.device.opcua.DrumPotOpcConfig;
import com.example.test5.device.opcua.DrumPotOpcNodes;
import com.example.test5.device.opcua.OpcUaClientHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 滚筒锅 OPC UA 调试页（局域网直连，文档《十套滚桶锅OPC》BrowseName）。
 */
public class DrumPotOpcDebugActivity extends AppCompatActivity {

    private static final String TAG = OpcUaClientHelper.LOG_TAG;

    private TextInputEditText endpointInput;
    private TextInputEditText namespaceInput;
    private TextInputEditText browseNodeInput;
    private TextInputEditText nodeIdInput;
    private TextInputEditText valueInput;
    private TextView responseText;
    private MaterialButton connectButton;
    private MaterialButton disconnectButton;

    private int selectedPotPosition = DrumPotOpcConfig.POT_POS_COOK;
    private int selectedRotateGear = 1;
    private int selectedHeatGear = 1;

    private final OpcUaClientHelper client = new OpcUaClientHelper();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drum_pot_opc_debug);

        MaterialToolbar toolbar = findViewById(R.id.opc_toolbar);
        endpointInput = findViewById(R.id.opc_endpoint_input);
        namespaceInput = findViewById(R.id.opc_namespace_input);
        browseNodeInput = findViewById(R.id.opc_browse_node_input);
        nodeIdInput = findViewById(R.id.opc_node_id_input);
        valueInput = findViewById(R.id.opc_value_input);
        responseText = findViewById(R.id.opc_response_text);
        connectButton = findViewById(R.id.opc_btn_connect);
        disconnectButton = findViewById(R.id.opc_btn_disconnect);

        toolbar.setNavigationOnClickListener(v -> finish());
        endpointInput.setText(DrumPotOpcConfig.defaultEndpointUrl());
        namespaceInput.setText(String.valueOf(DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX));
        browseNodeInput.setText(DrumPotOpcConfig.SERVER_INTERFACE_NODE_ID);
        nodeIdInput.setText(DrumPotOpcConfig.SERVER_INTERFACE_NODE_ID);
        valueInput.setText("1");

        connectButton.setOnClickListener(v -> connect());
        disconnectButton.setOnClickListener(v -> disconnect());
        findViewById(R.id.opc_btn_browse_objects).setOnClickListener(v -> browse(""));
        findViewById(R.id.opc_btn_browse_interface).setOnClickListener(v -> loadInterfaceNodes());
        findViewById(R.id.opc_btn_read).setOnClickListener(v -> readNode());
        findViewById(R.id.opc_btn_write).setOnClickListener(v -> writeNode());
        findViewById(R.id.opc_btn_pulse).setOnClickListener(v -> pulseNode());

        findViewById(R.id.opc_chip_pos_feed).setOnClickListener(v ->
                selectPotPosition(DrumPotOpcConfig.POT_POS_FEED));
        findViewById(R.id.opc_chip_pos_cook).setOnClickListener(v ->
                selectPotPosition(DrumPotOpcConfig.POT_POS_COOK));
        findViewById(R.id.opc_chip_pos_serve).setOnClickListener(v ->
                selectPotPosition(DrumPotOpcConfig.POT_POS_SERVE));
        findViewById(R.id.opc_chip_pos_wash).setOnClickListener(v ->
                selectPotPosition(DrumPotOpcConfig.POT_POS_WASH));
        findViewById(R.id.opc_btn_move_pot).setOnClickListener(v -> movePotPosition());

        findViewById(R.id.opc_btn_start).setOnClickListener(v -> pulse(DrumPotOpcNodes.START));
        findViewById(R.id.opc_btn_stop).setOnClickListener(v -> pulse(DrumPotOpcNodes.STOP));
        findViewById(R.id.opc_btn_reset).setOnClickListener(v -> pulse(DrumPotOpcNodes.RESET));

        findViewById(R.id.opc_chip_rotate_gear_0).setOnClickListener(v -> selectRotateGear(0));
        findViewById(R.id.opc_chip_rotate_gear_1).setOnClickListener(v -> selectRotateGear(1));
        findViewById(R.id.opc_chip_rotate_gear_2).setOnClickListener(v -> selectRotateGear(2));
        findViewById(R.id.opc_chip_rotate_gear_3).setOnClickListener(v -> selectRotateGear(3));
        findViewById(R.id.opc_btn_set_rotate_gear).setOnClickListener(v -> setRotateGear());
        findViewById(R.id.opc_btn_rotate_start).setOnClickListener(v -> pulse(DrumPotOpcNodes.ROTATE_START));
        findViewById(R.id.opc_btn_rotate_stop).setOnClickListener(v -> pulse(DrumPotOpcNodes.ROTATE_STOP));

        findViewById(R.id.opc_chip_heat_gear_0).setOnClickListener(v -> selectHeatGear(0));
        findViewById(R.id.opc_chip_heat_gear_1).setOnClickListener(v -> selectHeatGear(1));
        findViewById(R.id.opc_chip_heat_gear_2).setOnClickListener(v -> selectHeatGear(2));
        findViewById(R.id.opc_chip_heat_gear_3).setOnClickListener(v -> selectHeatGear(3));
        findViewById(R.id.opc_btn_set_heat_gear).setOnClickListener(v -> setHeatGear());
        findViewById(R.id.opc_btn_heat_start).setOnClickListener(v -> pulse(DrumPotOpcNodes.HEAT_START));
        findViewById(R.id.opc_btn_heat_stop).setOnClickListener(v -> pulse(DrumPotOpcNodes.HEAT_STOP));

        findViewById(R.id.opc_btn_liquid_feed).setOnClickListener(v -> startLiquidFeed());
        findViewById(R.id.opc_btn_solid_feed).setOnClickListener(v -> startSolidFeed());
        findViewById(R.id.opc_btn_read_feed).setOnClickListener(v -> refreshFeedStatus());
        findViewById(R.id.opc_btn_exhaust_on).setOnClickListener(v -> pulse(DrumPotOpcNodes.EXHAUST_ON));
        findViewById(R.id.opc_btn_exhaust_off).setOnClickListener(v -> pulse(DrumPotOpcNodes.EXHAUST_OFF));
        findViewById(R.id.opc_btn_refresh_status).setOnClickListener(v -> refreshStatus());
        findViewById(R.id.opc_btn_clear_log).setOnClickListener(v -> clearLog());

        Log.i(TAG, "[ui] DrumPotOpcDebugActivity opened, endpoint="
                + DrumPotOpcConfig.defaultEndpointUrl());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.disconnect();
        executor.shutdownNow();
    }

    private void connect() {
        String endpoint = textOf(endpointInput);
        if (TextUtils.isEmpty(endpoint)) {
            toast(R.string.drum_pot_opc_error_endpoint);
            return;
        }
        setBusy(true);
        Log.i(TAG, "[ui] connect click endpoint=" + endpoint);
        appendLog(getString(R.string.device_connecting) + " " + endpoint);
        executor.execute(() -> {
            try {
                client.connect(endpoint, 15_000L);
                runOnUiThread(() -> {
                    setConnectedUi(true);
                    appendLog(getString(R.string.device_connected));
                    appendLog(getString(R.string.drum_pot_opc_hint_after_connect));
                    Log.i(TAG, "[ui] connect success");
                    setBusy(false);
                });
            } catch (Exception e) {
                Log.e(TAG, "[ui] connect failed", e);
                runOnUiThread(() -> {
                    appendLog(getString(R.string.device_error_network, e.getMessage()));
                    setConnectedUi(false);
                    setBusy(false);
                });
            }
        });
    }

    private void disconnect() {
        Log.i(TAG, "[ui] disconnect click");
        client.disconnect();
        setConnectedUi(false);
        appendLog(getString(R.string.device_disconnected));
    }

    private void loadInterfaceNodes() {
        if (!ensureConnected()) {
            return;
        }
        Log.i(TAG, "[ui] loadInterfaceNodes click");
        setBusy(true);
        executor.execute(() -> {
            try {
                String result = client.loadInterfaceNodeMap(DrumPotOpcConfig.SERVER_INTERFACE_NODE_ID);
                runOnUiThread(() -> {
                    appendLog(result);
                    appendLog(getString(R.string.drum_pot_opc_node_map_loaded));
                    Log.i(TAG, "[ui] loadInterfaceNodes success");
                    setBusy(false);
                });
            } catch (Exception e) {
                Log.e(TAG, "[ui] loadInterfaceNodes failed", e);
                runOnUiThread(() -> {
                    appendLog(getString(R.string.device_error_network, e.getMessage()));
                    setBusy(false);
                });
            }
        });
    }

    private void browse(String nodeId) {
        if (!ensureConnected()) {
            return;
        }
        String target = TextUtils.isEmpty(nodeId) ? textOf(browseNodeInput) : nodeId;
        Log.i(TAG, "[ui] browse click target=" + (TextUtils.isEmpty(target) ? "ObjectsFolder" : target));
        setBusy(true);
        executor.execute(() -> {
            try {
                String result = client.browse(target);
                runOnUiThread(() -> {
                    appendLog("browse " + (TextUtils.isEmpty(target) ? "ObjectsFolder" : target) + ":\n" + result);
                    setBusy(false);
                });
            } catch (Exception e) {
                Log.e(TAG, "[ui] browse failed", e);
                runOnUiThread(() -> {
                    appendLog(getString(R.string.device_error_network, e.getMessage()));
                    setBusy(false);
                });
            }
        });
    }

    private void readNode() {
        if (!ensureConnected()) {
            return;
        }
        String nodeId = textOf(nodeIdInput);
        if (TextUtils.isEmpty(nodeId)) {
            toast(R.string.drum_pot_opc_error_node_id);
            return;
        }
        runTask(() -> appendLog("read " + nodeId + " -> " + client.read(nodeId)));
    }

    private void writeNode() {
        if (!ensureConnected()) {
            return;
        }
        String nodeId = textOf(nodeIdInput);
        String value = textOf(valueInput);
        if (TextUtils.isEmpty(nodeId)) {
            toast(R.string.drum_pot_opc_error_node_id);
            return;
        }
        runTask(() -> appendLog("write " + nodeId + " = " + value + " -> " + client.write(nodeId, value)));
    }

    private void pulseNode() {
        if (!ensureConnected()) {
            return;
        }
        String nodeId = textOf(nodeIdInput);
        if (TextUtils.isEmpty(nodeId)) {
            toast(R.string.drum_pot_opc_error_node_id);
            return;
        }
        runTask(() -> appendLog(client.pulseTrue(nodeId)));
    }

    private void pulse(String browseName) {
        if (!ensureConnected()) {
            return;
        }
        if (!client.hasNodeMap()) {
            toast(R.string.drum_pot_opc_load_map_first);
            return;
        }
        String nodeId = resolveNodeId(browseName);
        Log.i(TAG, "[ui] pulse click browseName=" + browseName + " nodeId=" + nodeId);
        runTask(() -> appendLog(browseName + " (" + nodeId + "): " + client.pulseTrue(nodeId)));
    }

    private void movePotPosition() {
        if (!ensureConnected()) {
            return;
        }
        if (!client.hasNodeMap()) {
            toast(R.string.drum_pot_opc_load_map_first);
            return;
        }
        int ns = parseNamespace();
        if (ns < 0) {
            return;
        }
        Log.i(TAG, "[ui] movePot click position=" + selectedPotPosition);
        runTask(() -> appendLog(client.movePotPosition(ns, selectedPotPosition)));
    }

    private void setRotateGear() {
        if (!ensureConnected() || !ensureNodeMap()) {
            return;
        }
        Log.i(TAG, "[ui] setRotateGear click gear=" + selectedRotateGear);
        runTask(() -> appendLog(client.write(
                resolveNodeId(DrumPotOpcNodes.ROTATE_SPEED_GEAR),
                String.valueOf(selectedRotateGear))));
    }

    private void setHeatGear() {
        if (!ensureConnected() || !ensureNodeMap()) {
            return;
        }
        Log.i(TAG, "[ui] setHeatGear click gear=" + selectedHeatGear);
        runTask(() -> appendLog(client.write(
                resolveNodeId(DrumPotOpcNodes.HEAT_GEAR),
                String.valueOf(selectedHeatGear))));
    }

    private void refreshStatus() {
        if (!ensureConnected()) {
            return;
        }
        if (!client.hasNodeMap()) {
            toast(R.string.drum_pot_opc_load_map_first);
            return;
        }
        Log.i(TAG, "[ui] refreshStatus click");
        runTask(() -> {
            appendLog("--- 运行状态 ---");
            readStatus(DrumPotOpcNodes.AUTO_RUNNING);
            readStatus(DrumPotOpcNodes.POT_POSITION);
            readStatus(DrumPotOpcNodes.POT_POSITION_RUNNING);
            readStatus(DrumPotOpcNodes.WASHING);
            readStatus(DrumPotOpcNodes.HEAT_GEAR);
            readStatus(DrumPotOpcNodes.ROTATE_SPEED_GEAR);
            readStatus(DrumPotOpcNodes.ROTATE_START);
            readStatus(DrumPotOpcNodes.ROTATE_STOP);
            readStatus(DrumPotOpcNodes.HEAT_START);
            readStatus(DrumPotOpcNodes.HEAT_STOP);
            readStatus(DrumPotOpcNodes.AXIS_CURRENT_POSITION);
            readStatus(DrumPotOpcNodes.WASH_TIME);
            readStatus(DrumPotOpcNodes.BLOW_TIME);
            appendLog("--- 状态结束 ---");
        });
    }

    private void readStatus(String browseName) throws Exception {
        String nodeId = resolveNodeId(browseName);
        appendLog(browseName + ": " + client.read(nodeId));
    }

    private void selectPotPosition(int position) {
        selectedPotPosition = position;
        valueInput.setText(String.valueOf(position));
        Log.i(TAG, "[ui] selectPotPosition=" + position);
        appendLog(getString(R.string.drum_pot_opc_selected_pos, position));
    }

    private void selectRotateGear(int gear) {
        selectedRotateGear = gear;
        valueInput.setText(String.valueOf(gear));
        Log.i(TAG, "[ui] selectRotateGear=" + gear);
        appendLog(getString(R.string.drum_pot_opc_selected_rotate_gear, gear));
    }

    private void selectHeatGear(int gear) {
        selectedHeatGear = gear;
        valueInput.setText(String.valueOf(gear));
        Log.i(TAG, "[ui] selectHeatGear=" + gear);
        appendLog(getString(R.string.drum_pot_opc_selected_heat_gear, gear));
    }

    private void runTask(OpcTask task) {
        setBusy(true);
        executor.execute(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Log.e(TAG, "[ui] task failed", e);
                runOnUiThread(() -> appendLog(getString(R.string.device_error_network, e.getMessage())));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    /** 液体1：选择1号通道，定时500ms，再脉冲启动（若存在重量节点则写10g小量）。 */
    private void startLiquidFeed() {
        if (!ensureConnected() || !ensureNodeMap()) {
            return;
        }
        Log.i(TAG, "[ui] startLiquidFeed click");
        runTask(() -> {
            appendLog("--- 液体投料(小量) ---");
            if (client.hasBrowseName(DrumPotOpcNodes.LIQUID_WEIGHT)) {
                appendLog(client.write(
                        resolveNodeId(DrumPotOpcNodes.LIQUID_WEIGHT),
                        String.valueOf(DrumPotOpcConfig.FEED_LIQUID_WEIGHT)));
            }
            appendLog(client.write(
                    resolveNodeId(DrumPotOpcNodes.LIQUID_SELECT),
                    String.valueOf(DrumPotOpcConfig.FEED_LIQUID_CHANNEL)));
            if (client.hasBrowseName(DrumPotOpcNodes.LIQUID1_TIMER)) {
                appendLog(client.write(
                        resolveNodeId(DrumPotOpcNodes.LIQUID1_TIMER),
                        String.valueOf(DrumPotOpcConfig.FEED_LIQUID1_TIME)));
            }
            appendLog(client.pulseTrue(resolveNodeId(DrumPotOpcNodes.LIQUID_START)));
            appendLog("--- 液体投料指令已发 ---");
        });
    }

    /** 固体1：选择1号通道，时间1，再脉冲启动。 */
    private void startSolidFeed() {
        if (!ensureConnected() || !ensureNodeMap()) {
            return;
        }
        Log.i(TAG, "[ui] startSolidFeed click");
        runTask(() -> {
            appendLog("--- 固体投料(小量) ---");
            appendLog(client.write(
                    resolveNodeId(DrumPotOpcNodes.SOLID_SELECT),
                    String.valueOf(DrumPotOpcConfig.FEED_SOLID_CHANNEL)));
            if (client.hasBrowseName(DrumPotOpcNodes.SOLID1_TIMER)) {
                appendLog(client.write(
                        resolveNodeId(DrumPotOpcNodes.SOLID1_TIMER),
                        String.valueOf(DrumPotOpcConfig.FEED_SOLID_TIME)));
            }
            appendLog(client.write(
                    resolveNodeId(DrumPotOpcNodes.SOLID_TIME),
                    String.valueOf(DrumPotOpcConfig.FEED_SOLID_TIME)));
            appendLog(client.pulseTrue(resolveNodeId(DrumPotOpcNodes.SOLID_START)));
            appendLog("--- 固体投料指令已发 ---");
        });
    }

    private void refreshFeedStatus() {
        if (!ensureConnected() || !ensureNodeMap()) {
            return;
        }
        Log.i(TAG, "[ui] refreshFeedStatus click");
        runTask(() -> {
            appendLog("--- 投料参数 ---");
            readStatusIfMapped(DrumPotOpcNodes.LIQUID_SELECT);
            readStatusIfMapped(DrumPotOpcNodes.LIQUID_WEIGHT);
            readStatusIfMapped(DrumPotOpcNodes.LIQUID1_TIMER);
            readStatusIfMapped(DrumPotOpcNodes.LIQUID_START);
            readStatusIfMapped(DrumPotOpcNodes.SOLID_SELECT);
            readStatusIfMapped(DrumPotOpcNodes.SOLID_TIME);
            readStatusIfMapped(DrumPotOpcNodes.SOLID1_TIMER);
            readStatusIfMapped(DrumPotOpcNodes.SOLID_START);
            appendLog("--- 投料参数结束 ---");
        });
    }

    private void readStatusIfMapped(String browseName) throws Exception {
        if (client.hasBrowseName(browseName)) {
            readStatus(browseName);
        }
    }

    private boolean ensureNodeMap() {
        if (client.hasNodeMap()) {
            return true;
        }
        toast(R.string.drum_pot_opc_load_map_first);
        return false;
    }

    private interface OpcTask {
        void run() throws Exception;
    }

    private String resolveNodeId(String browseName) {
        int ns = parseNamespace();
        if (ns < 0) {
            ns = DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX;
        }
        return client.resolveNodeId(browseName, ns);
    }

    private String nodeId(String browseName) {
        return resolveNodeId(browseName);
    }

    private int parseNamespace() {
        String raw = textOf(namespaceInput);
        if (TextUtils.isEmpty(raw)) {
            return DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX;
        }
        try {
            int ns = Integer.parseInt(raw.trim());
            if (ns < 0 || ns > 65535) {
                toast(R.string.drum_pot_opc_error_namespace);
                return -1;
            }
            return ns;
        } catch (NumberFormatException e) {
            toast(R.string.drum_pot_opc_error_namespace);
            return -1;
        }
    }

    private boolean ensureConnected() {
        if (client.isConnected()) {
            return true;
        }
        toast(R.string.device_error_not_connected);
        return false;
    }

    private void setConnectedUi(boolean connected) {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
    }

    private void setBusy(boolean busy) {
        connectButton.setEnabled(!busy && !client.isConnected());
        disconnectButton.setEnabled(!busy && client.isConnected());
    }

    private void appendLog(String line) {
        Log.i(TAG, "[screen] " + line);
        if (logBuilder.length() > 0) {
            logBuilder.append('\n');
        }
        logBuilder.append(line);
        responseText.setText(logBuilder.toString());
    }

    private void clearLog() {
        logBuilder.setLength(0);
        responseText.setText(R.string.device_response_empty);
        Log.i(TAG, "[ui] screen log cleared");
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
