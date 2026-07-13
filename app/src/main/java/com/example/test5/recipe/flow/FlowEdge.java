package com.example.test5.recipe.flow;

/** 节点之间的连线：from 完成后执行 to。 */
public final class FlowEdge {

    public String fromNodeId;
    public String toNodeId;
    /** left / right / top / bottom，可为空（绘制时自动推断）。 */
    public String fromPort;
    public String toPort;

    public FlowEdge() {
    }

    public FlowEdge(String fromNodeId, String toNodeId) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
    }

    public FlowEdge(String fromNodeId, String fromPort, String toNodeId, String toPort) {
        this.fromNodeId = fromNodeId;
        this.fromPort = fromPort;
        this.toNodeId = toNodeId;
        this.toPort = toPort;
    }
}
