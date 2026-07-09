package com.example.test5.order.mq;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.test5.order.StoreConfig;

/** MQ 与门店设备参数（SharedPreferences）。 */
public final class MqSettingsStore {

    private static final String PREFS = "mq_settings";
    private static final String KEY_HOST = "mq_host";
    private static final String KEY_PORT = "mq_port";
    private static final String KEY_USER = "mq_user";
    private static final String KEY_PASS = "mq_pass";
    private static final String KEY_QUEUE = "mq_queue";
    private static final String KEY_EQUIPMENT_NUM = "equipment_num";
    private static final String KEY_AUTO_PRODUCE = "auto_produce";
    private static final String KEY_AUTO_PRODUCE_POLICY = "auto_produce_policy_v2";

    private MqSettingsStore() {
    }

    public static String getHost(Context context) {
        return prefs(context).getString(KEY_HOST, MqConfig.DEFAULT_HOST);
    }

    public static int getPort(Context context) {
        return prefs(context).getInt(KEY_PORT, MqConfig.DEFAULT_PORT);
    }

    public static String getUser(Context context) {
        return prefs(context).getString(KEY_USER, MqConfig.DEFAULT_USER);
    }

    public static String getPass(Context context) {
        return prefs(context).getString(KEY_PASS, MqConfig.DEFAULT_PASS);
    }

    public static String getQueue(Context context) {
        return prefs(context).getString(KEY_QUEUE, MqConfig.DEFAULT_QUEUE);
    }

    public static String getEquipmentNum(Context context) {
        return prefs(context).getString(KEY_EQUIPMENT_NUM, MqConfig.DEFAULT_EQUIPMENT_NUM);
    }

    public static boolean isAutoProduce(Context context) {
        return prefs(context).getBoolean(KEY_AUTO_PRODUCE, false);
    }

    /** 产线流程未就绪时默认只收单不取料；已安装设备升级后一次性关闭自动取料。 */
    public static void ensurePolicyDefaults(Context context) {
        SharedPreferences p = prefs(context);
        if (p.contains(KEY_AUTO_PRODUCE_POLICY)) {
            return;
        }
        p.edit()
                .putBoolean(KEY_AUTO_PRODUCE, false)
                .putBoolean(KEY_AUTO_PRODUCE_POLICY, true)
                .apply();
    }

    public static int getStoreId(Context context) {
        return StoreConfig.STORE_ID;
    }

    public static String getStoreTopic(Context context) {
        return StoreConfig.STORE_NAME;
    }

    public static void setHost(Context context, String host) {
        prefs(context).edit().putString(KEY_HOST, host.trim()).apply();
    }

    public static void setPort(Context context, int port) {
        prefs(context).edit().putInt(KEY_PORT, port).apply();
    }

    public static void setUser(Context context, String user) {
        prefs(context).edit().putString(KEY_USER, user.trim()).apply();
    }

    public static void setPass(Context context, String pass) {
        prefs(context).edit().putString(KEY_PASS, pass).apply();
    }

    public static void setQueue(Context context, String queue) {
        prefs(context).edit().putString(KEY_QUEUE, queue.trim()).apply();
    }

    public static void setEquipmentNum(Context context, String equipmentNum) {
        prefs(context).edit().putString(KEY_EQUIPMENT_NUM, equipmentNum.trim()).apply();
    }

    public static void setAutoProduce(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_AUTO_PRODUCE, enabled).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
