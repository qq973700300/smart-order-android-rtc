package com.example.test5.device.opcua;

/** 滚筒锅 OPC UA 默认连接参数（局域网，与 PLC 实际配置一致）。 */
public final class DrumPotOpcConfig {

    /** 常见 OPC UA 端口；Endpoint 以 PLC 实际为准。 */
    public static final int DEFAULT_PORT = 4840;
    public static final String DEFAULT_HOST = "192.168.2.60";

    /**
     * 服务器接口根节点（UaExpert 实测：ns=4;i=1，Numeric）。
     * BrowseName：ns=4，「服务器接口_1」（注意中间有下划线，不是「服务器接口1」）。
     */
    public static final String SERVER_INTERFACE_BROWSE_NAME = "服务器接口_1";
    public static final String SERVER_INTERFACE_NODE_ID = "ns=4;i=1";
    public static final int DEFAULT_NAMESPACE_INDEX = 4;

    /** 锅点位：0 加菜/加料，1 炒菜，2 倒菜，3 洗锅 */
    public static final int POT_POS_FEED = 0;
    public static final int POT_POS_COOK = 1;
    public static final int POT_POS_SERVE = 2;
    public static final int POT_POS_WASH = 3;

    /** 与上位机 AppConfig / Modbus 滚筒轴位置一致（Float）。 */
    public static final float AXIS_POS_FEED = -25f;
    public static final float AXIS_POS_COOK = 10f;
    public static final float AXIS_POS_SERVE = 53f;
    public static final float AXIS_POS_WASH = 90f;

    /** OPC UA 绝对定位：写目标位置 + 脉冲触发。 */
    public static final String BROWSE_ABS_POSITION_VALUE = "绝对定位位置";
    public static final String BROWSE_ABS_POSITION_TRIGGER = "绝对定位";
    public static final String NODE_ABS_POSITION_VALUE = "ns=4;i=276";
    public static final String NODE_ABS_POSITION_TRIGGER = "ns=4;i=287";

    /** 只读：轴当前实际位置（BrowseName，NodeId 由 browse 加载）。 */
    public static final String BROWSE_CURRENT_POSITION = "当前位置";
    /** 定位完成标志（与 Modbus 寄存器 41「滚筒轴绝度定位完成」对应，名称以 PLC 为准）。 */
    public static final String BROWSE_ABS_POSITION_DONE = "绝对定位完成";
    public static final String BROWSE_ABS_POSITION_DONE_ALT = "绝度定位完成";

    /** 当前位置与目标差值在此范围内视为到位。 */
    public static final float POSITION_TOLERANCE = 2.0f;

    /** 绝对定位等待超时（与上位机 SetDrumPotLocation 30s 一致）。 */
    public static final long ABS_MOVE_TIMEOUT_MS = 30_000L;
    public static final long ABS_MOVE_POLL_MS = 500L;

    /** 投料调试默认小量参数（现场确认后再调大）。 */
    public static final int FEED_LIQUID_CHANNEL = 1;
    public static final int FEED_LIQUID1_TIME = 500;
    public static final int FEED_SOLID_CHANNEL = 1;
    public static final int FEED_SOLID_TIME = 1;

    private DrumPotOpcConfig() {
    }

    public static String defaultEndpointUrl() {
        return "opc.tcp://" + DEFAULT_HOST + ":" + DEFAULT_PORT;
    }
}
