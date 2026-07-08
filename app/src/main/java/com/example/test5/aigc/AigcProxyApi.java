package com.example.test5.aigc;

import com.example.test5.BuildConfig;
import com.example.test5.net.NetworkDiagnostics;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** 调用 Node/Spring 代理：getScenes、StartVoiceChat、StopVoiceChat */
public final class AigcProxyApi {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    private AigcProxyApi() {
    }

    public static String proxyHost() {
        return BuildConfig.AIGC_PROXY_HOST;
    }

    public static String defaultSceneId() {
        return BuildConfig.AIGC_SCENE_ID;
    }

    public static AigcSceneInfo fetchScene(String sceneId) throws IOException {
        String url = proxyHost() + "/getScenes";
        NetworkDiagnostics.logBeforeCloudRequest("getScenes", url);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create("{}", JSON))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            android.util.Log.i(NetworkDiagnostics.TAG,
                    "[cloud] getScenes HTTP " + response.code()
                            + " bodyLen=" + body.length());
            if (!response.isSuccessful()) {
                throw new IOException("getScenes HTTP " + response.code() + ": " + body);
            }
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject metadata = root.getAsJsonObject("ResponseMetadata");
            if (metadata != null && metadata.has("Error") && !metadata.get("Error").isJsonNull()) {
                throw new IOException("getScenes 失败: " + metadata.get("Error"));
            }
            JsonObject result = root.getAsJsonObject("Result");
            if (result == null) {
                throw new IOException("getScenes 无 Result 字段: " + body);
            }
            JsonArray scenes = result.getAsJsonArray("scenes");
            if (scenes == null) {
                throw new IOException("getScenes 无 scenes 字段: " + body);
            }
            for (int i = 0; i < scenes.size(); i++) {
                AigcSceneInfo info = GSON.fromJson(scenes.get(i), AigcSceneInfo.class);
                if (info.scene != null && sceneId.equals(info.scene.id)) {
                    return info;
                }
            }
            throw new IOException("未找到场景: " + sceneId);
        }
    }

    public static void startVoiceChat(String sceneId, String userId, String roomId) throws IOException {
        invokeProxy("StartVoiceChat", sceneId, userId, roomId);
    }

    public static void stopVoiceChat(String sceneId) throws IOException {
        invokeProxy("StopVoiceChat", sceneId, null, null, null);
    }

    /** 打断当前 AI 语音输出（唱歌前调用，避免 TTS 与 MP3 叠在一起）。 */
    public static void interruptVoiceChat(String sceneId) throws IOException {
        JsonObject extra = new JsonObject();
        extra.addProperty("Command", "Interrupt");
        invokeProxy("UpdateVoiceChat", sceneId, null, null, extra);
    }

    private static void invokeProxy(String action, String sceneId, String userId, String roomId) throws IOException {
        invokeProxy(action, sceneId, userId, roomId, null);
    }

    private static void invokeProxy(
            String action,
            String sceneId,
            String userId,
            String roomId,
            JsonObject extra
    ) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("SceneID", sceneId);
        if (userId != null && !userId.isEmpty()) {
            payload.addProperty("UserId", userId);
        }
        if (roomId != null && !roomId.isEmpty()) {
            payload.addProperty("RoomId", roomId);
        }
        if (extra != null) {
            for (String key : extra.keySet()) {
                payload.add(key, extra.get(key));
            }
        }
        String url = proxyHost() + "/proxy?Action=" + action + "&Version=2024-12-01";
        NetworkDiagnostics.logBeforeCloudRequest(action, url);
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            android.util.Log.i(NetworkDiagnostics.TAG,
                    "[cloud] " + action + " HTTP " + response.code()
                            + " bodyLen=" + body.length());
            if (!response.isSuccessful()) {
                throw new IOException(action + " HTTP " + response.code() + ": " + body);
            }
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject metadata = root.getAsJsonObject("ResponseMetadata");
            if (metadata != null && metadata.has("Error") && !metadata.get("Error").isJsonNull()) {
                throw new IOException(action + " 失败: " + metadata.get("Error"));
            }
        }
    }
}
