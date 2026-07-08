package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.device.tashi.StockBinConnectionManager;
import com.example.test5.device.settings.DeviceSettingsStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

/** 料仓 TCP 地址设置。 */
public class StockBinSettingsActivity extends AppCompatActivity {

    private TextInputEditText hostInput;
    private TextInputEditText portInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_bin_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        hostInput = findViewById(R.id.stock_bin_host_input);
        portInput = findViewById(R.id.stock_bin_port_input);
        MaterialButton saveButton = findViewById(R.id.stock_bin_save_button);

        toolbar.setNavigationOnClickListener(v -> finish());
        hostInput.setText(DeviceSettingsStore.getStockBinHost(this));
        portInput.setText(String.valueOf(DeviceSettingsStore.getStockBinPort(this)));
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
        DeviceSettingsStore.setStockBinHost(this, host);
        DeviceSettingsStore.setStockBinPort(this, port);
        StockBinConnectionManager.getInstance(this).reconnectAsync();
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
