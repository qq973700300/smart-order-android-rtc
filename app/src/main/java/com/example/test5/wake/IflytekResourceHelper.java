package com.example.test5.wake;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 将 assets/aikit_resources/ivw 解压到 workDir/aikit_resources/ivw/。
 * 路径与讯飞 SDK 文档 7.4 一致。
 */
final class IflytekResourceHelper {

    private static final String TAG = "IflytekResource";
    private static final String ASSET_ROOT = "aikit_resources/ivw";
    private static final String MARKER = ".assets_copied_v2";

    private IflytekResourceHelper() {
    }

    static File ensureIvwDir(Context context) throws IOException {
        File ivwDir = IflytekSdkHolder.getIvwDir(context);
        File marker = new File(ivwDir, MARKER);
        if (marker.exists() && hasRequiredResources(ivwDir)) {
            return ivwDir;
        }
        if (ivwDir.exists()) {
            deleteRecursive(ivwDir);
        }
        if (!ivwDir.mkdirs() && !ivwDir.isDirectory()) {
            throw new IOException("无法创建 IVW 目录: " + ivwDir);
        }
        copyAssetFolder(context.getAssets(), ASSET_ROOT, ivwDir);
        if (!hasRequiredResources(ivwDir)) {
            throw new IOException("IVW 资源不完整，缺少 IVW_MLP_1 等文件");
        }
        if (!marker.createNewFile()) {
            Log.w(TAG, "marker 创建失败: " + marker);
        }
        Log.i(TAG, "IVW 资源已解压到: " + ivwDir.getAbsolutePath());
        return ivwDir;
    }

    private static boolean hasRequiredResources(File ivwDir) {
        return new File(ivwDir, "IVW_MLP_1").exists()
                && new File(ivwDir, "IVW_GRAM_1").exists();
    }

    private static void copyAssetFolder(AssetManager assets, String assetPath, File destDir)
            throws IOException {
        String[] entries = assets.list(assetPath);
        if (entries == null || entries.length == 0) {
            copyAssetFile(assets, assetPath, destDir);
            return;
        }
        for (String name : entries) {
            String childAsset = assetPath + "/" + name;
            File childDest = new File(destDir, name);
            String[] nested = assets.list(childAsset);
            if (nested != null && nested.length > 0) {
                if (!childDest.mkdirs() && !childDest.isDirectory()) {
                    throw new IOException("无法创建目录: " + childDest);
                }
                copyAssetFolder(assets, childAsset, childDest);
            } else {
                copyAssetFile(assets, childAsset, childDest.getParentFile(), name);
            }
        }
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, File destDir)
            throws IOException {
        String name = assetPath.substring(assetPath.lastIndexOf('/') + 1);
        copyAssetFile(assets, assetPath, destDir, name);
    }

    private static void copyAssetFile(AssetManager assets, String assetPath, File destDir,
                                      String fileName) throws IOException {
        if (destDir != null && !destDir.exists() && !destDir.mkdirs()) {
            throw new IOException("无法创建目录: " + destDir);
        }
        File outFile = new File(destDir, fileName);
        try (InputStream in = assets.open(assetPath);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
