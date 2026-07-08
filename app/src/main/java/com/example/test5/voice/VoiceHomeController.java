package com.example.test5.voice;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.test5.R;
import com.example.test5.net.NetworkDiagnostics;
import com.example.test5.aigc.AigcProxyApi;
import com.example.test5.aigc.AigcSceneInfo;
import com.example.test5.aigc.RtcAigcManager;
import com.example.test5.aigc.SubtitleTracker;
import com.example.test5.aigc.VoiceSessionAutoEndController;
import com.example.test5.order.OrderCart;
import com.example.test5.order.OrderSubmitDialogs;
import com.example.test5.wake.WakeForegroundService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 主页语音会话：唤醒或点击 AI 后在本页连 RTC，驱动头像说话动画。 */
public final class VoiceHomeController {

    public interface Callback {
        void onStatus(@NonNull String status);

        void onSpeakingChanged(boolean speaking);

        void onSessionRunningChanged(boolean running);

        void onError(@NonNull String message);

        void onCameraPermissionRequired();

        void onSubtitle(@NonNull String userText, @NonNull String botText);
    }

    private static final long BOT_SPEAKING_HOLD_MS = 450L;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final SubtitleTracker subtitleTracker = new SubtitleTracker();
    private final VoiceSessionAutoEndController sessionAutoEnd =
            new VoiceSessionAutoEndController(mainHandler, this::handleAutoEnd);

    private Callback callback = noopCallback();
    private final Runnable botSpeakingOffRunnable = () -> callback.onSpeakingChanged(false);

    private final RtcAigcManager rtcManager;

    private boolean sessionRunning;
    private boolean visionEnabled;
    private boolean wakePausedForSession;
    private boolean hasCameraPermission;
    private String sceneId = AigcProxyApi.defaultSceneId();
    @Nullable
    private AigcSceneInfo cachedSceneInfo;

    public VoiceHomeController(@NonNull Context context) {
        appContext = context.getApplicationContext();
        rtcManager = new RtcAigcManager(appContext);
        rtcManager.setSceneId(sceneId);
        rtcManager.setListener(new RtcAigcManager.Listener() {
            @Override
            public void onStatus(String status) {
                postStatus(status);
            }

            @Override
            public void onSubtitle(String userId, String text, boolean definite,
                                   boolean paragraph, boolean fromBot) {
                mainHandler.post(() -> {
                    subtitleTracker.update(userId, text, definite, paragraph, fromBot);
                    SubtitleTracker.DualLatest dual = subtitleTracker.renderDualLatest();
                    callback.onSubtitle(dual.userText, dual.botText);
                    if (sessionRunning) {
                        sessionAutoEnd.setSingMode(rtcManager.isSingMode());
                        sessionAutoEnd.onSubtitle(text, definite, paragraph, fromBot);
                    }
                    if (fromBot && text != null && !text.trim().isEmpty()) {
                        mainHandler.removeCallbacks(botSpeakingOffRunnable);
                        callback.onSpeakingChanged(true);
                        mainHandler.postDelayed(botSpeakingOffRunnable, BOT_SPEAKING_HOLD_MS);
                    }
                });
            }

            @Override
            public void onCartUpdated(String cartText) {
            }

            @Override
            public void onOrderSubmitted(boolean success, String message, String submittedSummary) {
                mainHandler.post(() -> {
                    OrderSubmitDialogs.show(appContext, success, message, submittedSummary);
                    if (success && sessionRunning) {
                        sessionAutoEnd.onSubmitSuccess();
                    }
                });
            }

            @Override
            public void onEndConversationRequested() {
                mainHandler.post(() -> {
                    if (sessionRunning) {
                        sessionAutoEnd.onEndConversationToolCalled();
                    }
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    callback.onError(message);
                    abortSession();
                });
            }
        });
    }

    public void setCallback(@NonNull Callback callback) {
        this.callback = callback;
    }

    public void setHasCameraPermission(boolean hasCameraPermission) {
        this.hasCameraPermission = hasCameraPermission;
    }

    public boolean isSessionRunning() {
        return sessionRunning;
    }

    public void startSession() {
        if (sessionRunning) {
            return;
        }
        sessionRunning = true;
        wakePausedForSession = true;
        WakeForegroundService.pauseForRtc(appContext);
        callback.onSessionRunningChanged(true);
        postStatus(appContext.getString(R.string.voice_clerk_connecting));
        NetworkDiagnostics.logSnapshot("voice_session_start proxy=" + AigcProxyApi.proxyHost());

        executor.execute(() -> {
            WakeForegroundService.ensureMicReleased(appContext);
            try {
                AigcSceneInfo sceneInfo = AigcProxyApi.fetchScene(sceneId);
                if (resolveVision(sceneInfo) && !hasCameraPermission) {
                    cachedSceneInfo = sceneInfo;
                    mainHandler.post(callback::onCameraPermissionRequired);
                    return;
                }
                continueStartSession(sceneInfo);
            } catch (Exception e) {
                android.util.Log.e(NetworkDiagnostics.TAG, "[cloud] voice_session failed", e);
                mainHandler.post(() -> {
                    callback.onError(e.getMessage());
                    abortSession();
                });
            }
        });
    }

    public void continueAfterCameraGranted() {
        if (!sessionRunning || cachedSceneInfo == null) {
            return;
        }
        AigcSceneInfo sceneInfo = cachedSceneInfo;
        cachedSceneInfo = null;
        hasCameraPermission = true;
        executor.execute(() -> continueStartSession(sceneInfo));
    }

    public void continueWithoutCamera() {
        if (!sessionRunning || cachedSceneInfo == null) {
            return;
        }
        AigcSceneInfo sceneInfo = cachedSceneInfo;
        cachedSceneInfo = null;
        executor.execute(() -> continueStartSession(sceneInfo));
    }

    public void stopSession() {
        if (!sessionRunning) {
            return;
        }
        sessionAutoEnd.stop();
        sessionRunning = false;
        visionEnabled = false;
        cachedSceneInfo = null;
        mainHandler.removeCallbacks(botSpeakingOffRunnable);
        callback.onSpeakingChanged(false);
        callback.onSessionRunningChanged(false);
        postStatus(appContext.getString(R.string.voice_clerk_stopping));
        resumeWakeIfNeeded();
        executor.execute(() -> {
            try {
                AigcProxyApi.stopVoiceChat(sceneId);
            } catch (Exception ignored) {
            }
            mainHandler.post(rtcManager::stop);
        });
    }

    public void release() {
        mainHandler.removeCallbacks(botSpeakingOffRunnable);
        sessionAutoEnd.stop();
        if (sessionRunning) {
            stopSession();
        }
        rtcManager.release();
        executor.shutdownNow();
        WakeForegroundService.resumeAfterRtc(appContext);
    }

    private void continueStartSession(@Nullable AigcSceneInfo sceneInfo) {
        if (sceneInfo == null || sceneInfo.rtc == null) {
            mainHandler.post(() -> {
                callback.onError("场景配置为空");
                abortSession();
            });
            return;
        }
        visionEnabled = resolveVision(sceneInfo);
        boolean startCamera = visionEnabled && hasCameraPermission;

        if (sceneInfo.scene != null && sceneInfo.scene.botName != null) {
            subtitleTracker.setBotUserId(sceneInfo.scene.botName);
        }

        subtitleTracker.clear();
        OrderCart.getInstance().clear();
        mainHandler.post(() -> callback.onSubtitle("", ""));
        rtcManager.start(sceneInfo, visionEnabled, startCamera);
        try {
            AigcProxyApi.startVoiceChat(sceneId, sceneInfo.rtc.userId, sceneInfo.rtc.roomId);
        } catch (Exception e) {
            rtcManager.stop();
            mainHandler.post(() -> {
                callback.onError(e.getMessage());
                abortSession();
            });
            return;
        }

        mainHandler.post(() -> {
            sessionAutoEnd.start();
            int statusRes;
            if (visionEnabled && rtcManager.isCameraEnabled()) {
                statusRes = R.string.voice_clerk_running_vision;
            } else if (visionEnabled) {
                statusRes = R.string.voice_clerk_running_no_camera;
            } else {
                statusRes = R.string.voice_clerk_running;
            }
            postStatus(appContext.getString(statusRes));
        });
    }

    private void abortSession() {
        sessionAutoEnd.stop();
        sessionRunning = false;
        visionEnabled = false;
        cachedSceneInfo = null;
        mainHandler.removeCallbacks(botSpeakingOffRunnable);
        callback.onSpeakingChanged(false);
        callback.onSessionRunningChanged(false);
        resumeWakeIfNeeded();
    }

    private void handleAutoEnd(VoiceSessionAutoEndController.EndReason reason) {
        if (!sessionRunning) {
            return;
        }
        stopSession();
        int messageRes;
        switch (reason) {
            case SUBMIT_COMPLETE:
                messageRes = R.string.voice_clerk_auto_end_submit;
                break;
            case END_CONVERSATION:
                messageRes = R.string.voice_clerk_auto_end_conversation;
                break;
            case SILENCE:
                messageRes = R.string.voice_clerk_auto_end_silence;
                break;
            case MAX_DURATION:
            default:
                messageRes = R.string.voice_clerk_auto_end_max_duration;
                break;
        }
        postStatus(appContext.getString(messageRes));
    }

    private void resumeWakeIfNeeded() {
        if (wakePausedForSession) {
            wakePausedForSession = false;
            WakeForegroundService.resumeAfterRtc(appContext);
        }
    }

    private void postStatus(String status) {
        mainHandler.post(() -> callback.onStatus(status));
    }

    private static boolean resolveVision(@Nullable AigcSceneInfo sceneInfo) {
        return sceneInfo != null && sceneInfo.scene != null && sceneInfo.scene.isVision;
    }

    private static Callback noopCallback() {
        return new Callback() {
            @Override
            public void onStatus(@NonNull String status) {
            }

            @Override
            public void onSpeakingChanged(boolean speaking) {
            }

            @Override
            public void onSessionRunningChanged(boolean running) {
            }

            @Override
            public void onError(@NonNull String message) {
            }

            @Override
            public void onCameraPermissionRequired() {
            }

            @Override
            public void onSubtitle(@NonNull String userText, @NonNull String botText) {
            }
        };
    }
}
