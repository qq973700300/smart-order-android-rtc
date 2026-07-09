package com.example.test5.order.mq;

/** RabbitMQ 默认参数（与 SSKJYingJiang AppConfig 一致）。 */
public final class MqConfig {

    public static final String DEFAULT_HOST = "36.133.99.107";
    public static final int DEFAULT_PORT = 5672;
    public static final String DEFAULT_USER = "ssycp";
    public static final String DEFAULT_PASS = "ssycp";
    public static final String DEFAULT_QUEUE = "bantian_queue";
    /** 与 C# 日志 EquipmentNum 一致；留空则接收全部设备订单。 */
    public static final String DEFAULT_EQUIPMENT_NUM = "SH_CCJ_LZD_BDCFA254-";

    private MqConfig() {
    }
}
