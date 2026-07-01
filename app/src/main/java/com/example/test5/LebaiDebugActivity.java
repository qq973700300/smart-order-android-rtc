package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.lebai.LebaiConfig;
import com.example.test5.lebai.LebaiJsonRpcClient;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 乐白机械臂 JSON-RPC 调试页：通过 HTTP 3021 调用 start_sys / start_task / wait_task 等。
 */
public class LebaiDebugActivity extends AppCompatActivity {

    private TextInputEditText hostInput;
    private TextInputEditText portInput;
    private TextInputEditText sceneIdInput;
    private TextInputEditText taskIdInput;
    private TextView responseText;
    private MaterialButton pingButton;
    private MaterialButton startSysButton;
    private MaterialButton stopSysButton;
    private MaterialButton startTaskButton;
    private MaterialButton waitTaskButton;
    private MaterialButton loadTaskButton;
    private MaterialButton cancelTaskButton;
    private MaterialButton runSceneButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private Integer lastTaskId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lebai_debug);

        MaterialToolbar toolbar = findViewById(R.id.lebai_debug_toolbar);
        hostInput = findViewById(R.id.lebai_host_input);
        portInput = findViewById(R.id.lebai_port_input);
        sceneIdInput = findViewById(R.id.lebai_scene_id_input);
        taskIdInput = findViewById(R.id.lebai_task_id_input);
        responseText = findViewById(R.id.lebai_response_text);
        pingButton = findViewById(R.id.lebai_btn_ping);
        startSysButton = findViewById(R.id.lebai_btn_start_sys);
        stopSysButton = findViewById(R.id.lebai_btn_stop_sys);
        startTaskButton = findViewById(R.id.lebai_btn_start_task);
        waitTaskButton = findViewById(R.id.lebai_btn_wait_task);
        loadTaskButton = findViewById(R.id.lebai_btn_load_task);
        cancelTaskButton = findViewById(R.id.lebai_btn_cancel_task);
        runSceneButton = findViewById(R.id.lebai_btn_run_scene);

        toolbar.setNavigationOnClickListener(v -> finish());

        hostInput.setText(LebaiConfig.DEFAULT_HOST);
        portInput.setText(String.valueOf(LebaiConfig.DEFAULT_HTTP_PORT));
        sceneIdInput.setText(String.valueOf(LebaiConfig.SCENE_PICKUP));

        findViewById(R.id.lebai_chip_prep).setOnClickListener(v ->
                sceneIdInput.setText(String.valueOf(LebaiConfig.SCENE_PREP)));
        findViewById(R.id.lebai_chip_pickup).setOnClickListener(v ->
                sceneIdInput.setText(String.valueOf(LebaiConfig.SCENE_PICKUP)));
        findViewById(R.id.lebai_chip_delivery).setOnClickListener(v ->
                sceneIdInput.setText(String.valueOf(LebaiConfig.SCENE_DELIVERY)));
        findViewById(R.id.lebai_chip_charge).setOnClickListener(v ->
                sceneIdInput.setText(String.valueOf(LebaiConfig.SCENE_CHARGE)));

        findViewById(R.id.lebai_btn_ping).setOnClickListener(v -> runAction(Action.PING));
        findViewById(R.id.lebai_btn_start_sys).setOnClickListener(v -> runAction(Action.START_SYS));
        findViewById(R.id.lebai_btn_stop_sys).setOnClickListener(v -> runAction(Action.STOP_SYS));
        findViewById(R.id.lebai_btn_start_task).setOnClickListener(v -> runAction(Action.START_TASK));
        findViewById(R.id.lebai_btn_wait_task).setOnClickListener(v -> runAction(Action.WAIT_TASK));
        findViewById(R.id.lebai_btn_load_task).setOnClickListener(v -> runAction(Action.LOAD_TASK));
        findViewById(R.id.lebai_btn_cancel_task).setOnClickListener(v -> runAction(Action.CANCEL_TASK));
        findViewById(R.id.lebai_btn_run_scene).setOnClickListener(v -> runAction(Action.RUN_SCENE));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private enum Action {
        PING,
        START_SYS,
        STOP_SYS,
        START_TASK,
        WAIT_TASK,
        LOAD_TASK,
        CANCEL_TASK,
        RUN_SCENE,
    }

    private void runAction(Action action) {
        String host = textOf(hostInput);
        String portText = textOf(portInput);
        if (TextUtils.isEmpty(host)) {
            toast(R.string.lebai_error_host);
            return;
        }
        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            toast(R.string.lebai_error_port);
            return;
        }

        setBusy(true);
        responseText.setText(R.string.lebai_requesting);

        executor.execute(() -> {
            String resultText;
            try {
                LebaiJsonRpcClient client = new LebaiJsonRpcClient(host, port);
                LebaiJsonRpcClient.RpcCallResult result = execute(client, action);
                if (result.taskId != null) {
                    lastTaskId = result.taskId;
                }
                resultText = result.formatForDisplay();
            } catch (Exception e) {
                resultText = getString(R.string.lebai_error_network, e.getMessage());
            }

            String finalResultText = resultText;
            runOnUiThread(() -> {
                responseText.setText(finalResultText);
                if (lastTaskId != null) {
                    taskIdInput.setText(String.valueOf(lastTaskId));
                }
                setBusy(false);
            });
        });
    }

    private LebaiJsonRpcClient.RpcCallResult execute(LebaiJsonRpcClient client, Action action) throws Exception {
        switch (action) {
            case PING:
                return client.loadTaskList();
            case START_SYS:
                return client.startSys();
            case STOP_SYS:
                return client.stopSys();
            case START_TASK: {
                String sceneId = textOf(sceneIdInput);
                if (TextUtils.isEmpty(sceneId)) {
                    throw new IllegalArgumentException(getString(R.string.lebai_error_scene_id));
                }
                return client.startTask(sceneId, false);
            }
            case WAIT_TASK:
                return client.waitTask(parseTaskId());
            case LOAD_TASK:
                return client.loadTask(parseTaskId());
            case CANCEL_TASK:
                return client.cancelTask(parseTaskId());
            case RUN_SCENE: {
                String sceneId = textOf(sceneIdInput);
                if (TextUtils.isEmpty(sceneId)) {
                    throw new IllegalArgumentException(getString(R.string.lebai_error_scene_id));
                }
                return client.runSceneUntilDone(sceneId);
            }
            default:
                throw new IllegalStateException("unknown action");
        }
    }

    private int parseTaskId() {
        String taskText = textOf(taskIdInput);
        if (!TextUtils.isEmpty(taskText)) {
            return Integer.parseInt(taskText.trim());
        }
        if (lastTaskId != null) {
            return lastTaskId;
        }
        throw new IllegalArgumentException(getString(R.string.lebai_error_task_id));
    }

    private void setBusy(boolean busy) {
        pingButton.setEnabled(!busy);
        startSysButton.setEnabled(!busy);
        stopSysButton.setEnabled(!busy);
        startTaskButton.setEnabled(!busy);
        waitTaskButton.setEnabled(!busy);
        loadTaskButton.setEnabled(!busy);
        cancelTaskButton.setEnabled(!busy);
        runSceneButton.setEnabled(!busy);
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
