package com.example.test5.wake;

import android.content.Context;
import android.util.Log;

import com.example.test5.R;
import com.iflytek.aikit.core.AiHelper;
import com.iflytek.aikit.core.BaseLibrary;
import com.iflytek.aikit.core.CoreListener;
import com.iflytek.aikit.core.ErrType;
import com.iflytek.aikit.core.LogLvl;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** AIKit SDK 初始化与鉴权状态（单例）。 */
public final class IflytekSdkHolder {

    private static final String TAG = "IflytekSdk";
    /** IVW 离线唤醒能力 ID，须与控制台及 SDK 包一致。 */
    public static final String IVW_ABILITY_ID = "e867a88f2";
    private static final AtomicBoolean initializing = new AtomicBoolean(false);
    private static final AtomicInteger authCode = new AtomicInteger(-1);

    private IflytekSdkHolder() {
    }

    public static void init(Context context) {
        Context app = context.getApplicationContext();
        if (!initializing.compareAndSet(false, true)) {
            return;
        }
        String workDir = getWorkDir(app).getAbsolutePath();
        if (!workDir.endsWith(File.separator)) {
            workDir += File.separator;
        }
        File logDir = new File(workDir, "logs");
        //noinspection ResultOfMethodCallIgnored
        logDir.mkdirs();
        String resDir = getIvwDir(app).getAbsolutePath();
        if (!resDir.endsWith(File.separator)) {
            resDir += File.separator;
        }

        BaseLibrary.Params params = BaseLibrary.Params.builder()
                .appId(app.getString(R.string.iflytek_app_id))
                .apiKey(app.getString(R.string.iflytek_api_key))
                .apiSecret(app.getString(R.string.iflytek_api_secret))
                .workDir(workDir)
                .resDir(resDir)
                .ability(IVW_ABILITY_ID)
                .build();

        AiHelper.getInst().setLogInfo(LogLvl.INFO, 1,
                new File(logDir, "aikit.log").getAbsolutePath());
        AiHelper.getInst().registerListener(new CoreListener() {
            @Override
            public void onAuthStateChange(ErrType type, int code) {
                if (type == ErrType.AUTH) {
                    authCode.set(code);
                    Log.i(TAG, "AIKit 鉴权结果: " + code);
                } else {
                    Log.w(TAG, "AIKit 事件 type=" + type + " code=" + code);
                }
            }
        });

        new Thread(() -> AiHelper.getInst().initEntry(app, params), "aikit-init").start();
    }

    public static boolean isAuthorized() {
        return authCode.get() == 0;
    }

    public static int getAuthCode() {
        return authCode.get();
    }

    public static File getWorkDir(Context context) {
        return new File(context.getFilesDir(), "iflytek");
    }

    /** IVW 模型与 keyword.txt 所在目录（workDir/aikit_resources/ivw/）。 */
    public static File getIvwDir(Context context) {
        return new File(getWorkDir(context), "aikit_resources/ivw");
    }

    /** 引擎 loadData / start 阶段错误说明。 */
    public static String describeEngineError(int code) {
        switch (code) {
            case 18105:
                return "workDir 内未找到 IVW 资源，请检查 aikit_resources/ivw 是否解压成功";
            case 18301:
                return "SDK 未初始化或鉴权未完成";
            case 18304:
                return "参数不合法，检查 keyword.txt 路径与门限格式";
            case 600100:
                return "唤醒资源路径错误";
            case 600127:
                return "加载资源到唤醒引擎失败，唤醒词可能与 bundled 资源不匹配";
            case 100011:
                return "个性化资源加载失败，常见原因：资源路径不对，或自定义唤醒词未在控制台制作";
            default:
                return "详见 Logcat 标签 IflytekWake / aikit.log";
        }
    }
    public static String describeAuthError(int code) {
        switch (code) {
            case 18007:
                return "apiKey / apiSecret 与 appId 不匹配";
            case 18200:
                return "SDK 与控制台应用不一致，请用控制台下载的 SDK";
            case 18401:
                return "未获取到设备标识，可尝试授予电话状态权限";
            case 18601:
                return "能力 ID 未授权或 appId 配置有误";
            case 18700:
                return "IVW 能力未开通，请在控制台申请离线语音唤醒";
            case 18706:
                return "当前 CPU 架构不支持（如 x86 模拟器）";
            case 18707:
                return "授权已过期，请在控制台续期";
            case 18708:
                return "无可用授权：设备架构不支持，或设备授权数已满";
            case 18717:
                return "组合能力授权量已用完";
            default:
                return "详见讯飞文档错误码表";
        }
    }
}
