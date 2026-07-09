package com.example.test5.recipe.flow;

import com.example.test5.device.opcua.DrumPotOpcMaterials;
import com.example.test5.device.yuejiang.YuejiangConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 三种设备可拆分的原子动作目录。 */
public final class FlowStepCatalog {

    private static final List<FlowActionDef> ALL;

    static {
        List<FlowActionDef> list = new ArrayList<>();
        list.add(new FlowActionDef(
                FlowDeviceType.STOCK_BIN,
                "pick",
                "出货取料",
                Collections.singletonList(new FlowParamDef("slot_code", "料仓位置码", "150"))));

        list.add(new FlowActionDef(
                FlowDeviceType.YUEJIANG,
                "run_script",
                "运行脚本 ccjcf1",
                Collections.singletonList(new FlowParamDef("script", "脚本名", YuejiangConfig.SCRIPT_CCJCF1))));
        list.add(new FlowActionDef(
                FlowDeviceType.YUEJIANG,
                "run_script",
                "运行脚本 ccjcf2",
                Collections.singletonList(new FlowParamDef("script", "脚本名", YuejiangConfig.SCRIPT_CCJCF2))));
        list.add(new FlowActionDef(
                FlowDeviceType.YUEJIANG,
                "run_script",
                "运行脚本 yuandian",
                Collections.singletonList(new FlowParamDef("script", "脚本名", YuejiangConfig.SCRIPT_YUANDIAN))));
        list.add(new FlowActionDef(
                FlowDeviceType.YUEJIANG,
                "run_script",
                "运行脚本 zhumianji",
                Collections.singletonList(new FlowParamDef("script", "脚本名", YuejiangConfig.SCRIPT_ZHUMIANJI))));
        list.add(new FlowActionDef(
                FlowDeviceType.YUEJIANG,
                "run_script",
                "运行脚本 nawan",
                Collections.singletonList(new FlowParamDef("script", "脚本名", YuejiangConfig.SCRIPT_NAWAN))));
        list.add(new FlowActionDef(
                FlowDeviceType.YUEJIANG,
                "run_script",
                "运行脚本 ccjcs",
                Collections.singletonList(new FlowParamDef("script", "脚本名", YuejiangConfig.SCRIPT_CCJCS))));

        for (DrumPotOpcMaterials.Def material : DrumPotOpcMaterials.all()) {
            list.add(new FlowActionDef(
                    FlowDeviceType.DRUM_POT,
                    material.actionId,
                    material.label,
                    Collections.singletonList(new FlowParamDef(
                            "amount_ms", "出料时间(ms)", DrumPotOpcMaterials.defaultAmount(material)))));
        }

        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "heat_start",
                "加热启动",
                Collections.singletonList(new FlowParamDef("gear", "加热档位 0-3", "2"))));
        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "heat_stop",
                "加热停止",
                Collections.emptyList()));
        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "start",
                "启动",
                Collections.emptyList()));
        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "stop",
                "停止",
                Collections.emptyList()));
        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "reset",
                "复位",
                Collections.emptyList()));
        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "rotate_start",
                "开始旋转",
                Collections.singletonList(new FlowParamDef("gear", "转速档位 0-3", "1"))));
        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "rotate_stop",
                "停止旋转",
                Collections.emptyList()));
        list.add(new FlowActionDef(
                FlowDeviceType.DRUM_POT,
                "wait",
                "炒锅等待（秒）",
                Collections.singletonList(new FlowParamDef("seconds", "等待秒数", "65"))));

        list.add(new FlowActionDef(
                FlowDeviceType.FLOW_CONTROL,
                "join",
                "汇合等待",
                Collections.emptyList()));
        list.add(new FlowActionDef(
                FlowDeviceType.FLOW_CONTROL,
                "delay",
                "等待（秒）",
                Collections.singletonList(new FlowParamDef("seconds", "等待秒数", "5"))));
        ALL = Collections.unmodifiableList(list);
    }

    private FlowStepCatalog() {
    }

    public static List<FlowActionDef> all() {
        return ALL;
    }

    public static List<FlowActionDef> forDevice(FlowDeviceType device) {
        List<FlowActionDef> result = new ArrayList<>();
        for (FlowActionDef def : ALL) {
            if (def.device == device) {
                result.add(def);
            }
        }
        return result;
    }

    public static FlowActionDef find(String deviceId, String actionId, String label) {
        for (FlowActionDef def : ALL) {
            if (def.device.id.equals(deviceId)
                    && def.actionId.equals(actionId)
                    && (label == null || def.label.equals(label))) {
                return def;
            }
        }
        return null;
    }

    public static Map<String, String> defaultParams(FlowActionDef def) {
        Map<String, String> map = new LinkedHashMap<>();
        for (FlowParamDef param : def.params) {
            map.put(param.key, param.defaultValue);
        }
        return map;
    }

    public static RecipeFlow createDefaultTemplate() {
        RecipeFlow flow = new RecipeFlow();
        // 并行示例：取料 || 预热锅 → 汇合 → 机械臂 → 加油 → 炒菜
        FlowNode pick = FlowNode.create(findAction(FlowDeviceType.STOCK_BIN, "pick", "出货取料"), 40f, 80f);
        FlowNode heat = FlowNode.create(findAction(FlowDeviceType.DRUM_POT, "heat_start", "加热启动"), 340f, 80f);
        FlowNode join = FlowNode.create(findAction(FlowDeviceType.FLOW_CONTROL, "join", "汇合等待"), 190f, 240f);
        FlowNode arm = FlowNode.create(findAction(FlowDeviceType.YUEJIANG, "run_script", "运行脚本 ccjcf2"), 190f, 380f);
        FlowNode oil = FlowNode.create(findAction(FlowDeviceType.DRUM_POT, "discharge_oil", "加油"), 190f, 520f);
        FlowNode rotate = FlowNode.create(findAction(FlowDeviceType.DRUM_POT, "rotate_start", "开始旋转"), 190f, 660f);
        FlowNode wait = FlowNode.create(findAction(FlowDeviceType.DRUM_POT, "wait", "炒锅等待（秒）"), 190f, 800f);
        FlowNode heatStop = FlowNode.create(findAction(FlowDeviceType.DRUM_POT, "heat_stop", "加热停止"), 190f, 940f);
        flow.nodes.addAll(Arrays.asList(pick, heat, join, arm, oil, rotate, wait, heatStop));
        flow.edges.add(new FlowEdge(pick.id, join.id));
        flow.edges.add(new FlowEdge(heat.id, join.id));
        flow.edges.add(new FlowEdge(join.id, arm.id));
        flow.edges.add(new FlowEdge(arm.id, oil.id));
        flow.edges.add(new FlowEdge(oil.id, rotate.id));
        flow.edges.add(new FlowEdge(rotate.id, wait.id));
        flow.edges.add(new FlowEdge(wait.id, heatStop.id));
        return flow;
    }

    private static FlowActionDef findAction(FlowDeviceType device, String actionId, String label) {
        FlowActionDef def = find(device.id, actionId, label);
        if (def == null) {
            throw new IllegalStateException("缺少默认动作: " + device.label + " " + label);
        }
        return def;
    }
}
