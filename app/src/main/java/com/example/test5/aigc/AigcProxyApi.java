package com.example.test5.aigc;

import com.example.test5.BuildConfig;
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
        Request request = new Request.Builder()
                .url(proxyHost() + "/getScenes")
                .post(RequestBody.create("{}", JSON))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
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
        invokeProxy("StopVoiceChat", sceneId, null, null);
    }

    private static void invokeProxy(String action, String sceneId, String userId, String roomId) throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("SceneID", sceneId);
        if (userId != null && !userId.isEmpty()) {
            payload.addProperty("UserId", userId);
        }
        if (roomId != null && !roomId.isEmpty()) {
            payload.addProperty("RoomId", roomId);
        }
        String url = proxyHost() + "/proxy?Action=" + action + "&Version=2024-12-01";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
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
