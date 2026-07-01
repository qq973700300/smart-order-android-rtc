package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.device.modbus.DrumPotConfig;
import com.example.test5.device.modbus.DrumPotRegisters;
import com.example.test5.device.modbus.ModbusTcpClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 滚筒锅 Modbus TCP 调试页（默认 192.168.2.107:502）。
 */
public class DrumPotModbusDebugActivity extends AppCompatActivity {

    private TextInputEditText hostInput;
    private TextInputEditText portInput;
    private TextInputEditText unitIdInput;
    private TextInputEditText registerInput;
    private TextInputEditText valueInput;
    private TextInputEditText locationInput;
    private TextView responseText;
    private MaterialButton connectButton;
    private MaterialButton disconnectButton;

    private final ModbusTcpClient client = new ModbusTcpClient();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final StringBuilder logBuilder = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drum_pot_modbus_debug);

        MaterialToolbar toolbar = findViewById(R.id.drum_pot_toolbar);
        hostInput = findViewById(R.id.drum_pot_host_input);
        portInput = findViewById(R.id.drum_pot_port_input);
        unitIdInput = findViewById(R.id.drum_pot_unit_id_input);
        registerInput = findViewById(R.id.drum_pot_register_input);
        valueInput = findViewById(R.id.drum_pot_value_input);
        locationInput = findViewById(R.id.drum_pot_location_input);
        responseText = findViewById(R.id.drum_pot_response_text);
        connectButton = findViewById(R.id.drum_pot_btn_connect);
        disconnectButton = findViewById(R.id.drum_pot_btn_disconnect);

        toolbar.setNavigationOnClickListener(v -> finish());
        hostInput.setText(DrumPotConfig.DEFAULT_HOST);
        portInput.setText(String.valueOf(DrumPotConfig.DEFAULT_PORT));
        unitIdInput.setText(String.valueOf(DrumPotConfig.DEFAULT_UNIT_ID));
        registerInput.setText(String.valueOf(DrumPotRegisters.AXIS_CURRENT_POSITION));
        valueInput.setText("1");
        locationInput.setText(String.valueOf(DrumPotConfig.LOCATION_COOK));

        connectButton.setOnClickListener(v -> connect());
        disconnectButton.setOnClickListener(v -> disconnect());
        findViewById(R.id.drum_pot_btn_read).setOnClickListener(v -> readRegister());
        findViewById(R.id.drum_pot_btn_write).setOnClickListener(v -> writeRegister());
        findViewById(R.id.drum_pot_btn_read_position).setOnClickListener(v ->
                readFixedRegister(DrumPotRegisters.AXIS_CURRENT_POSITION));
        findViewById(R.id.drum_pot_btn_move).setOnClickListener(v -> moveToLocation());
        findViewById(R.id.drum_pot_btn_rotate_start).setOnClickListener(v -> startRotate());
        findViewById(R.id.drum_pot_btn_rotate_stop).setOnClickListener(v -> stopRotate());
        findViewById(R.id.drum_pot_btn_heat_on).setOnClickListener(v -> writeFixed(DrumPotRegisters.HEAT_POWER, 100));
        findViewById(R.id.drum_pot_btn_heat_off).setOnClickListener(v -> writeFixed(DrumPotRegisters.HEAT_POWER, 0));
        findViewById(R.id.drum_pot_btn_axis_home).setOnClickListener(v -> pulseRegister(DrumPotRegisters.AXIS_HOME));
        findViewById(R.id.drum_pot_btn_axis_reset).setOnClickListener(v -> pulseRegister(DrumPotRegisters.AXIS_RESET));

        findViewById(R.id.drum_pot_chip_feed).setOnClickListener(v ->
                locationInput.setText(String.valueOf(DrumPotConfig.LOCATION_FEED)));
        findViewById(R.id.drum_pot_chip_cook).setOnClickListener(v ->
                locationInput.setText(String.valueOf(DrumPotConfig.LOCATION_COOK)));
        findViewById(R.id.drum_pot_chip_serve).setOnClickListener(v ->
                locationInput.setText(String.valueOf(DrumPotConfig.LOCATION_SERVE)));
        findViewById(R.id.drum_pot_chip_wash).setOnClickListener(v ->
                locationInput.setText(String.valueOf(DrumPotConfig.LOCATION_WASH)));
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
        int unitId = parseUnitId();
        if (unitId < 0) {
            return;
        }
        setBusy(true);
        appendLog(getString(R.string.device_connecting));
        executor.execute(() -> {
            try {
                client.connect(host, port, unitId, 5000);
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

    private void readRegister() {
        int address = parseRegisterAddress();
        if (address < 0) {
            return;
        }
        runModbus(() -> {
            ModbusTcpClient.ModbusResult result = client.readHoldingRegister(address);
            appendLog(result.formatForDisplay());
        });
    }

    private void readFixedRegister(int address) {
        registerInput.setText(String.valueOf(address));
        readRegister();
    }

    private void writeRegister() {
        int address = parseRegisterAddress();
        if (address < 0) {
            return;
        }
        int value = parseValue();
        if (value == Integer.MIN_VALUE) {
            return;
        }
        writeFixed(address, value);
    }

    private void writeFixed(int address, int value) {
        runModbus(() -> {
            ModbusTcpClient.ModbusResult result = client.writeSingleRegister(
                    address, ModbusTcpClient.intToRegisterValue(value));
            appendLog(result.formatForDisplay());
        });
    }

    private void moveToLocation() {
        int location = parseLocation();
        if (location == Integer.MIN_VALUE) {
            return;
        }
        runModbus(() -> {
            appendLog("move location=" + location);
            ModbusTcpClient.ModbusResult pos = client.writeSingleRegister(
                    DrumPotRegisters.AXIS_ABSOLUTE_POSITION,
                    ModbusTcpClient.intToRegisterValue(location));
            appendLog(pos.formatForDisplay());
            ModbusTcpClient.ModbusResult move = client.writeSingleRegister(
                    DrumPotRegisters.AXIS_ABSOLUTE_MOVE, 1);
            appendLog(move.formatForDisplay());
            ModbusTcpClient.ModbusResult done = client.readHoldingRegister(DrumPotRegisters.AXIS_POSITION_DONE);
            appendLog("定位完成寄存器=" + done.value);
        });
    }

    private void startRotate() {
        runModbus(() -> {
            appendLog(client.writeSingleRegister(DrumPotRegisters.MOTOR_ENABLE, 1).formatForDisplay());
            appendLog(client.writeSingleRegister(DrumPotRegisters.MOTOR_DIR, 1).formatForDisplay());
            appendLog(client.writeSingleRegister(DrumPotRegisters.ROTATE_SPEED, 15).formatForDisplay());
        });
    }

    private void stopRotate() {
        runModbus(() -> {
            appendLog(client.writeSingleRegister(DrumPotRegisters.MOTOR_ENABLE, 0).formatForDisplay());
            appendLog(client.writeSingleRegister(DrumPotRegisters.MOTOR_DIR, 0).formatForDisplay());
            appendLog(client.writeSingleRegister(DrumPotRegisters.ROTATE_SPEED, 0).formatForDisplay());
        });
    }

    private void pulseRegister(int address) {
        runModbus(() -> {
            appendLog(client.writeSingleRegister(address, 1).formatForDisplay());
            Thread.sleep(200);
            appendLog(client.writeSingleRegister(address, 0).formatForDisplay());
        });
    }

    private void runModbus(ModbusAction action) {
        if (!client.isConnected()) {
            toast(R.string.device_error_not_connected);
            return;
        }
        setBusy(true);
        executor.execute(() -> {
            try {
                action.run();
            } catch (Exception e) {
                appendLog(getString(R.string.device_error_network, e.getMessage()));
            } finally {
                runOnUiThread(() -> setBusy(false));
            }
        });
    }

    private interface ModbusAction {
        void run() throws Exception;
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

    private int parseUnitId() {
        try {
            return Integer.parseInt(textOf(unitIdInput));
        } catch (NumberFormatException e) {
            toast(R.string.drum_pot_error_unit_id);
            return -1;
        }
    }

    private int parseRegisterAddress() {
        try {
            return Integer.parseInt(textOf(registerInput));
        } catch (NumberFormatException e) {
            toast(R.string.drum_pot_error_register);
            return -1;
        }
    }

    private int parseValue() {
        try {
            return Integer.parseInt(textOf(valueInput));
        } catch (NumberFormatException e) {
            toast(R.string.drum_pot_error_value);
            return Integer.MIN_VALUE;
        }
    }

    private int parseLocation() {
        try {
            return Integer.parseInt(textOf(locationInput));
        } catch (NumberFormatException e) {
            toast(R.string.drum_pot_error_location);
            return Integer.MIN_VALUE;
        }
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
