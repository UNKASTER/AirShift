# 航勤智排（AirShift）

面向航司地服人员的 Android 排班助手。应用在手机本机识别固定模板排班截图，只提取当前用户的保障航班，并由手机直接调用飞常准 Aviation MCP 获取实时动态。项目不需要服务器、云函数或自有域名。

## 已实现

- 首次启动询问姓名并永久保存在本机，设置中可修改。
- Android 系统图片选择器导入排班截图，不申请整个相册权限。
- PP-OCRv6 tiny + ONNX Runtime 离线中文 OCR；按表头锚点分列、按 Y 坐标聚类还原数据行。
- 自动清理航班号中的 `&`、`#` 等符号。
- 姓名精确匹配、单字 OCR 容错，以及同组人员签名容错。
- 仅从截图右侧 VIP 区域提取航班号，并标记当前用户实际执勤的 VIP 航班；不展示或保存人员信息及其他附加栏目。
- 显示机号、机型、进出港航班、机场三字码与名称、计划/预计/实际时间和到达桥位/机位。
- GPS 与当日航班两端机场坐标比对，15 km 内自动判断当前机场。
- 提醒规则：
  - 有进港航班（包括进港后接续出港）：只在预计落地前 10 分钟提醒；
  - 仅出港航班：预计起飞前 1 小时提醒。
- 预计时间变化后重排闹钟；应用前台每 5 分钟、后台每 15 分钟刷新 4 小时内的任务，减少不必要的付费 API 调用。
- 相同航班与日期的查询在手机端缓存 120 秒，并设置每分钟 30 次的进程级调用保护。
- 飞常准 API Key 由用户在设置中手动输入，可测试连接或随时清除。
- 开机后恢复排班提醒。

## 本机环境

- Android Studio Quail 3（2026.1.3.7）
- Android SDK 33 与 37，最低支持 Android 13（API 33）
- Gradle 9.5.0（国内镜像下载，使用官方 SHA-256 校验）
- Kotlin 2.4.10 / Jetpack Compose

由于项目位于 OneDrive，本机生成目录通过忽略的 `local.properties` 放在 `%LOCALAPPDATA%/CodexBuild/AirShift`，避免同步程序锁定 Gradle 中间文件。

## 构建与测试

在 PowerShell 7 中运行：

```powershell
$env:JAVA_HOME = 'C:\Users\BradJ\AppData\Local\Programs\android-studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test lintDebug assembleDebug
```

连接 Android 设备或启动模拟器后，可用项目内的真实排班图回归样本验证完整 OCR 链路：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Debug APK 输出到：

```text
C:\Users\BradJ\AppData\Local\CodexBuild\AirShift\app\outputs\apk\debug\app-debug.apk
```

## 飞常准端侧直连

应用仅供个人安装使用。用户在设置中手动输入自己的飞常准 API Key；手机通过 HTTPS 直接请求飞常准 Aviation MCP 的 `searchFlightsByNumber` 工具，不经过任何自建服务。

API Key 使用 Android Keystore 管理的 AES-GCM 密钥加密后保存在应用私有存储中，明文不会进入 saved-instance-state。设置页提供“测试连接”和“清除 API Key”；清除后会同时停止后台实时刷新。项目不会在源码、资源、`BuildConfig`、`local.properties`、测试样例、Git 或 APK 中预置共享密钥。

为保护个人 API 配额，应用进程内对同一航班与日期缓存 120 秒，并保留每分钟 30 次查询保护。一次刷新中的重复航班与日期只会发起一次请求；前台与 WorkManager 共用同一缓存和限流状态。

已实测 `searchFlightsByNumber` 的字段映射：

| 应用字段 | 飞常准字段 |
|---|---|
| 计划起飞/到达 | `FlightDeptimePlanDate` / `FlightArrtimePlanDate` |
| 预计起飞/到达 | `VeryZhunReadyDeptimeDate`、`FlightDeptimeReadyDate` / `VeryZhunReadyArrtimeDate`、`FlightArrtimeReadyDate` |
| 实际起飞/到达 | `FlightDeptimeDate` / `FlightArrtimeDate` |
| 登机口关闭（近似） | `EstimateBoardingEndTime` |
| 实际离位 | `FlightOutgateTime` |
| 始发登机口 | `BoardGate` |
| 出发/到达机位 | `DepStandGate` / `ArrStandGate` |
| 廊桥属性 | `arr_bridge`（回退到 `bridge`） |
| 机场定位 | `DepAirportLat/Lon`、`ArrAirportLat/Lon` |

## 隐私与限制

- 姓名、排班与图片识别结果只保存在设备本机；图片不会上传。实时刷新时只向飞常准发送航班号、日期及 Aviation MCP 协议所需字段。
- 飞常准 API Key 只在用户设备上加密保存并作为 `X-API-Key` 请求头发送给飞常准；应用不会记录密钥、完整请求头或原始敏感错误响应。
- VIP 区域只生成当前用户任务上的布尔标记，原始文字不会持久化；旧版本保存的附加信息会在新版启动时删除。
- 定位仅在本机用于匹配排班相关机场。
- Debug APK 使用调试签名，仅用于开发验证。个人正式安装前仍需配置发布签名，并自行遵守飞常准服务条款与 API 配额限制。

## 开源许可

本项目代码采用 [Apache License 2.0](LICENSE) 许可。PP-OCRv6 模型、PaddleOCR Android SDK、ONNX Runtime、OpenCV、飞常准及其他第三方服务、数据与依赖仍受其各自条款约束，详见 [第三方声明](THIRD_PARTY_NOTICES.md)；本项目不提供或授权任何第三方 API 密钥。
