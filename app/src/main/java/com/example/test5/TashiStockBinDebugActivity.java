package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.device.TcpTextClient;
import com.example.test5.device.tashi.TashiConfig;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 塔石料仓 ASCII TCP 调试页（默认 192.168.2.80:10123）。
 */
public class TashiStockBinDebugActivity extends AppCompatActivity {

    private TextInputEditText hostInput;
    private TextInputEditText portInput;
    private TextInputEditText shipCodeInput;
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
        setContentView(R.layout.activity_tashi_stock_bin_debug);

        MaterialToolbar toolbar = findViewById(R.id.tashi_toolbar);
        hostInput = findViewById(R.id.tashi_host_input);
        portInput = findViewById(R.id.tashi_port_input);
        shipCodeInput = findViewById(R.id.tashi_ship_code_input);
        customCommandInput = findViewById(R.id.tashi_custom_command_input);
        responseText = findViewById(R.id.tashi_response_text);
        connectButton = findViewById(R.id.tashi_btn_connect);
        disconnectButton = findViewById(R.id.tashi_btn_disconnect);

        toolbar.setNavigationOnClickListener(v -> finish());
        hostInput.setText(TashiConfig.DEFAULT_HOST);
        portInput.setText(String.valueOf(TashiConfig.DEFAULT_PORT));
        shipCodeInput.setText("110");

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
        findViewById(R.id.tashi_btn_clear).setOnClickListener(v -> sendPreset(TashiConfig.CMD_CLEAR));
        findViewById(R.id.tashi_btn_online).setOnClickListener(v -> sendPreset(TashiConfig.CMD_ONLINE));
        findViewById(R.id.tashi_btn_aott).setOnClickListener(v -> sendPreset(TashiConfig.CMD_AOTT));
        findViewById(R.id.tashi_btn_version).setOnClickListener(v -> sendPreset(TashiConfig.CMD_VERSION));
        findViewById(R.id.tashi_btn_ship).setOnClickListener(v -> sendShip());
        findViewById(R.id.tashi_btn_custom).setOnClickListener(v -> sendCustom());
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

    private void sendPreset(String command) {
        sendCommand(command);
    }

    private void sendShip() {
        String code = textOf(shipCodeInput);
        if (code.length() != 3) {
            toast(R.string.tashi_error_ship_code);
            return;
        }
        try {
            sendCommand(TashiConfig.buildShipCommand(code));
        } catch (IllegalArgumentException e) {
            toast(R.string.tashi_error_ship_code);
        }
    }

    private void sendCustom() {
        String command = textOf(customCommandInput);
        if (TextUtils.isEmpty(command)) {
            toast(R.string.tashi_error_custom_command);
            return;
        }
        sendCommand(command);
    }

    private void sendCommand(String command) {
        if (!client.isConnected()) {
            toast(R.string.device_error_not_connected);
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                TcpTextClient.SendResult result = client.send(command);
                appendLog("TX: " + result.text + " (" + result.bytesSent + " bytes)");
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
