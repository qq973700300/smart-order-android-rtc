package com.example.test5.aigc;

import android.os.Handler;
import android.util.Log;

/**
 * 语音会话自动结束：送厨闭环、end_conversation 工具、静音超时、最长时长兜底。
 * 结束对话仅通过 LLM 调用 end_conversation，不做本地 ASR 口令匹配。
 */
public final class VoiceSessionAutoEndController {

    private static final String TAG = "VoiceSessionAutoEnd";
    private static final long POST_SUBMIT_DELAY_MS = 10_000L;
    private static final long POST_SUBMIT_FALLBACK_MS = 20_000L;
    private static final long SILENCE_TIMEOUT_MS = 25_000L;
    private static final long MAX_SESSION_MS = 5 * 60_000L;

    public enum EndReason {
        SUBMIT_COMPLETE,
        END_CONVERSATION,
        SILENCE,
        MAX_DURATION
    }

    public interface Callback {
        void onAutoEndRequested(EndReason reason);
    }

    private final Handler handler;
    private final Callback callback;

    private boolean active;
    private boolean singMode;
    /** 送厨或 end_conversation 工具调用后，等待 AI 告别再延迟结束 */
    private EndReason awaitingFarewellFor;

    private final Runnable maxDurationRunnable = () -> fire(EndReason.MAX_DURATION);
    private final Runnable silenceRunnable = () -> fire(EndReason.SILENCE);
    private final Runnable farewellFallbackRunnable = this::fireAwaitingFarewellFallback;

    public VoiceSessionAutoEndController(Handler handler, Callback callback) {
        this.handler = handler;
        this.callback = callback;
    }

    public void start() {
        stop();
        active = true;
        singMode = false;
        awaitingFarewellFor = null;
        handler.postDelayed(maxDurationRunnable, MAX_SESSION_MS);
        Log.i(TAG, "started max=" + MAX_SESSION_MS + "ms silence=" + SILENCE_TIMEOUT_MS + "ms");
    }

    public void stop() {
        active = false;
        singMode = false;
        awaitingFarewellFor = null;
        handler.removeCallbacks(maxDurationRunnable);
        handler.removeCallbacks(silenceRunnable);
        handler.removeCallbacks(farewellFallbackRunnable);
    }

    public void setSingMode(boolean singMode) {
        this.singMode = singMode;
        if (singMode) {
            handler.removeCallbacks(silenceRunnable);
        }
    }

    public void onSubmitSuccess() {
        beginAwaitingFarewell(EndReason.SUBMIT_COMPLETE);
    }

    /** LLM 调用 end_conversation 工具后触发。 */
    public void onEndConversationToolCalled() {
        beginAwaitingFarewell(EndReason.END_CONVERSATION);
    }

    private void beginAwaitingFarewell(EndReason reason) {
        if (!active) {
            return;
        }
        awaitingFarewellFor = reason;
        handler.removeCallbacks(farewellFallbackRunnable);
        handler.removeCallbacks(silenceRunnable);
        handler.postDelayed(farewellFallbackRunnable, POST_SUBMIT_FALLBACK_MS);
        Log.i(TAG, "awaiting farewell for " + reason + " fallback=" + POST_SUBMIT_FALLBACK_MS + "ms");
    }

    private void fireAwaitingFarewellFallback() {
        if (!active || awaitingFarewellFor == null) {
            return;
        }
        EndReason reason = awaitingFarewellFor;
        awaitingFarewellFor = null;
        fire(reason);
    }

    private void scheduleFarewellEndAfterBot() {
        if (!active || awaitingFarewellFor == null) {
            return;
        }
        EndReason reason = awaitingFarewellFor;
        awaitingFarewellFor = null;
        handler.removeCallbacks(farewellFallbackRunnable);
        handler.removeCallbacks(silenceRunnable);
        handler.postDelayed(() -> fire(reason), POST_SUBMIT_DELAY_MS);
        Log.i(TAG, "bot farewell for " + reason + ", end in " + POST_SUBMIT_DELAY_MS + "ms");
    }

    public void onSubtitle(String text, boolean definite, boolean paragraph, boolean fromBot) {
        if (!active || singMode || text == null || text.isEmpty()) {
            return;
        }
        if (!fromBot) {
            if (definite || paragraph) {
                handler.removeCallbacks(farewellFallbackRunnable);
                awaitingFarewellFor = null;
                handler.removeCallbacks(silenceRunnable);
            }
            return;
        }
        if (!definite) {
            return;
        }
        if (awaitingFarewellFor != null) {
            scheduleFarewellEndAfterBot();
            return;
        }
        scheduleSilenceTimer();
    }

    private void scheduleSilenceTimer() {
        handler.removeCallbacks(silenceRunnable);
        handler.postDelayed(silenceRunnable, SILENCE_TIMEOUT_MS);
    }

    private void fire(EndReason reason) {
        if (!active) {
            return;
        }
        stop();
        Log.i(TAG, "auto end: " + reason);
        callback.onAutoEndRequested(reason);
    }
}
