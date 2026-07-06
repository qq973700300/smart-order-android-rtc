package com.example.test5.wake;

/** 唤醒前台服务总开关；调试 RTC 音频时可设为 false。 */
public final class WakeConfig {

    /** false = 不启动唤醒服务、不初始化 AIKit、不占麦克风 */
    public static final boolean ENABLE_WAKE_SERVICE = true;

    private WakeConfig() {
    }
}
