package com.example.test5.aigc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.test5.order.RestaurantFunctionHandler;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ss.bytertc.engine.RTCEngine;
import com.ss.bytertc.engine.RTCRoom;
import com.ss.bytertc.engine.RTCRoomConfig;
import com.ss.bytertc.engine.UserInfo;
import com.ss.bytertc.engine.data.EngineConfig;
import com.ss.bytertc.engine.handler.IRTCEngineEventHandler;
import com.ss.bytertc.engine.handler.IRTCRoomEventHandler;
import com.ss.bytertc.engine.type.ChannelProfile;
import com.ss.bytertc.engine.type.MessageConfig;

import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * RTC 进房 + 音频 + 可选摄像头推流（无本地预览）+ Function Calling。
 */
public final class RtcAigcManager {

    private static final String TAG = "RtcAigcManager";

    public interface Listener {
        void onStatus(String status);

        void onSubtitle(String userId, String text, boolean definite, boolean paragraph, boolean fromBot);

        void onCartUpdated(String cartText);

        void onOrderSubmitted(boolean success, String message, String submittedSummary);

        void onError(String message);
    }

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final RestaurantFunctionHandler functionHandler = new RestaurantFunctionHandler();
    private final SingSongPlayer singSongPlayer = new SingSongPlayer();
    private final SingStopKeywordListener singStopKeywordListener = new SingStopKeywordListener();
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private Listener listener;

    private RTCEngine rtcEngine;
    private RTCRoom rtcRoom;
    private String botUserId = "";
    private String sceneId = "";
    private boolean cameraEnabled;
    private boolean singMode;
    private boolean voiceInputPausedForSing;

    public RtcAigcManager(Context context) {
        this.appContext = context.getApplicationContext();
        functionHandler.setCartListener(cartText -> {
            if (listener != null) {
                listener.onCartUpdated(cartText);
            }
        });
        functionHandler.setSubmitListener((success, message, summary) -> {
            if (listener != null) {
                mainHandler.post(() -> listener.onOrderSubmitted(success, message, summary));
            }
        });
        functionHandler.setSingListener(this::beginSingSession);
        singSongPlayer.setListener(this::onSongPlaybackEnded);
    }

    public void setSceneId(String sceneId) {
        this.sceneId = sceneId != null ? sceneId : "";
    }

    public boolean isSingMode() {
        return singMode;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int getDishQuantity(String dishName) {
        return functionHandler.getQuantity(dishName);
    }

    public String getBotUserId() {
        return botUserId;
    }

    public boolean isCameraEnabled() {
        return cameraEnabled;
    }

    public void start(AigcSceneInfo sceneInfo, boolean visionMode, boolean startCamera) {
        if (sceneInfo == null || sceneInfo.rtc == null) {
            notifyError("场景 RTC 配置为空");
            return;
        }
        AigcSceneInfo.RtcInfo rtc = sceneInfo.rtc;
        if (isEmpty(rtc.appId) || isEmpty(rtc.roomId) || isEmpty(rtc.userId) || isEmpty(rtc.token)) {
            notifyError("RTC AppId/RoomId/UserId/Token 未配置完整");
            return;
        }
        if (sceneInfo.scene != null && !isEmpty(sceneInfo.scene.botName)) {
            botUserId = sceneInfo.scene.botName;
        }
        Log.i(TAG, "start visionMode=" + visionMode + " startCamera=" + startCamera);

        stopRtcOnly();
        notifyStatus("正在初始化 RTC…");

        EngineConfig engineConfig = new EngineConfig();
        engineConfig.context = appContext;
        engineConfig.appID = rtc.appId;

        rtcEngine = RTCEngine.createRTCEngine(engineConfig, new IRTCEngineEventHandler() {
            @Override
            public void onError(int err) {
                Log.e(TAG, "RTC onError code=" + err);
                notifyError("RTC 错误: " + err);
            }

            @Override
            public void onWarning(int warn) {
                Log.w(TAG, "RTC onWarning code=" + warn);
            }
        });

        rtcRoom = rtcEngine.createRTCRoom(rtc.roomId);
        rtcRoom.setRTCRoomEventHandler(new IRTCRoomEventHandler() {
            @Override
            public void onRoomStateChanged(String roomId, String uid, int state, String extraInfo) {
                Log.d(TAG, "roomState=" + state + " uid=" + uid + " extra=" + extraInfo);
            }

            @Override
            public void onUserJoined(UserInfo userInfo) {
                Log.i(TAG, "onUserJoined uid=" + userInfo.uid);
            }

            @Override
            public void onUserPublishStreamVideo(String streamId, com.ss.bytertc.engine.data.StreamInfo streamInfo, boolean isPublish) {
                Log.i(TAG, "onUserPublishStreamVideo uid=" + streamInfo.userId + " publish=" + isPublish);
            }

            @Override
            public void onUserPublishStreamAudio(String streamId, com.ss.bytertc.engine.data.StreamInfo streamInfo, boolean isPublish) {
                Log.i(TAG, "onUserPublishStreamAudio uid=" + streamInfo.userId + " publish=" + isPublish);
            }

            @Override
            public void onRoomBinaryMessageReceived(String uid, ByteBuffer message) {
                handleBinaryMessage(uid, message);
            }

            @Override
            public void onUserBinaryMessageReceived(long msgId, String uid, ByteBuffer message) {
                handleBinaryMessage(uid, message);
            }
        });

        if (startCamera) {
            startVideoCaptureOnMainThread();
        }

        rtcEngine.startAudioCapture();

        UserInfo userInfo = new UserInfo(rtc.userId, "");
        RTCRoomConfig roomConfig = new RTCRoomConfig(
                ChannelProfile.CHANNEL_PROFILE_CHAT_ROOM,
                "",
                true,
                visionMode,
                true,
                false
        );
        int joinResult = rtcRoom.joinRoom(rtc.token, userInfo, true, roomConfig);
        Log.i(TAG, "joinRoom result=" + joinResult + " (0=ok)");
        if (joinResult != 0) {
            notifyError("joinRoom 失败: " + joinResult);
            return;
        }
        rtcRoom.publishStreamAudio(true);
        if (startCamera) {
            int pub = rtcRoom.publishStreamVideo(true);
            Log.i(TAG, "publishStreamVideo result=" + pub + " (0=ok)");
        }

        notifyStatus("已进房，等待启动智能体…");
    }

    public void start(AigcSceneInfo sceneInfo) {
        boolean vision = sceneInfo != null && sceneInfo.scene != null && sceneInfo.scene.isVision;
        start(sceneInfo, vision, false);
    }

    public void stop() {
        stopRtcOnly();
        notifyStatus("已停止");
    }

    public void release() {
        finishSingSession(false, false);
        stop();
        singSongPlayer.release();
        singStopKeywordListener.stop();
        functionHandler.shutdown();
        backgroundExecutor.shutdownNow();
    }

    private void startVideoCaptureOnMainThread() {
        CountDownLatch latch = new CountDownLatch(1);
        mainHandler.post(() -> {
            try {
                if (rtcEngine != null) {
                    rtcEngine.startVideoCapture();
                    cameraEnabled = true;
                    Log.i(TAG, "startVideoCapture ok (no local preview)");
                }
            } catch (Exception e) {
                Log.e(TAG, "startVideoCapture failed", e);
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void stopRtcOnly() {
        try {
            finishSingSession(false, false);
            if (rtcEngine != null && cameraEnabled) {
                rtcEngine.stopVideoCapture();
                cameraEnabled = false;
            }
            if (rtcEngine != null) {
                rtcEngine.stopAudioCapture();
            }
            if (rtcRoom != null) {
                rtcRoom.leaveRoom();
                rtcRoom.destroy();
                rtcRoom = null;
            }
            if (rtcEngine != null) {
                RTCEngine.destroyRTCEngine();
                rtcEngine = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "stopRtcOnly", e);
        }
    }

    private void handleBinaryMessage(String uid, ByteBuffer message) {
        if (message == null) {
            return;
        }
        byte[] bytes = new byte[message.remaining()];
        message.get(bytes);
        try {
            TlvCodec.ParsedTlv tlv = TlvCodec.decode(bytes);
            if (TlvCodec.TYPE_FUNCTION_CALL.equals(tlv.type)) {
                handleFunctionCall(tlv.value);
            } else if (TlvCodec.TYPE_SUBTITLE.equals(tlv.type)) {
                handleSubtitle(tlv.value);
            }
        } catch (Exception e) {
            Log.w(TAG, "binary parse failed from " + uid, e);
        }
    }

    private void handleFunctionCall(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray toolCalls = root.getAsJsonArray("tool_calls");
        if (toolCalls == null || toolCalls.size() == 0) {
            return;
        }
        JsonObject call = toolCalls.get(0).getAsJsonObject();
        String toolCallId = call.has("id") ? call.get("id").getAsString() : "";
        JsonObject function = call.getAsJsonObject("function");
        String name = function.get("name").getAsString();
        String args = function.has("arguments") ? function.get("arguments").getAsString() : "{}";

        Log.i(TAG, "FunctionCall " + name + " " + args);
        if (RestaurantFunctionHandler.TOOL_SING_SONG.equals(name) && singMode) {
            sendFunctionResult(toolCallId, "{\"ok\":true,\"message\":\"已经在播放歌曲\"}");
            return;
        }
        String content = functionHandler.execute(name, args);
        sendFunctionResult(toolCallId, content);
    }

    private void sendFunctionResult(String toolCallId, String content) {
        if (rtcRoom == null || isEmpty(botUserId)) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("ToolCallID", toolCallId);
        payload.addProperty("Content", content);
        byte[] tlv = TlvCodec.encode(TlvCodec.TYPE_FUNCTION_RESULT, payload.toString());
        rtcRoom.sendUserBinaryMessage(botUserId, tlv, MessageConfig.RELIABLE_ORDERED);
    }

    private void handleSubtitle(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray data = root.getAsJsonArray("data");
            if (data == null || data.size() == 0) {
                return;
            }
            JsonObject item = data.get(0).getAsJsonObject();
            String text = item.has("text") ? item.get("text").getAsString() : "";
            String userId = item.has("userId") ? item.get("userId").getAsString() : "";
            boolean definite = item.has("definite") && item.get("definite").getAsBoolean();
            boolean paragraph = item.has("paragraph") && item.get("paragraph").getAsBoolean();
            boolean fromBot = !isEmpty(botUserId) && (botUserId.equals(userId) || userId.contains("voiceChat_"));
            if (singMode && fromBot) {
                return;
            }
            if (listener != null && (!isEmpty(userId) || !text.isEmpty())) {
                listener.onSubtitle(userId, text, definite, paragraph, fromBot);
            }
        } catch (Exception e) {
            Log.w(TAG, "subtitle parse", e);
        }
    }

    private void notifyStatus(String status) {
        if (listener != null) {
            listener.onStatus(status);
        }
    }

    private void notifyError(String message) {
        Log.e(TAG, message);
        if (listener != null) {
            listener.onError(message);
        }
    }

    private void beginSingSession() {
        mainHandler.post(() -> {
            if (singMode) {
                return;
            }
            singMode = true;
            pauseVoiceInputForSing();
            notifyStatus("正在播放《鹅企的说唱》，说「停下」或「别唱」可停止");
            singStopKeywordListener.start(appContext, phrase -> mainHandler.post(this::stopSingByUser));
            singSongPlayer.play(appContext, SingSongPlayer.ASSET_EQI_QIYE_RAP);
        });
        if (!isEmpty(sceneId)) {
            backgroundExecutor.execute(() -> {
                try {
                    AigcProxyApi.interruptVoiceChat(sceneId);
                } catch (Exception e) {
                    Log.w(TAG, "interruptVoiceChat failed", e);
                }
            });
        }
    }

    private void stopSingByUser() {
        if (!singMode) {
            return;
        }
        Log.i(TAG, "Sing stopped by user keyword");
        finishSingSession(true, true);
    }

    private void onSongPlaybackEnded() {
        finishSingSession(false, true);
    }

    /** @param stoppedByUser 是否用户口令停止；@param notifyStatus 是否更新状态文案 */
    private void finishSingSession(boolean stoppedByUser, boolean notifyStatus) {
        mainHandler.post(() -> {
            if (!singMode) {
                return;
            }
            singMode = false;
            singStopKeywordListener.stop();
            singSongPlayer.stop();
            resumeVoiceInputAfterSing();
            if (notifyStatus && listener != null) {
                listener.onStatus(stoppedByUser
                        ? "已停止播放，可以继续点餐"
                        : "播放结束，可以继续点餐");
            }
        });
    }

    private void pauseVoiceInputForSing() {
        if (voiceInputPausedForSing) {
            return;
        }
        voiceInputPausedForSing = true;
        try {
            if (rtcRoom != null) {
                rtcRoom.publishStreamAudio(false);
            }
            if (rtcEngine != null) {
                rtcEngine.stopAudioCapture();
            }
            Log.i(TAG, "Voice input paused for sing mode");
        } catch (Exception e) {
            Log.w(TAG, "pauseVoiceInputForSing", e);
        }
    }

    private void resumeVoiceInputAfterSing() {
        if (!voiceInputPausedForSing) {
            return;
        }
        voiceInputPausedForSing = false;
        try {
            if (rtcEngine != null) {
                rtcEngine.startAudioCapture();
            }
            if (rtcRoom != null) {
                rtcRoom.publishStreamAudio(true);
            }
            Log.i(TAG, "Voice input resumed after sing mode");
        } catch (Exception e) {
            Log.w(TAG, "resumeVoiceInputAfterSing", e);
        }
    }

    private static boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }
}
