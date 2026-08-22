# 航勤智排（AirShift）

面向航司地服人员的 Android 排班助手。应用在本机识别固定模板排班截图，只提取当前用户的保障航班，并通过安全网关读取飞常准实时动态。

## 已实现

- 首次启动询问姓名并永久保存在本机，设置中可修改。
- Android 系统图片选择器导入排班截图，不申请整个相册权限。
- ML Kit 离线中文 OCR；按固定模板的真实列边界拆表。
- 自动清理航班号中的 `&`、`#` 等符号。
- 姓名精确匹配、单字 OCR 容错，以及同组人员签名容错。
- 显示机号、机型、进出港航班、城市、计划/预计/实际时间和到达桥位/机位。
- GPS 与当日航班两端机场坐标比对，15 km 内自动判断当前机场。
- 提醒规则：
  - 有进港航班（包括进港后接续出港）：只在预计落地前 10 分钟提醒；
  - 仅出港航班：预计起飞前 1 小时提醒。
- 预计时间变化后重排闹钟；后台每 30 分钟刷新 4 小时内的任务，减少不必要的付费 API 调用。
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
.\gradlew.bat test assembleDebug gateway:installDist
```

Debug APK 输出到：

```text
C:\Users\BradJ\AppData\Local\CodexBuild\AirShift\app\outputs\apk\debug\app-debug.apk
```

## 飞常准安全网关

应用不会包含 `VariFlight` 密钥。`gateway` 模块从服务端环境变量读取密钥，再调用官方 Aviation MCP：

```powershell
$env:JAVA_HOME = 'C:\Users\BradJ\AppData\Local\Programs\android-studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:VariFlight = [Environment]::GetEnvironmentVariable('VariFlight', 'User')
.\gradlew.bat gateway:run
```

默认仅监听 `127.0.0.1:8787`。Android 模拟器的 Debug 版可在设置中填写 `http://10.0.2.2:8787`。给真实手机或同事使用前，应把网关部署到 HTTPS 服务并增加访问控制与限流；不要把 API 密钥改写到 APK 或源码里。

已实测 `searchFlightsByNumber` 的字段映射：

| 应用字段 | 飞常准字段 |
|---|---|
| 计划起飞/到达 | `FlightDeptimePlanDate` / `FlightArrtimePlanDate` |
| 预计起飞/到达 | `VeryZhunReadyDeptimeDate`、`FlightDeptimeReadyDate` / `VeryZhunReadyArrtimeDate`、`FlightArrtimeReadyDate` |
| 实际起飞/到达 | `FlightDeptimeDate` / `FlightArrtimeDate` |
| 到达桥位/机位 | `ArrStandGate` |
| 廊桥属性 | `arr_bridge`（回退到 `bridge`） |
| 机场定位 | `DepAirportLat/Lon`、`ArrAirportLat/Lon` |

网关健康检查：`GET /health`；航班接口：`GET /v1/flights/{flightNumber}?date=YYYY-MM-DD`。

## 隐私与限制

- 姓名、排班与图片识别结果只保存在设备本机；图片不会上传到网关。
- 定位仅在本机用于匹配排班相关机场。
- Debug APK 使用调试签名，仅用于开发验证。正式分发前需配置发布签名、HTTPS 网关和服务端访问控制。

## 开源许可

本项目代码采用 [Apache License 2.0](LICENSE) 许可。飞常准、ML Kit 及其他第三方服务、数据与依赖仍受其各自条款约束；本项目不提供或授权任何第三方 API 密钥。
