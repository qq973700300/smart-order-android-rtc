package com.example.test5;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigManageAdapter;
import com.example.test5.recipe.DishsConfigStore;
import com.example.test5.recipe.flow.RecipeFlowStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 自定义菜谱：添加/修改均进入流程编排界面。 */
public class CustomMenuActivity extends AppCompatActivity {

    private DishsConfigManageAdapter adapter;
    private TextView countView;
    private TextInputEditText searchInput;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> editLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                reloadList(currentKeyword());
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dishs_config_manage);

        MaterialToolbar toolbar = findViewById(R.id.dishs_manage_toolbar);
        searchInput = findViewById(R.id.dishs_search_input);
        countView = findViewById(R.id.dishs_count_text);
        RecyclerView recycler = findViewById(R.id.dishs_manage_recycler);
        MaterialButton addButton = findViewById(R.id.dishs_add_button);
        MaterialButton editButton = findViewById(R.id.dishs_edit_button);
        MaterialButton deleteButton = findViewById(R.id.dishs_delete_button);

        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new DishsConfigManageAdapter(this, item -> {
        });
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        addButton.setOnClickListener(v -> openFlowEdit(null));
        editButton.setOnClickListener(v -> {
            DishsConfig selected = adapter.getSelectedItem();
            if (selected == null) {
                Toast.makeText(this, R.string.dishs_select_first, Toast.LENGTH_SHORT).show();
                return;
            }
            openFlowEdit(selected.id);
        });
        deleteButton.setOnClickListener(v -> confirmDelete());

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                reloadList(s != null ? s.toString() : "");
            }
        });

        reloadList("");
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadList(currentKeyword());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void openFlowEdit(String id) {
        Intent intent = new Intent(this, FlowRecipeEditActivity.class);
        if (id != null) {
            intent.putExtra(FlowRecipeEditActivity.EXTRA_RECIPE_ID, id);
        }
        editLauncher.launch(intent);
    }

    private void confirmDelete() {
        DishsConfig selected = adapter.getSelectedItem();
        if (selected == null) {
            Toast.makeText(this, R.string.dishs_select_first, Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.dishs_delete)
                .setMessage(getString(R.string.dishs_delete_confirm, selected.dishName))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.dishs_delete, (d, w) -> executor.execute(() -> {
                    DishsConfigStore.delete(this, selected.id);
                    RecipeFlowStore.delete(this, selected.id);
                    runOnUiThread(() -> reloadList(currentKeyword()));
                }))
                .show();
    }

    private String currentKeyword() {
        return searchInput.getText() != null ? searchInput.getText().toString() : "";
    }

    private void reloadList(String keyword) {
        executor.execute(() -> {
            List<DishsConfig> list = DishsConfigStore.search(this, keyword);
            runOnUiThread(() -> {
                adapter.setItems(list);
                countView.setText(getString(R.string.dishs_count_format, list.size()));
            });
        });
    }
}
