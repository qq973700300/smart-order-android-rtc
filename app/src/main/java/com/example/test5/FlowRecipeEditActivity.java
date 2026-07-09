package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigStore;
import com.example.test5.recipe.flow.FlowActionDef;
import com.example.test5.recipe.flow.FlowDeviceType;
import com.example.test5.recipe.flow.FlowNode;
import com.example.test5.recipe.flow.FlowParamDef;
import com.example.test5.recipe.flow.FlowStepCatalog;
import com.example.test5.recipe.flow.RecipeFlow;
import com.example.test5.recipe.flow.RecipeFlowExecutor;
import com.example.test5.recipe.flow.RecipeFlowStore;
import com.example.test5.ui.flow.FlowCanvasView;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 自定义菜谱流程编排：料仓 / 越疆机械臂 / 滚筒锅拖拽连线。 */
public class FlowRecipeEditActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_ID = "extra_recipe_id";

    private TextInputEditText nameInput;
    private TextView hintText;
    private FlowCanvasView canvas;
    private RecipeFlow currentFlow;

    private String editingRecipeId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flow_recipe_edit);

        MaterialToolbar toolbar = findViewById(R.id.flow_edit_toolbar);
        nameInput = findViewById(R.id.flow_dish_name_input);
        hintText = findViewById(R.id.flow_hint_text);
        canvas = findViewById(R.id.flow_canvas);
        LinearLayout paletteContainer = findViewById(R.id.flow_palette_container);
        MaterialButton zoomOutButton = findViewById(R.id.flow_zoom_out_button);
        MaterialButton zoomInButton = findViewById(R.id.flow_zoom_in_button);
        MaterialButton resetViewButton = findViewById(R.id.flow_reset_view_button);
        MaterialButton editNodeButton = findViewById(R.id.flow_edit_node_button);
        MaterialButton deleteNodeButton = findViewById(R.id.flow_delete_node_button);
        MaterialButton testButton = findViewById(R.id.flow_test_button);
        MaterialButton saveButton = findViewById(R.id.flow_save_button);

        toolbar.setNavigationOnClickListener(v -> finish());
        editingRecipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);

        buildPalette(paletteContainer);
        canvas.setListener(new FlowCanvasView.Listener() {
            @Override
            public void onNodeSelected(FlowNode node) {
                updateHint(node);
            }

            @Override
            public void onFlowChanged() {
                // no-op
            }
        });

        zoomOutButton.setOnClickListener(v -> canvas.zoomOut());
        zoomInButton.setOnClickListener(v -> canvas.zoomIn());
        resetViewButton.setOnClickListener(v -> canvas.resetViewport());
        editNodeButton.setOnClickListener(v -> editSelectedNode());
        deleteNodeButton.setOnClickListener(v -> canvas.removeSelectedNode());
        testButton.setOnClickListener(v -> testRun());
        saveButton.setOnClickListener(v -> save());

        if (editingRecipeId != null) {
            toolbar.setTitle(R.string.dishs_edit);
            loadExisting(editingRecipeId);
        } else {
            toolbar.setTitle(R.string.dishs_add);
            currentFlow = FlowStepCatalog.createDefaultTemplate();
            canvas.setFlow(currentFlow);
        }
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void buildPalette(LinearLayout container) {
        for (FlowDeviceType device : FlowDeviceType.values()) {
            TextView title = new TextView(this);
            title.setText(device.label);
            title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleLp.topMargin = dp(8);
            titleLp.bottomMargin = dp(4);
            title.setLayoutParams(titleLp);
            container.addView(title);

            for (FlowActionDef def : FlowStepCatalog.forDevice(device)) {
                MaterialButton chip = new MaterialButton(
                        this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                chip.setText(def.label);
                chip.setAllCaps(false);
                chip.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(6);
                chip.setLayoutParams(lp);
                chip.setOnClickListener(v -> addAction(def));
                container.addView(chip);
            }
        }
    }

    private void addAction(FlowActionDef def) {
        float x = 120f + currentFlow.nodes.size() * 24f;
        float y = 100f + currentFlow.nodes.size() * 120f;
        canvas.addNode(def, x, y);
        currentFlow = canvas.getFlow();
        Toast.makeText(this, R.string.flow_node_added, Toast.LENGTH_SHORT).show();
    }

    private void loadExisting(String recipeId) {
        executor.execute(() -> {
            DishsConfig config = DishsConfigStore.findById(this, recipeId);
            RecipeFlow loadedFlow = RecipeFlowStore.getByRecipeId(this, recipeId);
            if (loadedFlow == null && config != null) {
                loadedFlow = FlowStepCatalog.createDefaultTemplate();
                loadedFlow.recipeId = recipeId;
                loadedFlow.dishName = config.dishName;
            }
            final DishsConfig configToBind = config;
            final RecipeFlow flowToBind = loadedFlow;
            runOnUiThread(() -> {
                if (configToBind == null) {
                    Toast.makeText(this, R.string.dishs_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                currentFlow = flowToBind;
                canvas.setFlow(currentFlow);
                nameInput.setText(configToBind.dishName);
            });
        });
    }

    private void updateHint(FlowNode node) {
        if (node == null) {
            hintText.setText(R.string.flow_canvas_hint);
            return;
        }
        String summary = node.paramSummary();
        hintText.setText(summary.isEmpty()
                ? getString(R.string.flow_node_selected, node.label)
                : getString(R.string.flow_node_selected_params, node.label, summary));
    }

    private void editSelectedNode() {
        FlowNode node = canvas.getSelectedNode();
        if (node == null) {
            Toast.makeText(this, R.string.flow_select_node_first, Toast.LENGTH_SHORT).show();
            return;
        }
        FlowActionDef def = FlowStepCatalog.find(node.deviceId, node.actionId, node.label);
        if (def == null || def.params.isEmpty()) {
            Toast.makeText(this, R.string.flow_node_no_params, Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(16);
        layout.setPadding(padding, padding, padding, 0);
        List<TextInputEditText> inputs = new ArrayList<>();
        for (FlowParamDef param : def.params) {
            TextInputLayout wrapper = new TextInputLayout(this);
            wrapper.setHint(param.label);
            TextInputEditText editText = new TextInputEditText(wrapper.getContext());
            String value = node.params != null ? node.params.get(param.key) : null;
            editText.setText(value != null ? value : param.defaultValue);
            wrapper.addView(editText);
            layout.addView(wrapper);
            inputs.add(editText);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(node.label)
                .setView(layout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    for (int i = 0; i < def.params.size(); i++) {
                        FlowParamDef param = def.params.get(i);
                        TextInputEditText editText = inputs.get(i);
                        String text = editText.getText() != null ? editText.getText().toString().trim() : "";
                        node.params.put(param.key, text);
                    }
                    canvas.invalidate();
                    updateHint(node);
                })
                .show();
    }

    private void save() {
        String dishName = textOf(nameInput);
        if (TextUtils.isEmpty(dishName)) {
            Toast.makeText(this, R.string.dishs_error_name, Toast.LENGTH_SHORT).show();
            return;
        }
        RecipeFlow flow = canvas.getFlow();
        if (flow.nodes.isEmpty()) {
            Toast.makeText(this, R.string.flow_error_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            DishsConfig config = new DishsConfig();
            if (editingRecipeId != null) {
                DishsConfig existing = DishsConfigStore.findById(this, editingRecipeId);
                if (existing != null) {
                    config = existing.copy();
                }
                config.id = editingRecipeId;
            }
            config.dishName = dishName;
            RecipeFlowExecutor.applyRecipeFields(config, flow);
            if (config.id == null || config.id.isEmpty()) {
                config.id = UUID.randomUUID().toString().replace("-", "").toLowerCase();
            }
            flow.recipeId = config.id;
            flow.dishName = dishName;

            if (editingRecipeId == null) {
                DishsConfigStore.add(this, config);
            } else {
                DishsConfigStore.update(this, config);
            }
            RecipeFlowStore.save(this, flow);

            runOnUiThread(() -> {
                Toast.makeText(this, R.string.flow_saved, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    private void testRun() {
        String dishName = textOf(nameInput);
        if (TextUtils.isEmpty(dishName)) {
            Toast.makeText(this, R.string.dishs_error_name, Toast.LENGTH_SHORT).show();
            return;
        }
        RecipeFlow flow = canvas.getFlow();
        if (flow.nodes.isEmpty()) {
            Toast.makeText(this, R.string.flow_error_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        DishsConfig temp = new DishsConfig();
        temp.dishName = dishName;
        RecipeFlowExecutor.applyRecipeFields(temp, flow);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.flow_test_run)
                .setMessage(R.string.flow_test_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.flow_test_run, (d, w) -> executor.execute(() -> {
                    RecipeFlowExecutor runner = new RecipeFlowExecutor(this);
                    RecipeFlowExecutor.Result result = runner.execute(flow, temp, (index, total, node, message) ->
                            runOnUiThread(() -> hintText.setText(
                                    getString(R.string.flow_test_progress, index, total, message))));
                    runOnUiThread(() -> Toast.makeText(this,
                            result.ok ? result.message : result.message,
                            Toast.LENGTH_LONG).show());
                }))
                .show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }
}
