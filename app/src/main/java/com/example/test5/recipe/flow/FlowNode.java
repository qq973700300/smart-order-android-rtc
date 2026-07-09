package com.example.test5.recipe.flow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 画布上的一个步骤节点。 */
public final class FlowNode {

    public String id;
    public String deviceId;
    public String actionId;
    public String label;
    public float x;
    public float y;
    public Map<String, String> params = new LinkedHashMap<>();

    public static FlowNode create(FlowActionDef def, float x, float y) {
        FlowNode node = new FlowNode();
        node.id = newId();
        node.deviceId = def.device.id;
        node.actionId = def.actionId;
        node.label = def.label;
        node.x = x;
        node.y = y;
        node.params.putAll(FlowStepCatalog.defaultParams(def));
        return node;
    }

    public FlowDeviceType deviceType() {
        return FlowDeviceType.fromId(deviceId);
    }

    public String displayTitle() {
        FlowDeviceType device = deviceType();
        String deviceLabel = device != null ? device.label : deviceId;
        return deviceLabel + "\n" + label;
    }

    public String paramSummary() {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(entry.getValue());
        }
        return sb.toString();
    }

    public String deviceLabel() {
        FlowDeviceType device = deviceType();
        return device != null ? device.label : deviceId;
    }

    public FlowNode copy() {
        FlowNode copy = new FlowNode();
        copy.id = id;
        copy.deviceId = deviceId;
        copy.actionId = actionId;
        copy.label = label;
        copy.x = x;
        copy.y = y;
        copy.params = new LinkedHashMap<>(params);
        return copy;
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
