package com.example.test5.order.mq;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.example.test5.MainActivity;
import com.example.test5.R;

/** 前台服务：保持 RabbitMQ 订单订阅，App 退到后台或被划掉后仍可收单。 */
public final class OrderMqForegroundService extends Service {

    private static final int NOTIFICATION_ID = 1002;
    private static final String CHANNEL_ID = "order_mq";
    private static final long STATUS_POLL_MS = 5_000L;

    public static final String ACTION_RESTART = "com.example.test5.order.mq.RESTART";

    private static volatile OrderMqForegroundService instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable statusPoller = new Runnable() {
        @Override
        public void run() {
            updateNotification(OrderMqManager.getInstance().getConnectionStatus());
            mainHandler.postDelayed(this, STATUS_POLL_MS);
        }
    };

    public static void start(Context context) {
        Intent intent = new Intent(context, OrderMqForegroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void restart(Context context) {
        Intent intent = new Intent(context, OrderMqForegroundService.class);
        intent.setAction(ACTION_RESTART);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void stopIfRunning(Context context) {
        OrderMqForegroundService service = instance;
        if (service != null) {
            service.stopForeground(true);
            service.stopSelf();
        }
        context.stopService(new Intent(context, OrderMqForegroundService.class));
    }

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        Notification notification = buildNotification(getString(R.string.order_mq_status_connecting));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        OrderMqManager.getInstance().startConsumerIfNeeded(this);
        mainHandler.post(statusPoller);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_RESTART.equals(intent.getAction())) {
            OrderMqManager.getInstance().restartConsumer(this);
            updateNotification(OrderMqManager.getInstance().getConnectionStatus());
        } else {
            OrderMqManager.getInstance().startConsumerIfNeeded(this);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mainHandler.removeCallbacks(statusPoller);
        OrderMqManager.getInstance().stopConsumer();
        instance = null;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.order_mq_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.order_mq_channel_desc));
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification(String status) {
        Intent tap = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, tap,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(getString(R.string.order_mq_notification_title))
                .setContentText(status)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String status) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(status));
        }
    }
}
