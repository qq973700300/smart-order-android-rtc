package com.example.test5.order;

import android.content.Context;

import com.example.test5.device.opcua.DrumPotVoiceController;
import com.example.test5.device.tashi.StockBinVoiceController;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/** 料仓 / 滚筒等设备 Function Calling。 */
public final class DeviceFunctionHandler {

    public static final String TOOL_STOCK_BIN_PICK = "stock_bin_pick";
    public static final String TOOL_DRUM_POT_CONTROL = "drum_pot_control";

    private final Context appContext;
    private final Gson gson = new Gson();

    public DeviceFunctionHandler(Context context) {
        appContext = context.getApplicationContext();
    }

    /** 若 toolName 非设备工具则返回 null。 */
    public String execute(String toolName, String argumentsJson) {
        try {
            JsonObject args = argumentsJson == null || argumentsJson.isEmpty()
                    ? new JsonObject()
                    : gson.fromJson(argumentsJson, JsonObject.class);
            switch (toolName) {
                case TOOL_STOCK_BIN_PICK:
                    return toJson(pickStock(args));
                case TOOL_DRUM_POT_CONTROL:
                    return toJson(controlDrumPot(args));
                default:
                    return null;
            }
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    private StockBinVoiceController.Result pickStock(JsonObject args) {
        String slotCode = getString(args, "slot_code");
        if (slotCode.isEmpty()) {
            slotCode = getString(args, "code");
        }
        return StockBinVoiceController.pick(appContext, slotCode);
    }

    private DrumPotVoiceController.Result controlDrumPot(JsonObject args) {
        String actionText = getString(args, "action").toLowerCase();
        int gear = getInt(args, "gear", 1);
        DrumPotVoiceController.Action action;
        switch (actionText) {
            case "start":
            case "启动":
                action = DrumPotVoiceController.Action.START;
                break;
            case "stop":
            case "停止":
                action = DrumPotVoiceController.Action.STOP;
                break;
            case "reset":
            case "复位":
                action = DrumPotVoiceController.Action.RESET;
                break;
            case "rotate_start":
            case "rotate":
            case "旋转":
            case "开始旋转":
                action = DrumPotVoiceController.Action.ROTATE_START;
                break;
            case "rotate_stop":
            case "停止旋转":
            case "停转":
                action = DrumPotVoiceController.Action.ROTATE_STOP;
                break;
            default:
                return DrumPotVoiceController.Result.fail("未知 action: " + actionText);
        }
        return DrumPotVoiceController.control(appContext, action, gear);
    }

    private String toJson(StockBinVoiceController.Result result) {
        JsonObject node = new JsonObject();
        node.addProperty("ok", result.ok);
        node.addProperty("message", result.message);
        return gson.toJson(node);
    }

    private String toJson(DrumPotVoiceController.Result result) {
        JsonObject node = new JsonObject();
        node.addProperty("ok", result.ok);
        node.addProperty("message", result.message);
        return gson.toJson(node);
    }

    private static String getString(JsonObject args, String key) {
        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString().trim() : "";
    }

    private static int getInt(JsonObject args, String key, int defaultValue) {
        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsInt() : defaultValue;
    }

    private String error(String message) {
        JsonObject node = new JsonObject();
        node.addProperty("ok", false);
        node.addProperty("message", message == null ? "" : message);
        return gson.toJson(node);
    }
}
