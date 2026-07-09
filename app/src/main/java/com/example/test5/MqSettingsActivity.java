package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.order.mq.MqSettingsStore;
import com.example.test5.order.mq.OrderMqManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;

/** RabbitMQ 订单接收参数（与 SSKJYingJiang AppConfig 一致）。 */
public class MqSettingsActivity extends AppCompatActivity {

    private TextInputEditText hostInput;
    private TextInputEditText portInput;
    private TextInputEditText userInput;
    private TextInputEditText passInput;
    private TextInputEditText queueInput;
    private TextInputEditText equipmentInput;
    private MaterialSwitch autoProduceSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mq_settings);

        MaterialToolbar toolbar = findViewById(R.id.settings_toolbar);
        hostInput = findViewById(R.id.mq_host_input);
        portInput = findViewById(R.id.mq_port_input);
        userInput = findViewById(R.id.mq_user_input);
        passInput = findViewById(R.id.mq_pass_input);
        queueInput = findViewById(R.id.mq_queue_input);
        equipmentInput = findViewById(R.id.mq_equipment_input);
        autoProduceSwitch = findViewById(R.id.mq_auto_produce_switch);
        MaterialButton saveButton = findViewById(R.id.mq_save_button);

        toolbar.setNavigationOnClickListener(v -> finish());

        hostInput.setText(MqSettingsStore.getHost(this));
        portInput.setText(String.valueOf(MqSettingsStore.getPort(this)));
        userInput.setText(MqSettingsStore.getUser(this));
        passInput.setText(MqSettingsStore.getPass(this));
        queueInput.setText(MqSettingsStore.getQueue(this));
        equipmentInput.setText(MqSettingsStore.getEquipmentNum(this));
        autoProduceSwitch.setChecked(MqSettingsStore.isAutoProduce(this));

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
        MqSettingsStore.setHost(this, host);
        MqSettingsStore.setPort(this, port);
        MqSettingsStore.setUser(this, textOf(userInput));
        MqSettingsStore.setPass(this, textOf(passInput));
        MqSettingsStore.setQueue(this, textOf(queueInput));
        MqSettingsStore.setEquipmentNum(this, textOf(equipmentInput));
        MqSettingsStore.setAutoProduce(this, autoProduceSwitch.isChecked());
        OrderMqManager.getInstance().restart(this);
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
