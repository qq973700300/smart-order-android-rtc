package com.example.test5.aigc;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

/** 播放内置歌曲资源（客人点歌时由 sing_song 工具触发）。 */
public final class SingSongPlayer {

    private static final String TAG = "SingSongPlayer";

    /** assets 内固定曲目：鹅企的说唱 */
    public static final String ASSET_EQI_QIYE_RAP = "songs/eqi_qiye_rap.mp3";

    public interface Listener {
        void onPlaybackEnded();
    }

    private MediaPlayer mediaPlayer;
    private Listener listener;

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public synchronized void play(Context context, String assetPath) {
        stopInternal(false);
        try {
            AssetFileDescriptor afd = context.getAssets().openFd(assetPath);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mediaPlayer.setOnCompletionListener(mp -> stopInternal(true));
            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error what=" + what + " extra=" + extra);
                stopInternal(true);
                return true;
            });
            mediaPlayer.prepare();
            mediaPlayer.start();
            Log.i(TAG, "Playing " + assetPath);
        } catch (IOException e) {
            Log.e(TAG, "play failed: " + assetPath, e);
            stopInternal(true);
        }
    }

    public synchronized void stop() {
        stopInternal(true);
    }

    private void stopInternal(boolean notify) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (Exception ignored) {
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (notify && listener != null) {
            listener.onPlaybackEnded();
        }
    }

    public synchronized void release() {
        listener = null;
        stopInternal(false);
    }

    public synchronized boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.isPlaying();
    }
}
