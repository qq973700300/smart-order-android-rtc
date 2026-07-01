package com.example.test5.device.tashi;

/** 塔石料仓 TCP 默认参数（与上位机 AppConfig.StockBinIP3 一致）。 */
public final class TashiConfig {

    public static final String DEFAULT_HOST = "192.168.2.80";
    public static final int DEFAULT_PORT = 10123;

    public static final String CMD_AOTT = "(AOTT)";
    public static final String CMD_CLEAR = "(CLEAR)";
    public static final String CMD_ONLINE = "(ONLINE)";
    public static final String CMD_VERSION = "(VERSION)";
    public static final String ACK = "(ACK)";
    public static final String NACK = "(NACK)";

    private TashiConfig() {
    }

    /** 三位出货码 → (SHIP:0S|NUM:xxx|ID:xxxxxxxx) */
    public static String buildShipCommand(String threeDigitCode) {
        if (threeDigitCode == null || threeDigitCode.length() != 3) {
            throw new IllegalArgumentException("出货码必须为 3 位数字");
        }
        String id = String.valueOf(System.currentTimeMillis() % 100_000_000L);
        while (id.length() < 8) {
            id = "0" + id;
        }
        return "(SHIP:0S|NUM:" + threeDigitCode + "|ID:" + id + ")";
    }
}
