package com.example.test5.recipe.flow;

/** 节点参数定义（如料仓码、脚本名、档位）。 */
public final class FlowParamDef {

    public final String key;
    public final String label;
    public final String defaultValue;

    public FlowParamDef(String key, String label, String defaultValue) {
        this.key = key;
        this.label = label;
        this.defaultValue = defaultValue != null ? defaultValue : "";
    }
}
