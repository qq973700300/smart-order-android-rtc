package com.example.test5.device.yuejiang;

import android.content.Context;

import com.example.test5.device.TcpTextClient;

/** 越疆机械臂一次性脚本执行（流程引擎用）。 */
public final class YuejiangRobotController {

    private YuejiangRobotController() {
    }

    public static Result runScript(Context context, String scriptName) {
        if (scriptName == null || scriptName.trim().isEmpty()) {
            return Result.fail("脚本名不能为空");
        }
        String script = scriptName.trim();
        TcpTextClient client = new TcpTextClient();
        try {
            client.connect(YuejiangConfig.DEFAULT_HOST, YuejiangConfig.DEFAULT_PORT, 5000);
            client.send(YuejiangConfig.runScript(script) + "\n");
            Thread.sleep(300);
            String response = client.drainReceivedText();
            String message = response == null || response.isEmpty()
                    ? "已发送脚本 " + script
                    : "脚本 " + script + "：" + response.trim();
            return Result.ok(message);
        } catch (Exception e) {
            return Result.fail("越疆机械臂失败: " + e.getMessage());
        } finally {
            client.disconnect();
        }
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
