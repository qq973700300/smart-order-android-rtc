package com.example.test5.recipe.flow;

import java.util.Collections;
import java.util.List;

/** 单个可拖入画布的原子动作定义。 */
public final class FlowActionDef {

    public final FlowDeviceType device;
    public final String actionId;
    public final String label;
    public final List<FlowParamDef> params;

    public FlowActionDef(FlowDeviceType device, String actionId, String label, List<FlowParamDef> params) {
        this.device = device;
        this.actionId = actionId;
        this.label = label;
        this.params = params == null ? Collections.emptyList() : params;
    }

    public String displayTitle() {
        return device.label + " · " + label;
    }
}
