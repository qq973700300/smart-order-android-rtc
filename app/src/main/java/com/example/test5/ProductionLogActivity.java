package com.example.test5;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.log.ProductionLogAdapter;
import com.example.test5.log.ProductionLogStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** 设置入口：查看本地持久化的生产/订单日志。 */
public class ProductionLogActivity extends AppCompatActivity implements ProductionLogStore.Listener {

    private ProductionLogAdapter adapter;
    private TextView emptyView;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_production_log);

        MaterialToolbar toolbar = findViewById(R.id.production_log_toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        emptyView = findViewById(R.id.production_log_empty);
        recyclerView = findViewById(R.id.production_log_recycler);

        adapter = new ProductionLogAdapter();
        adapter.setOnItemClickListener(this::showDetailDialog);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        refreshList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.production_log_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_clear_logs) {
            confirmClearLogs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onStart() {
        super.onStart();
        ProductionLogStore.addListener(this);
        refreshList();
    }

    @Override
    protected void onStop() {
        ProductionLogStore.removeListener(this);
        super.onStop();
    }

    @Override
    public void onLogsChanged() {
        runOnUiThread(this::refreshList);
    }

    private void refreshList() {
        adapter.setItems(ProductionLogStore.snapshot());
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void showDetailDialog(ProductionLogStore.Entry entry) {
        StringBuilder body = new StringBuilder();
        body.append(getString(R.string.production_log_detail_time, formatTime(entry.timestampMs)));
        body.append("\n").append(getString(
                R.string.production_log_detail_level,
                entry.level != null ? entry.level : "INFO"));
        body.append("\n").append(getString(
                R.string.production_log_detail_category,
                entry.category != null ? entry.category : "SYSTEM"));
        if (!entry.orderNumber.isEmpty()) {
            body.append("\n").append(getString(
                    R.string.production_log_detail_order,
                    entry.orderNumber));
        }
        if (!entry.dishName.isEmpty()) {
            body.append("\n").append(getString(
                    R.string.production_log_detail_dish,
                    entry.dishName));
        }
        if (!entry.title.isEmpty()) {
            body.append("\n\n").append(entry.title);
        }
        if (!entry.message.isEmpty()) {
            body.append("\n").append(entry.message);
        }

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.production_log_detail_title)
                .setMessage(body.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void confirmClearLogs() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.production_log_clear_title)
                .setMessage(R.string.production_log_clear_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.production_log_clear_confirm, (dialog, which) -> {
                    ProductionLogStore.clear();
                    refreshList();
                })
                .show();
    }

    private String formatTime(long timestampMs) {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date(timestampMs));
    }
}
