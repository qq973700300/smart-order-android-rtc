package com.example.test5;

import android.app.Application;

import com.example.test5.wake.IflytekSdkHolder;
import com.example.test5.wake.WakeConfig;

/** 应用入口：初始化讯飞 AIKit（首次需联网鉴权，之后可离线）。 */
public class SmartOrderApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        if (WakeConfig.ENABLE_WAKE_SERVICE) {
            IflytekSdkHolder.init(this);
        }
    }
}
