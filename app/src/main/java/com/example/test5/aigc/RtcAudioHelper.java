package com.example.test5.aigc;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * 工控平板 / 无听筒设备上 RTC 远端语音容易无声：
 * 需强制扬声器、拉高通话/媒体音量、申请音频焦点。
 */
public final class RtcAudioHelper {

    private static final String TAG = "RtcAudioHelper";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static AudioFocusRequest audioFocusRequest;
    private static AudioManager.OnAudioFocusChangeListener focusListener;

    private RtcAudioHelper() {
    }

    /** 进房前后调用；delayMs 后再补一次，兼容慢启动的平板 ROM。 */
    public static void preparePlayback(Context context) {
        preparePlayback(context, 0);
    }

    public static void preparePlayback(Context context, long delayMs) {
        Runnable task = () -> applyPlaybackRoute(context.getApplicationContext());
        if (delayMs <= 0) {
            MAIN.post(task);
        } else {
            MAIN.postDelayed(task, delayMs);
        }
    }

    public static void release(Context context) {
        AudioManager am = (AudioManager) context.getApplicationContext()
                .getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            am.abandonAudioFocusRequest(audioFocusRequest);
            audioFocusRequest = null;
        } else if (focusListener != null) {
            am.abandonAudioFocus(focusListener);
            focusListener = null;
        }
        am.setSpeakerphoneOn(false);
        am.setMode(AudioManager.MODE_NORMAL);
    }

    private static void applyPlaybackRoute(Context appContext) {
        AudioManager am = (AudioManager) appContext.getSystemService(Context.AUDIO_SERVICE);
        if (am == null) {
            return;
        }
        requestAudioFocus(am);

        boolean tabletLike = !appContext.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        // 工控平板无听筒：MODE_IN_COMMUNICATION 可能路由到死设备，用 NORMAL 更稳
        int mode = tabletLike ? AudioManager.MODE_NORMAL : AudioManager.MODE_IN_COMMUNICATION;
        am.setMode(mode);
        am.setSpeakerphoneOn(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            routeToBuiltinSpeaker(am);
        }

        ensureAudible(am, AudioManager.STREAM_VOICE_CALL);
        ensureAudible(am, AudioManager.STREAM_MUSIC);

        Log.i(TAG, "playback route tabletLike=" + tabletLike
                + " mode=" + am.getMode()
                + " speaker=" + am.isSpeakerphoneOn()
                + " voiceVol=" + am.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                + "/" + am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                + " musicVol=" + am.getStreamVolume(AudioManager.STREAM_MUSIC)
                + "/" + am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
    }

    private static void requestAudioFocus(AudioManager am) {
        if (focusListener == null) {
            focusListener = focusChange -> Log.d(TAG, "focusChange=" + focusChange);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest == null) {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(attrs)
                        .setOnAudioFocusChangeListener(focusListener)
                        .build();
            }
            am.requestAudioFocus(audioFocusRequest);
        } else {
            am.requestAudioFocus(focusListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN);
        }
    }

    private static void ensureAudible(AudioManager am, int stream) {
        int max = am.getStreamMaxVolume(stream);
        if (max <= 0) {
            return;
        }
        int cur = am.getStreamVolume(stream);
        int target = Math.max(cur, (int) (max * 0.75f));
        if (target > cur) {
            am.setStreamVolume(stream, target, 0);
        }
    }

    private static void routeToBuiltinSpeaker(AudioManager am) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return;
        }
        try {
            for (android.media.AudioDeviceInfo device : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                if (device.getType() == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    am.setCommunicationDevice(device);
                    Log.i(TAG, "setCommunicationDevice=BUILTIN_SPEAKER");
                    break;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "setCommunicationDevice failed", e);
        }
    }
}
