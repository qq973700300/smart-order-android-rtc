package com.example.test5;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.test5.order.mq.OrderInbox;
import com.example.test5.order.mq.OrderInboxAdapter;
import com.example.test5.order.mq.OrderMqManager;
import com.google.android.material.appbar.MaterialToolbar;

/** 实时订单列表：小程序 / 云端经 RabbitMQ 推送的订单。 */
public class OrderInboxActivity extends AppCompatActivity implements OrderInbox.Listener {

    private OrderInboxAdapter adapter;
    private TextView mqStatusView;
    private TextView emptyView;
    private RecyclerView recyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_inbox);

        MaterialToolbar toolbar = findViewById(R.id.order_inbox_toolbar);
        mqStatusView = findViewById(R.id.order_mq_status_text);
        recyclerView = findViewById(R.id.order_inbox_recycler);
        emptyView = findViewById(R.id.order_inbox_empty);

        toolbar.setNavigationOnClickListener(v -> finish());

        adapter = new OrderInboxAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        refreshList();
    }

    @Override
    protected void onStart() {
        super.onStart();
        OrderInbox.getInstance().addListener(this);
        refreshMqStatus();
    }

    @Override
    protected void onStop() {
        OrderInbox.getInstance().removeListener(this);
        super.onStop();
    }

    @Override
    public void onOrdersChanged() {
        runOnUiThread(this::refreshList);
    }

    private void refreshList() {
        adapter.setItems(OrderInbox.getInstance().snapshot());
        boolean empty = adapter.getItemCount() == 0;
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
        refreshMqStatus();
    }

    private void refreshMqStatus() {
        mqStatusView.setText(getString(
                R.string.order_mq_status_format,
                OrderMqManager.getInstance().getConnectionStatus()));
    }
}
