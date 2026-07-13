package com.example.test5.order.mq;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.test5.log.ProductionLogStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 内存订单收件箱：供主页与订单列表展示。 */
public final class OrderInbox {

    public interface Listener {
        void onOrdersChanged();
    }

    public enum Status {
        RECEIVED,
        PROCESSING,
        DONE,
        FAILED,
        SKIPPED
    }

    public static final class Entry {
        public final long id;
        public final long receivedAtMs;
        public final String orderNumber;
        public final String topic;
        public final String dishName;
        public final String tableNumber;
        public final String cookNumber;
        public final String source;
        public volatile Status status;
        public volatile String statusMessage;
        public final String rawJson;

        Entry(
                long id,
                long receivedAtMs,
                String orderNumber,
                String topic,
                String dishName,
                String tableNumber,
                String cookNumber,
                String source,
                Status status,
                String statusMessage,
                String rawJson
        ) {
            this.id = id;
            this.receivedAtMs = receivedAtMs;
            this.orderNumber = orderNumber != null ? orderNumber : "";
            this.topic = topic != null ? topic : "";
            this.dishName = dishName != null ? dishName : "";
            this.tableNumber = tableNumber != null ? tableNumber : "";
            this.cookNumber = cookNumber != null ? cookNumber : "";
            this.source = source != null ? source : "";
            this.status = status;
            this.statusMessage = statusMessage != null ? statusMessage : "";
            this.rawJson = rawJson != null ? rawJson : "";
        }
    }

    private static final int MAX_ENTRIES = 100;
    private static volatile OrderInbox instance;

    private final List<Entry> entries = new ArrayList<>();
    private final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private long nextId = 1;

    private OrderInbox() {
    }

    public static OrderInbox getInstance() {
        if (instance == null) {
            synchronized (OrderInbox.class) {
                if (instance == null) {
                    instance = new OrderInbox();
                }
            }
        }
        return instance;
    }

    public synchronized Entry add(
            String orderNumber,
            String topic,
            String dishName,
            String tableNumber,
            String cookNumber,
            String source,
            String rawJson
    ) {
        Entry entry = new Entry(
                nextId++,
                System.currentTimeMillis(),
                orderNumber,
                topic,
                dishName,
                tableNumber,
                cookNumber,
                source,
                Status.RECEIVED,
                "",
                rawJson
        );
        entries.add(0, entry);
        while (entries.size() > MAX_ENTRIES) {
            entries.remove(entries.size() - 1);
        }
        logReceived(entry);
        notifyChanged();
        return entry;
    }

    public synchronized void updateStatus(Entry entry, Status status, String message) {
        if (entry == null) {
            return;
        }
        entry.status = status;
        entry.statusMessage = message != null ? message : "";
        logStatus(entry, status, message);
        notifyChanged();
    }

    @NonNull
    public synchronized List<Entry> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    @Nullable
    public synchronized Entry latest() {
        return entries.isEmpty() ? null : entries.get(0);
    }

    public void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onOrdersChanged();
        }
    }

    private static void logReceived(Entry entry) {
        String sourceLabel = "local".equals(entry.source)
                ? "本机"
                : ("voice".equals(entry.source) ? "语音" : "小程序");
        ProductionLogStore.append(
                ProductionLogStore.Level.INFO,
                ProductionLogStore.Category.ORDER,
                "收到订单",
                sourceLabel + " · " + entry.dishName,
                entry.orderNumber,
                entry.dishName
        );
    }

    private static void logStatus(Entry entry, Status status, String message) {
        ProductionLogStore.Level level = ProductionLogStore.Level.INFO;
        ProductionLogStore.Category category = ProductionLogStore.Category.ORDER;
        String title = "状态更新";
        if (status == Status.FAILED) {
            level = ProductionLogStore.Level.ERROR;
            title = "生产失败";
        } else if (status == Status.SKIPPED) {
            level = ProductionLogStore.Level.WARN;
            title = "已跳过";
        } else if (status == Status.DONE) {
            title = "生产完成";
        } else if (status == Status.PROCESSING) {
            category = ProductionLogStore.Category.FLOW;
            title = "流程执行";
        }
        String body = message != null && !message.isEmpty() ? message : status.name();
        ProductionLogStore.append(
                level,
                category,
                title,
                body,
                entry.orderNumber,
                entry.dishName
        );
    }
}
