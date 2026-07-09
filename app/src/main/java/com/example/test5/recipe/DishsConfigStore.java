package com.example.test5.recipe;

import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** 读写 internal storage 中的 DishsConfig.xml（格式与 SSKJYingJiang 一致）。 */
public final class DishsConfigStore {

    private static final String ASSET_PATH = "config/DishsConfig.xml";
    private static final String FILE_NAME = "DishsConfig.xml";

    private static volatile List<DishsConfig> cache;

    private DishsConfigStore() {
    }

    public static synchronized List<DishsConfig> getAll(Context context) {
        ensureLoaded(context);
        return new ArrayList<>(cache);
    }

    public static synchronized List<DishsConfig> search(Context context, String keyword) {
        ensureLoaded(context);
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>(cache);
        }
        String key = keyword.trim();
        List<DishsConfig> result = new ArrayList<>();
        for (DishsConfig item : cache) {
            if (item.dishName != null && item.dishName.contains(key)) {
                result.add(item);
            }
        }
        return result;
    }

    public static synchronized DishsConfig findById(Context context, String id) {
        ensureLoaded(context);
        if (id == null) {
            return null;
        }
        for (DishsConfig item : cache) {
            if (id.equals(item.id)) {
                return item.copy();
            }
        }
        return null;
    }

    public static synchronized DishsConfig findByDishName(Context context, String dishName) {
        ensureLoaded(context);
        if (dishName == null || dishName.isEmpty()) {
            return null;
        }
        for (DishsConfig item : cache) {
            if (item.dishName != null && dishName.contains(item.dishName)) {
                return item.copy();
            }
        }
        for (DishsConfig item : cache) {
            if (item.dishName != null && item.dishName.contains(dishName)) {
                return item.copy();
            }
        }
        return null;
    }

    public static synchronized boolean isValidDishName(Context context, String dishName) {
        ensureLoaded(context);
        if (dishName == null || dishName.isEmpty()) {
            return false;
        }
        for (DishsConfig item : cache) {
            if (dishName.equals(item.dishName)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized void add(Context context, DishsConfig config) {
        ensureLoaded(context);
        DishsConfig item = config.copy();
        if (item.id == null || item.id.isEmpty()) {
            item.id = newId();
        }
        cache.add(item);
        save(context, cache);
    }

    public static synchronized void update(Context context, DishsConfig config) {
        ensureLoaded(context);
        for (int i = 0; i < cache.size(); i++) {
            if (config.id != null && config.id.equals(cache.get(i).id)) {
                cache.set(i, config.copy());
                save(context, cache);
                return;
            }
        }
    }

    public static synchronized void delete(Context context, String id) {
        ensureLoaded(context);
        if (id == null) {
            return;
        }
        cache.removeIf(item -> id.equals(item.id));
        save(context, cache);
    }

    public static synchronized void ensureLoaded(Context context) {
        if (cache != null) {
            return;
        }
        Context app = context.getApplicationContext();
        File file = getFile(app);
        if (!file.exists()) {
            copyAssetToFile(app, file);
        }
        cache = readFromFile(file);
    }

    public static synchronized void reload(Context context) {
        cache = null;
        ensureLoaded(context);
    }

    private static File getFile(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    private static void copyAssetToFile(Context context, File dest) {
        try (InputStream in = context.getAssets().open(ASSET_PATH);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (Exception e) {
            throw new IllegalStateException("无法复制默认菜谱: " + ASSET_PATH, e);
        }
    }

    private static List<DishsConfig> readFromFile(File file) {
        List<DishsConfig> list = new ArrayList<>();
        try (FileInputStream in = new FileInputStream(file)) {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(in, StandardCharsets.UTF_8.name());
            int event = parser.getEventType();
            DishsConfig current = null;
            String tag = null;
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    tag = parser.getName();
                    if ("DishsConfig".equals(tag)) {
                        current = new DishsConfig();
                    } else if (current != null) {
                        String text = readText(parser);
                        applyField(current, tag, text);
                    }
                } else if (event == XmlPullParser.END_TAG && "DishsConfig".equals(parser.getName()) && current != null) {
                    if (current.id == null || current.id.isEmpty()) {
                        current.id = newId();
                    }
                    list.add(current);
                    current = null;
                }
                event = parser.next();
            }
        } catch (Exception e) {
            throw new IllegalStateException("读取菜谱失败: " + file.getAbsolutePath(), e);
        }
        return list;
    }

    private static String readText(XmlPullParser parser) throws Exception {
        if (parser.next() != XmlPullParser.TEXT) {
            return "";
        }
        return parser.getText() != null ? parser.getText().trim() : "";
    }

    private static void applyField(DishsConfig item, String tag, String text) {
        switch (tag) {
            case "Id":
                item.id = text;
                break;
            case "DishName":
                item.dishName = text;
                break;
            case "DishLocation":
                item.dishLocation = text;
                break;
            case "StatusType":
                item.statusType = parseBool(text);
                break;
            case "OutWater":
                item.outWater = parseInt(text);
                break;
            case "OutOil":
                item.outOil = parseInt(text);
                break;
            case "OutSalt":
                item.outSalt = parseInt(text);
                break;
            case "OutShengSauce":
                item.outShengSauce = parseInt(text);
                break;
            case "OutLaoSauce":
                item.outLaoSauce = parseInt(text);
                break;
            case "OutVinegar":
                item.outVinegar = parseInt(text);
                break;
            case "FriedTime":
                item.friedTime = parseInt(text);
                break;
            case "WhiteSugar":
                item.whiteSugar = parseInt(text);
                break;
            case "OldsoySauce":
                item.oldsoySauce = parseInt(text);
                break;
            default:
                break;
        }
    }

    private static void save(Context context, List<DishsConfig> list) {
        File file = getFile(context.getApplicationContext());
        File temp = new File(file.getAbsolutePath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            XmlSerializer serializer = Xml.newSerializer();
            serializer.setOutput(out, StandardCharsets.UTF_8.name());
            serializer.startDocument(StandardCharsets.UTF_8.name(), true);
            serializer.startTag(null, "Data");
            for (DishsConfig item : list) {
                writeItem(serializer, item);
            }
            serializer.endTag(null, "Data");
            serializer.endDocument();
            serializer.flush();
        } catch (Exception e) {
            throw new IllegalStateException("保存菜谱失败", e);
        }
        if (file.exists() && !file.delete()) {
            throw new IllegalStateException("无法覆盖旧菜谱文件");
        }
        if (!temp.renameTo(file)) {
            throw new IllegalStateException("无法写入菜谱文件");
        }
    }

    private static void writeItem(XmlSerializer serializer, DishsConfig item) throws Exception {
        serializer.startTag(null, "DishsConfig");
        writeTag(serializer, "Id", item.id);
        writeTag(serializer, "DishName", item.dishName);
        writeTag(serializer, "DishLocation", item.dishLocation);
        writeTag(serializer, "StatusType", item.statusType ? "True" : "False");
        writeTag(serializer, "OutWater", String.valueOf(item.outWater));
        writeTag(serializer, "OutOil", String.valueOf(item.outOil));
        writeTag(serializer, "OutSalt", String.valueOf(item.outSalt));
        writeTag(serializer, "OutShengSauce", String.valueOf(item.outShengSauce));
        writeTag(serializer, "OutLaoSauce", String.valueOf(item.outLaoSauce));
        writeTag(serializer, "OutVinegar", String.valueOf(item.outVinegar));
        writeTag(serializer, "FriedTime", String.valueOf(item.friedTime));
        writeTag(serializer, "WhiteSugar", String.valueOf(item.whiteSugar));
        writeTag(serializer, "OldsoySauce", String.valueOf(item.oldsoySauce));
        serializer.endTag(null, "DishsConfig");
    }

    private static void writeTag(XmlSerializer serializer, String name, String value) throws Exception {
        serializer.startTag(null, name);
        serializer.text(value != null ? value : "");
        serializer.endTag(null, name);
    }

    private static boolean parseBool(String text) {
        if (text == null) {
            return false;
        }
        String v = text.trim().toLowerCase(Locale.US);
        return "true".equals(v) || "1".equals(v);
    }

    private static int parseInt(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.US);
    }
}
