package com.example.test5.update;

import com.example.test5.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/** 向 Node 代理查询是否有新版本 */
public final class AppUpdateApi {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    private AppUpdateApi() {
    }

    public static AppUpdateInfo checkUpdate() throws IOException {
        JsonObject payload = new JsonObject();
        payload.addProperty("versionCode", BuildConfig.VERSION_CODE);
        payload.addProperty("packageName", BuildConfig.APPLICATION_ID);

        Request request = new Request.Builder()
                .url(BuildConfig.AIGC_PROXY_HOST + "/appUpdateCheck")
                .post(RequestBody.create(payload.toString(), JSON))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            String body = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException("appUpdateCheck HTTP " + response.code() + ": " + body);
            }
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject metadata = root.getAsJsonObject("ResponseMetadata");
            if (metadata != null && metadata.has("Error") && !metadata.get("Error").isJsonNull()) {
                throw new IOException("检查更新失败: " + metadata.get("Error"));
            }
            JsonObject result = root.getAsJsonObject("Result");
            if (result == null) {
                throw new IOException("检查更新无 Result: " + body);
            }
            return GSON.fromJson(result, AppUpdateInfo.class);
        }
    }

    public static OkHttpClient downloadClient() {
        return CLIENT;
    }
}
