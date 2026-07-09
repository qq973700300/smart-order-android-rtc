package com.example.test5.order.mq;

import android.content.Context;

/** App 级 MQ 消费者管理；由 {@link OrderMqForegroundService} 保活。 */
public final class OrderMqManager {

    private static volatile OrderMqManager instance;
    private OrderMqConsumer consumer;

    private OrderMqManager() {
    }

    public static OrderMqManager getInstance() {
        if (instance == null) {
            synchronized (OrderMqManager.class) {
                if (instance == null) {
                    instance = new OrderMqManager();
                }
            }
        }
        return instance;
    }

    /** 启动 MQ 前台服务（Application 入口）。 */
    public void start(Context context) {
        OrderMqForegroundService.start(context.getApplicationContext());
    }

    /** 保存 MQ 设置后重启连接。 */
    public synchronized void restart(Context context) {
        OrderMqForegroundService.restart(context.getApplicationContext());
    }

    synchronized void startConsumerIfNeeded(Context context) {
        if (consumer != null) {
            return;
        }
        consumer = new OrderMqConsumer(context.getApplicationContext());
        consumer.start();
    }

    synchronized void restartConsumer(Context context) {
        stopConsumer();
        consumer = new OrderMqConsumer(context.getApplicationContext());
        consumer.start();
    }

    synchronized void stopConsumer() {
        if (consumer != null) {
            consumer.stop();
            consumer = null;
        }
    }

    public String getConnectionStatus() {
        OrderMqConsumer c = consumer;
        if (c != null) {
            return c.getConnectionStatus();
        }
        return OrderMqForegroundService.isRunning() ? "服务已启动，连接初始化中" : "未启动";
    }
}
