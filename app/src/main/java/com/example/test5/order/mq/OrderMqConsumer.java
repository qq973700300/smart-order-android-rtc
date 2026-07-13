package com.example.test5.order.mq;

import android.content.Context;
import android.util.Log;

import com.example.test5.log.ProductionLogStore;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * 订阅 RabbitMQ 队列接收小程序/云端订单（与 SSKJYingJiang ConnectionMQ 一致）。
 */
final class OrderMqConsumer implements Runnable {

    private static final String TAG = "OrderMq";

    private final Context appContext;
    private final OrderProductionExecutor executor;
    private volatile boolean running;
    private volatile String connectionStatus = "未连接";
    private Connection connection;
    private Channel channel;

    OrderMqConsumer(Context context) {
        appContext = context.getApplicationContext();
        executor = new OrderProductionExecutor(appContext);
    }

    void start() {
        if (running) {
            return;
        }
        running = true;
        Thread thread = new Thread(this, "OrderMqConsumer");
        thread.setDaemon(false);
        thread.start();
    }

    void stop() {
        running = false;
        closeQuietly();
    }

    String getConnectionStatus() {
        return connectionStatus;
    }

    @Override
    public void run() {
        while (running) {
            try {
                connectAndConsume();
            } catch (Exception e) {
                Log.e(TAG, "MQ loop error", e);
                connectionStatus = "连接异常: " + e.getMessage();
            }
            closeQuietly();
            if (running) {
                connectionStatus = "重连中…";
                sleep(10_000);
            }
        }
        connectionStatus = "已停止";
    }

    private void connectAndConsume() throws IOException, TimeoutException {
        String host = MqSettingsStore.getHost(appContext);
        int port = MqSettingsStore.getPort(appContext);
        String queue = MqSettingsStore.getQueue(appContext);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setPort(port);
        factory.setUsername(MqSettingsStore.getUser(appContext));
        factory.setPassword(MqSettingsStore.getPass(appContext));
        factory.setVirtualHost("/");
        factory.setAutomaticRecoveryEnabled(true);
        factory.setNetworkRecoveryInterval(10_000);
        factory.setConnectionTimeout(15_000);

        Log.i(TAG, "connecting " + host + ":" + port + " queue=" + queue);
        connectionStatus = "连接中 " + host + ":" + port;
        connection = factory.newConnection();
        channel = connection.createChannel();
        channel.queueDeclare(queue, true, false, false, null);
        channel.basicQos(0, 1, false);

        DefaultConsumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(
                    String consumerTag,
                    Envelope envelope,
                    AMQP.BasicProperties properties,
                    byte[] body
            ) throws IOException {
                String msg = new String(body, StandardCharsets.UTF_8);
                boolean ok = handleMessage(msg);
                long tag = envelope.getDeliveryTag();
                if (ok) {
                    channel.basicAck(tag, false);
                } else {
                    channel.basicNack(tag, false, true);
                }
            }
        };
        channel.basicConsume(queue, false, consumer);
        connectionStatus = "已连接，等待订单";
        Log.i(TAG, "connected, consuming " + queue);
        ProductionLogStore.append(
                ProductionLogStore.Level.INFO,
                ProductionLogStore.Category.MQ,
                "MQ 已连接",
                host + ":" + port + " · " + queue
        );

        while (running && connection.isOpen()) {
            sleep(2_000);
        }
    }

    private boolean handleMessage(String json) {
        if (json == null || json.trim().isEmpty()) {
            return true;
        }
        Log.i(TAG, "RX order: " + json);

        OrderMqModels.DataItem dataItem = executor.parseDataItem(json);
        if (dataItem == null) {
            Log.w(TAG, "parse DataItem failed");
            ProductionLogStore.append(
                    ProductionLogStore.Level.ERROR,
                    ProductionLogStore.Category.MQ,
                    "MQ 订单解析失败",
                    json.length() > 120 ? json.substring(0, 120) + "…" : json
            );
            return false;
        }

        if (!acceptEquipment(dataItem.equipmentNum)) {
            Log.i(TAG, "skip equipment " + dataItem.equipmentNum);
            OrderInbox.Entry entry = OrderInbox.getInstance().add(
                    dataItem.orderNumber,
                    dataItem.topic,
                    "(设备不匹配)",
                    "",
                    "",
                    "miniprogram",
                    json
            );
            OrderInbox.getInstance().updateStatus(
                    entry,
                    OrderInbox.Status.SKIPPED,
                    "设备号不匹配: " + dataItem.equipmentNum);
            return true;
        }

        boolean allOk = true;
        if (dataItem.orderNumber != null && dataItem.orderNumber.contains("YY_")) {
            OrderMqModels.VoiceItem voice = executor.parseVoiceItem(dataItem.message);
            if (voice == null || voice.dishName == null) {
                return false;
            }
            OrderInbox.Entry entry = OrderInbox.getInstance().add(
                    dataItem.orderNumber,
                    dataItem.topic,
                    voice.dishName,
                    voice.tableNumber,
                    voice.cookNumber,
                    "voice",
                    json
            );
            if (!executor.runProduction(entry, voice.dishName)) {
                allOk = false;
            }
        } else {
            OrderMqModels.MessageDetail detail = executor.parseMessageDetail(dataItem.message);
            if (detail == null || detail.detail == null || detail.detail.isEmpty()) {
                Log.w(TAG, "empty Detail in message");
                return false;
            }
            for (OrderMqModels.DetailItem item : detail.detail) {
                OrderInbox.Entry entry = OrderInbox.getInstance().add(
                        dataItem.orderNumber,
                        dataItem.topic,
                        item.dishName,
                        item.tableNumber,
                        item.cookNumber,
                        "miniprogram",
                        json
                );
                if (!executor.runProduction(entry, item.dishName)) {
                    allOk = false;
                }
            }
        }
        return allOk;
    }

    private boolean acceptEquipment(String equipmentNum) {
        String filter = MqSettingsStore.getEquipmentNum(appContext);
        if (filter == null || filter.isEmpty()) {
            return true;
        }
        return filter.equals(equipmentNum);
    }

    private void closeQuietly() {
        try {
            if (channel != null && channel.isOpen()) {
                channel.close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
        channel = null;
        connection = null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
