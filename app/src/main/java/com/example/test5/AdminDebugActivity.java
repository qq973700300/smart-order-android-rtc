package com.example.test5;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.test5.BuildConfig;
import com.example.test5.update.AppUpdateManager;
import com.example.test5.wake.WakeConfig;
import com.example.test5.wake.WakeForegroundService;
import com.example.test5.wake.WakeStatusHolder;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/** 管理员调试入口：原主页设备调试与版本更新。 */
public class AdminDebugActivity extends AppCompatActivity {

    private TextView wakeStatusView;
    private boolean wakeServiceStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_debug);

        MaterialToolbar toolbar = findViewById(R.id.admin_debug_toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        wakeStatusView = findViewById(R.id.wake_status_text);

        MaterialButton openButton = findViewById(R.id.open_voice_clerk_button);
        openButton.setOnClickListener(v ->
                startActivity(new Intent(this, VoiceClerkActivity.class)));

        findViewById(R.id.open_dishs_manage_button).setOnClickListener(v ->
                startActivity(new Intent(this, DishsConfigManageActivity.class)));

        findViewById(R.id.open_tashi_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, TashiStockBinDebugActivity.class)));

        findViewById(R.id.open_yuejiang_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, YuejiangRobotDebugActivity.class)));

        findViewById(R.id.open_drum_pot_opc_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, DrumPotOpcDebugActivity.class)));

        findViewById(R.id.open_drum_pot_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, DrumPotModbusDebugActivity.class)));

        findViewById(R.id.open_lebai_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, LebaiDebugActivity.class)));

        findViewById(R.id.open_order_subscribe_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, OrderSubscribeDebugActivity.class)));

        findViewById(R.id.open_wake_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, WakeDebugActivity.class)));

        TextView versionView = findViewById(R.id.app_version_text);
        versionView.setText(getString(
                R.string.app_update_version_label,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE));

        findViewById(R.id.check_update_button).setOnClickListener(v ->
                AppUpdateManager.checkAndPrompt(this, true));

        refreshWakeStatusFromService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            WakeForegroundService.stopIfRunning(this);
            wakeServiceStarted = false;
            updateWakeStatus(getString(R.string.wake_service_disabled));
            return;
        }
        refreshWakeStatusFromService();
    }

    private void refreshWakeStatusFromService() {
        String detail = WakeStatusHolder.getDetail();
        if (detail != null && !detail.isEmpty()) {
            updateWakeStatus(detail);
            return;
        }
        switch (WakeStatusHolder.getState()) {
            case LISTENING:
                updateWakeStatus(getString(R.string.wake_status_listening));
                break;
            case AUTH_WAIT:
                updateWakeStatus(getString(R.string.wake_status_auth_wait));
                break;
            case AUTH_FAILED:
                updateWakeStatus(getString(R.string.wake_status_auth_failed,
                        com.example.test5.wake.IflytekSdkHolder.getAuthCode()));
                break;
            case PAUSED_RTC:
                updateWakeStatus(getString(R.string.wake_status_paused_rtc));
                break;
            case ERROR:
            case TRIGGERED:
            case INIT:
            default:
                updateWakeStatus(getString(R.string.wake_home_hint));
                break;
        }
    }

    private void updateWakeStatus(String text) {
        if (wakeStatusView != null) {
            wakeStatusView.setText(text);
        }
    }
}
