package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.device.opcua.DrumPotConnectionManager;
import com.example.test5.device.settings.DeviceSettingsStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** 滚筒锅 OPC UA 地址设置。 */
public class DrumPotSettingsActivity extends AppCompatActivity {

    private TextInputEditText hostInput;
    private TextInputEditText portInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_drum_pot_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        hostInput = findViewById(R.id.drum_pot_host_input);
        portInput = findViewById(R.id.drum_pot_port_input);
        MaterialButton saveButton = findViewById(R.id.drum_pot_save_button);

        toolbar.setNavigationOnClickListener(v -> finish());
        hostInput.setText(DeviceSettingsStore.getDrumPotHost(this));
        portInput.setText(String.valueOf(DeviceSettingsStore.getDrumPotPort(this)));
        saveButton.setOnClickListener(v -> save());
    }

    private void save() {
        String host = textOf(hostInput);
        if (TextUtils.isEmpty(host)) {
            toast(R.string.device_error_host);
            return;
        }
        int port;
        try {
            port = Integer.parseInt(textOf(portInput));
        } catch (NumberFormatException e) {
            toast(R.string.device_error_port);
            return;
        }
        DeviceSettingsStore.setDrumPotHost(this, host);
        DeviceSettingsStore.setDrumPotPort(this, port);
        DrumPotConnectionManager.getInstance(this).reconnectAsync();
        toast(R.string.device_settings_saved);
        finish();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}
