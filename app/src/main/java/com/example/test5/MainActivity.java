package com.example.test5;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test5.BuildConfig;
import com.example.test5.wake.WakeForegroundService;
import com.example.test5.wake.WakeStatusHolder;
import com.example.test5.update.AppUpdateManager;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private TextView wakeStatusView;
    private boolean wakeServiceStarted;

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    requestNotificationIfNeeded();
                } else {
                    updateWakeStatus(getString(R.string.wake_home_need_mic));
                }
            });

    private final ActivityResultLauncher<String> notifyPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    startWakeServiceIfReady());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wakeStatusView = findViewById(R.id.wake_status_text);

        MaterialButton openButton = findViewById(R.id.open_voice_clerk_button);
        openButton.setOnClickListener(v ->
                startActivity(new Intent(this, VoiceClerkActivity.class)));

        findViewById(R.id.open_tashi_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, TashiStockBinDebugActivity.class)));

        findViewById(R.id.open_yuejiang_debug_button).setOnClickListener(v ->
                startActivity(new Intent(this, YuejiangRobotDebugActivity.class)));

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

        ensureWakePermissions();
        AppUpdateManager.checkAndPrompt(this, false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            WakeForegroundService.start(this);
            wakeServiceStarted = true;
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
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    updateWakeStatus(getString(R.string.wake_home_need_mic));
                } else {
                    updateWakeStatus(getString(R.string.wake_home_hint));
                }
                break;
        }
    }

    private void ensureWakePermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateWakeStatus(getString(R.string.wake_home_hint));
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        requestNotificationIfNeeded();
    }

    private void requestNotificationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifyPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        startWakeServiceIfReady();
    }

    private void startWakeServiceIfReady() {
        if (wakeServiceStarted) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            updateWakeStatus(getString(R.string.wake_home_need_mic));
            return;
        }
        WakeForegroundService.start(this);
        wakeServiceStarted = true;
        refreshWakeStatusFromService();
    }

    private void updateWakeStatus(String text) {
        if (wakeStatusView != null) {
            wakeStatusView.setText(text);
        }
    }
}
