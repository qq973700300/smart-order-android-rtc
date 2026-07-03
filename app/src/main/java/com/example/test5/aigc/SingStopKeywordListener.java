package com.example.test5.aigc;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Locale;

/**
 * 唱歌期间本地监听「停下 / 别唱」等口令（此时不上传云端 ASR，避免被歌声误触发）。
 */
public final class SingStopKeywordListener {

    private static final String TAG = "SingStopKeyword";

    public interface Callback {
        void onStopKeywordDetected(String phrase);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private Callback callback;
    private boolean active;

    public void start(Context context, Callback callback) {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer unavailable");
            return;
        }
        this.callback = callback;
        active = true;
        mainHandler.post(() -> {
            if (recognizer != null) {
                recognizer.destroy();
            }
            recognizer = SpeechRecognizer.createSpeechRecognizer(context.getApplicationContext());
            recognizer.setRecognitionListener(createListener());
            startListening();
        });
    }

    public void stop() {
        active = false;
        callback = null;
        mainHandler.post(() -> {
            if (recognizer != null) {
                recognizer.cancel();
                recognizer.destroy();
                recognizer = null;
            }
        });
    }

    private RecognitionListener createListener() {
        return new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
            }

            @Override
            public void onBeginningOfSpeech() {
            }

            @Override
            public void onRmsChanged(float rmsdB) {
            }

            @Override
            public void onBufferReceived(byte[] buffer) {
            }

            @Override
            public void onEndOfSpeech() {
            }

            @Override
            public void onError(int error) {
                if (active) {
                    mainHandler.postDelayed(SingStopKeywordListener.this::startListening, 400);
                }
            }

            @Override
            public void onResults(Bundle results) {
                handleResults(results);
                if (active) {
                    startListening();
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                handleResults(partialResults);
            }

            @Override
            public void onEvent(int eventType, Bundle params) {
            }
        };
    }

    private void handleResults(Bundle results) {
        if (!active || callback == null || results == null) {
            return;
        }
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (texts == null) {
            return;
        }
        for (String text : texts) {
            if (isStopPhrase(text)) {
                Log.i(TAG, "Stop keyword: " + text);
                callback.onStopKeywordDetected(text);
                return;
            }
        }
    }

    static boolean isStopPhrase(String text) {
        if (text == null) {
            return false;
        }
        String normalized = text.replace(" ", "").trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.contains("停下")
                || normalized.contains("别唱")
                || normalized.contains("不要唱")
                || normalized.contains("别唱了")
                || normalized.contains("停止唱")
                || normalized.contains("停止播放")
                || normalized.contains("关掉")
                || normalized.contains("关了吧");
    }

    private void startListening() {
        if (!active || recognizer == null) {
            return;
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        recognizer.startListening(intent);
    }
}
