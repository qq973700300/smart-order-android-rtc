package com.example.test5.order;

import android.content.Context;
import android.content.Intent;

import com.example.test5.FlowProductionRunActivity;

/** 启动流程运行监视页并触发送厨。 */
public final class FlowProductionLauncher {

    private static volatile RestaurantFunctionHandler pendingHandler;

    private FlowProductionLauncher() {
    }

    public static void launchManualSubmit(Context context, RestaurantFunctionHandler handler) {
        pendingHandler = handler;
        Intent intent = new Intent(context, FlowProductionRunActivity.class);
        context.startActivity(intent);
    }

    public static RestaurantFunctionHandler consumeHandler() {
        RestaurantFunctionHandler handler = pendingHandler;
        pendingHandler = null;
        return handler;
    }
}
