package com.example.test5.wake;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.test5.MainActivity;
import com.example.test5.R;
import com.example.test5.MainActivity;

/** 前台服务：24h 离线唤醒监听，命中后跳转语音点餐。 */
public class WakeForegroundService extends Service {

    private static final String TAG = "WakeService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "wake_listen";
    private static final long AUTH_POLL_MS = 500L;
    private static final long AUTH_TIMEOUT_MS = 60_000L;
    private static final int WAKE_LAUNCH_REQUEST_CODE = 2001;

    public static final String ACTION_RESUME = "com.example.test5.wake.RESUME";
    public static final String ACTION_PAUSE = "com.example.test5.wake.PAUSE";

    private static volatile WakeForegroundService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private IflytekWakeEngine wakeEngine;
    private boolean engineRunning;
    private boolean pausedForRtc;
    private long authWaitStartedAt;
    private long lastWakeLaunchAtMs;

    public static void start(Context context) {
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            stopIfRunning(context);
            return;
        }
        Intent intent = new Intent(context, WakeForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void resumeAfterRtc(Context context) {
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            return;
        }
        Intent intent = new Intent(context, WakeForegroundService.class);
        intent.setAction(ACTION_RESUME);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void pauseForRtc(Context context) {
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            return;
        }
        Intent intent = new Intent(context, WakeForegroundService.class);
        intent.setAction(ACTION_PAUSE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    /** 关闭已在运行的唤醒服务。 */
    public static void stopIfRunning(Context context) {
        WakeForegroundService service = instance;
        if (service != null) {
            service.stopListeningInternal();
            service.stopForeground(true);
            service.stopSelf();
        }
        context.stopService(new Intent(context, WakeForegroundService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        WakeStatusHolder.update(WakeStatusHolder.State.INIT, getString(R.string.wake_status_init));
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.wake_status_init)));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            stopListeningInternal();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (intent != null && ACTION_RESUME.equals(intent.getAction())) {
            resumeListening();
            return START_STICKY;
        }
        if (intent != null && ACTION_PAUSE.equals(intent.getAction())) {
            pauseForRtc();
            return START_STICKY;
        }
        if (!engineRunning && !pausedForRtc) {
            waitForAuthAndStart();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopListeningInternal();
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void waitForAuthAndStart() {
        authWaitStartedAt = System.currentTimeMillis();
        mainHandler.post(authPollRunnable);
    }

    private final Runnable authPollRunnable = new Runnable() {
        @Override
        public void run() {
            if (IflytekSdkHolder.isAuthorized()) {
                startListeningInternal();
                return;
            }
            if (System.currentTimeMillis() - authWaitStartedAt > AUTH_TIMEOUT_MS) {
                String msg = getString(R.string.wake_status_auth_failed,
                        IflytekSdkHolder.getAuthCode());
                WakeStatusHolder.update(WakeStatusHolder.State.AUTH_FAILED, msg);
                updateNotification(msg);
                return;
            }
            String waitMsg = getString(R.string.wake_status_auth_wait);
            WakeStatusHolder.update(WakeStatusHolder.State.AUTH_WAIT, waitMsg);
            updateNotification(waitMsg);
            mainHandler.postDelayed(this, AUTH_POLL_MS);
        }
    };

    private void startListeningInternal() {
        if (engineRunning || pausedForRtc) {
            return;
        }
        String[] keywords = IflytekKeywordHelper.loadKeywords(this);
        wakeEngine = new IflytekWakeEngine(this, keywords);
        wakeEngine.setCmThresholdScore(900);
        wakeEngine.setListener(new IflytekWakeEngine.Listener() {
            @Override
            public void onWakeDetected(String keyword, String rawResult) {
                onWakeHit(keyword);
            }

            @Override
            public void onError(String message) {
                String msg = getString(R.string.wake_status_error, message);
                WakeStatusHolder.update(WakeStatusHolder.State.ERROR, msg);
                updateNotification(msg);
            }

            @Override
            public void onStateChanged(String state) {
                String msg = getString(R.string.wake_status_listening);
                WakeStatusHolder.update(WakeStatusHolder.State.LISTENING, msg);
                updateNotification(msg);
            }
        });
        wakeEngine.start();
        engineRunning = true;
        String listeningMsg = getString(R.string.wake_status_listening);
        WakeStatusHolder.update(WakeStatusHolder.State.LISTENING, listeningMsg);
        updateNotification(listeningMsg);
    }

    private void stopListeningInternal() {
        mainHandler.removeCallbacks(authPollRunnable);
        engineRunning = false;
        pausedForRtc = false;
        releaseWakeEngine(2000L);
    }

    private void releaseWakeEngine(long awaitMs) {
        IflytekWakeEngine engine = wakeEngine;
        wakeEngine = null;
        if (engine != null) {
            engine.stopAndAwait(awaitMs);
        }
    }

    private void pauseForRtc() {
        pausedForRtc = true;
        engineRunning = false;
        releaseWakeEngine(2000L);
        updateNotification(getString(R.string.wake_status_paused_rtc));
        WakeStatusHolder.update(WakeStatusHolder.State.PAUSED_RTC,
                getString(R.string.wake_status_paused_rtc));
    }

    /** 唤醒进 RTC 前确保 IVW 已释放麦克风。 */
    public static void ensureMicReleased(Context context) {
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            return;
        }
        WakeForegroundService service = instance;
        if (service != null) {
            service.releaseWakeEngine(2000L);
        }
        pauseForRtc(context);
    }

    private void resumeListening() {
        pausedForRtc = false;
        if (!engineRunning) {
            if (IflytekSdkHolder.isAuthorized()) {
                startListeningInternal();
            } else {
                waitForAuthAndStart();
            }
        }
    }

    private void onWakeHit(String keyword) {
        long now = System.currentTimeMillis();
        if (now - lastWakeLaunchAtMs < 3000L) {
            Log.i(TAG, "wake ignored (cooldown): " + keyword);
            return;
        }
        lastWakeLaunchAtMs = now;
        Log.i(TAG, "wake: " + keyword);
        pauseForRtc();
        String triggeredMsg = getString(R.string.wake_status_triggered, keyword);
        WakeStatusHolder.update(WakeStatusHolder.State.TRIGGERED, triggeredMsg);
        updateNotification(triggeredMsg);

        launchVoiceClerk(keyword);
    }

    private void launchVoiceClerk(String keyword) {
        Intent launch = buildVoiceClerkIntent(keyword);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;
        PendingIntent fullScreenPi = PendingIntent.getActivity(this, WAKE_LAUNCH_REQUEST_CODE,
                launch, flags);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                fullScreenPi.send();
            } else {
                startActivity(launch);
            }
        } catch (PendingIntent.CanceledException e) {
            Log.w(TAG, "PendingIntent launch failed, fallback startActivity", e);
            try {
                startActivity(launch);
            } catch (Exception ex) {
                Log.e(TAG, "startActivity failed after wake", ex);
                showWakeLaunchNotification(keyword, fullScreenPi);
                resumeListening();
            }
        }

        // 高优先级通知：若未自动跳转，用户可点通知进入
        showWakeLaunchNotification(keyword, fullScreenPi);
    }

    private Intent buildVoiceClerkIntent(String keyword) {
        Intent launch = new Intent(this, MainActivity.class);
        launch.putExtra(MainActivity.EXTRA_AUTO_START, true);
        launch.putExtra(MainActivity.EXTRA_WAKE_KEYWORD, keyword);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        return launch;
    }

    private void showWakeLaunchNotification(String keyword, PendingIntent contentPi) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.wake_launch_notification_title))
                .setContentText(getString(R.string.wake_launch_notification_body, keyword))
                .setContentIntent(contentPi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setFullScreenIntent(contentPi, true);
        }
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID + 1, builder.build());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.wake_channel_desc));
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String text) {
        Intent tap = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.wake_notification_title))
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(text));
        }
    }
}
