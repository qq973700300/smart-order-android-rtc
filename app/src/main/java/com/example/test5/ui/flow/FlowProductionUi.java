package com.example.test5.ui.flow;

import androidx.annotation.Nullable;

import com.example.test5.recipe.flow.FlowNode;
import com.example.test5.recipe.flow.RecipeFlow;

/** 送厨/试跑时流程运行界面回调。 */
public interface FlowProductionUi {

    void onDishStarted(String dishName);

    void onDishFlowReady(String dishName, @Nullable RecipeFlow flow);

    void onStepStarted(String dishName, @Nullable FlowNode node, int index, int total, String message);

    void onStepFinished(String dishName, @Nullable FlowNode node, int index, int total, boolean success, String message);

    void onDishFinished(String dishName, boolean success, String message);

    void onWaitTick(String dishName, FlowNode node, int remainingSeconds, int totalSeconds);

    void onRemainingSeconds(String dishName, int remainingSeconds);
}
