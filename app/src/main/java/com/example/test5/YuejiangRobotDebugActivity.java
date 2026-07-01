package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.device.TcpTextClient;
import com.example.test5.device.yuejiang.YuejiangConfig;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 越疆（Dobot）机械臂 ASCII TCP 调试页（默认 192.168.2.52:29999）。
 */
public class YuejiangRobotDebugActivity extends AppCompatActivity {

    private TextInputEditText hostInput;
    private TextInputEditText portInput;
    private TextInputEditText scriptInput;
    private TextInputEditText customCommandInput;
    private TextView responseText;
    private MaterialButton connectButton;
    private MaterialButton disconnectButton;

    private final TcpTextClient client = new TcpTextClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_yuejiang_robot_debug);

        MaterialToolbar toolbar = findViewById(R.id.yuejiang_toolbar);
        hostInput = findViewById(R.id.yuejiang_host_input);
        portInput = findViewById(R.id.yuejiang_port_input);
        scriptInput = findViewById(R.id.yuejiang_script_input);
        customCommandInput = findViewById(R.id.yuejiang_custom_command_input);
        responseText = findViewById(R.id.yuejiang_response_text);
        connectButton = findViewById(R.id.yuejiang_btn_connect);
        disconnectButton = findViewById(R.id.yuejiang_btn_disconnect);

        toolbar.setNavigationOnClickListener(v -> finish());
        hostInput.setText(YuejiangConfig.DEFAULT_HOST);
        portInput.setText(String.valueOf(YuejiangConfig.DEFAULT_PORT));
        scriptInput.setText(YuejiangConfig.SCRIPT_YUANDIAN);

        client.addListener(new TcpTextClient.Listener() {
            @Override
            public void onMessage(String line) {
                appendLog("RX: " + line);
            }

            @Override
            public void onDisconnected(String reason) {
                appendLog("断开: " + reason);
                runOnUiThread(() -> setConnectedUi(false));
            }
        });

        connectButton.setOnClickListener(v -> connect());
        disconnectButton.setOnClickListener(v -> disconnect());

        findViewById(R.id.yuejiang_btn_clear_error).setOnClickListener(v ->
                sendCommand(YuejiangConfig.clearError()));
        findViewById(R.id.yuejiang_btn_emergency_stop).setOnClickListener(v ->
                sendCommand(YuejiangConfig.emergencyStop()));
        findViewById(R.id.yuejiang_btn_reset).setOnClickListener(v ->
                sendCommand(YuejiangConfig.resetRobot()));
        findViewById(R.id.yuejiang_btn_power_on).setOnClickListener(v ->
                sendCommand(YuejiangConfig.powerOn()));
        findViewById(R.id.yuejiang_btn_robot_mode).setOnClickListener(v ->
                sendCommand(YuejiangConfig.robotMode()));
        findViewById(R.id.yuejiang_btn_enable).setOnClickListener(v ->
                sendCommand(YuejiangConfig.enableRobot()));
        findViewById(R.id.yuejiang_btn_run_script).setOnClickListener(v -> runScript());
        findViewById(R.id.yuejiang_btn_quick_start).setOnClickListener(v -> quickStart());
        findViewById(R.id.yuejiang_btn_custom).setOnClickListener(v -> sendCustom());

        findViewById(R.id.yuejiang_chip_yuandian).setOnClickListener(v ->
                scriptInput.setText(YuejiangConfig.SCRIPT_YUANDIAN));
        findViewById(R.id.yuejiang_chip_zhumianji).setOnClickListener(v ->
                scriptInput.setText(YuejiangConfig.SCRIPT_ZHUMIANJI));
        findViewById(R.id.yuejiang_chip_nawan).setOnClickListener(v ->
                scriptInput.setText(YuejiangConfig.SCRIPT_NAWAN));
        findViewById(R.id.yuejiang_chip_ccjcs).setOnClickListener(v ->
                scriptInput.setText(YuejiangConfig.SCRIPT_CCJCS));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.disconnect();
        executor.shutdownNow();
    }

    private void connect() {
        String host = textOf(hostInput);
        if (TextUtils.isEmpty(host)) {
            toast(R.string.device_error_host);
            return;
        }
        int port = parsePort();
        if (port < 0) {
            return;
        }
        setBusy(true);
        appendLog(getString(R.string.device_connecting));
        executor.execute(() -> {
            try {
                client.connect(host, port, 5000);
                runOnUiThread(() -> {
                    setConnectedUi(true);
                    appendLog(getString(R.string.device_connected));
                    setBusy(false);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog(getString(R.string.device_error_network, e.getMessage()));
                    setConnectedUi(false);
                    setBusy(false);
                });
            }
        });
    }

    private void disconnect() {
        executor.execute(() -> {
            client.disconnect();
            runOnUiThread(() -> {
                appendLog(getString(R.string.device_disconnected));
                setConnectedUi(false);
            });
        });
    }

    private void runScript() {
        String script = textOf(scriptInput);
        if (TextUtils.isEmpty(script)) {
            toast(R.string.yuejiang_error_script);
            return;
        }
        sendCommand(YuejiangConfig.runScript(script));
    }

    private void sendCustom() {
        String command = textOf(customCommandInput);
        if (TextUtils.isEmpty(command)) {
            toast(R.string.yuejiang_error_custom_command);
            return;
        }
        sendCommand(command);
    }

    private void quickStart() {
        if (!client.isConnected()) {
            toast(R.string.device_error_not_connected);
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                sendAndLog(YuejiangConfig.clearError());
                Thread.sleep(1000);
                sendAndLog(YuejiangConfig.powerOn());
                Thread.sleep(5000);
                sendAndLog(YuejiangConfig.enableRobot());
                appendLog(getString(R.string.yuejiang_quick_start_done));
            } catch (Exception e) {
                appendLog(getString(R.string.device_error_network, e.getMessage()));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private void sendAndLog(String command) throws Exception {
        TcpTextClient.SendResult result = client.send(command);
        appendLog("TX: " + result.text);
    }

    private void sendCommand(String command) {
        if (!client.isConnected()) {
            toast(R.string.device_error_not_connected);
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                sendAndLog(command);
            } catch (Exception e) {
                appendLog(getString(R.string.device_error_network, e.getMessage()));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private void appendLog(String line) {
        runOnUiThread(() -> {
            if (logBuilder.length() > 0) {
                logBuilder.append('\n');
            }
            logBuilder.append(line);
            responseText.setText(logBuilder.toString());
        });
    }

    private void setConnectedUi(boolean connected) {
        connectButton.setEnabled(!connected);
        disconnectButton.setEnabled(connected);
    }

    private void setBusy(boolean busy) {
        connectButton.setEnabled(!busy && !client.isConnected());
        disconnectButton.setEnabled(!busy && client.isConnected());
    }

    private int parsePort() {
        try {
            return Integer.parseInt(textOf(portInput));
        } catch (NumberFormatException e) {
            toast(R.string.device_error_port);
            return -1;
        }
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
