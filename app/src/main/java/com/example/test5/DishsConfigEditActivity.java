package com.example.test5;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.recipe.DishsConfig;
import com.example.test5.recipe.DishsConfigStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 单条菜谱编辑（对应上位机 XmlChild）。 */
public class DishsConfigEditActivity extends AppCompatActivity {

    public static final String EXTRA_ID = "extra_id";

    private TextInputEditText nameInput;
    private TextInputEditText locationInput;
    private TextInputEditText heatInput;
    private TextInputEditText waterInput;
    private TextInputEditText oilInput;
    private TextInputEditText saltInput;
    private TextInputEditText shengInput;
    private TextInputEditText laoInput;
    private TextInputEditText vinegarInput;
    private TextInputEditText friedTimeInput;
    private TextInputEditText whiteSugarInput;
    private TextInputEditText oldSoyInput;

    private String editingId;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dishs_config_edit);

        MaterialToolbar toolbar = findViewById(R.id.dishs_edit_toolbar);
        nameInput = findViewById(R.id.dishs_edit_name);
        locationInput = findViewById(R.id.dishs_edit_location);
        heatInput = findViewById(R.id.dishs_edit_heat);
        waterInput = findViewById(R.id.dishs_edit_water);
        oilInput = findViewById(R.id.dishs_edit_oil);
        saltInput = findViewById(R.id.dishs_edit_salt);
        shengInput = findViewById(R.id.dishs_edit_sheng);
        laoInput = findViewById(R.id.dishs_edit_lao);
        vinegarInput = findViewById(R.id.dishs_edit_vinegar);
        friedTimeInput = findViewById(R.id.dishs_edit_fried_time);
        whiteSugarInput = findViewById(R.id.dishs_edit_white_sugar);
        oldSoyInput = findViewById(R.id.dishs_edit_old_soy);
        MaterialButton saveButton = findViewById(R.id.dishs_save_button);

        toolbar.setNavigationOnClickListener(v -> finish());
        editingId = getIntent().getStringExtra(EXTRA_ID);

        if (editingId != null) {
            toolbar.setTitle(R.string.dishs_edit);
            loadExisting(editingId);
        } else {
            toolbar.setTitle(R.string.dishs_add);
        }

        saveButton.setOnClickListener(v -> save());
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private void loadExisting(String id) {
        executor.execute(() -> {
            DishsConfig config = DishsConfigStore.findById(this, id);
            runOnUiThread(() -> {
                if (config == null) {
                    Toast.makeText(this, R.string.dishs_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                bind(config);
            });
        });
    }

    private void bind(DishsConfig config) {
        setText(nameInput, config.dishName);
        setText(locationInput, config.dishLocation);
        setText(heatInput, config.statusType ? "True" : "False");
        setText(waterInput, String.valueOf(config.outWater));
        setText(oilInput, String.valueOf(config.outOil));
        setText(saltInput, String.valueOf(config.outSalt));
        setText(shengInput, String.valueOf(config.outShengSauce));
        setText(laoInput, String.valueOf(config.outLaoSauce));
        setText(vinegarInput, String.valueOf(config.outVinegar));
        setText(friedTimeInput, String.valueOf(config.friedTime));
        setText(whiteSugarInput, String.valueOf(config.whiteSugar));
        setText(oldSoyInput, String.valueOf(config.oldsoySauce));
    }

    private void save() {
        String name = textOf(nameInput);
        String location = textOf(locationInput);
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.dishs_error_name, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(location)) {
            Toast.makeText(this, R.string.dishs_error_location, Toast.LENGTH_SHORT).show();
            return;
        }

        DishsConfig config = new DishsConfig();
        config.id = editingId;
        config.dishName = name;
        config.dishLocation = location;
        config.statusType = parseBool(textOf(heatInput));
        config.outWater = parseInt(textOf(waterInput));
        config.outOil = parseInt(textOf(oilInput));
        config.outSalt = parseInt(textOf(saltInput));
        config.outShengSauce = parseInt(textOf(shengInput));
        config.outLaoSauce = parseInt(textOf(laoInput));
        config.outVinegar = parseInt(textOf(vinegarInput));
        config.friedTime = parseInt(textOf(friedTimeInput));
        config.whiteSugar = parseInt(textOf(whiteSugarInput));
        config.oldsoySauce = parseInt(textOf(oldSoyInput));

        executor.execute(() -> {
            if (editingId == null) {
                DishsConfigStore.add(this, config);
            } else {
                config.id = editingId;
                DishsConfigStore.update(this, config);
            }
            runOnUiThread(() -> {
                Toast.makeText(this, R.string.dishs_saved, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            });
        });
    }

    private static void setText(TextInputEditText editText, String value) {
        editText.setText(value);
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private static boolean parseBool(String text) {
        if (text == null) {
            return false;
        }
        String v = text.trim().toLowerCase(Locale.US);
        return "true".equals(v) || "1".equals(v) || "是".equals(v);
    }

    private static int parseInt(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
