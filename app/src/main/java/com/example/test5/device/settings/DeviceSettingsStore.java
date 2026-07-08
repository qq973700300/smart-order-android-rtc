package com.example.test5.device.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.test5.device.opcua.DrumPotOpcConfig;
import com.example.test5.device.tashi.TashiConfig;

/** 料仓 / 滚筒等设备 IP 配置（SharedPreferences）。 */
public final class DeviceSettingsStore {

    private static final String PREFS = "device_settings";
    private static final String KEY_STOCK_BIN_HOST = "stock_bin_host";
    private static final String KEY_STOCK_BIN_PORT = "stock_bin_port";
    private static final String KEY_DRUM_POT_HOST = "drum_pot_host";
    private static final String KEY_DRUM_POT_PORT = "drum_pot_port";

    private DeviceSettingsStore() {
    }

    public static String getStockBinHost(Context context) {
        return prefs(context).getString(KEY_STOCK_BIN_HOST, TashiConfig.DEFAULT_HOST);
    }

    public static int getStockBinPort(Context context) {
        return prefs(context).getInt(KEY_STOCK_BIN_PORT, TashiConfig.DEFAULT_PORT);
    }

    public static void setStockBinHost(Context context, String host) {
        prefs(context).edit().putString(KEY_STOCK_BIN_HOST, host.trim()).apply();
    }

    public static void setStockBinPort(Context context, int port) {
        prefs(context).edit().putInt(KEY_STOCK_BIN_PORT, port).apply();
    }

    public static String getDrumPotHost(Context context) {
        return prefs(context).getString(KEY_DRUM_POT_HOST, DrumPotOpcConfig.DEFAULT_HOST);
    }

    public static int getDrumPotPort(Context context) {
        return prefs(context).getInt(KEY_DRUM_POT_PORT, DrumPotOpcConfig.DEFAULT_PORT);
    }

    public static void setDrumPotHost(Context context, String host) {
        prefs(context).edit().putString(KEY_DRUM_POT_HOST, host.trim()).apply();
    }

    public static void setDrumPotPort(Context context, int port) {
        prefs(context).edit().putInt(KEY_DRUM_POT_PORT, port).apply();
    }

    public static String getDrumPotEndpointUrl(Context context) {
        return "opc.tcp://" + getDrumPotHost(context) + ":" + getDrumPotPort(context);
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
