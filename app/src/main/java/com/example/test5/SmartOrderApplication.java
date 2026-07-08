package com.example.test5;

import android.app.Application;

import com.example.test5.device.opcua.DrumPotConnectionManager;
import com.example.test5.device.tashi.StockBinConnectionManager;
import com.example.test5.net.NetworkDiagnostics;
import com.example.test5.recipe.DishsConfigStore;
import com.example.test5.wake.IflytekSdkHolder;
import com.example.test5.wake.WakeConfig;

/** 应用入口：初始化讯飞 AIKit（首次需联网鉴权，之后可离线）。 */
public class SmartOrderApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NetworkDiagnostics.init(this);
        StockBinConnectionManager.getInstance(this).connectAsync();
        DrumPotConnectionManager.getInstance(this).connectAsync();
        new Thread(() -> DishsConfigStore.ensureLoaded(this), "DishsConfigInit").start();
        if (WakeConfig.ENABLE_WAKE_SERVICE) {
            IflytekSdkHolder.init(this);
        }
    }
}
