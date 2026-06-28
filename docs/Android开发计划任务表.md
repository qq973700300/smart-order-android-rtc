# 智能点餐 · Android 开发计划任务表

> **交付物**：`test5` Android APK（门店平板/手机语音点餐）  
> **工程路径**：`D:\Program Files\AndroidProjects\test5`  
> **技术栈**：VolcEngineRTC + OkHttp 代理 + Function Calling + OrderSubscribe + Room 离线  
> **人力**：1 人 · **8h/天**  
> **更新**：2026-06-22

---

## 一、Android 在整体架构中的位置

```text
┌─────────────────────────────────────────────────────────┐
│  test5 Android（本计划全部在此工程内开发）                 │
│  VoiceClerkActivity / RtcAigcManager / FunctionHandler   │
│  HttpHelper → OrderSubscribe                             │
│  Room + WorkManager（离线，A6 阶段）                       │
└───────────────────────┬─────────────────────────────────┘
                        │ OkHttp HTTP
                        ▼
┌─────────────────────────────────────────────────────────┐
│  Node 3001 或 Spring 8080（代理，AK/SK 不进 APK）         │
│  getScenes / StartVoiceChat / StopVoiceChat              │
└───────────────────────┬─────────────────────────────────┘
                        │ 火山 OpenAPI
                        ▼
                   火山 RTC-AIGC 云端
```

**说明**：Web Demo 仅用于你本人验证凭证；**不计入 Android 开发工时**。Android 开发只需保证 PC 上代理已跑通、`Restaurant.json` 已填好。

---

## 二、现有代码与待办对照

| 状态 | Android 文件 | 说明 |
|------|--------------|------|
| ✅ 已有 | `VoiceClerkActivity.java` | 主界面：开始/停止、状态、购物车、字幕 |
| ✅ 已有 | `MainActivity.java` | 入口（待扩展导航） |
| ✅ 已有 | `aigc/AigcProxyApi.java` | getScenes、Start/StopVoiceChat |
| ✅ 已有 | `aigc/RtcAigcManager.java` | 进房、采音、TLV tool/func、字幕 |
| ✅ 已有 | `aigc/TlvCodec.java` | 二进制 TLV 编解码 |
| ✅ 已有 | `order/RestaurantFunctionHandler.java` | 四 tool + 内存购物车 + 送厨 |
| ✅ 已有 | `order/HttpHelper.java` | OrderSubscribe |
| ✅ 已有 | `order/MenuCatalog.java`、`StoreConfig.java` | 菜单与门店 |
| ⬜ 待做 | `local.properties` | 真机 `AIGC_PROXY_HOST` |
| ⬜ 待做 | Gradle Sync / 真机安装 | 依赖与编译 |
| ⬜ 待做 | 全链路联调验收 | RTC + FC + 送厨 |
| ⬜ 待做 | 从 test3 迁移 UI | Live2D、手动点餐、调试页 |
| ⬜ 待做 | `data/` 包 | Room、SyncEngine、WorkManager |

---

## 三、工时总览（纯 Android）

| 阶段 | 目标 | 任务数 | 工时 | 天数 |
|------|------|--------|------|------|
| **A0** | 工程配置 + 代理连通 + 首次安装 | 8 | **8h** | **1 天** |
| **A1** | RTC 进房 + 双向语音 | 9 | **16h** | **2 天** |
| **A2** | Function Calling 购物车 | 10 | **12h** | **1.5 天** |
| **A3** | OrderSubscribe 送厨 | 6 | **8h** | **1 天** |
| **A4** | 主链路验收 + 真机 | 6 | **4h** | **0.5 天** |
| **A5** | UI / Live2D / 手动点餐 | 11 | **44h** | **5.5 天** |
| **A6** | 智慧厨房离线模式 | 32 | **112h** | **14 天** |
| **A7** | Release 发布上线 | 9 | **28h** | **3.5 天** |

| 路径 | 包含阶段 | 总工时 | 总天数 |
|------|----------|--------|--------|
| **最短**（跳过 A5 UI） | A0→A1→A2→A3→A4→A6→A7 | **188h** | **23.5 天** |
| **推荐**（含 UI） | A0→…→A7 全做 | **232h** | **29 天** |

> **阻塞前置**（非 Android 代码）：PC 上 Node/Spring 代理 + `Restaurant.json` 凭证。若未就绪，A0 无法完成，需先填凭证（约 0.5～1 天，在 PC 完成）。

---

## 四、推荐日历（含 UI，29 工作日）

| 天 | 阶段 | 任务 ID | 主题 |
|----|------|---------|------|
| D1 | A0 | A0-01～08 | Gradle + 代理 + 安装 |
| D2～D3 | A1 | A1-01～09 | RTC 进房与语音 |
| D4 | A2 | A2-01～10 | Function Calling |
| D5 | A3 | A3-01～06 | OrderSubscribe |
| D5 下午 | A4 | A4-01～06 | 主链路验收 |
| D6～D10 | A5 | A5-01～11 | UI 迁移 |
| D11～D24 | A6 | A6-01～32 | 离线模式 |
| D25～D29 | A7 | A7-01～09 | 发布上线 |

---

## 五、A0 · 工程配置与代理连通（8h · 8 项）

> **目标**：Android Studio Sync 成功，App 装到设备，能访问 PC 代理。

### A0-01 · Gradle Sync 成功（2h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | 用 Android Studio 打开 `test5`，Sync Project |
| 2 | 若报 `127.0.0.1:7890 Connection refused`：检查 `gradle.properties` 与用户目录 `~/.gradle/gradle.properties` 代理项 |
| 3 | 确认 `settings.gradle` 含火山 Maven：`https://artifact.bytedance.com/repository/Volcengine/` |
| 4 | Sync 至无红色错误 |

**涉及文件**：`gradle.properties`、`settings.gradle`、`gradle/libs.versions.toml`

---

### A0-02 · VolcEngineRTC 依赖就绪（1h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | 确认 `libs.versions.toml` → `volc-rtc = "3.60.102.4900"` |
| 2 | `app/build.gradle` → `implementation libs.volc.rtc` |
| 3 | `ndk { abiFilters 'armeabi-v7a', 'arm64-v8a' }` |
| 4 | External Libraries 可见 RTC AAR |

**涉及文件**：`app/build.gradle`、`gradle/libs.versions.toml`

---

### A0-03 · 配置代理地址（1h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | 模拟器：`build.gradle` 中 `AIGC_PROXY_HOST = "http://10.0.2.2:3001"`（已默认） |
| 2 | 真机：改为 PC 局域网 IP，如 `"http://192.168.1.100:3001"` |
| 3 | 或在 `local.properties` 增加 `AIGC_PROXY_HOST=...`，build.gradle 读取（可选优化） |
| 4 | Rebuild 使 `BuildConfig.AIGC_PROXY_HOST` 生效 |

**涉及文件**：`app/build.gradle`、`local.properties`（可选）

---

### A0-04 · 权限与网络安全（0.5h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | `AndroidManifest.xml`：`INTERNET`、`RECORD_AUDIO` |
| 2 | `network_security_config.xml`：允许 cleartext（开发期 HTTP 代理） |
| 3 | Manifest 引用 `android:networkSecurityConfig` |

**涉及文件**：`AndroidManifest.xml`、`res/xml/network_security_config.xml`

---

### A0-05 · 首次编译安装（1h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | Build → Run，选择模拟器或真机 |
| 2 | 打开 App → MainActivity → 进入 VoiceClerkActivity |
| 3 | 界面显示：状态、购物车、字幕、开始/停止按钮 |
| 4 | 无启动 crash |

**涉及文件**：`MainActivity.java`、`VoiceClerkActivity.java`

---

### A0-06 · 验证代理 HTTP 可达（1h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | PC 运行 Node `npm run dev`（3001） |
| 2 | 真机与 PC 同一 WiFi；Windows 防火墙放行 3001 |
| 3 | 点击「开始对话」，Logcat 过滤 `AigcProxy` / OkHttp |
| 4 | 若失败：区分「连不上 PC」vs「401 AK/SK」 |

**涉及文件**：`aigc/AigcProxyApi.java`

---

### A0-07 · fetchScene 解析（1h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | `AigcProxyApi.fetchScene("Restaurant")` 返回 200 |
| 2 | 解析 `AigcSceneInfo`：rtc.appId、roomId、userId、token |
| 3 | `scene.botName` 非空（用于回传 func 目标用户） |
| 4 | 失败时在 `VoiceClerkActivity` Toast 显示原因 |

**涉及文件**：`aigc/AigcSceneInfo.java`、`aigc/AigcProxyApi.java`

---

### A0-08 · 麦克风权限流程（0.5h）

| 子步骤 | Android 操作 |
|--------|--------------|
| 1 | 首次点「开始」弹出 RECORD_AUDIO 授权 |
| 2 | 拒绝时 Toast `R.string.mic_denied` |
| 3 | 授权后自动 `startSession()` |

**涉及文件**：`VoiceClerkActivity.java`、`res/values/strings.xml`

**A0 完成标准**：App 安装成功 + fetchScene HTTP 200（不要求此时已有语音）。

---

## 六、A1 · RTC 进房与双向语音（16h · 9 项）

> **目标**：对着 Android 设备说话，能听到 AI 语音回复。

### A1-01 · StartVoiceChat 调通（4h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `startSession()` 中先 `fetchScene` 再 `AigcProxyApi.startVoiceChat` |
| 2 | Logcat 确认无 `ResponseMetadata.Error` |
| 3 | 常见错误：InvalidAuthorization → 查 PC 端 AK/SK；AppId 不匹配 → 查 Restaurant.json |
| 4 | 成功后 `statusView` 显示「对话中」 |

**文件**：`VoiceClerkActivity.java`、`aigc/AigcProxyApi.java`

---

### A1-02 · RTCEngine 初始化（2h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `EngineConfig.appID = sceneInfo.rtc.appId` |
| 2 | `RTCEngine.createRTCEngine(engineConfig, handler)` |
| 3 | `onError` 回调 Log + UI 提示 |

**文件**：`aigc/RtcAigcManager.java`

---

### A1-03 · joinRoom（3h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `createRTCRoom(roomId)` + `setRTCRoomEventHandler` |
| 2 | `joinRoom(token, userInfo, roomConfig)` 返回 0 |
| 3 | `onRoomStateChanged` 状态为已连接 |
| 4 | Token 过期 / AppId 不匹配排查 |

**文件**：`aigc/RtcAigcManager.java`

---

### A1-04 · 本地音频采集（2h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `startAudioCapture()` 在 joinRoom 成功后调用 |
| 2 | 确认未静音、权限已授予 |
| 3 | 模拟器需配置虚拟麦克风 |

**文件**：`aigc/RtcAigcManager.java`

---

### A1-05 · 订阅远端 AI 音频（2h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 确认 AI bot 发布音频流（SDK 默认自动播放或需 subscribe） |
| 2 | 调节音量 / 音频路由 |
| 3 | 3～5 秒内听到 WelcomeMessage 播报 |

**文件**：`aigc/RtcAigcManager.java`

---

### A1-06 · 字幕 TLV 显示（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `handleBinaryMessage` 解析 `TYPE_SUBTITLE` |
| 2 | `VoiceClerkActivity.onSubtitle` 追加到 `subtitleView` |
| 3 | 区分 userId（用户 ASR vs AI 文本） |

**文件**：`RtcAigcManager.java`、`VoiceClerkActivity.java`

---

### A1-07 · StopVoiceChat + 离房（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 点停止 → `AigcProxyApi.stopVoiceChat` |
| 2 | `stopAudioCapture` → `leaveRoom` → `destroyRTCEngine` |
| 3 | 按钮状态恢复，可再次开始 |

**文件**：`VoiceClerkActivity.java`、`RtcAigcManager.java`

---

### A1-08 · 生命周期防泄漏（0.5h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `onDestroy` 调用 `rtcManager.release()` |
| 2 | `executor.shutdownNow()` |
| 3 | 旋转屏幕 / 返回键不 leak |

**文件**：`VoiceClerkActivity.java`

---

### A1-09 · 真机 WiFi 联调（0.5h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 真机改 `AIGC_PROXY_HOST` 为 PC IP |
| 2 | 完整：开始 → 说话 → 听回复 → 停止 |

**A1 完成标准**：Android 设备上完成一轮语音对话（不要求 tool  yet）。

---

## 七、A2 · Function Calling（12h · 10 项）

> **目标**：说「来一份芹菜炒肉」，App 购物车 TextView 更新；AI 继续播报。

### A2-01 · 确认收到二进制消息（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | Logcat `RtcAigcManager`：`onUserBinaryMessageReceived` 有 byte 长度 |
| 2 | 确认 `botUserId` 来自 `sceneInfo.scene.botName` |

**文件**：`RtcAigcManager.java`

---

### A2-02 · TLV 解析 type=tool（2h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `TlvCodec.decode` 得到 type + JSON |
| 2 | 解析 `tool_calls[0].function.name` 和 `arguments` |
| 3 | Log：`FunctionCall add_dish {...}` |

**文件**：`aigc/TlvCodec.java`、`RtcAigcManager.java`

---

### A2-03 · add_dish 执行（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `RestaurantFunctionHandler.execute("add_dish", args)` |
| 2 | 校验 `MenuCatalog.isValidDish` |
| 3 | 内存 `LinkedHashMap` 数量 +1 |

**文件**：`order/RestaurantFunctionHandler.java`、`order/MenuCatalog.java`

---

### A2-04 · remove_dish 执行（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 已有菜时 quantity 置 0 |
| 2 | 无菜时返回 `ok:false` JSON |

**文件**：`order/RestaurantFunctionHandler.java`

---

### A2-05 · get_cart 执行（0.5h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 返回 JSON：`cart_text` 字段 |
| 2 | AI 口播与 `cart_text` 一致 |

**文件**：`order/RestaurantFunctionHandler.java`

---

### A2-06 · 回传 TLV type=func（3h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 构造 `{"ToolCallID":"...","Content":"..."}` |
| 2 | `TlvCodec.encode(TYPE_FUNCTION_RESULT, json)` |
| 3 | `rtcRoom.sendUserBinaryMessage(botUserId, tlv, RELIABLE_ORDERED)` |
| 4 | AI 收到后继续 TTS |

**文件**：`RtcAigcManager.java`、`TlvCodec.java`

---

### A2-07 · 购物车 UI 刷新（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `CartListener.onCartUpdated` → 主线程 `cartView.setText` |
| 2 | add/remove/get 后 UI 即时更新 |

**文件**：`VoiceClerkActivity.java`、`RtcAigcManager.java`

---

### A2-08 · submit_order 先走 mock（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 空车返回错误 JSON |
| 2 | 非空车暂返回 `ok:true` 模拟（A3 再接 HTTP） |

**文件**：`order/RestaurantFunctionHandler.java`

---

### A2-09 · 多 tool 连续调用（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 连续加 3 道菜，每次 tool 独立回 func |
| 2 | 购物车累计正确 |

---

### A2-10 · FC 异常不崩溃（0.5h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 畸形 JSON、未知 tool 名 → 返回 error JSON，不 crash |
| 2 | `handleBinaryMessage` try-catch |

**A2 完成标准**：四条 tool 在 Android 上均可触发，购物车 UI 正确（submit 可先 mock）。

---

## 八、A3 · OrderSubscribe 送厨（8h · 6 项）

> **目标**：语音「点好了」→ 真实 HTTP 送厨 → 后端 code 200。

### A3-01 · submit_order 接 HttpHelper（3h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `submitOrderSync()` 遍历 cart，调 `HttpHelper.submitOrder` |
| 2 | 每份间隔 300ms（与现实现一致） |
| 3 | 成功份数扣减 cart，失败保留 |

**文件**：`order/RestaurantFunctionHandler.java`、`order/HttpHelper.java`

---

### A3-02 · 门店参数确认（0.5h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `StoreConfig.STORE_ID = 10027` |
| 2 | `STORE_NAME = 深圳云厨` |
| 3 | message 格式 `{菜名:序号}` |

**文件**：`order/StoreConfig.java`

---

### A3-03 · 成功/失败 UI 反馈（2h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 送厨成功：Toast + 清空购物车显示 |
| 2 | 失败：Toast 显示 `result.msg` |
| 3 | AI 收到 func Content 后继续播报「机器开始做了」 |

**文件**：`RestaurantFunctionHandler.java`、`VoiceClerkActivity.java`

---

### A3-04 · HttpHelper 日志（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | Logcat TAG=`HttpHelper`：URL、body、response、httpCode |
| 2 | 便于门店现场排查 |

**文件**：`order/HttpHelper.java`

---

### A3-05 · 网络不可用处理（1h）

| 子步骤 | 操作 |
|--------|------|
| 1 | IOException → 返回 `ok:false` 给 AI |
| 2 | 不 ANR（已在后台 executor） |

**文件**：`RestaurantFunctionHandler.java`

---

### A3-06 · 送厨用例验收（0.5h）

| 用例 | 预期 |
|------|------|
| 单菜 x1 | 1 次 HTTP，code 200 |
| 同菜 x2 | 2 次 HTTP |
| 空车 submit | 拒绝，无 HTTP |
| 多菜 | 按菜循环提交 |

**A3 完成标准**：Android 语音点餐 → 真实送厨成功。

---

## 九、A4 · 主链路验收（4h · 6 项）

| ID | 任务 | 子步骤 | 工时 |
|----|------|--------|------|
| A4-01 | 完整流程 | 开始 → 加 2 菜 → get_cart → submit → 停止 | 1h |
| A4-02 | 取消菜 | add 后 remove，再 submit 不应含已取消菜 | 0.5h |
| A4-03 | 代理断开 | 关 Node，点开始，Toast 友好提示 | 0.5h |
| A4-04 | 弱网 | 限速或断网瞬间，不 crash | 0.5h |
| A4-05 | 真机门店网络 | 门店 WiFi 下全链路 | 1h |
| A4-06 | Bug 缓冲 | 修复联调问题 | 0.5h |

**A4 完成标准**：可对外演示「Android 语音点餐 + 送厨」最小可用版本（MVP）。

---

## 十、A5 · UI 与体验（44h · 11 项）

> **来源**：从 `test3` 迁移到 `test5`，语音内核保持 RTC-AIGC，不用 Dialog 词匹配。

### A5-01 · Live2D 模块迁入（4h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 复制 test3 的 `com.live2d.demo` 包 + `assets/` 模型 |
| 2 | `settings.gradle` 若有独立 module 则 include |
| 3 | Sync 编译通过 |

**新建/复制**：`com/live2d/...`、`assets/live2d/`

---

### A5-02 · ClerkLive2DView 嵌入（4h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 从 test3 复制 `ClerkLive2DView.java` |
| 2 | `activity_voice_clerk.xml` 增加 Live2D 容器 |
| 3 | `VoiceClerkActivity` 初始化 Live2D |

**文件**：`ClerkLive2DView.java`、`res/layout/activity_voice_clerk.xml`

---

### A5-03 · 店员状态联动（4h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 状态枚举：idle / listening / thinking / speaking |
| 2 | RTC 状态、字幕到达时切换 Live2D 动作 |
| 3 | `statusView` 与状态条一致 |

**文件**：`VoiceClerkActivity.java`、`ClerkLive2DView.java`

---

### A5-04 · 字幕区优化（4h）

| 子步骤 | 操作 |
|--------|------|
| 1 | RecyclerView 或 ScrollView 替代无限 append |
| 2 | 区分用户/AI 样式（颜色或前缀） |
| 3 | 自动滚到底部 |

**文件**：`activity_voice_clerk.xml`、`VoiceClerkActivity.java`

---

### A5-05 · 订单摘要条（3h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 顶部或侧边显示「当前 N 道菜 · 合计」 |
| 2 | 与 `RestaurantFunctionHandler` 购物车同步 |

---

### A5-06 · 手动点餐页（6h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 复制 test3 `ManualOrderActivity` + `MenuDishAdapter` + 布局 |
| 2 | 网格展示 `MenuCatalog` 10 道菜 |
| 3 | +/- 按钮改购物车 |

**新建**：`ManualOrderActivity.java`、`MenuDishAdapter.java`、`res/layout/activity_manual_order.xml`

---

### A5-07 · 语音/手动共用购物车（4h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 抽取 `CartRepository`（单例或 ViewModel） |
| 2 | `RestaurantFunctionHandler` 与手动页都调 Repository |
| 3 | 两页面数据一致 |

**新建**：`order/CartRepository.java`；改 `RestaurantFunctionHandler.java`

---

### A5-08 · OrderSubscribe 调试页（3h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 复制 test3 `OrderSubscribeDebugActivity` |
| 2 | Manifest 注册 |
| 3 | 可手动填菜名送厨测接口 |

**新建**：`OrderSubscribeDebugActivity.java`

---

### A5-09 · 主导航（3h）

| 子步骤 | 操作 |
|--------|------|
| 1 | `MainActivity` 三入口：语音店员 / 手动点餐 / 接口调试 |
| 2 | Material 按钮或 Card 布局 |

**文件**：`MainActivity.java`、`activity_main.xml`

---

### A5-10 · 权限与错误态 UI（4h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 首次麦克风说明 Dialog |
| 2 | RTC 失败、代理失败全屏或 Snackbar |
| 3 | 加载中 Progress |

---

### A5-11 · UI 联调缓冲（5h）

| 子步骤 | 操作 |
|--------|------|
| 1 | 间距、字体、Live2D 帧率 |
| 2 | 横屏/竖屏（若门店平板需固定方向） |

**A5 完成标准**：门店可演示级 UI，语音 + 手动双入口。

---

## 十一、A6 · 智慧厨房离线模式（112h · 32 项）

> **目标**：断网可点餐、送厨入本地队列；联网后 WorkManager 自动同步 OrderSubscribe。

### 6.1 设计（A6-01～04 · 8h）

| ID | 任务 | Android 产出 |
|----|------|--------------|
| A6-01 | 离线能力矩阵 | 文档：RTC 断/业务 HTTP 断 分别怎么处理 |
| A6-02 | Room 表设计 | `SyncQueueItem`、`SyncLog`、`CartSnapshot` Entity 草稿 |
| A6-03 | 同步流程 | FIFO + clientOpId 幂等流程图 |
| A6-04 | 临时单号规则 | `localOrderId = UUID` 生成时机 |

---

### 6.2 Room 本地存储（A6-05～10 · 16h）

| ID | 任务 | 涉及文件 | 工时 |
|----|------|----------|------|
| A6-05 | 引入 Room 依赖 | `app/build.gradle` | 1h |
| A6-06 | SyncQueueItem Entity + DAO | `data/entity/SyncQueueItem.java`、`data/dao/SyncQueueDao.java` | 3h |
| A6-07 | CartSnapshot / PendingOrder | `data/entity/CartSnapshot.java` | 3h |
| A6-08 | SyncLog 审计表 | `data/entity/SyncLog.java` | 2h |
| A6-09 | AppDatabase + Migration | `data/AppDatabase.java` | 2h |
| A6-10 | CartRepository 接 Room | `order/CartRepository.java` | 5h |

**A6-10 子步骤**：
1. `addDish/remove/getCart` 写内存 + 异步 persist  
2. `RestaurantFunctionHandler` 改为调 `CartRepository`  
3. `ManualOrderActivity` 同样调 Repository  

---

### 6.3 离线操作（A6-11～16 · 16h）

| ID | 任务 | 涉及文件 | 工时 |
|----|------|----------|------|
| A6-11 | NetworkMonitor | `util/NetworkMonitor.java` | 2h |
| A6-12 | 离线 Banner UI | `VoiceClerkActivity`、layout | 2h |
| A6-13 | 离线 add/remove/get 纯本地 | `CartRepository.java` | 3h |
| A6-14 | 在线 submit → HttpHelper | 保持 A3 逻辑 | 2h |
| A6-15 | 离线 submit → sync_queue | `CartRepository.submit()` | 4h |
| A6-16 | 手动页离线 submit | `ManualOrderActivity.java` | 3h |

**A6-15 子步骤**：
1. 序列化 cart JSON + storeId + timestamp  
2. insert `SyncQueueItem(status=PENDING, clientOpId=UUID)`  
3. UI 显示「待送厨（离线）」  

---

### 6.4 WorkManager 定时同步（A6-17～22 · 16h）

| ID | 任务 | 涉及文件 | 工时 |
|----|------|----------|------|
| A6-17 | 引入 WorkManager | `app/build.gradle` | 0.5h |
| A6-18 | PeriodicWork 15min | `sync/OrderSyncWorker.java` | 4h |
| A6-19 | Application 注册 | `Test5Application.java` | 2h |
| A6-20 | 约束：仅 CONNECTED 网络执行 | `OrderSyncWorker.java` | 1.5h |
| A6-21 | 指数退避 + maxRetry=5 | Worker + DAO | 3h |
| A6-22 | FAILED 死信 + 重试按钮 | `SyncStatusActivity.java`（可选） | 5h |

---

### 6.5 SyncEngine 联网上传（A6-23～28 · 24h）

| ID | 任务 | 涉及文件 | 工时 |
|----|------|----------|------|
| A6-23 | SyncEngine FIFO 消费 | `sync/SyncEngine.java` | 4h |
| A6-24 | 逐条 HttpHelper.submitOrder | `SyncEngine.java` | 4h |
| A6-25 | 写 sync_log | `SyncEngine` + DAO | 3h |
| A6-26 | 成功 SYNCED / 失败 retryCount++ | DAO update | 3h |
| A6-27 | localOrderId → serverOrderNumber 映射 | `data/entity/OrderMapping.java` | 2h |
| A6-28 | UI 待同步/失败计数 | `VoiceClerkActivity` 或设置页 | 3h |
| A6-29 | 批量 API 预留接口 | `sync/BatchSubscribeApi.java` | 3h |
| A6-30 | 启动时恢复队列展示 | `Application.onCreate` | 2h |

---

### 6.6 离线验收（A6-31～32 · 16h）

| ID | 用例 | 操作 | 工时 |
|----|------|------|------|
| A6-31 | 断网下单 | 飞行模式 → 手动/语音 submit → 开网 → 15min 内同步 | 4h |
| A6-32 | 杀进程不丢单 | force-stop → 重启 → 队列条数不变 | 2h |
| A6-33 | FIFO 顺序 | 离线连续 3 次 submit → 联网顺序正确 | 3h |
| A6-34 | 幂等 | 同 clientOpId 不重复送厨 | 3h |
| A6-35 | RTC 断 vs HTTP 断 | 分别断，UI 提示正确 | 2h |
| A6-36 | Bug 缓冲 | — | 2h |

**A6 完成标准**：断网可营业，联网自动送厨，不丢单。

---

## 十二、A7 · Release 发布上线（28h · 9 项）

| ID | 任务 | Android 操作 | 工时 |
|----|------|--------------|------|
| A7-01 | 门店真机全链路 | WiFi + 语音 + FC + 送厨 + 离线 | 4h |
| A7-02 | release 构建 | `minifyEnabled`、签名 config | 3h |
| A7-03 | ProGuard keep 规则 | RTC、Room、Gson、OkHttp | 3h |
| A7-04 | 权限与隐私复查 | Manifest 仅必要权限 | 2h |
| A7-05 | 日志规范 | TAG：`HttpHelper`/`RtcAigc`/`SyncEngine` | 2h |
| A7-06 | FC 排查手册 | 5 条常见问题写入 `docs/` | 3h |
| A7-07 | 代理地址生产配置 | release 改 Spring 内网地址或 BuildVariant | 2h |
| A7-08 | 试运行问题修复 | 门店 1～2 天反馈 | 5h |
| A7-09 | 最终验收 APK | 安装包 + 版本号 + 简要说明 | 4h |

**A7 完成标准**：signed release APK 可在门店设备安装使用。

---

## 十三、Android 文件规划（A6/A5 将新增）

```text
test5/app/src/main/java/com/example/test5/
├── MainActivity.java
├── VoiceClerkActivity.java
├── ManualOrderActivity.java          ← A5
├── OrderSubscribeDebugActivity.java  ← A5
├── SyncStatusActivity.java           ← A6 可选
├── ClerkLive2DView.java              ← A5 从 test3
├── Test5Application.java             ← A6
├── aigc/
│   ├── AigcProxyApi.java
│   ├── AigcSceneInfo.java
│   ├── RtcAigcManager.java
│   └── TlvCodec.java
├── order/
│   ├── CartRepository.java           ← A5/A6
│   ├── RestaurantFunctionHandler.java
│   ├── HttpHelper.java
│   ├── MenuCatalog.java
│   └── StoreConfig.java
├── data/
│   ├── AppDatabase.java              ← A6
│   ├── entity/
│   └── dao/
├── sync/
│   ├── SyncEngine.java               ← A6
│   ├── OrderSyncWorker.java          ← A6
│   └── BatchSubscribeApi.java        ← A6 预留
└── util/
    └── NetworkMonitor.java           ← A6
```

---

## 十四、任务 ID 全索引（81 项 Android）

```text
A0:  A0-01 … A0-08   （8）  工程配置
A1:  A1-01 … A1-09   （9）  RTC 语音
A2:  A2-01 … A2-10   （10） Function Calling
A3:  A3-01 … A3-06   （6）  送厨
A4:  A4-01 … A4-06   （6）  MVP 验收
A5:  A5-01 … A5-11   （11） UI
A6:  A6-01 … A6-36   （32） 离线（含验收子项）
A7:  A7-01 … A7-09   （9）  发布
```

---

## 十五、与 PC 端的前置依赖（非 Android 工时）

| 前置项 | 谁做 | Android 如何验证 |
|--------|------|------------------|
| `Restaurant.json` 填 AK/SK、RTC、ASR、TTS、EndPointId | PC | A0-06 fetchScene 200 |
| Node `npm run dev` 3001 | PC | A0-06 |
| 防火墙放行 3001 | PC | 真机 A0-06 |
| OrderSubscribe 后端可用 | 后端 | A3-06 code 200 |

**Android 开发起点**：建议从 **A0-01** 开始；若 PC 代理已 OK，可 **A0-03 + A0-06** 快速通过后进入 **A1**。

---

## 十六、当前建议「本周」Focus

| 优先级 | 任务 ID | 内容 | 工时 |
|--------|---------|------|------|
| P0 | A0-01～08 | Gradle + 安装 + fetchScene | 8h |
| P0 | A1-01～09 | 真机听到 AI 说话 | 16h |
| P1 | A2-01～10 | 说菜名 → 购物车更新 | 12h |

完成以上三项后，你手上有一个 **可演示的 Android 语音点餐 MVP（尚未真实送厨）**，再接 A3。
