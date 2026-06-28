package com.example.test5.order;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class HttpHelper {

    private static final String TAG = "HttpHelper";
    private static final String ORDER_SUBSCRIBE_URL = "https://www.xaerss.com/business/OrderSubscribe";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    public static final class OrderSubscribeResult {
        public final boolean success;
        public final String msg;
        public final String orderNumber;
        public final long subscribeId;

        OrderSubscribeResult(boolean success, String msg, String orderNumber, long subscribeId) {
            this.success = success;
            this.msg = msg;
            this.orderNumber = orderNumber;
            this.subscribeId = subscribeId;
        }

        static OrderSubscribeResult failure(String msg) {
            return new OrderSubscribeResult(false, msg, "", 0L);
        }
    }

    /** 调试页用：含原始 HTTP 与解析结果 */
    public static final class OrderSubscribeDebugResult {
        public final String requestUrl;
        public final String requestBody;
        public final int httpCode;
        public final String responseBody;
        public final OrderSubscribeResult parsed;

        OrderSubscribeDebugResult(
                String requestUrl,
                String requestBody,
                int httpCode,
                String responseBody,
                OrderSubscribeResult parsed
        ) {
            this.requestUrl = requestUrl;
            this.requestBody = requestBody;
            this.httpCode = httpCode;
            this.responseBody = responseBody;
            this.parsed = parsed;
        }
    }

    private HttpHelper() {
    }

    public static OrderSubscribeResult submitOrder(
            int storeId,
            String equipmentNum,
            String topic,
            String message
    ) throws IOException {
        return submitOrderWithDetails(storeId, equipmentNum, topic, message).parsed;
    }

    public static OrderSubscribeDebugResult submitOrderWithDetails(
            int storeId,
            String equipmentNum,
            String topic,
            String message
    ) throws IOException {
        JsonObject body = new JsonObject();
        body.addProperty("storeId", storeId);
        body.addProperty("equipmentNum", equipmentNum == null ? "" : equipmentNum);
        body.addProperty("topic", topic);
        body.addProperty("message", message);

        String bodyJson = body.toString();
        logHttp("POST " + ORDER_SUBSCRIBE_URL);
        logHttp("Request body: " + bodyJson);

        Request request = new Request.Builder()
                .url(ORDER_SUBSCRIBE_URL)
                .post(RequestBody.create(bodyJson, JSON))
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            int httpCode = response.code();
            String responseBody = response.body() != null ? response.body().string() : "";
            logHttp("Response HTTP " + httpCode + ": " + responseBody);

            OrderSubscribeResult parsed;
            if (!response.isSuccessful()) {
                Log.w(TAG, "OrderSubscribe failed: HTTP " + httpCode);
                parsed = OrderSubscribeResult.failure("HTTP " + httpCode);
            } else {
                parsed = parseResponse(responseBody);
                if (parsed.success) {
                    Log.i(TAG, "OrderSubscribe ok: orderNumber=" + parsed.orderNumber
                            + ", subscribeId=" + parsed.subscribeId);
                } else {
                    Log.w(TAG, "OrderSubscribe rejected: " + parsed.msg);
                }
            }
            return new OrderSubscribeDebugResult(
                    ORDER_SUBSCRIBE_URL, bodyJson, httpCode, responseBody, parsed);
        } catch (IOException e) {
            Log.e(TAG, "OrderSubscribe network error", e);
            throw e;
        }
    }

    private static void logHttp(String message) {
        Log.i(TAG, message);
    }

    private static OrderSubscribeResult parseResponse(String body) {
        try {
            Map<String, Object> root = GSON.fromJson(body, MAP_TYPE);
            if (root == null) {
                return OrderSubscribeResult.failure("空响应");
            }
            Object codeObj = root.get("code");
            int code = codeObj instanceof Number ? ((Number) codeObj).intValue() : -1;
            String msg = root.get("msg") != null ? String.valueOf(root.get("msg")) : "";
            if (code != 200) {
                return OrderSubscribeResult.failure(msg.isEmpty() ? "code=" + code : msg);
            }
            Object dataObj = root.get("data");
            if (!(dataObj instanceof Map)) {
                return new OrderSubscribeResult(true, msg, "", 0L);
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) dataObj;
            String orderNumber = data.get("orderNumber") != null
                    ? String.valueOf(data.get("orderNumber")) : "";
            long subscribeId = 0L;
            Object subscribeIdObj = data.get("subscribeId");
            if (subscribeIdObj instanceof Number) {
                subscribeId = ((Number) subscribeIdObj).longValue();
            }
            return new OrderSubscribeResult(true, msg, orderNumber, subscribeId);
        } catch (Exception e) {
            return OrderSubscribeResult.failure("解析失败: " + e.getMessage());
        }
    }
}
