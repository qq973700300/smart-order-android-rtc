package com.example.test5.device.opcua;

/**
 * 文档《十套滚桶锅OPC》BrowseName 常量。
 * NodeId 一般为 ns={index};s={BrowseName}，命名空间索引在调试页配置。
 */
public final class DrumPotOpcNodes {

    public static final String START = "启动";
    public static final String STOP = "停止";
    public static final String RESET = "复位";

    public static final String POT_POSITION = "锅点位";
    public static final String POT_POSITION_START = "锅点位启动";
    public static final String POT_POSITION_RUNNING = "锅点位运行中";

    public static final String WASH_START = "洗锅启动";
    public static final String WASHING = "洗锅中";

    public static final String ROTATE_SPEED_GEAR = "转速控制档位";
    public static final String ROTATE_START = "锅旋转启动";
    public static final String ROTATE_STOP = "锅旋转停止";
    public static final String MOTOR_DIRECTION = "电机正反转";

    public static final String AUTO_RUNNING = "自动运行中";
    public static final String AXIS_CURRENT_POSITION = "轴当前位置";
    public static final String WASH_TIME = "洗锅定时时间";
    public static final String BLOW_TIME = "吹锅定时时间";

    public static final String HEAT_GEAR = "加热档位";
    public static final String HEAT_START = "加热启动";
    public static final String HEAT_STOP = "加热停止";

    public static final String LIQUID_WEIGHT = "液体投料重量";
    public static final String LIQUID_SELECT = "液体选择";
    public static final String LIQUID_START = "液体投料启动";
    public static final String LIQUID1_TIMER = "液体1投料定时";

    public static final String SOLID_TIME = "固体投料时间";
    public static final String SOLID_SELECT = "固体选择";
    public static final String SOLID_START = "固体投料启动";
    public static final String SOLID1_TIMER = "固1定时时间";

    public static final String EXHAUST_ON = "抽油烟打开";
    public static final String EXHAUST_OFF = "抽油烟关闭";

    private DrumPotOpcNodes() {
    }
}
