package com.example.test5.device.modbus;

/** 滚筒锅/炒菜机 Modbus 默认参数（与上位机 AppConfig.FriedDishIP 一致）。 */
public final class DrumPotConfig {

    public static final String DEFAULT_HOST = "192.168.2.107";
    public static final int DEFAULT_PORT = 502;
    public static final int DEFAULT_UNIT_ID = 1;

    /** 与 AppConfig 位置常量一致 */
    public static final int LOCATION_FEED = -25;
    public static final int LOCATION_COOK = 10;
    public static final int LOCATION_SERVE = 53;
    public static final int LOCATION_WASH = 90;

    private DrumPotConfig() {
    }
}
