package com.example.test5.recipe.flow;

/** 流程编辑器支持的三种设备。 */
public enum FlowDeviceType {
    STOCK_BIN("stock_bin", "料仓"),
    YUEJIANG("yuejiang", "越疆机械臂"),
    DRUM_POT("drum_pot", "滚筒锅"),
    FLOW_CONTROL("flow_control", "流程控制");

    public final String id;
    public final String label;

    FlowDeviceType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public static FlowDeviceType fromId(String id) {
        if (id == null) {
            return null;
        }
        for (FlowDeviceType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
