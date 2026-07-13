package com.example.test5.log;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** 本地持久化生产日志，供设置页查看。 */
public final class ProductionLogStore {

    public interface Listener {
        void onLogsChanged();
    }

    public enum Level {
        INFO,
        WARN,
        ERROR
    }

    public enum Category {
        ORDER,
        FLOW,
        MQ,
        DEVICE,
        SYSTEM
    }

    public static final class Entry {
        public long id;
        public long timestampMs;
        public String level;
        public String category;
        public String title;
        public String message;
        public String orderNumber;
        public String dishName;

        Entry copy() {
            Entry e = new Entry();
            e.id = id;
            e.timestampMs = timestampMs;
            e.level = level;
            e.category = category;
            e.title = title;
            e.message = message;
            e.orderNumber = orderNumber;
            e.dishName = dishName;
            return e;
        }
    }

    private static final String FILE_NAME = "production_logs.json";
    private static final int MAX_ENTRIES = 500;
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<Entry>>() {
    }.getType();

    private static volatile Context appContext;
    private static volatile List<Entry> cache;
    private static long nextId = 1;
    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();

    private ProductionLogStore() {
    }

    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    public static synchronized void append(
            Level level,
            Category category,
            String title,
            String message
    ) {
        append(level, category, title, message, "", "");
    }

    public static synchronized void append(
            Level level,
            Category category,
            String title,
            String message,
            String orderNumber,
            String dishName
    ) {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        ensureLoaded(ctx);
        Entry entry = new Entry();
        entry.id = nextId++;
        entry.timestampMs = System.currentTimeMillis();
        entry.level = level != null ? level.name() : Level.INFO.name();
        entry.category = category != null ? category.name() : Category.SYSTEM.name();
        entry.title = title != null ? title : "";
        entry.message = message != null ? message : "";
        entry.orderNumber = orderNumber != null ? orderNumber : "";
        entry.dishName = dishName != null ? dishName : "";
        cache.add(0, entry);
        while (cache.size() > MAX_ENTRIES) {
            cache.remove(cache.size() - 1);
        }
        persist(ctx);
        notifyChanged();
    }

    public static synchronized List<Entry> snapshot() {
        Context ctx = appContext;
        if (ctx == null) {
            return Collections.emptyList();
        }
        ensureLoaded(ctx);
        List<Entry> copy = new ArrayList<>(cache.size());
        for (Entry entry : cache) {
            copy.add(entry.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public static synchronized void clear() {
        Context ctx = appContext;
        if (ctx == null) {
            return;
        }
        ensureLoaded(ctx);
        cache.clear();
        persist(ctx);
        notifyChanged();
    }

    public static void addListener(Listener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    private static void ensureLoaded(Context context) {
        if (cache != null) {
            return;
        }
        File file = getFile(context);
        if (!file.exists()) {
            cache = new ArrayList<>();
            return;
        }
        try (InputStreamReader reader = new InputStreamReader(
                new FileInputStream(file), StandardCharsets.UTF_8)) {
            List<Entry> loaded = GSON.fromJson(reader, LIST_TYPE);
            cache = loaded != null ? new ArrayList<>(loaded) : new ArrayList<>();
            long maxId = 0;
            for (Entry entry : cache) {
                if (entry.id > maxId) {
                    maxId = entry.id;
                }
            }
            nextId = maxId + 1;
        } catch (Exception e) {
            cache = new ArrayList<>();
        }
    }

    private static void persist(Context context) {
        File file = getFile(context);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(cache, writer);
        } catch (Exception ignored) {
        }
    }

    private static File getFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void notifyChanged() {
        for (Listener listener : listeners) {
            listener.onLogsChanged();
        }
    }
}
