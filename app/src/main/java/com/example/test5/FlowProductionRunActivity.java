package com.example.test5;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.order.FlowProductionLauncher;
import com.example.test5.order.OrderSubmitDialogs;
import com.example.test5.order.RestaurantFunctionHandler;
import com.example.test5.recipe.flow.FlowNode;
import com.example.test5.recipe.flow.FlowTimeEstimate;
import com.example.test5.recipe.flow.RecipeFlow;
import com.example.test5.ui.flow.FlowCanvasView;
import com.example.test5.ui.flow.FlowProductionUi;
import com.example.test5.ui.flow.FlowStepErrorHandler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/** 送厨时展示流程图并实时高亮当前步骤。 */
public class FlowProductionRunActivity extends AppCompatActivity
        implements FlowProductionUi, FlowStepErrorHandler {

    private RestaurantFunctionHandler orderHandler;
    private FlowCanvasView canvas;
    private TextView dishNameView;
    private TextView stepTextView;
    private TextView timerView;
    private TextView fallbackTextView;
    private MaterialToolbar toolbar;

    private String currentDishName = "";
    private int flowRemainingSeconds;
    private int stepRemainingSeconds;
    private boolean submitStarted;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow_production_run);

        orderHandler = FlowProductionLauncher.consumeHandler();
        if (orderHandler == null) {
            finish();
            return;
        }

        toolbar = findViewById(R.id.flow_run_toolbar);
        dishNameView = findViewById(R.id.flow_run_dish_name);
        stepTextView = findViewById(R.id.flow_run_step_text);
        timerView = findViewById(R.id.flow_run_timer_text);
        fallbackTextView = findViewById(R.id.flow_run_fallback_text);
        canvas = findViewById(R.id.flow_run_canvas);

        toolbar.setNavigationOnClickListener(v -> finish());
        canvas.setRunMode(true);
        canvas.post(canvas::fitFlowInView);
        updateTimerText();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (submitStarted || orderHandler == null) {
            return;
        }
        submitStarted = true;
        orderHandler.setFlowProductionUi(this);
        orderHandler.setFlowStepErrorHandler(this);
        orderHandler.setSubmitListener((success, message, summary) -> runOnUiThread(() -> {
            orderHandler.setFlowProductionUi(null);
            orderHandler.setFlowStepErrorHandler(null);
            OrderSubmitDialogs.show(this, success, message, summary);
            finish();
        }));
        executor.execute(() -> orderHandler.execute(
                RestaurantFunctionHandler.TOOL_SUBMIT_ORDER,
                "{}"
        ));
    }

    @Override
    protected void onDestroy() {
        if (orderHandler != null) {
            orderHandler.setFlowProductionUi(null);
            orderHandler.setFlowStepErrorHandler(null);
        }
        executor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onDishStarted(String dishName) {
        runOnUiThread(() -> {
            currentDishName = dishName;
            dishNameView.setText(dishName);
            stepTextView.setText(R.string.flow_run_preparing);
            stepRemainingSeconds = 0;
            canvas.clearExecutionState();
            updateTimerText();
        });
    }

    @Override
    public void onDishFlowReady(String dishName, @Nullable RecipeFlow flow) {
        runOnUiThread(() -> {
            flowRemainingSeconds = FlowTimeEstimate.totalWaitSeconds(flow);
            stepRemainingSeconds = 0;
            updateTimerText();
            if (flow != null && !flow.nodes.isEmpty()) {
                canvas.setVisibility(View.VISIBLE);
                fallbackTextView.setVisibility(View.GONE);
                canvas.setFlow(flow);
                canvas.clearExecutionState();
                canvas.post(canvas::fitFlowInView);
            } else {
                canvas.setVisibility(View.GONE);
                fallbackTextView.setVisibility(View.VISIBLE);
                fallbackTextView.setText(getString(R.string.flow_run_no_flow, dishName));
            }
        });
    }

    @Override
    public void onStepStarted(String dishName, @Nullable FlowNode node, int index, int total, String message) {
        runOnUiThread(() -> {
            stepTextView.setText(formatStep(index, total, message));
            if (node != null) {
                canvas.setNodeActive(node.id, true);
            }
        });
    }

    @Override
    public void onStepFinished(
            String dishName,
            @Nullable FlowNode node,
            int index,
            int total,
            boolean success,
            String message
    ) {
        runOnUiThread(() -> {
            if (node == null) {
                stepTextView.setText(message);
                stepRemainingSeconds = 0;
                updateTimerText();
                return;
            }
            canvas.setNodeActive(node.id, false);
            if (success) {
                canvas.markNodeCompleted(node.id);
                int waitSec = FlowTimeEstimate.nodeWaitSeconds(node);
                if (waitSec > 0) {
                    flowRemainingSeconds = Math.max(0, flowRemainingSeconds - waitSec);
                }
            } else if (!message.startsWith("已跳过")) {
                canvas.markNodeFailed(node.id);
                stepTextView.setText(getString(R.string.flow_run_step_failed, node.label, message));
            } else {
                canvas.markNodeCompleted(node.id);
            }
            stepRemainingSeconds = 0;
            updateTimerText();
        });
    }

    @Override
    public void onDishFinished(String dishName, boolean success, String message) {
        runOnUiThread(() -> {
            stepRemainingSeconds = 0;
            if (success) {
                stepTextView.setText(getString(R.string.flow_run_dish_done, dishName));
            } else if (currentDishName.equals(dishName)) {
                stepTextView.setText(message);
            }
            updateTimerText();
        });
    }

    @Override
    public void onWaitTick(String dishName, FlowNode node, int remainingSeconds, int totalSeconds) {
        runOnUiThread(() -> {
            stepRemainingSeconds = remainingSeconds;
            onRemainingSeconds(dishName, flowRemainingSeconds);
        });
    }

    @Override
    public void onRemainingSeconds(String dishName, int remainingSeconds) {
        runOnUiThread(() -> {
            flowRemainingSeconds = remainingSeconds;
            updateTimerText();
        });
    }

    @Override
    public Decision onStepFailed(@Nullable FlowNode node, String errorMessage) {
        AtomicReference<Decision> ref = new AtomicReference<>(Decision.STOP);
        CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            String stepName = node != null ? node.label : getString(R.string.flow_run_unknown_step);
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.flow_run_step_error_title)
                    .setMessage(getString(R.string.flow_run_step_error_message, stepName, errorMessage))
                    .setCancelable(false)
                    .setPositiveButton(R.string.flow_run_skip_step, (d, w) -> {
                        ref.set(Decision.SKIP);
                        latch.countDown();
                    })
                    .setNegativeButton(R.string.flow_run_stop_flow, (d, w) -> {
                        ref.set(Decision.STOP);
                        latch.countDown();
                    })
                    .show();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ref.get();
    }

    private void updateTimerText() {
        if (stepRemainingSeconds > 0) {
            timerView.setText(getString(
                    R.string.flow_run_timer_step,
                    formatDuration(stepRemainingSeconds),
                    formatDuration(flowRemainingSeconds)));
        } else if (flowRemainingSeconds > 0) {
            timerView.setText(getString(
                    R.string.flow_run_timer_flow,
                    formatDuration(flowRemainingSeconds)));
        } else {
            timerView.setText(R.string.flow_run_timer_none);
        }
    }

    private static String formatDuration(int seconds) {
        int min = seconds / 60;
        int sec = seconds % 60;
        return String.format(Locale.getDefault(), "%d:%02d", min, sec);
    }

    private String formatStep(int index, int total, String message) {
        if (total > 0) {
            return getString(R.string.flow_test_progress, index, total, message);
        }
        return message;
    }
}
