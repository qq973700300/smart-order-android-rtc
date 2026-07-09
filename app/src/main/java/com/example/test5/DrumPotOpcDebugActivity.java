package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;

import com.example.test5.device.opcua.DrumPotOpcConfig;
import com.example.test5.device.opcua.DrumPotOpcKnownNodes;
import com.example.test5.device.opcua.OpcUaClientHelper;
import com.example.test5.device.settings.DeviceSettingsStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 滚筒锅 OPC UA 调试页：浏览 服务器接口_1 后动态加载 PLC 变量网格。 */
public class DrumPotOpcDebugActivity extends AppCompatActivity {

    private static final String TAG = OpcUaClientHelper.LOG_TAG;
    private static final int NODES_PER_ROW = 5;

    private TextInputEditText endpointInput;
    private TextInputEditText namespaceInput;
    private TextInputEditText browseNodeInput;
    private TextInputEditText nodeIdInput;
    private TextInputEditText valueInput;
    private TextView responseText;
    private NestedScrollView scrollView;
    private MaterialButton connectButton;
    private MaterialButton disconnectButton;
    private LinearLayout knownNodesContainer;

    private List<DrumPotOpcKnownNodes.Entry> displayedEntries = Collections.emptyList();

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
        scrollView = findViewById(R.id.opc_debug_scroll);
        connectButton = findViewById(R.id.opc_btn_connect);
        disconnectButton = findViewById(R.id.opc_btn_disconnect);
        knownNodesContainer = findViewById(R.id.opc_known_nodes_container);

        toolbar.setNavigationOnClickListener(v -> finish());
        endpointInput.setText(DeviceSettingsStore.getDrumPotEndpointUrl(this));
        namespaceInput.setText(String.valueOf(DrumPotOpcConfig.DEFAULT_NAMESPACE_INDEX));
        browseNodeInput.setText(DrumPotOpcConfig.SERVER_INTERFACE_NODE_ID);
        nodeIdInput.setText(DrumPotOpcConfig.SERVER_INTERFACE_NODE_ID);
        valueInput.setText("1");

        connectButton.setOnClickListener(v -> connect());
        disconnectButton.setOnClickListener(v -> disconnect());
        findViewById(R.id.opc_btn_browse_objects).setOnClickListener(v -> browseObjectsFolder());
        findViewById(R.id.opc_btn_browse_interface).setOnClickListener(v -> loadInterfaceNodes());
        findViewById(R.id.opc_btn_read).setOnClickListener(v -> readNode());
        findViewById(R.id.opc_btn_write).setOnClickListener(v -> writeNode());
        findViewById(R.id.opc_btn_pulse).setOnClickListener(v -> pulseNode());
        findViewById(R.id.opc_btn_read_all_nodes).setOnClickListener(v -> readAllKnownNodes());
        findViewById(R.id.opc_btn_clear_log).setOnClickListener(v -> clearLog());

        showNodesPlaceholder();
        Log.i(TAG, "[ui] DrumPotOpcDebugActivity opened, endpoint="
                + DrumPotOpcConfig.defaultEndpointUrl());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.disconnect();
        executor.shutdownNow();
    }

    private void showNodesPlaceholder() {
        knownNodesContainer.removeAllViews();
        displayedEntries = Collections.emptyList();
        TextView placeholder = new TextView(this);
        placeholder.setText(R.string.drum_pot_opc_nodes_placeholder);
        int paddingPx = (int) (12 * getResources().getDisplayMetrics().density);
        placeholder.setPadding(paddingPx, paddingPx, paddingPx, paddingPx);
        placeholder.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        knownNodesContainer.addView(placeholder);
    }

    private void bindKnownNodeCards(List<DrumPotOpcKnownNodes.Entry> entries) {
        knownNodesContainer.removeAllViews();
        displayedEntries = entries;
        if (entries.isEmpty()) {
            showNodesPlaceholder();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        float density = getResources().getDisplayMetrics().density;
        int marginPx = (int) (3 * density);
        LinearLayout currentRow = null;
        int col = 0;

        for (DrumPotOpcKnownNodes.Entry entry : entries) {
            if (col == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                rowLp.bottomMargin = marginPx;
                currentRow.setLayoutParams(rowLp);
                knownNodesContainer.addView(currentRow);
            }

            View cell = inflater.inflate(R.layout.item_opc_known_node, currentRow, false);
            LinearLayout.LayoutParams cellLp = new LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f);
            cellLp.setMargins(marginPx, marginPx, marginPx, marginPx);
            cell.setLayoutParams(cellLp);
            bindKnownNodeCell(cell, entry);
            currentRow.addView(cell);

            col++;
            if (col >= NODES_PER_ROW) {
                if (currentRow != null) {
                    applyRowRightSafeInset(currentRow);
                }
                col = 0;
            }
        }

        if (col != 0 && currentRow != null) {
            for (int i = col; i < NODES_PER_ROW; i++) {
                View spacer = new View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 0, 1f));
                spacer.setVisibility(View.INVISIBLE);
                currentRow.addView(spacer);
            }
            applyRowRightSafeInset(currentRow);
        }
    }

    /** 每行最右侧卡片与屏幕右缘留出滑动安全区，减少误触。 */
    private void applyRowRightSafeInset(LinearLayout row) {
        int childCount = row.getChildCount();
        if (childCount <= 0) {
            return;
        }
        View lastVisible = null;
        for (int i = childCount - 1; i >= 0; i--) {
            View child = row.getChildAt(i);
            if (child.getVisibility() == View.VISIBLE) {
                lastVisible = child;
                break;
            }
        }
        if (lastVisible == null) {
            return;
        }
        int safeInsetPx = getResources().getDimensionPixelSize(R.dimen.opc_debug_row_safe_inset_end);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) lastVisible.getLayoutParams();
        lp.setMarginEnd(safeInsetPx);
        lastVisible.setLayoutParams(lp);
    }

    private void bindKnownNodeCell(View row, DrumPotOpcKnownNodes.Entry entry) {
        TextView labelView = row.findViewById(R.id.opc_node_label);
        TextView nodeIdView = row.findViewById(R.id.opc_node_id_text);
        MaterialButton readBtn = row.findViewById(R.id.opc_node_btn_read);
        MaterialButton writeBtn = row.findViewById(R.id.opc_node_btn_write);
        MaterialButton pulseBtn = row.findViewById(R.id.opc_node_btn_pulse);

        labelView.setText(entry.label);
        nodeIdView.setText(shortNodeId(entry.nodeId));

        readBtn.setOnClickListener(v -> readKnownNode(entry));
        if (entry.writable) {
            writeBtn.setVisibility(View.VISIBLE);
            writeBtn.setOnClickListener(v -> writeKnownNode(entry));
        } else {
            writeBtn.setVisibility(View.GONE);
        }
        if (entry.pulseable) {
            pulseBtn.setVisibility(View.VISIBLE);
            pulseBtn.setOnClickListener(v -> pulseKnownNode(entry));
        } else {
            pulseBtn.setVisibility(View.GONE);
        }
    }

    private static String shortNodeId(String nodeId) {
        int idx = nodeId.indexOf(";i=");
        return idx >= 0 ? nodeId.substring(idx + 1) : nodeId;
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
        showNodesPlaceholder();
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
                List<DrumPotOpcKnownNodes.Entry> entries = client.getInterfaceVariableEntries();
                runOnUiThread(() -> {
                    appendLog(result);
                    appendLog(getString(R.string.drum_pot_opc_node_map_loaded, entries.size()));
                    bindKnownNodeCards(entries);
                    Log.i(TAG, "[ui] loadInterfaceNodes success, variables=" + entries.size());
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

    private void browseObjectsFolder() {
        if (!ensureConnected()) {
            return;
        }
        Log.i(TAG, "[ui] browse ObjectsFolder click");
        setBusy(true);
        executor.execute(() -> {
            try {
                String result = client.browse(null);
                runOnUiThread(() -> {
                    appendLog("browse ObjectsFolder (ns=0;i=85):\n" + result);
                    setBusy(false);
                });
            } catch (Exception e) {
                Log.e(TAG, "[ui] browse ObjectsFolder failed", e);
                runOnUiThread(() -> {
                    appendLog(getString(R.string.device_error_network, e.getMessage()));
                    setBusy(false);
                });
            }
        });
    }

    /** 浏览输入框指定 NodeId（留空则 ObjectsFolder）。 */
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

    private void readKnownNode(DrumPotOpcKnownNodes.Entry entry) {
        if (!ensureConnected()) {
            return;
        }
        nodeIdInput.setText(entry.nodeId);
        runTask(() -> appendLog(entry.label + " read: " + client.read(entry.nodeId)));
    }

    private void writeKnownNode(DrumPotOpcKnownNodes.Entry entry) {
        if (!ensureConnected()) {
            return;
        }
        String value = textOf(valueInput);
        if (TextUtils.isEmpty(value)) {
            toast(R.string.drum_pot_opc_error_value);
            return;
        }
        nodeIdInput.setText(entry.nodeId);
        runTask(() -> appendLog(entry.label + " write " + value + ": "
                + client.write(entry.nodeId, value)));
    }

    private void pulseKnownNode(DrumPotOpcKnownNodes.Entry entry) {
        if (!ensureConnected()) {
            return;
        }
        nodeIdInput.setText(entry.nodeId);
        runTask(() -> appendLog(entry.label + " pulse: " + client.pulseTrue(entry.nodeId)));
    }

    private void readAllKnownNodes() {
        if (!ensureConnected()) {
            return;
        }
        if (displayedEntries.isEmpty()) {
            toast(R.string.drum_pot_opc_load_map_first);
            return;
        }
        runTask(() -> {
            appendLog("--- 读取全部 PLC 变量 ---");
            for (DrumPotOpcKnownNodes.Entry entry : displayedEntries) {
                appendLog(entry.label + " (" + entry.nodeId + "): " + client.read(entry.nodeId));
            }
            appendLog("--- 读取结束 ---");
        });
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

    private interface OpcTask {
        void run() throws Exception;
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
        runOnUiThread(() -> {
            if (logBuilder.length() > 0) {
                logBuilder.append('\n');
            }
            logBuilder.append(line);
            responseText.setText(logBuilder.toString());
            if (scrollView != null) {
                scrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
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
