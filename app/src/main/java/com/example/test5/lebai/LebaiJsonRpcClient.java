package com.example.test5.lebai;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 乐白机械臂 HTTP JSON-RPC 客户端（真机默认端口 3021）。
 * 文档：https://help.lebai.ltd/dev/jsonrpc.html
 */
public final class LebaiJsonRpcClient {

    private static final String TAG = "LebaiJsonRpc";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final OkHttpClient httpClient;
    private final String baseUrl;

    public LebaiJsonRpcClient(String host, int port) {
        this(host, port, 20, 120);
    }

    public LebaiJsonRpcClient(String host, int port, int connectTimeoutSec, int readTimeoutSec) {
        baseUrl = "http://" + host.trim() + ":" + port + "/";
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutSec, TimeUnit.SECONDS)
                .readTimeout(readTimeoutSec, TimeUnit.SECONDS)
                .writeTimeout(connectTimeoutSec, TimeUnit.SECONDS)
                .build();
    }

    public RpcCallResult call(String method, JsonElement params) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("jsonrpc", "2.0");
        body.addProperty("method", method);
        body.add("params", params != null ? params : new JsonArray());
        int id = NEXT_ID.getAndIncrement();
        body.addProperty("id", id);

        String bodyJson = GSON.toJson(body);
        logHttp("POST " + baseUrl);
        logHttp("Request: " + bodyJson);

        Request request = new Request.Builder()
                .url(baseUrl)
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            int httpCode = response.code();
            String responseBody = response.body() != null ? response.body().string() : "";
            logHttp("HTTP " + httpCode + ": " + responseBody);

            if (!response.isSuccessful()) {
                return RpcCallResult.failure(baseUrl, bodyJson, httpCode, responseBody,
                        "HTTP " + httpCode);
            }
            return parseResponse(baseUrl, bodyJson, httpCode, responseBody);
        }
    }

    public RpcCallResult startSys() throws IOException {
        JsonArray params = new JsonArray();
        params.add(new JsonObject());
        return call("start_sys", params);
    }

    public RpcCallResult stopSys() throws IOException {
        JsonArray params = new JsonArray();
        params.add(new JsonObject());
        return call("stop_sys", params);
    }

    public RpcCallResult loadTaskList() throws IOException {
        JsonArray params = new JsonArray();
        params.add(new JsonObject());
        return call("load_task_list", params);
    }

    public RpcCallResult startTask(String sceneId, boolean parallel) throws IOException {
        JsonObject task = new JsonObject();
        task.addProperty("name", sceneId);
        task.add("params", new JsonArray());
        task.addProperty("dir", "");
        task.addProperty("is_parallel", parallel);
        task.addProperty("loop_to", 1);
        JsonArray params = new JsonArray();
        params.add(task);
        return call("start_task", params);
    }

    public RpcCallResult waitTask(int taskId) throws IOException {
        JsonObject arg = new JsonObject();
        arg.addProperty("id", taskId);
        JsonArray params = new JsonArray();
        params.add(arg);
        return call("wait_task", params);
    }

    public RpcCallResult loadTask(int taskId) throws IOException {
        JsonObject arg = new JsonObject();
        arg.addProperty("id", taskId);
        JsonArray params = new JsonArray();
        params.add(arg);
        return call("load_task", params);
    }

    public RpcCallResult cancelTask(int taskId) throws IOException {
        JsonObject arg = new JsonObject();
        arg.addProperty("id", taskId);
        JsonArray params = new JsonArray();
        params.add(arg);
        return call("cancel_task", params);
    }

    /**
     * 等价于上位机 RunSceneUntilDone：start_task + wait_task。
     *
     * @return 若 start_task 返回 task id，会写入 {@link RpcCallResult#taskId}
     */
    public RpcCallResult runSceneUntilDone(String sceneId) throws IOException {
        RpcCallResult start = startTask(sceneId, false);
        if (!start.success) {
            return start;
        }
        Integer taskId = start.taskId;
        if (taskId == null) {
            return RpcCallResult.failure(start.url, start.requestJson, start.httpCode, start.responseBody,
                    "start_task 成功但未解析到 task id");
        }
        RpcCallResult wait = waitTask(taskId);
        wait.taskId = taskId;
        wait.sceneId = sceneId;
        return wait;
    }

    private static RpcCallResult parseResponse(
            String url,
            String requestJson,
            int httpCode,
            String responseBody
    ) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            if (root.has("error") && !root.get("error").isJsonNull()) {
                JsonObject error = root.getAsJsonObject("error");
                String message = error.has("message") ? error.get("message").getAsString() : error.toString();
                int code = error.has("code") ? error.get("code").getAsInt() : -1;
                return RpcCallResult.failure(url, requestJson, httpCode, responseBody,
                        "RPC error " + code + ": " + message);
            }
            RpcCallResult result = RpcCallResult.success(url, requestJson, httpCode, responseBody);
            if (root.has("result") && root.get("result").isJsonObject()) {
                JsonObject res = root.getAsJsonObject("result");
                if (res.has("id") && !res.get("id").isJsonNull()) {
                    result.taskId = res.get("id").getAsInt();
                }
                if (res.has("state") && !res.get("state").isJsonNull()) {
                    result.taskState = res.get("state").getAsString();
                }
                if (res.has("done") && !res.get("done").isJsonNull()) {
                    result.taskDone = res.get("done").getAsBoolean();
                }
            }
            return result;
        } catch (Exception e) {
            return RpcCallResult.failure(url, requestJson, httpCode, responseBody,
                    "解析响应失败: " + e.getMessage());
        }
    }

    private static void logHttp(String message) {
        Log.i(TAG, message);
    }

    public static final class RpcCallResult {
        public final boolean success;
        public final String url;
        public final String requestJson;
        public final int httpCode;
        public final String responseBody;
        public final String errorMessage;
        public Integer taskId;
        public String taskState;
        public Boolean taskDone;
        public String sceneId;

        private RpcCallResult(
                boolean success,
                String url,
                String requestJson,
                int httpCode,
                String responseBody,
                String errorMessage
        ) {
            this.success = success;
            this.url = url;
            this.requestJson = requestJson;
            this.httpCode = httpCode;
            this.responseBody = responseBody;
            this.errorMessage = errorMessage;
        }

        static RpcCallResult success(String url, String requestJson, int httpCode, String responseBody) {
            return new RpcCallResult(true, url, requestJson, httpCode, responseBody, "");
        }

        static RpcCallResult failure(
                String url,
                String requestJson,
                int httpCode,
                String responseBody,
                String errorMessage
        ) {
            return new RpcCallResult(false, url, requestJson, httpCode, responseBody, errorMessage);
        }

        public String formatForDisplay() {
            StringBuilder builder = new StringBuilder();
            builder.append("URL\n").append(url).append("\n\n");
            builder.append("Request\n").append(requestJson).append("\n\n");
            builder.append("HTTP ").append(httpCode).append("\n\n");
            builder.append("Response\n").append(responseBody).append("\n\n");
            builder.append("Parsed\n");
            builder.append("success=").append(success).append('\n');
            if (taskId != null) {
                builder.append("taskId=").append(taskId).append('\n');
            }
            if (sceneId != null) {
                builder.append("sceneId=").append(sceneId).append('\n');
            }
            if (taskState != null) {
                builder.append("taskState=").append(taskState).append('\n');
            }
            if (taskDone != null) {
                builder.append("taskDone=").append(taskDone).append('\n');
            }
            if (!success && errorMessage != null && !errorMessage.isEmpty()) {
                builder.append("error=").append(errorMessage);
            }
            return builder.toString();
        }
    }
}
