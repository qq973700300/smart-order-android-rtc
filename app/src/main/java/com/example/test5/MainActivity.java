package com.example.test5;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.test5.device.opcua.DrumPotConnectionManager;
import com.example.test5.device.tashi.StockBinConnectionManager;
import com.example.test5.net.NetworkDiagnostics;
import com.example.test5.ui.AiAvatarVisualizerView;
import com.example.test5.update.AppUpdateManager;
import com.example.test5.voice.VoiceHomeController;
import com.example.test5.wake.WakeConfig;
import com.example.test5.wake.WakeForegroundService;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/** 顾客主页：AI 虚拟体 + 手动点餐 / 自定义菜单；右上角菜单含联系客服与设置。 */
public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_AUTO_START = "auto_start";
    public static final String EXTRA_WAKE_KEYWORD = "wake_keyword";

    private static final String ADMIN_PASSWORD = "123456";

    private TextView statusView;
    private TextView subtitleUserView;
    private TextView subtitleBotView;
    private TextView subtitlePlaceholderView;
    private AiAvatarVisualizerView aiAvatarView;
    private VoiceHomeController voiceController;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean wakeServiceStarted;
    private boolean pendingAutoStart;
    private String pendingWakeKeyword = "";

    private final ActivityResultLauncher<String> micPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (Boolean.TRUE.equals(granted)) {
                    requestNotificationIfNeeded();
                    startVoiceIfPending();
                } else {
                    updateStatus(getString(R.string.wake_home_need_mic));
                }
            });

    private final ActivityResultLauncher<String> notifyPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted ->
                    startWakeServiceIfReady());

    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                voiceController.setHasCameraPermission(Boolean.TRUE.equals(granted));
                if (Boolean.TRUE.equals(granted)) {
                    voiceController.continueAfterCameraGranted();
                } else {
                    Toast.makeText(this, R.string.camera_denied, Toast.LENGTH_SHORT).show();
                    voiceController.continueWithoutCamera();
                }
            });

    private final Runnable autoStartRunnable = this::ensureMicAndStartVoice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusView = findViewById(R.id.home_status_text);
        subtitleUserView = findViewById(R.id.home_subtitle_user);
        subtitleBotView = findViewById(R.id.home_subtitle_bot);
        subtitlePlaceholderView = findViewById(R.id.home_subtitle_placeholder);
        aiAvatarView = findViewById(R.id.home_ai_avatar);
        MaterialButton settingsButton = findViewById(R.id.home_settings_button);
        MaterialButton manualOrderButton = findViewById(R.id.home_manual_order_button);
        MaterialButton customMenuButton = findViewById(R.id.home_custom_menu_button);

        voiceController = new VoiceHomeController(this);
        voiceController.setHasCameraPermission(hasCameraPermission());
        voiceController.setCallback(new VoiceHomeController.Callback() {
            @Override
            public void onStatus(@NonNull String status) {
                updateStatus(status);
            }

            @Override
            public void onSpeakingChanged(boolean speaking) {
                aiAvatarView.setSpeaking(speaking);
            }

            @Override
            public void onSessionRunningChanged(boolean running) {
                aiAvatarView.setListening(running);
                if (!running) {
                    updateStatus(getString(R.string.home_ai_hint));
                    updateSubtitle("", "");
                }
            }

            @Override
            public void onSubtitle(@NonNull String userText, @NonNull String botText) {
                updateSubtitle(userText, botText);
            }

            @Override
            public void onError(@NonNull String message) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();
                updateStatus(getString(R.string.voice_clerk_error, message));
            }

            @Override
            public void onCameraPermissionRequired() {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
            }
        });

        settingsButton.setOnClickListener(this::showSettingsMenu);
        manualOrderButton.setOnClickListener(v ->
                startActivity(new Intent(this, ManualOrderActivity.class)));
        customMenuButton.setOnClickListener(v -> openCustomMenu());
        aiAvatarView.setOnClickListener(v -> onAiAvatarClicked());

        ensureWakePermissions();
        NetworkDiagnostics.logSnapshot("main_onCreate");
        AppUpdateManager.checkAndPrompt(this, false);
        handleLaunchIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleLaunchIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            WakeForegroundService.stopIfRunning(this);
            wakeServiceStarted = false;
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
                && !voiceController.isSessionRunning()) {
            WakeForegroundService.start(this);
            wakeServiceStarted = true;
        }
        StockBinConnectionManager.getInstance(this).connectAsync();
        DrumPotConnectionManager.getInstance(this).connectAsync();
        if (pendingAutoStart && !voiceController.isSessionRunning()) {
            scheduleAutoStart();
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacks(autoStartRunnable);
        voiceController.release();
        super.onDestroy();
    }

    private void onAiAvatarClicked() {
        if (voiceController.isSessionRunning()) {
            voiceController.stopSession();
            return;
        }
        ensureMicAndStartVoice();
    }

    private void ensureMicAndStartVoice() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
            return;
        }
        pendingAutoStart = false;
        voiceController.setHasCameraPermission(hasCameraPermission());
        voiceController.startSession();
    }

    private void startVoiceIfPending() {
        if (pendingAutoStart) {
            scheduleAutoStart();
        }
    }

    private void handleLaunchIntent(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_AUTO_START, false)) {
            return;
        }
        pendingAutoStart = true;
        pendingWakeKeyword = parseWakeKeyword(intent.getStringExtra(EXTRA_WAKE_KEYWORD));
        intent.removeExtra(EXTRA_AUTO_START);
        intent.removeExtra(EXTRA_WAKE_KEYWORD);

        if (voiceController.isSessionRunning()) {
            pendingAutoStart = false;
            updateStatus(getString(R.string.voice_clerk_running));
            return;
        }
        if (hasWindowFocus()) {
            scheduleAutoStart();
        }
    }

    private void scheduleAutoStart() {
        if (!pendingAutoStart || voiceController.isSessionRunning()) {
            return;
        }
        pendingAutoStart = false;
        if (!pendingWakeKeyword.isEmpty()) {
            updateStatus(getString(R.string.voice_clerk_wake_starting, pendingWakeKeyword));
        } else {
            updateStatus(getString(R.string.voice_clerk_wake_starting_generic));
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        mainHandler.removeCallbacks(autoStartRunnable);
        mainHandler.postDelayed(autoStartRunnable, 1200L);
    }

    private void showSettingsMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.main_settings_menu, menu.getMenu());
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.menu_contact_service) {
                showContactServiceDialog();
                return true;
            }
            if (id == R.id.menu_stock_bin_settings) {
                startActivity(new Intent(this, StockBinSettingsActivity.class));
                return true;
            }
            if (id == R.id.menu_drum_pot_settings) {
                startActivity(new Intent(this, DrumPotSettingsActivity.class));
                return true;
            }
            if (id == R.id.menu_admin_debug) {
                showAdminPasswordDialog();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void showAdminPasswordDialog() {
        TextInputLayout inputLayout = new TextInputLayout(this);
        inputLayout.setHint(getString(R.string.admin_password_hint));
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        inputLayout.setPadding(padding, padding / 2, padding, 0);

        TextInputEditText passwordInput = new TextInputEditText(inputLayout.getContext());
        passwordInput.setInputType(
                android.text.InputType.TYPE_CLASS_NUMBER
                        | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        inputLayout.addView(passwordInput);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.admin_password_title)
                .setView(inputLayout)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.admin_password_confirm, (dialog, which) -> {
                    CharSequence text = passwordInput.getText();
                    if (text != null && ADMIN_PASSWORD.contentEquals(text)) {
                        startActivity(new Intent(this, AdminDebugActivity.class));
                    } else {
                        Toast.makeText(this, R.string.admin_password_wrong, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void openCustomMenu() {
        startActivity(new Intent(this, CustomMenuActivity.class));
    }

    private void showContactServiceDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.home_contact_service)
                .setMessage(R.string.home_contact_service_message)
                .setPositiveButton(R.string.home_contact_service_voice, (d, w) ->
                        ensureMicAndStartVoice())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static String parseWakeKeyword(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        int idx = raw.indexOf("\"keyword\"");
        if (idx >= 0) {
            int start = raw.indexOf('"', idx + 9);
            if (start >= 0) {
                int end = raw.indexOf('"', start + 1);
                if (end > start) {
                    return raw.substring(start + 1, end);
                }
            }
        }
        return raw.length() > 32 ? raw.substring(0, 32) + "…" : raw;
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void ensureWakePermissions() {
        if (!WakeConfig.ENABLE_WAKE_SERVICE) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
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
        if (wakeServiceStarted || !WakeConfig.ENABLE_WAKE_SERVICE) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        WakeForegroundService.start(this);
        wakeServiceStarted = true;
    }

    private void updateStatus(String text) {
        if (statusView != null) {
            statusView.setText(text);
        }
    }

    private void updateSubtitle(String userText, String botText) {
        boolean hasUser = userText != null && !userText.trim().isEmpty();
        boolean hasBot = botText != null && !botText.trim().isEmpty();

        if (subtitlePlaceholderView != null) {
            subtitlePlaceholderView.setVisibility(hasUser || hasBot ? View.GONE : View.VISIBLE);
        }
        if (subtitleUserView != null) {
            if (hasUser) {
                subtitleUserView.setVisibility(View.VISIBLE);
                subtitleUserView.setText(getString(R.string.home_subtitle_user_format, userText.trim()));
            } else {
                subtitleUserView.setVisibility(View.GONE);
            }
        }
        if (subtitleBotView != null) {
            if (hasBot) {
                subtitleBotView.setVisibility(View.VISIBLE);
                subtitleBotView.setText(getString(R.string.home_subtitle_bot_format, botText.trim()));
            } else {
                subtitleBotView.setVisibility(View.GONE);
            }
        }
    }
}
