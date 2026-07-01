package com.example.test5.lebai;

/** 乐白机械臂默认连接参数（与英江上位机 AppConfig.LebaiRobot 一致，可在调试页修改）。 */
public final class LebaiConfig {

    /** 真机 JSON-RPC HTTP 端口；模拟环境一般为 3020。 */
    public static final int DEFAULT_HTTP_PORT = 3021;

    public static final String DEFAULT_HOST = "192.168.2.190";

    /** 上位机 RobotScene 中使用的场景 ID */
    public static final int SCENE_PREP = 10354;
    public static final int SCENE_PICKUP = 10349;
    public static final int SCENE_DELIVERY = 10350;
    public static final int SCENE_CHARGE = 10353;

    private LebaiConfig() {
    }
}
