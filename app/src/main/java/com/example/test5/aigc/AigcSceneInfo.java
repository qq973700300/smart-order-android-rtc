package com.example.test5.aigc;

import com.google.gson.annotations.SerializedName;

/** /getScenes 返回的单场景 RTC 参数 */
public final class AigcSceneInfo {

    public static final class SceneMeta {
        public String id;
        public String name;
        @SerializedName("botName")
        public String botName;
        /** 服务端 VISION_ENABLE → getScenes 注入，Android 据此决定是否开摄像头 */
        public boolean isVision;
    }

    public static final class RtcInfo {
        @SerializedName("AppId")
        public String appId;
        @SerializedName("RoomId")
        public String roomId;
        @SerializedName("UserId")
        public String userId;
        @SerializedName("Token")
        public String token;
    }

    public SceneMeta scene;
    public RtcInfo rtc;
}
