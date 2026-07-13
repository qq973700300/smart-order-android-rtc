package com.example.test5.recipe.flow;

/** 估算流程中等待类步骤的总时长。 */
public final class FlowTimeEstimate {

    private FlowTimeEstimate() {
    }

    public static int totalWaitSeconds(RecipeFlow flow) {
        if (flow == null) {
            return 0;
        }
        int total = 0;
        for (FlowNode node : flow.nodes) {
            total += nodeWaitSeconds(node);
        }
        return total;
    }

    public static int nodeWaitSeconds(FlowNode node) {
        if (node == null || node.actionId == null) {
            return 0;
        }
        if ("delay".equals(node.actionId) || "wait".equals(node.actionId)) {
            return parseSeconds(node.params != null ? node.params.get("seconds") : null);
        }
        return 0;
    }

    private static int parseSeconds(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(text.trim()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
