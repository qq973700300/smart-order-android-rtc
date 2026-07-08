package com.example.test5.device.tashi;

import android.content.Context;

/**
 * 语音取料：委托 {@link StockBinConnectionManager} 长连接出货。
 */
public final class StockBinVoiceController {

    private StockBinVoiceController() {
    }

    public static Result pick(Context context, String slotCode) {
        return StockBinConnectionManager.getInstance(context).pick(slotCode);
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
