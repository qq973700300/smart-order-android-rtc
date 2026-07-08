package com.example.test5.device.opcua;

import android.content.Context;

/** 语音控制滚筒锅：委托 {@link DrumPotConnectionManager} 长连接。 */
public final class DrumPotVoiceController {

    public enum Action {
        START,
        STOP,
        RESET,
        ROTATE_START,
        ROTATE_STOP
    }

    private DrumPotVoiceController() {
    }

    public static Result control(Context context, Action action, int gear) {
        return DrumPotConnectionManager.getInstance(context).control(action, gear);
    }

    public static final class Result {
        public final boolean ok;
        public final String message;

        private Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }

        public static Result ok(String message) {
            return new Result(true, message);
        }

        public static Result fail(String message) {
            return new Result(false, message);
        }
    }
}
