package com.example.test5;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test5.wake.IflytekSdkHolder;
import com.example.test5.wake.IflytekWakeEngine;
import com.example.test5.wake.WakeForegroundService;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 讯飞 IVW 唤醒调试页：独立引擎实例，不影响前台服务逻辑的可视化验证。
 */
public class WakeDebugActivity extends AppCompatActivity {

    private static final long AUTH_POLL_MS = 400L;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final StringBuilder logBuilder = new StringBuilder();
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private TextView authStatusView;
    private TextView engineStateView;
    private TextView volumeView;
    private TextView logView;
    private ScrollView logScroll;
    private TextInputEditText keywordsInput;
    private TextInputEditText thresholdInput;
    private MaterialButton startButton;
    private MaterialButton stopButton;

    private IflytekWakeEngine wakeEngine;
    private boolean listening;

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    appendLog("已获得麦克风权限");
                    refreshAuthStatus();
                } else {
                    appendLog("麦克风权限被拒绝");
                    toast(getString(R.string.mic_denied));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wake_debug);

        MaterialToolbar toolbar = findViewById(R.id.wake_debug_toolbar);
        authStatusView = findViewById(R.id.wake_debug_auth_status);
        engineStateView = findViewById(R.id.wake_debug_engine_state);
        volumeView = findViewById(R.id.wake_debug_volume);
        logView = findViewById(R.id.wake_debug_log);
        logScroll = findViewById(R.id.wake_debug_log_scroll);
        keywordsInput = findViewById(R.id.wake_debug_keywords_input);
        thresholdInput = findViewById(R.id.wake_debug_threshold_input);
        startButton = findViewById(R.id.wake_debug_start_button);
        stopButton = findViewById(R.id.wake_debug_stop_button);

        toolbar.setNavigationOnClickListener(v -> finish());

        keywordsInput.setText(buildDefaultKeywords());
        thresholdInput.setText("800");

        startButton.setOnClickListener(v -> startDebugListening());
        stopButton.setOnClickListener(v -> stopDebugListening());
        findViewById(R.id.wake_debug_clear_log_button).setOnClickListener(v -> clearLog());
        findViewById(R.id.wake_debug_refresh_auth_button).setOnClickListener(v -> refreshAuthStatus());
        findViewById(R.id.wake_debug_demo_keyword_button).setOnClickListener(v -> {
            keywordsInput.setText("你好小迪");
            appendLog("已填入 Demo 唤醒词「你好小迪」（与 bundled 资源匹配，建议先测此词）");
        });

        setListeningUi(false);
        appendLog("工作目录: " + IflytekSdkHolder.getWorkDir(this).getAbsolutePath());
        appendLog("IVW 资源: " + IflytekSdkHolder.getIvwDir(this).getAbsolutePath());
        appendLog("Logcat 标签: IflytekSdk / IflytekWake");

        WakeForegroundService.pauseForRtc(this);
        appendLog("已暂停后台唤醒服务，避免与本页抢麦克风");

        ensureMicPermission();
        refreshAuthStatus();
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        stopDebugListening();
        WakeForegroundService.resumeAfterRtc(this);
        super.onDestroy();
    }

    private void ensureMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }

    private void refreshAuthStatus() {
        int code = IflytekSdkHolder.getAuthCode();
        if (IflytekSdkHolder.isAuthorized()) {
            authStatusView.setText(R.string.wake_debug_auth_ok);
            appendLog("AIKit 鉴权成功");
            return;
        }
        if (code == -1) {
            authStatusView.setText(R.string.wake_debug_auth_pending);
            mainHandler.postDelayed(this::refreshAuthStatus, AUTH_POLL_MS);
        } else {
            authStatusView.setText(getString(R.string.wake_debug_auth_failed, code,
                    IflytekSdkHolder.describeAuthError(code)));
            String hint = IflytekSdkHolder.describeAuthError(code);
            appendLog("AIKit 鉴权失败，code=" + code + "，" + hint);
            if (code == 18708) {
                appendLog("排查：1) 控制台确认已开通 IVW(e867a88f2) 2) 查看设备授权是否已满 3) 用 arm64 真机而非 x86 模拟器 4) 清除应用数据后联网重试");
            }
        }
    }

    private void startDebugListening() {
        if (listening) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast(getString(R.string.mic_denied));
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        if (!IflytekSdkHolder.isAuthorized()) {
            toast(getString(R.string.wake_debug_auth_not_ready));
            refreshAuthStatus();
            return;
        }

        String[] keywords = parseKeywords(keywordsInput.getText());
        if (keywords.length == 0) {
            toast(getString(R.string.wake_debug_keywords_empty));
            return;
        }
        int threshold = parseThreshold();
        if (threshold < 0) {
            toast(getString(R.string.wake_debug_threshold_invalid));
            return;
        }

        stopDebugListening();
        wakeEngine = new IflytekWakeEngine(this, keywords);
        wakeEngine.setCmThresholdScore(threshold);
        wakeEngine.setListener(new IflytekWakeEngine.Listener() {
            @Override
            public void onWakeDetected(String keyword, String rawResult) {
                engineStateView.setText(getString(R.string.wake_debug_state_hit, keyword));
                appendLog(">>> 唤醒命中: " + rawResult);
            }

            @Override
            public void onError(String message) {
                engineStateView.setText(getString(R.string.wake_debug_state_error));
                appendLog("错误: " + message);
            }

            @Override
            public void onStateChanged(String state) {
                engineStateView.setText(state);
                appendLog("状态: " + state);
            }

            @Override
            public void onVolume(int volume) {
                volumeView.setText(getString(R.string.wake_debug_volume, volume));
            }

            @Override
            public void onEngineEvent(String key, String value) {
                if ("func_wake_up".equals(key) || "func_pre_wakeup".equals(key)) {
                    return;
                }
                appendLog("引擎: " + key + " = " + value);
            }
        });

        appendLog("开始监听，唤醒词: " + TextUtils.join(" / ", keywords)
                + "，门限=" + threshold);
        wakeEngine.start();
        listening = true;
        setListeningUi(true);
    }

    private void stopDebugListening() {
        if (wakeEngine != null) {
            wakeEngine.stop();
            wakeEngine = null;
        }
        if (listening) {
            appendLog("已停止监听");
        }
        listening = false;
        setListeningUi(false);
        engineStateView.setText(R.string.wake_debug_state_idle);
        volumeView.setText(R.string.wake_debug_volume_idle);
    }

    private void setListeningUi(boolean active) {
        startButton.setEnabled(!active);
        stopButton.setEnabled(active);
        keywordsInput.setEnabled(!active);
        thresholdInput.setEnabled(!active);
    }

    private String buildDefaultKeywords() {
        String[] defaults = getResources().getStringArray(R.array.wake_keywords);
        return TextUtils.join("，", defaults);
    }

    private String[] parseKeywords(CharSequence raw) {
        if (raw == null) {
            return new String[0];
        }
        String text = raw.toString().replace('，', ',');
        String[] parts = text.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result.toArray(new String[0]);
    }

    private int parseThreshold() {
        CharSequence raw = thresholdInput.getText();
        if (raw == null || raw.toString().trim().isEmpty()) {
            return 800;
        }
        try {
            return Integer.parseInt(raw.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void clearLog() {
        logBuilder.setLength(0);
        logView.setText(R.string.wake_debug_log_empty);
    }

    private void appendLog(String line) {
        logBuilder.append('[').append(timeFormat.format(new Date())).append("] ")
                .append(line).append('\n');
        logView.setText(logBuilder.toString());
        logScroll.post(() -> logScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
