package com.example.test5.ui.flow;

import com.example.test5.recipe.flow.FlowNode;

/** 流程步骤失败时由 UI 决定跳过或停止。 */
public interface FlowStepErrorHandler {

    enum Decision {
        SKIP,
        STOP
    }

    /** 在工作线程调用，阻塞直到用户在界面选择。 */
    Decision onStepFailed(FlowNode node, String errorMessage);
}
