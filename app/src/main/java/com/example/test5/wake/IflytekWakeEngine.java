package com.example.test5.wake;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.iflytek.aikit.core.AiAudio;
import com.iflytek.aikit.core.AiHandle;
import com.iflytek.aikit.core.AiHelper;
import com.iflytek.aikit.core.AiListener;
import com.iflytek.aikit.core.AiRequest;
import com.iflytek.aikit.core.AiResponse;
import com.iflytek.aikit.core.AiStatus;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 讯飞离线唤醒 IVW（能力 ID e867a88f2）。
 * 持续录音并送 PCM，检测到 func_wake_up / func_pre_wakeup 时回调。
 */
public final class IflytekWakeEngine {

    private static final String TAG = "IflytekWake";
    private static final String ABILITY_ID = "e867a88f2";
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 1280;

    public interface Listener {
        void onWakeDetected(String keyword, String rawResult);

        void onError(String message);

        void onStateChanged(String state);

        /** 调试页用：当前录音音量估算值。 */
        default void onVolume(int volume) {
        }

        /** 调试页用：引擎原始回调。 */
        default void onEngineEvent(String key, String value) {
        }
    }

    private final Context appContext;
    private final String[] keywords;
    private final AtomicBoolean sessionOpen = new AtomicBoolean(false);
    private final AtomicBoolean recording = new AtomicBoolean(false);
    private final AtomicBoolean firstFrame = new AtomicBoolean(true);

    private HandlerThread workerThread;
    private Handler workerHandler;
    private Handler mainHandler;
    private Listener listener;
    private AudioRecord audioRecord;
    private AiHandle aiHandle;
    private File ivwDir;
    private long lastWakeAtMs;
    private int cmThresholdScore = 800;

    public IflytekWakeEngine(Context context, String[] keywords) {
        this.appContext = context.getApplicationContext();
        this.keywords = keywords != null ? keywords : new String[0];
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 门限分数，Demo 默认 800，越小越灵敏。 */
    public void setCmThresholdScore(int score) {
        this.cmThresholdScore = Math.max(0, score);
    }

    public void start() {
        ensureWorker();
        workerHandler.post(this::startInternal);
    }

    public void stop() {
        stopAndAwait(0);
    }

    /** 停止唤醒并等待麦克风释放（RTC 进房前调用）。 */
    public void stopAndAwait(long timeoutMs) {
        if (workerHandler == null) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        workerHandler.post(() -> {
            stopInternal();
            latch.countDown();
        });
        if (timeoutMs <= 0) {
            return;
        }
        try {
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                Log.w(TAG, "stopAndAwait timeout " + timeoutMs + "ms");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureWorker() {
        if (workerThread != null) {
            return;
        }
        workerThread = new HandlerThread("iflytek-ivw");
        workerThread.start();
        workerHandler = new Handler(workerThread.getLooper());
        mainHandler = new Handler(Looper.getMainLooper());
        AiHelper.getInst().registerListener(ABILITY_ID, aiListener);
    }

    private void startInternal() {
        if (!IflytekSdkHolder.isAuthorized()) {
            notifyError("SDK 鉴权未完成，code=" + IflytekSdkHolder.getAuthCode());
            return;
        }
        notifyState("准备资源…");
        try {
            ivwDir = IflytekResourceHelper.ensureIvwDir(appContext);
        } catch (IOException e) {
            notifyError("IVW 资源解压失败: " + e.getMessage());
            return;
        }
        if (!writeKeywordFile()) {
            notifyError("唤醒词文件写入失败");
            return;
        }
        int ret = loadAndStartEngine();
        if (ret != 0) {
            String hint = IflytekSdkHolder.describeEngineError(ret);
            notifyError("唤醒引擎启动失败: " + ret + "（" + hint + "）");
            return;
        }
        if (!startAudioRecord()) {
            notifyError("麦克风启动失败");
            endEngineSession();
            return;
        }
        firstFrame.set(true);
        recording.set(true);
        notifyState("监听中");
        scheduleReadLoop();
    }

    private void stopInternal() {
        recording.set(false);
        releaseAudioRecord();
        endEngineSession();
        notifyState("已停止");
    }

    private int loadAndStartEngine() {
        String keywordPath = new File(ivwDir, "keyword.txt").getAbsolutePath();
        Log.i(TAG, "loadData keywordPath=" + keywordPath + " resDir=" + ivwDir.getAbsolutePath());
        AiRequest.Builder customBuilder = AiRequest.builder();
        customBuilder.customText("key_word", keywordPath, 0);
        int ret = AiHelper.getInst().loadData(ABILITY_ID, customBuilder.build());
        if (ret != 0) {
            Log.e(TAG, "loadData failed: " + ret);
            return ret;
        }
        int[] indexs = {0};
        ret = AiHelper.getInst().specifyDataSet(ABILITY_ID, "key_word", indexs);
        if (ret != 0) {
            Log.e(TAG, "specifyDataSet failed: " + ret);
            return ret;
        }

        AiRequest.Builder paramBuilder = AiRequest.builder();
        String[] effectiveKeywords = effectiveKeywords();
        String thresholdParam = buildThresholdParam(effectiveKeywords);
        Log.i(TAG, "keywords=" + effectiveKeywords.length + " threshold=" + thresholdParam);
        paramBuilder.param("wdec_param_nCmThreshold", thresholdParam);
        paramBuilder.param("gramLoad", true);
        sessionOpen.set(false);
        aiHandle = AiHelper.getInst().start(ABILITY_ID, paramBuilder.build(), null);
        if (aiHandle == null || aiHandle.getCode() != 0) {
            int code = aiHandle != null ? aiHandle.getCode() : -1;
            Log.e(TAG, "start failed: " + code);
            return code;
        }
        sessionOpen.set(true);
        return 0;
    }

    /**
     * 讯飞文档：wdec_param_nCmThreshold 格式为「文件索引 词索引:门限」，多词用 | 分隔。
     * 例：1 个词 {@code 0 0:600}；3 个词 {@code 0 0:600|0 1:600|0 2:600}
     * 见 https://www.xfyun.cn/doc/asr/AIkit_awaken/Android-SDK.html
     */
    private String buildThresholdParam(String[] effectiveKeywords) {
        if (effectiveKeywords.length == 0) {
            return "0 0:" + cmThresholdScore;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < effectiveKeywords.length; i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(0).append(' ').append(i).append(':').append(cmThresholdScore);
        }
        return sb.toString();
    }

    private String[] effectiveKeywords() {
        List<String> list = new ArrayList<>();
        for (String keyword : keywords) {
            if (keyword == null) {
                continue;
            }
            String trimmed = keyword.trim();
            while (trimmed.endsWith(";")) {
                trimmed = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        return list.toArray(new String[0]);
    }

    private boolean writeKeywordFile() {
        String[] effectiveKeywords = effectiveKeywords();
        File keywordFile = new File(ivwDir, "keyword.txt");
        File binFile = new File(ivwDir, "keyword.bin");
        //noinspection ResultOfMethodCallIgnored
        keywordFile.delete();
        //noinspection ResultOfMethodCallIgnored
        binFile.delete();
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(keywordFile), StandardCharsets.UTF_8))) {
            for (String keyword : effectiveKeywords) {
                writer.write(keyword);
                writer.write(';');
                writer.newLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "write keyword.txt failed", e);
            return false;
        }
        return keywordFile.exists() && keywordFile.length() > 0;
    }

    private boolean startAudioRecord() {
        if (audioRecord != null) {
            return true;
        }
        int minBuffer = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, BUFFER_SIZE);
        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize);
        } catch (SecurityException e) {
            Log.e(TAG, "AudioRecord permission denied", e);
            return false;
        }
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            releaseAudioRecord();
            return false;
        }
        audioRecord.startRecording();
        return true;
    }

    private void scheduleReadLoop() {
        if (!recording.get() || workerHandler == null) {
            return;
        }
        workerHandler.post(this::readOnce);
    }

    private void readOnce() {
        if (!recording.get() || audioRecord == null || !sessionOpen.get()) {
            return;
        }
        byte[] buffer = new byte[BUFFER_SIZE];
        int read = audioRecord.read(buffer, 0, BUFFER_SIZE);
        if (read > 0 && read != AudioRecord.ERROR_INVALID_OPERATION) {
            notifyVolume(calculateVolume(buffer, read));
            AiStatus status = firstFrame.compareAndSet(true, false)
                    ? AiStatus.BEGIN : AiStatus.CONTINUE;
            writeAudio(buffer, read, status);
        }
        scheduleReadLoop();
    }

    private void writeAudio(byte[] data, int length, AiStatus status) {
        if (!sessionOpen.get() || aiHandle == null) {
            return;
        }
        byte[] chunk = length == data.length ? data : java.util.Arrays.copyOf(data, length);
        AiRequest.Builder dataBuilder = AiRequest.builder();
        AiAudio aiAudio = AiAudio.get("wav").data(chunk).status(status).valid();
        dataBuilder.payload(aiAudio);
        int ret = AiHelper.getInst().write(dataBuilder.build(), aiHandle);
        if (ret != 0) {
            Log.w(TAG, "write failed: " + ret);
        }
    }

    private void endEngineSession() {
        if (!sessionOpen.get() || aiHandle == null) {
            aiHandle = null;
            sessionOpen.set(false);
            return;
        }
        int ret = AiHelper.getInst().end(aiHandle);
        Log.d(TAG, "end session: " + ret);
        aiHandle = null;
        sessionOpen.set(false);
        firstFrame.set(true);
    }

    private void releaseAudioRecord() {
        if (audioRecord == null) {
            return;
        }
        try {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord.stop();
            }
        } catch (IllegalStateException ignored) {
        }
        audioRecord.release();
        audioRecord = null;
    }

    private final AiListener aiListener = new AiListener() {
        @Override
        public void onResult(int handleId, List<AiResponse> outputData, Object usrContext) {
            if (outputData == null || outputData.isEmpty()) {
                return;
            }
            for (AiResponse item : outputData) {
                String key = item.getKey();
                byte[] bytes = item.getValue();
                String result = bytes != null ? new String(bytes) : "";
                notifyEngineEvent(key, result);
                if (!"func_wake_up".equals(key) && !"func_pre_wakeup".equals(key)) {
                    continue;
                }
                long now = System.currentTimeMillis();
                if (now - lastWakeAtMs < 2500L) {
                    continue;
                }
                lastWakeAtMs = now;
                Log.i(TAG, key + " -> " + result);
                notifyWake(result, key + ": " + result);
            }
        }

        @Override
        public void onEvent(int i, int i1, List<AiResponse> list, Object o) {
            Log.d(TAG, "onEvent " + i + " " + i1);
        }

        @Override
        public void onError(int abilityId, int code, String message, Object usrContext) {
            notifyError("IVW 错误 " + code + ": " + message);
        }
    };

    private void notifyWake(String keyword, String raw) {
        if (listener == null || mainHandler == null) {
            return;
        }
        mainHandler.post(() -> listener.onWakeDetected(keyword, raw));
    }

    private void notifyError(String message) {
        Log.e(TAG, message);
        if (listener == null || mainHandler == null) {
            return;
        }
        mainHandler.post(() -> listener.onError(message));
    }

    private void notifyState(String state) {
        if (listener == null || mainHandler == null) {
            return;
        }
        mainHandler.post(() -> listener.onStateChanged(state));
    }

    private void notifyVolume(int volume) {
        if (listener == null || mainHandler == null) {
            return;
        }
        mainHandler.post(() -> listener.onVolume(volume));
    }

    private void notifyEngineEvent(String key, String value) {
        if (listener == null || mainHandler == null) {
            return;
        }
        mainHandler.post(() -> listener.onEngineEvent(key, value));
    }

    private static int calculateVolume(byte[] buffer, int length) {
        double sumVolume = 0.0;
        for (int i = 0; i + 1 < length; i += 2) {
            int v1 = buffer[i] & 0xFF;
            int v2 = buffer[i + 1] & 0xFF;
            int temp = v1 + (v2 << 8);
            if (temp >= 0x8000) {
                temp = 0xffff - temp;
            }
            sumVolume += Math.abs(temp);
        }
        double avgVolume = sumVolume / Math.max(1, length / 2);
        return (int) (Math.log10(1 + avgVolume) * 10);
    }
}
