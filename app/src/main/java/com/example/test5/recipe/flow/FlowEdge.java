package com.example.test5.recipe.flow;

/** 节点之间的连线：from 完成后执行 to。 */
public final class FlowEdge {

    public String fromNodeId;
    public String toNodeId;

    public FlowEdge() {
    }

    public FlowEdge(String fromNodeId, String toNodeId) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
    }
}
