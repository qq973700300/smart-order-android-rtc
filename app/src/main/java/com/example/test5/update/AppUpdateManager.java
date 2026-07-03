package com.example.test5.update;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;

import com.example.test5.BuildConfig;
import com.example.test5.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** 检查、下载并安装 APK（通过同一 Node 代理分发） */
public final class AppUpdateManager {

    public interface CheckCallback {
        void onResult(@Nullable AppUpdateInfo info, @Nullable String error);
    }

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    @Nullable
    private static AlertDialog progressDialog;

    private AppUpdateManager() {
    }

    public static void checkAsync(@NonNull CheckCallback callback) {
        EXECUTOR.execute(() -> {
            try {
                AppUpdateInfo info = AppUpdateApi.checkUpdate();
                callback.onResult(info, null);
            } catch (IOException e) {
                callback.onResult(null, e.getMessage());
            }
        });
    }

    public static void checkAndPrompt(@NonNull Activity activity, boolean showNoUpdateToast) {
        checkAsync((info, error) -> activity.runOnUiThread(() -> {
            if (error != null) {
                if (showNoUpdateToast) {
                    Toast.makeText(activity,
                            activity.getString(R.string.app_update_check_failed, error),
                            Toast.LENGTH_LONG).show();
                }
                return;
            }
            if (info != null && info.hasUpdate) {
                showUpdateDialog(activity, info);
            } else if (showNoUpdateToast) {
                Toast.makeText(activity, R.string.app_update_already_latest, Toast.LENGTH_SHORT).show();
            }
        }));
    }

    public static void showUpdateDialog(@NonNull Activity activity, @NonNull AppUpdateInfo info) {
        String message = activity.getString(
                R.string.app_update_dialog_message,
                info.versionName,
                info.changelog != null && !info.changelog.isEmpty()
                        ? info.changelog
                        : activity.getString(R.string.app_update_no_changelog));

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_update_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.app_update_download, (d, w) ->
                        downloadAndInstall(activity, info))
                .setCancelable(!info.forceUpdate);

        if (!info.forceUpdate) {
            builder.setNegativeButton(R.string.app_update_later, null);
        }
        builder.show();
    }

    private static void downloadAndInstall(@NonNull Activity activity, @NonNull AppUpdateInfo info) {
        if (info.apkUrl == null || info.apkUrl.isEmpty()) {
            Toast.makeText(activity, R.string.app_update_no_apk_url, Toast.LENGTH_LONG).show();
            return;
        }
        if (!ensureInstallPermission(activity)) {
            return;
        }

        View progressView = LayoutInflater.from(activity).inflate(R.layout.dialog_app_update_progress, null);
        ProgressBar progressBar = progressView.findViewById(R.id.update_progress_bar);
        TextView progressText = progressView.findViewById(R.id.update_progress_text);
        progressBar.setIndeterminate(true);
        progressText.setText(R.string.app_update_downloading);

        progressDialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.app_update_downloading_title)
                .setView(progressView)
                .setCancelable(false)
                .create();
        progressDialog.show();

        EXECUTOR.execute(() -> {
            File apkFile = resolveApkFile(activity);
            try {
                downloadApk(info.apkUrl, apkFile, (read, total) -> activity.runOnUiThread(() -> {
                    if (total > 0) {
                        progressBar.setIndeterminate(false);
                        progressBar.setMax(100);
                        progressBar.setProgress((int) (read * 100 / total));
                        progressText.setText(activity.getString(
                                R.string.app_update_download_progress,
                                read / 1024,
                                total / 1024));
                    }
                }));
                activity.runOnUiThread(() -> {
                    dismissProgressDialog();
                    installApk(activity, apkFile);
                });
            } catch (IOException e) {
                activity.runOnUiThread(() -> {
                    dismissProgressDialog();
                    Toast.makeText(activity,
                            activity.getString(R.string.app_update_download_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private static File resolveApkFile(@NonNull Activity activity) {
        File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (dir == null) {
            dir = new File(activity.getCacheDir(), "updates");
        } else {
            dir = new File(dir, "updates");
        }
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "smart-order-update.apk");
    }

    private interface DownloadProgress {
        void onProgress(long readBytes, long totalBytes);
    }

    private static void downloadApk(String url, File dest, DownloadProgress progress) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = AppUpdateApi.downloadClient().newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new IOException("响应体为空");
            }
            long total = body.contentLength();
            try (InputStream in = body.byteStream();
                 FileOutputStream out = new FileOutputStream(dest)) {
                byte[] buffer = new byte[8192];
                long read = 0;
                int count;
                while ((count = in.read(buffer)) != -1) {
                    out.write(buffer, 0, count);
                    read += count;
                    progress.onProgress(read, total);
                }
                out.flush();
            }
        }
    }

    private static boolean ensureInstallPermission(@NonNull Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !activity.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(activity, R.string.app_update_need_install_permission, Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(intent);
            return false;
        }
        return true;
    }

    private static void installApk(@NonNull Activity activity, @NonNull File apkFile) {
        Uri uri = FileProvider.getUriForFile(
                activity,
                BuildConfig.APPLICATION_ID + ".fileprovider",
                apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
    }

    private static void dismissProgressDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        progressDialog = null;
    }
}
