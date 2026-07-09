package com.example.test5.recipe.flow;

import java.util.ArrayList;
import java.util.List;

/** 一条自定义菜谱对应的设备流程。 */
public final class RecipeFlow {

    public String recipeId;
    public String dishName;
    public final List<FlowNode> nodes = new ArrayList<>();
    public final List<FlowEdge> edges = new ArrayList<>();

    public RecipeFlow copy() {
        RecipeFlow copy = new RecipeFlow();
        copy.recipeId = recipeId;
        copy.dishName = dishName;
        for (FlowNode node : nodes) {
            copy.nodes.add(node.copy());
        }
        for (FlowEdge edge : edges) {
            copy.edges.add(new FlowEdge(edge.fromNodeId, edge.toNodeId));
        }
        return copy;
    }
}
