package com.example.test5;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test5.aigc.AigcProxyApi;
import com.example.test5.aigc.AigcSceneInfo;
import com.example.test5.aigc.RtcAigcManager;
import com.example.test5.aigc.SubtitleTracker;
import com.example.test5.order.OrderCart;
import com.example.test5.order.OrderSubmitDialogs;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** RTC-AIGC 语音点餐：进房 → StartVoiceChat → Function Calling；视觉场景后台推摄像头给 AI。 */
public class VoiceClerkActivity extends AppCompatActivity {

    private TextView statusView;
    private TextView cartView;
    private TextView lastOrderView;
    private TextView subtitleView;
    private ScrollView subtitleScroll;
    private MaterialButton startButton;
    private MaterialButton stopButton;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SubtitleTracker subtitleTracker = new SubtitleTracker();
    private RtcAigcManager rtcManager;

    private boolean sessionRunning;
    private boolean visionEnabled;
    private String sceneId = AigcProxyApi.defaultSceneId();
    private AigcSceneInfo cachedSceneInfo;

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!Boolean.TRUE.equals(granted)) {
                    toast(getString(R.string.mic_denied));
                    return;
                }
                startSession();
            });

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (!Boolean.TRUE.equals(granted)) {
                    toast(getString(R.string.camera_denied));
                }
                AigcSceneInfo sceneInfo = cachedSceneInfo;
                cachedSceneInfo = null;
                continueStartSession(sceneInfo);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        rtcManager = new RtcAigcManager(this);
        setContentView(R.layout.activity_voice_clerk);

        MaterialToolbar toolbar = findViewById(R.id.voice_clerk_toolbar);
        statusView = findViewById(R.id.status_text);
        cartView = findViewById(R.id.cart_text);
        lastOrderView = findViewById(R.id.last_order_text);
        subtitleView = findViewById(R.id.subtitle_text);
        subtitleScroll = findViewById(R.id.subtitle_scroll);
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        MaterialButton manualOrderButton = findViewById(R.id.manual_order_button);

        toolbar.setNavigationOnClickListener(v -> finish());
        manualOrderButton.setOnClickListener(v ->
                startActivity(new Intent(this, ManualOrderActivity.class)));
        findViewById(R.id.api_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, OrderSubscribeDebugActivity.class)));
        findViewById(R.id.lebai_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, LebaiDebugActivity.class)));

        rtcManager.setListener(new RtcAigcManager.Listener() {
            @Override
            public void onStatus(String status) {
                runOnMain(() -> statusView.setText(status));
            }

            @Override
            public void onSubtitle(String userId, String text, boolean definite,
                                   boolean paragraph, boolean fromBot) {
                runOnMain(() -> updateSubtitle(userId, text, definite, paragraph, fromBot));
            }

            @Override
            public void onCartUpdated(String cartText) {
                runOnMain(() -> cartView.setText(
                        cartText.isEmpty() ? getString(R.string.cart_empty) : cartText));
            }

            @Override
            public void onOrderSubmitted(boolean success, String message, String submittedSummary) {
                runOnMain(() -> {
                    refreshLastOrderDisplay();
                    OrderSubmitDialogs.show(VoiceClerkActivity.this, success, message, submittedSummary);
                });
            }

            @Override
            public void onError(String message) {
                runOnMain(() -> {
                    statusView.setText(message);
                    toast(message);
                    resetButtons();
                });
            }
        });

        startButton.setOnClickListener(v -> ensureMicAndStart());
        stopButton.setOnClickListener(v -> stopSession());
        refreshLastOrderDisplay();
    }

    @Override
    protected void onResume() {
        super.onResume();
        OrderCart.getInstance().setChangeListener(cartText ->
                runOnMain(() -> cartView.setText(
                        cartText.isEmpty() ? getString(R.string.cart_empty) : cartText)));
        String cartText = OrderCart.getInstance().buildCartText();
        cartView.setText(cartText.isEmpty() ? getString(R.string.cart_empty) : cartText);
        refreshLastOrderDisplay();
    }

    @Override
    protected void onPause() {
        OrderCart.getInstance().setChangeListener(null);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopSession();
        if (rtcManager != null) {
            rtcManager.release();
        }
        executor.shutdownNow();
    }

    private void ensureMicAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        } else {
            startSession();
        }
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startSession() {
        if (sessionRunning) {
            return;
        }
        sessionRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        subtitleTracker.clear();
        subtitleView.setText("");
        OrderCart.getInstance().clear();
        cartView.setText(R.string.cart_empty);
        statusView.setText(R.string.voice_clerk_connecting);

        executor.execute(() -> {
            try {
                AigcSceneInfo sceneInfo = AigcProxyApi.fetchScene(sceneId);
                if (resolveVision(sceneInfo) && !hasCameraPermission()) {
                    cachedSceneInfo = sceneInfo;
                    runOnMain(() -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA));
                    return;
                }
                continueStartSession(sceneInfo);
            } catch (Exception e) {
                runOnMain(() -> {
                    statusView.setText(getString(R.string.voice_clerk_error, e.getMessage()));
                    toast(e.getMessage());
                    resetButtons();
                });
            }
        });
    }

    /** 根据服务端 isVision 决定是否开启视觉模式与摄像头 */
    private void continueStartSession(AigcSceneInfo sceneInfo) {
        executor.execute(() -> {
            if (sceneInfo == null || sceneInfo.rtc == null) {
                runOnMain(() -> {
                    statusView.setText(getString(R.string.voice_clerk_error, "场景配置为空"));
                    toast(getString(R.string.voice_clerk_error, "场景配置为空"));
                    resetButtons();
                });
                return;
            }
            visionEnabled = resolveVision(sceneInfo);
            boolean startCamera = visionEnabled && hasCameraPermission();

            if (sceneInfo.scene != null && sceneInfo.scene.botName != null) {
                subtitleTracker.setBotUserId(sceneInfo.scene.botName);
            }

            rtcManager.start(sceneInfo, visionEnabled, startCamera);
            try {
                AigcProxyApi.startVoiceChat(sceneId, sceneInfo.rtc.userId, sceneInfo.rtc.roomId);
            } catch (Exception e) {
                rtcManager.stop();
                runOnMain(() -> {
                    statusView.setText(getString(R.string.voice_clerk_error, e.getMessage()));
                    toast(e.getMessage());
                    resetButtons();
                });
                return;
            }

            runOnMain(() -> {
                if (visionEnabled && rtcManager.isCameraEnabled()) {
                    statusView.setText(R.string.voice_clerk_running_vision);
                } else if (visionEnabled) {
                    statusView.setText(R.string.voice_clerk_running_no_camera);
                } else {
                    statusView.setText(R.string.voice_clerk_running);
                }
            });
        });
    }

    private void stopSession() {
        if (!sessionRunning) {
            return;
        }
        sessionRunning = false;
        visionEnabled = false;
        resetButtons();
        statusView.setText(R.string.voice_clerk_stopping);
        executor.execute(() -> {
            try {
                AigcProxyApi.stopVoiceChat(sceneId);
            } catch (Exception ignored) {
            }
            runOnMain(() -> rtcManager.stop());
        });
    }

    private void resetButtons() {
        sessionRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
    }

    private void updateSubtitle(String userId, String text, boolean definite,
                                boolean paragraph, boolean fromBot) {
        subtitleTracker.update(userId, text, definite, paragraph, fromBot);
        subtitleView.setText(subtitleTracker.render());
        subtitleScroll.post(() -> subtitleScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void refreshLastOrderDisplay() {
        String last = OrderCart.getInstance().getLastSubmittedSummary();
        lastOrderView.setText(last.isEmpty() ? getString(R.string.last_order_empty) : last);
    }

    private boolean resolveVision(AigcSceneInfo sceneInfo) {
        return sceneInfo != null && sceneInfo.scene != null && sceneInfo.scene.isVision;
    }

    private void runOnMain(@NonNull Runnable runnable) {
        mainHandler.post(runnable);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}
