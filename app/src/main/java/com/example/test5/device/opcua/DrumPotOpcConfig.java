package com.example.test5.device.opcua;



/** 滚筒锅 OPC UA 默认连接参数（局域网，与 PLC 实际配置一致）。 */

public final class DrumPotOpcConfig {



    /** 常见 OPC UA 端口；Endpoint 以 PLC 实际为准。 */

    public static final int DEFAULT_PORT = 4840;

    public static final String DEFAULT_HOST = "192.168.2.39";



    /**

     * 服务器接口根节点（UaExpert 实测：ns=4;i=1，Numeric）。

     * BrowseName：ns=4，「服务器接口_1」（注意中间有下划线，不是「服务器接口1」）。

     * 子变量多为 ns=4;i=2… 等数值 NodeId，需先浏览此节点加载映射。

     */

    public static final String SERVER_INTERFACE_BROWSE_NAME = "服务器接口_1";

    public static final String SERVER_INTERFACE_NODE_ID = "ns=4;i=1";

    public static final int DEFAULT_NAMESPACE_INDEX = 4;



    /** 锅点位：0 加菜/加料，1 炒菜，2 倒菜，3 洗锅 */

    public static final int POT_POS_FEED = 0;

    public static final int POT_POS_COOK = 1;

    public static final int POT_POS_SERVE = 2;

    public static final int POT_POS_WASH = 3;

    /** 投料调试默认小量参数（现场确认后再调大）。 */
    public static final int FEED_LIQUID_CHANNEL = 1;
    /** 液体1投料定时，单位与 PLC 一致（实测 ns=3;i=3006 多为毫秒）。 */
    public static final int FEED_LIQUID1_TIME = 500;
    public static final int FEED_LIQUID_WEIGHT = 10;
    public static final int FEED_SOLID_CHANNEL = 1;
    public static final int FEED_SOLID_TIME = 1;

    private DrumPotOpcConfig() {

    }



    public static String defaultEndpointUrl() {

        return "opc.tcp://" + DEFAULT_HOST + ":" + DEFAULT_PORT;

    }

}

