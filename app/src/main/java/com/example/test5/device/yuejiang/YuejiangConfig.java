package com.example.test5.device.yuejiang;

/** 越疆（Dobot）机械臂 TCP 默认参数（与上位机 AppConfig.RobotArmIP2 一致）。 */
public final class YuejiangConfig {

    public static final String DEFAULT_HOST = "192.168.2.52";
    public static final int DEFAULT_PORT = 29999;

    private YuejiangConfig() {
    }

    public static String clearError() {
        return "ClearError()";
    }

    public static String emergencyStop() {
        return "EmergencyStop()";
    }

    public static String resetRobot() {
        return "ResetRobot()";
    }

    public static String powerOn() {
        return "PowerOn()";
    }

    public static String robotMode() {
        return "RobotMode()";
    }

    public static String enableRobot() {
        return "EnableRobot()";
    }

    public static String runScript(String scriptName) {
        return "RunScript(" + scriptName + ")";
    }

    /** 与上位机 RobotArmType 枚举名一致 */
    public static final String SCRIPT_YUANDIAN = "yuandian";
    public static final String SCRIPT_ZHUMIANJI = "zhumianji";
    public static final String SCRIPT_NAWAN = "nawan";
    public static final String SCRIPT_CCJCS = "ccjcs";
    public static final String SCRIPT_CCJCF1 = "ccjcf1";
    public static final String SCRIPT_CCJCF2 = "ccjcf2";
    public static final String SCRIPT_CCJHYD = "ccjhyd";
}
