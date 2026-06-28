package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.order.HttpHelper;
import com.example.test5.order.StoreConfig;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * OrderSubscribe 接口手动调试页：可编辑 storeId / equipmentNum / topic / message 并查看完整响应。
 */
public class OrderSubscribeDebugActivity extends AppCompatActivity {

    private TextInputEditText storeIdInput;
    private TextInputEditText equipmentNumInput;
    private TextInputEditText topicInput;
    private TextInputEditText messageInput;
    private TextView responseText;
    private MaterialButton submitButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_subscribe_debug);

        MaterialToolbar toolbar = findViewById(R.id.order_subscribe_toolbar);
        storeIdInput = findViewById(R.id.store_id_input);
        equipmentNumInput = findViewById(R.id.equipment_num_input);
        topicInput = findViewById(R.id.topic_input);
        messageInput = findViewById(R.id.message_input);
        responseText = findViewById(R.id.response_text);
        submitButton = findViewById(R.id.submit_button);

        toolbar.setNavigationOnClickListener(v -> finish());

        storeIdInput.setText(String.valueOf(StoreConfig.STORE_ID));
        equipmentNumInput.setText(StoreConfig.EQUIPMENT_NUM);
        topicInput.setText(StoreConfig.STORE_NAME);
        messageInput.setText("{小炒黄牛肉:1}");

        findViewById(R.id.preset_chip_beef).setOnClickListener(v ->
                messageInput.setText("{小炒黄牛肉:1}"));
        findViewById(R.id.preset_chip_egg).setOnClickListener(v ->
                messageInput.setText("{外婆菜炒鸡蛋:1}"));
        findViewById(R.id.preset_chip_two_dishes).setOnClickListener(v ->
                messageInput.setText("{小炒黄牛肉:1}{外婆菜炒鸡蛋:1}"));

        submitButton.setOnClickListener(v -> submitRequest());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private void submitRequest() {
        String storeIdText = textOf(storeIdInput);
        String equipmentNum = textOf(equipmentNumInput);
        String topic = textOf(topicInput);
        String message = textOf(messageInput);

        if (TextUtils.isEmpty(storeIdText)) {
            Toast.makeText(this, R.string.order_subscribe_error_store_id, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(topic)) {
            Toast.makeText(this, R.string.order_subscribe_error_topic, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(message)) {
            Toast.makeText(this, R.string.order_subscribe_error_message, Toast.LENGTH_SHORT).show();
            return;
        }

        int storeId;
        try {
            storeId = Integer.parseInt(storeIdText.trim());
        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.order_subscribe_error_store_id, Toast.LENGTH_SHORT).show();
            return;
        }

        submitButton.setEnabled(false);
        responseText.setText(R.string.order_subscribe_requesting);

        executor.execute(() -> {
            String resultText;
            try {
                HttpHelper.OrderSubscribeDebugResult result = HttpHelper.submitOrderWithDetails(
                        storeId,
                        equipmentNum,
                        topic,
                        message
                );
                resultText = formatResult(result);
            } catch (Exception e) {
                resultText = getString(R.string.order_subscribe_error_network, e.getMessage());
            }

            String finalResultText = resultText;
            runOnUiThread(() -> {
                responseText.setText(finalResultText);
                submitButton.setEnabled(true);
            });
        });
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private String formatResult(HttpHelper.OrderSubscribeDebugResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("URL\n").append(result.requestUrl).append("\n\n");
        builder.append("Request\n").append(result.requestBody).append("\n\n");
        builder.append("HTTP ").append(result.httpCode).append("\n\n");
        builder.append("Response\n").append(result.responseBody).append("\n\n");
        builder.append("Parsed\n");
        builder.append("success=").append(result.parsed.success).append('\n');
        builder.append("msg=").append(result.parsed.msg).append('\n');
        builder.append("orderNumber=").append(result.parsed.orderNumber).append('\n');
        builder.append("subscribeId=").append(result.parsed.subscribeId);
        return builder.toString();
    }
}
