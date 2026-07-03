package com.example.test5.wake;

import androidx.annotation.Nullable;

/** 前台唤醒服务当前状态，供首页展示。 */
public final class WakeStatusHolder {

    public enum State {
        INIT,
        AUTH_WAIT,
        AUTH_FAILED,
        LISTENING,
        PAUSED_RTC,
        TRIGGERED,
        ERROR
    }

    private static volatile State state = State.INIT;
    private static volatile String detail = "";

    private WakeStatusHolder() {
    }

    public static void update(State newState, @Nullable String newDetail) {
        state = newState;
        detail = newDetail != null ? newDetail : "";
    }

    public static State getState() {
        return state;
    }

    public static String getDetail() {
        return detail;
    }
}
