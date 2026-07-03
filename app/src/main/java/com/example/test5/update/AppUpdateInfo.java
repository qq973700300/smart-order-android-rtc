package com.example.test5.update;

/** 服务端 /appUpdateCheck 返回的更新信息 */
public class AppUpdateInfo {

    public boolean hasUpdate;
    public int versionCode;
    public String versionName;
    public String apkUrl;
    public String changelog;
    public boolean forceUpdate;
}
