# 航勤智排（AirShift）项目规格

> 文档类型：分支扫描规格及合并验证记录（as-built specification）  
> 扫描日期：2026-08-30（Asia/Shanghai）  
> 基准分支：`main`  
> 扫描范围：原始扫描为只读；同日合并阶段已 fetch 远端并在本地 main 完成集成。当前结果见第 2.5、10 节。

## 1. 文档定位与解释规则

本规格描述仓库中已经存在的实现，而不是把 README、PLAN 或提交说明中的声明直接当作事实。第 2.1–2.4、3–9、12 节保留原始分支扫描记录，其中 `main` 指合并前的 `ea0b8df`；合并后的行为选择和验证状态以第 2.5、10 节为准。

本文使用以下范围标记：

- **共同基线**：`main` 以及两条功能分支共同具有的行为。
- **main**：在历史扫描段落中说明合并前主分支的实现状态。
- **CEA 分支**：`feat/cea-ui-rewrite` 相对 `main` 的增量或行为替换。
- **WPS 分支**：`feat/wps-excel-share-import` 相对 `main` 的增量。
- **待决策**：不同分支存在互斥或尚未统一的产品行为，不能视为任一分支已经完成的合并结果。

当前状态：本地 `main` 已同时包含 CEA UI、WPS 分享导入和自动停止刷新；原始分支差异保留为溯源资料，不再表示待合并状态。

## 2. 扫描基线

### 2.1 原始扫描的本地引用与规模

| 分支 | HEAD | 版本 | 跟踪文件 | Kotlin 文件 | Kotlin 行数 | 测试方法 | 相对 `main` |
|---|---|---|---:|---:|---:|---:|---|
| `main` | `ea0b8df57a89` | `0.1.0` / code 1 | 86 | 62 | 9,359 | 55 | 基线 |
| `feat/cea-ui-rewrite` | `28758a5fe843` | `0.3.0` / code 3 | 100 | 76 | 10,976 | 63 | 27 文件，`+2727/-1082` |
| `feat/wps-excel-share-import` | `c30c60f2f369` | `0.1.0` / code 1 | 89 | 65 | 9,734 | 69 | 8 文件，`+398/-15` |

测试方法数量按跟踪代码中的 `@Test` 统计；不等于本次已执行或已通过的测试数量。

本地还有两个 remote-tracking ref：

- `origin/main` 与本地 `main` 指向同一提交。
- `origin/feat/cea-ui-rewrite` 与本地 CEA 分支指向同一提交。

它们没有提供额外代码状态。仓库无 tag、无 submodule。

### 2.2 提交关系

```text
36b86c1  Initial open-source release
  └─ 8514912  PP-OCRv6 ONNX
      └─ 3d15ec5  飞常准直连与 OCR 加固
          └─ ea0b8df  main：Excel 与 MUC 更新
              ├─ 56b5241 ─ 62fb512 ─ 28758a5  feat/cea-ui-rewrite
              └─ 9463d7b ─ c30c60f              feat/wps-excel-share-import
```

本地分支合计覆盖 9 个可达提交。两条功能分支的 merge-base 均为 `ea0b8df`。

### 2.3 对象与工作树状态

- 扫描前工作树位于干净的 `main`，没有已跟踪或未跟踪改动。
- `git fsck --full` 没有报告缺失或损坏对象。
- Git 对象库存在 2 个悬空提交和若干悬空树。两个悬空提交具有相同树，且该树与可达提交 `8514912` 完全一致，不包含独有产品状态；悬空树不属于任何分支，因此不纳入分支规格。
- 三个分支均未发现高置信私钥或常见访问令牌特征。
- `feat/wps-excel-share-import` 的 `git diff --check` 无问题；CEA 分支仅发现 `ui/components/FlightRow.kt` 文件末尾多一个空行。

### 2.4 原始只读扫描的验证边界

本次完成了对象级静态扫描、分支树对比、提交图、文本差异、合并热点、配置、依赖、测试代码和高置信秘密模式检查。为保持分支扫描只读且不生成构建产物，本次没有运行 Gradle 测试、lint、assemble 或设备测试。因此本文不会声称任一分支当前“测试通过”或“构建成功”。README/PLAN 中的通过记录只视为历史声明。

### 2.5 2026-08-30 合并结果与验证

- 本地 `main`：`92cf8a3`，版本 `0.4.0` / code 4。`f5e2a75` 整合两个本地 feat；`92cf8a3` 纳入远端 WPS 重复提交的历史。
- 已纳入 `feat/cea-ui-rewrite`（`28758a5`）、`feat/wps-excel-share-import`（`c30c60f`）及 `origin/feat/wps-excel-share-import`（`cbd39ce`）。远端 `cbd39ce` 与本地 `9463d7b` 的父提交和文件树完全相同，故记录 ancestry 时保留已经验证的集成树，没有丢弃远端独有代码。
- 已执行 `test lintDebug assembleDebug connectedDebugAndroidTest`：构建成功；JVM 77 项中 76 项通过，1 项真实 XLS 外部样本测试因未配置样本路径而跳过；API 37 模拟器 24 项仪器测试全部通过。
- 同一 APK 和测试 APK 在 API 33 模拟器通过全部 24 项仪器测试，覆盖最低支持版本；包含 OCR、分享解析与 saved-state 恢复、旧页面回调隔离、存储兼容、刷新恢复及当前执勤页面。
- lint 无错误，保留 4 个既有警告（3 个依赖新版本提示、1 个第三方 OCR KTX 建议）；新增 Compose 测试采用现有兼容测试入口，编译有 3 个弃用提示，未升级依赖。
- 此次没有物理设备、真实 WPS/MUC 环境或飞常准凭据；未证明真实应用 URI 授权、完整进程死亡链路、后台调度时序和系统权限/定位/闹钟端到端行为。模拟器通过不等于第 10.4 节真机验收通过。
- 普通图片/文件选择器的正在进行导入在页面重建后会安全取消，需重新选择文件；WPS 分享保留待处理队列并重试。SavedStateHandle 恢复依赖 Android 保存状态和来源 URI 授权，不承诺任意崩溃或强制停止下的 exactly-once 导入。
- 本地合并未 push，未删除功能分支。原有 `spec.md` 未跟踪状态、`PLAN.md` 本地删除状态不纳入代码合并提交；合并前文档另有命名 stash 备份。

## 3. 产品定义

### 3.1 产品目标

AirShift 是面向航司地面服务保障人员的 Android 单用户排班助手。它应在手机本机完成排班识别、个人任务筛选、状态保存和 MUC 通知结构化处理，并直接向飞常准 Aviation MCP 查询相关航班动态。

核心价值是：

1. 从完整排班中只提取当前用户负责的航班。
2. 将计划、预计、实际航班时间和机位信息集中到任务卡。
3. 根据任务类型安排本地保障提醒。
4. 将 MUC 通知中的特服、登机口/机位变更和取消事件关联到对应航段。
5. 不建设自有服务器，不上传原始排班、图片、Excel 或 MUC 正文。

### 3.2 目标用户与运行前提

- 目标用户：需要执行接机、送机或过站保障的单个地服人员。
- 设备：Android 13（API 33）及以上。
- 可离线能力：姓名、已保存排班、图片 OCR、Excel 解析、MUC 文本解析和已安排提醒。
- 联网能力：飞常准实时航班刷新。
- 可选系统授权：通知、精确闹钟、定位、通知读取权限。
- 外部应用：MUC 包名固定为 `com.ceair.im.muc`；WPS 分支通过标准 Android 分享协议接收文件，不依赖 WPS SDK。

### 3.3 明确不在范围内

- 自建后端、云函数、数据库、账号体系或跨设备同步。
- 读取 MUC 数据库、历史聊天、图片附件或使用无障碍抓屏。
- 上传排班原文件或 MUC 原文进行云端 AI 处理。
- 自动提供或共享飞常准 API Key。
- iOS、Web 或 API 33 以下 Android 设备。
- 多用户、多排班账户、团队调度和管理员功能。
- 发布签名、应用商店发布流程和生产分发基础设施。

## 4. 系统架构

### 4.1 总体数据流

```text
姓名 + 排班来源
  ├─ 图片 URI → ImageDecoder → PP-OCRv6 / ONNX Runtime / OpenCV
  │                         → OcrToken → RosterTableParser
  └─ Excel URI → 文件签名
               ├─ OLE / .xls  → 私有缓存临时文件 → POI HSSF 事件流
               └─ ZIP / .xlsx → 受限 ZIP 读取 → SAX XML 解析
                            ↓
                    RosterParseResult
                            ↓
                    RosterAssignment 列表
           ┌────────────────┼────────────────┐
           ↓                ↓                ↓
      RosterStore       飞常准刷新       MUC 航段重匹配
           ↓                ↓                ↓
      Compose UI       AlarmManager      脱敏 StateFlow
                           +
                    WorkManager 后台刷新
```

### 4.2 模块职责

| 模块 | 路径 | 职责 |
|---|---|---|
| 应用编排 | `app/src/main/java/com/bradj/airshift/MainActivity.kt` | 启动、依赖构造、导入、刷新、权限、定位、提醒、设置和 Compose 状态编排 |
| 核心模型 | `model/` | 排班任务、任务类型；分支扩展执勤时间线或完成判断 |
| 排班解析 | `parser/` | 图片 OCR 后处理、`.xls`/`.xlsx` 读取、日期/姓名/航班/VIP 提取 |
| 实时航班 | `api/` | 飞常准请求、响应解析、缓存、限流、数据合并和 WorkManager |
| 本地数据 | `data/` | 排班/姓名 JSON、刷新状态和 Keystore API Key |
| 定位 | `location/` | 当前定位与排班机场匹配 |
| 提醒 | `reminder/` | 提醒规则、AlarmManager 调度、通知和开机恢复 |
| MUC 特服 | `specialservice/` | 通知提取、解析、匹配、时序归并、去重、持久化和 UI 状态 |
| OCR 引擎 | `com/paddle/ocr/` | 内嵌 PaddleOCR Android 推理流水线 |
| CEA UI | `ui/`，仅 CEA 分支 | 主题、底部导航、全部执勤、当前执勤、设置和可复用组件 |
| Android 资源 | `app/src/main/res/` | 图标、主题、字符串、数据提取规则 |
| 模型资产 | `app/src/main/assets/models/` | PP-OCRv6 tiny 检测、识别 ONNX 模型及字符表 |
| 测试 | `app/src/test/`、`app/src/androidTest/` | JVM 规则测试和 Android 设备集成测试 |
| 回归样本与工具 | `testdata/`、`tools/` | 合成排班图和可重复生成脚本 |

## 5. 共同基线功能规格

### 5.1 首次启动与个人信息

1. 首次启动没有本地姓名时，应用必须显示姓名录入页。
2. UI 接受 2–20 个字符；保存前去除首尾空白。
3. 姓名保存在应用私有 SharedPreferences，并可在设置中修改。
4. 修改姓名不会自动重新解析旧原文件；后续导入使用新姓名。
5. 没有姓名时不得开始排班筛选。

### 5.2 图片排班导入

1. 应用通过 Android 系统图片选择器取得单张图片 URI，不申请全相册读取权限。
2. 图片必须以软件 Bitmap 解码，并在使用完成后回收。
3. OCR 必须在本机使用 PP-OCRv6 tiny、ONNX Runtime 和 OpenCV 执行。
4. OCR 引擎在进程内复用；初始化和推理分别串行保护。
5. OCR 结果转换为包含文字及四边界坐标的 `OcrToken`。
6. 表格解析使用 9 列模板：机号、机型、进港航班、前站、预落、出港航班、到站、计离、接送机人员。
7. 表头足够时应拟合实际列位置；表头不足时回退固定比例模板并产生警告。
8. 数据行按机号锚点和 Y 坐标聚类，避免将相邻人员或航班串行合并。
9. 只保留人员栏规范化后包含完整当前姓名的行，不使用编辑距离或“一字模糊”算法。
10. 如果无法识别日期，应使用设备当天日期并产生警告；月日不含年份时选择距今天最近的前一年、当年或后一年。
11. 图片右侧附加区域只提取 VIP 航班号集合，并仅在用户任务上保存进港/出港 VIP 布尔值；不得保存附加区域原文或人员信息。
12. 没有匹配任务时返回空列表并显示可理解的警告，不得伪造任务。

当前图片路径没有文件大小、像素数或解码内存上限；这是已知风险，不是期望的安全保证。

### 5.3 Excel 排班导入

1. 应用通过系统文档选择器接受 `.xls` 和 `.xlsx`。
2. 实际格式必须由文件签名判断，而不是只信任扩展名或 MIME：
   - OLE 复合文档签名进入 `.xls` 路径；
   - ZIP 签名进入 `.xlsx` 路径；
   - 其他签名必须拒绝。
3. `.xlsx` 使用受限 ZIP 读取和 SAX 解析，不引入完整工作簿内存模型。
4. `.xlsx` 安全限制：单个相关 XML 最多 16 MiB、相关 XML 合计最多 32 MiB、工作表最多 64 个；DOCTYPE 和外部实体必须禁用。
5. `.xls` 先复制到应用私有缓存，再用 POI HSSF 事件模型读取；临时文件必须在 `finally` 中删除。
6. `.xls` 文件最多 256 MiB；每个工作表最多 10,000 行、100,000 个单元格，工作表最多 64 个。
7. 支持 1900/1904 Excel 日期系统、完整日期、月日、日期序列、时间小数、HHmm 数值/文本和 `+` 次日标记。
8. 工作表必须识别至少 6 个语义列，并包含机号、人员及至少一个进/出港航班列。
9. 表头别名包括机号/机尾号/飞机注册号，进出港航班变体，前站/到站变体，计划/预计时间变体，以及接送机人员/送机人员/保障人员等。
10. 有分隔符的人员名单执行规范化后的逐项精确匹配；无分隔符的长组合签名只有在长度至少为用户名两倍且包含完整用户名时才匹配。
11. 解析多张有效工作表，按 `stableId` 去重并按任务时间排序。
12. 加密、损坏、格式错误或没有可识别表头的工作簿必须返回安全错误，不得静默生成数据。

### 5.4 航班号、时间和任务构造

1. 航班号必须规范为大写，移除 `&`、`#`、空格等非字母数字符号。
2. 只接受 2–3 个字母加 3–4 位数字的航班号。
3. `CES` 前缀必须转换为 `MU`。
4. 任务类型：
   - 只有进港：`ARRIVAL_ONLY`；
   - 只有出港：`DEPARTURE_ONLY`；
   - 同时进出港：`TURNAROUND`。
5. 任务稳定 ID 由机号、进港航班、出港航班和任务日期拼接生成。
6. 任务至少保存以下字段组：
   - 排班：机号、机型、进出港航班、前站/到站、计划时间、人员；
   - 实时：预计/实际进出港时间、登机口、出发/到达机位、登机口关闭观察、实际离位、廊桥；
   - 机场：三字码、名称和本场信息；
   - 标志：进港 VIP、出港 VIP。

### 5.5 飞常准实时航班

1. 用户必须自行输入飞常准 API Key；仓库、BuildConfig、资源和 APK 不得预置共享密钥。
2. 客户端固定向 `https://ai.variflight.com/servers/aviation/mcp` 发送 HTTPS POST。
3. MCP 请求使用 JSON-RPC `tools/call`，工具名为 `searchFlightsByNumber`，参数为规范化航班号 `fnum` 和运行日期 `date`。
4. API Key 仅作为 `X-API-Key` 请求头发送。
5. 连接超时 5 秒，读取超时 15 秒；网络、超时、鉴权、限流和服务端错误必须映射成固定、脱敏的用户消息。
6. 客户端接受普通 JSON-RPC 和 `data:` Server-Sent Events 外层。
7. 航班载荷字段映射如下：

| 本地字段 | 飞常准字段 |
|---|---|
| 计划起飞/到达 | `FlightDeptimePlanDate` / `FlightArrtimePlanDate` |
| 预计起飞 | `VeryZhunReadyDeptimeDate`，回退 `FlightDeptimeReadyDate` |
| 预计到达 | `VeryZhunReadyArrtimeDate`，回退 `FlightArrtimeReadyDate` |
| 实际起飞/到达 | `FlightDeptimeDate` / `FlightArrtimeDate` |
| 实际离位 | `FlightOutgateTime` |
| 登机口关闭观察 | `EstimateBoardingEndTime` |
| 登机口 | `BoardGate` |
| 出发/到达机位 | `DepStandGate` / `ArrStandGate` |
| 廊桥 | `arr_bridge`，回退 `bridge` |
| 机场代码/名称 | `FlightDepcode`、`FlightDepAirport`、`FlightArrcode`、`FlightArrAirport` |
| 机场坐标 | `DepAirportLat/Lon`、`ArrAirportLat/Lon` |

8. 相同航班号和日期的成功结果缓存 120 秒；失败不得缓存；并发相同查询应合并。
9. 进程级滑动窗口最多接受每分钟 30 次查询；当前实现中缓存命中也消耗限流容量。
10. 导入后的首次刷新和手动刷新可查询全部导入航班。
11. 自动刷新只查询计划时间相对当前时间位于 `-60..240` 分钟的航段，并按航班号+日期去重。
12. 前台在有排班和 API Key 时每 5 分钟自动刷新；忙碌时 15 秒后重试循环。
13. 后台使用有网络约束的唯一 WorkManager 周期任务，每 15 分钟运行。
14. 部分查询成功时必须保存成功的实时数据并保留失败警告；后台所有查询均失败且至少有可重试错误时返回 `Result.retry()`。
15. 每次实时数据更新后应保存排班、重新关联 MUC 状态并重排提醒。

### 5.6 机场定位

1. 只有获得粗略或精确定位权限后才执行定位；拒绝权限不影响排班查看。
2. 候选机场仅来自已刷新航班且必须具有经纬度，按三字码去重。
3. 优先请求当前定位；失败时可回退最近定位。
4. 最近定位不得早于 10 分钟。
5. 计算设备到所有候选机场的球面距离，只在最近机场不超过 15 km 时返回匹配。
6. 定位仅用于显示当前排班相关机场，不上传或持久化原始位置。

### 5.7 本地保障提醒

1. 有进港航班的任务（包括过站任务）只创建一条进港提醒：预计到达优先、计划到达回退，目标为落地前 10 分钟。
2. 只有出港航班的任务创建出港提醒：预计起飞优先、计划起飞回退，目标为起飞前 1 小时。
3. 已有实际到达/起飞时间时，不再创建对应提醒。
4. 目标时间已经过去时跳过调度。
5. 有精确闹钟特殊权限时使用 `setExactAndAllowWhileIdle`；否则使用 `setAndAllowWhileIdle` 并向用户提示可能有偏差。
6. 刷新或重新导入前取消旧任务对应的 PendingIntent，然后按新时间重排。
7. 通知使用高重要性“航班保障提醒”频道，点击后打开主 Activity。
8. 设备开机完成后从本地排班恢复提醒。

### 5.8 MUC 通知识别

1. 通知监听服务只处理包名严格等于 `com.ceair.im.muc` 的新通知。
2. 通知移除或用户划走不得被解释为业务取消。
3. 文本提取优先读取 MessagingStyle `EXTRA_MESSAGES`，否则合并 `BIG_TEXT`、`TEXT_LINES`、`TEXT`、标题和会话标题。
4. 系统回调只做白名单和文本提取，后续处理进入单线程执行器。
5. 如果平台只暴露“新消息”等摘要，应用必须记录不可读状态，不得启用数据库读取或无障碍替代方案。
6. 文本先执行 NFKC、全角/半角、大小写和空白规范化。
7. 支持的特服类别：
   - 残障旅客；
   - 轮椅 `WCHR`、`WCHS`、`WCHC`；
   - UM 无陪伴儿童；
   - MAAS 全流程陪伴；
   - 客舱宠物。
8. 数量可来自 1–2 位阿拉伯数字或常见中文数量词；座位、手机号、票号、行李和重量不得被当作人数。
9. 支持带承运人完整航班号和 3–4 位数字简写；`CES` 转换为 `MU`。
10. 航段匹配使用航班数字部分；有承运人且存在精确承运人匹配时优先精确匹配，再按明确日期、通知日期、日期距离和稳定排序选择。
11. 高置信且已匹配排班的特服自动关联；高置信但无排班的候选保留最多 24 小时，排班变化后自动重试。
12. 登机口变更、机位变更、全部特服取消和行程取消分别建模；“登机口关闭时间”“机位时间”等不得误判为变更。
13. 更晚消息覆盖更早消息；取消建立时序墓碑，旧摘要不得恢复已取消记录；更晚有效更新可重新激活。
14. 相同消息使用本机随机密钥生成的 HMAC 指纹去重；原始正文、姓名、发送人、电话、票号、座位、图片和附件不得进入持久化状态。
15. 已匹配记录在航段实际、预计或计划完成时间后 24 小时过期；未匹配候选从处理时刻起 24 小时过期。
16. Repository 通过 `StateFlow` 向前台提供实时结构化状态。

低置信行为不是共同基线，见第 6.5 节。

### 5.9 main 用户界面

`main` 使用单页 Compose 界面，主要行为为：

- 顶部显示用户姓名、当前机场和设置入口。
- 导入卡支持图片和 Excel；工作中禁用重复操作。
- 显示状态、解析警告和精确闹钟提示。
- 下拉刷新实时航班。
- 列出所有任务卡，显示机号/机型、进出港航班、机场、计划/预计/实际时间、登机口/机位、VIP、特服和取消状态。
- 低置信 MUC 候选显示“待确认特服消息”，用户可选航段、类型、轮椅等级、数量后确认，或忽略。
- 设置以对话框展示姓名、通知读取状态、API Key 测试/保存/清除。
- 使用浅色主题，不提供深色模式或多语言资源化 UI。

## 6. 分支增量规格

### 6.1 CEA 分支概览

`feat/cea-ui-rewrite` 用 3 个提交重构 UI，同时小幅扩展模型和 MUC 低置信策略。它仍复用 `main` 的解析、飞常准、定位、提醒和大部分 MUC 状态机。

新增文件包括 `model/DutyTimeline.kt`、12 个 `ui/` 文件和 `DutyTimelineTest.kt`。唯一新增依赖是 `androidx.compose.material:material-icons-core`，Manifest 没有新增组件或权限。

### 6.2 CEA 设计系统与导航

1. UI 采用“中国东方航空 VI / 安静的效率感”浅色设计：东航红 `#C8102E` 用于品牌点缀、主按钮和出港强调；藏青用于标题；云白背景、白卡、微边框和克制阴影。
2. 统一 12/20 dp 圆角、8 dp 倍数间距、等宽数字样式和 AA 目标对比色。
3. 根界面使用 Material 3 `Scaffold + NavigationBar`，固定三个 Tab：全部执勤、当前执勤、设置。
4. 不引入导航框架；Tab 使用 `DutySection` 枚举，状态保存在 `DutyNavigationViewModel`（配置变化保留、进程重建回到当前执勤）。
5. 每次打开应用（冷启动、从后台恢复、进程重建后返回前台）都强制显示当前执勤页；旋转等配置变化不重置所选 Tab。

### 6.3 CEA 全部执勤页

- 保留问候、机场、图片/Excel 导入、状态、警告、精确闹钟提示和下拉刷新。
- 全部任务卡只用单个“特服”角标表示存在特服，不展示数量详情。
- MUC 登机口/机位变化只显示最小“变更”提示，不在该页展示新值。
- 保留计划/实时信息、任务类型、机号机型、机场、VIP 和取消状态。

### 6.4 CEA 当前执勤页与人工进度

1. 当前任务来源为 `assignments[currentDutyIndex]`。
2. 无排班时显示导入引导；索引达到任务数时显示“今日执勤全部完成”。
3. 当前任务倒计时每分钟刷新：
   - 有进港航段：`actualArrival ?: estimatedArrival ?: scheduledArrival` 减 10 分钟；
   - 仅出港：`actualDeparture ?: estimatedDeparture ?: scheduledDeparture` 减 60 分钟；
   - 目标已过时显示“应立即到位”。
4. 显示下一任务及下一到位时间。
5. 出港预计登机开始为最佳起飞时间前 40 分钟；预计登机口关闭为前 15 分钟。
6. 当前任务详情必须展示完整特服、新旧登机口/机位对比及更新时间。
7. “执勤完成”按钮将本地索引加 1，并立即进入下一任务；当前实现没有撤销、回退或二次确认。
8. 新排班导入后重置索引为 0；普通实时刷新不得重置。
9. 进度与当天日期保存在 `air_shift` SharedPreferences；读取时日期不是今天则视为索引 0。

### 6.5 CEA 低置信 MUC 策略

CEA 分支将 `main` 的人工确认流程替换为：

- 低置信特服候选直接忽略，不进入 pending 或记录列表。
- 最近处理结果显示“低置信已忽略 N 条”。
- Repository 的 `confirmReview` / `ignoreReview` API 和对应 UI 被删除。
- 高置信无排班候选仍保留并等待排班。

这是有意的行为破坏性变更，不能与 `main` 的人工确认规格同时成立，必须在集成前做产品选择。

### 6.6 CEA 航段字段扩展

CEA 分支为 `RosterAssignment` 增加：

- `inboundDepartureStand`：进港航班的始发机位；
- `outboundArrivalStand`：出港航班的目的地到达机位。

两者从飞常准进/出港 `FlightInfo` 分别填充，并以可空 JSON 字段持久化。旧排班 JSON 缺失这些字段时仍可加载。

### 6.7 WPS 分支概览

`feat/wps-excel-share-import` 包含两个独立增量：标准 Android Excel 分享导入，以及当日全部执勤完成后停止自动刷新。它不增加依赖或权限，版本号仍为 `0.1.0` / code 1。

### 6.8 WPS/Android 分享导入

1. `MainActivity` 使用 `singleTop`，Manifest 注册 `ACTION_SEND + CATEGORY_DEFAULT`。
2. 只注册并接受两个 MIME：
   - `application/vnd.ms-excel`；
   - `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`。
3. 只接受单个 `EXTRA_STREAM` 的 `content://` URI。
4. 错误 action 返回“不处理”；错误 MIME、缺少 stream、`file://` 和多 `ClipData` 项返回用户可见拒绝消息。
5. 不接受 `ACTION_VIEW`、`ACTION_SEND_MULTIPLE`、WPS 云链接或多文件分享。
6. 冷启动由 `onCreate` 入队，已启动时由 `onNewIntent` 入队；每个事件有单调 ID，重复 URI 也保留为独立 FIFO 事件。
7. 首次使用尚无姓名时先完成 onboarding，再消费分享文件。
8. 分享文件进入与系统文档选择器相同的 `ExcelRosterReader`、实时刷新、保存、定位和提醒流程。
9. 导入进度显示“正在解析分享的 Excel 排班…”，协议错误显示“Excel 分享导入失败…”。

### 6.9 WPS 自动停止刷新

1. 单个航段满足任一条件时视为完成：
   - 已有实际到达/起飞时间；
   - 当前时间达到预计时间（优先）或计划时间加 3 小时；
   - 航段没有任何可用时间，因无法跟踪而视为完成。
2. 不存在的进港或出港方向天然完成；过站任务必须进、出港都完成。
3. 排班列表必须非空且所有任务都完成，才是“全部执勤完成”。
4. 启动和保存排班时，已全部完成则不启用 WorkManager。
5. 前台 5 分钟循环检测全部完成后退出，并显示“今日执勤已全部完成，自动刷新已停止，导入新排班后恢复”。
6. 后台 Worker 在发起 API 请求前检测完成状态，完成时取消唯一周期任务。
7. 手动下拉刷新不受该短路限制。

### 6.10 安卓桌面小组件

1. `widget/DutyWidgetProvider` 注册 `APPWIDGET_UPDATE`（exported，供桌面 launcher 绑定）。数据直接读 `RosterStore`，不新建存储；小组件不再使用集合容器、`RemoteViewsService` 或 `BIND_REMOTEVIEWS` 服务。
2. 小组件以 `widget_duty_item` 单个完整大卡片固定显示 `nextIncompleteDutyIndex` 给出的当前未完成执勤，不提供滑动、翻页、自动轮播或历史/后续执勤浏览。
3. 当前执勤显示到位倒计时——已过点显示“应立即到位”，无法计算到位时间显示“暂无到位时间信息”；无排班或全部完成显示对应整卡提示。视觉沿用 CEA 设计令牌（#C8102E / #14284B / #4A5568 / #8A94A6）：VIP 琥珀徽章保留；布局为自上而下三区——meta 行（执勤进度 · 类型 · 机号 · 机型，13sp）、hero 区（左列 12sp 状态标签 + 36sp Heavy 红色倒计时 + 14sp 到位时间，右列纵向占满的红色“完成”大按钮，最小 96×56dp、16dp 圆角、2dp 投影，背景为 ripple drawable，按压时水波纹扩散并叠加 12% 黑色遮罩压暗）、登机牌撕线式虚线分隔（#E2E6EC）与底部航班信息行（浅红/浅蓝底 tonal 方向 chip，999dp 圆角，文字色随方向配套 + 17sp 航班号 + 登机口图标与值）；倒计时等数字统一 `tnum` 等宽，逐秒刷新不抖动；整卡背景叠加系统涟漪，点按卡片打开 App 有按压反馈。
4. 倒计时使用 RemoteViews 支持的 `Chronometer`（countDown 模式），由桌面 launcher 渲染逐秒跳动，小组件进程零唤醒；计时到 00:00 停住，“应立即到位”的翻转依赖下一次数据刷新或 30 分钟 `updatePeriodMillis` 兜底更新。
5. 底部航段行：进港行显示航班号、始发地机场中文名 + 三字码，行尾为到达站（本站）机位（`arrivalStand`，以「机位 」前缀标识；到达侧无登机口字段）；出港行显示航班号、目的地机场中文名 + 三字码，行尾为出发地登机口（`boardingGate`），缺登机口时回退出发机位（`departureStand`，同样加「机位 」前缀）；均含飞常准实时值，不叠加 MUC 变更提醒。执勤无任何航段（如仅有机号/时刻的备份任务）时隐藏撕线虚线。
6. 刷新挂接：`syncSavedRoster`（导入/执勤完成/前台合并共用）、`FlightRefreshWorker` 后台合并成功、`ReminderReceiver.onReceive`（置于通知权限检查之前）、`BootReceiver`；统一经 `DutyWidgetUpdater.notifyRosterChanged` 调 `updateAppWidget` 直接重绘最新当前执勤。
7. 页面模型 `DutyWidgetModel.toCurrentWidgetPage` 为纯 Kotlin 选择入口，单元测试覆盖固定选择当前执勤，以及空排班、全部完成、倒计时/过点/无时间与进出港字段。
8. 点击卡片非按钮区域通过 `setOnClickPendingIntent` 打开 `MainActivity`；红色“完成”按钮向非导出的 `DutyWidgetActionReceiver` 发送显式 `PendingIntent`，按与 App 相同的排班 generation 和当前索引双重校验原子推进进度。旧卡片重复点击不会完成下一项；成功后重排提醒、调整后台刷新资格、立即重绘，并只以一次性 Worker 补查新进入两项窗口的航班。
9. OriginOS 6（vivo/iQOO）适配：圆角取 `@android:dimen/system_app_widget_background_radius` 跟随系统挂件；`targetCellWidth/Height=4x3`、`minHeight=160dp` 适配 vivo 桌面 4 列网格；launcher 对超出格子高度的内容做底部裁剪（不居中裁），故三区内容按默认 4x3 尺寸压距适配（区间距 10dp），保证默认尺寸下 meta/hero/航段行完整可见；颜色固定深字白卡不随主题变化。OriginOS“原子组件”为 vivo 合作方专有体系，第三方 App 无法注册，本组件按标准 AppWidget 归入“应用挂件”。

### 6.11 视觉层重构（0.6.0 / code 16）

仅重做视觉层（布局、色彩、字体、组件样式、图标、动效），业务逻辑、数据结构与接口未改动。设计 token 集中于 `ui/theme/AirShiftTheme.kt`：

1. 色彩：东航红 `#C8102E`（仅主按钮/当前任务/紧急状态/品牌点缀/出港标签）、深藏青 `#14284B`（页头与大标题，唯一允许的 135° 藏青单色微渐变）、暖白背景 `#F7F8FA`、纯白卡片、藏青灰阶文字 `#14284B / #4A5568 / #8A94A6`；语义色：进港藏青蓝、正常/完成墨绿 `#0F7B5F`、延误/留意琥珀 `#D97706`。深色模式：背景 `#0F1A2E`、卡片 `#1A2740`，红色不变，随系统自动切换。
2. 字阶：倒计时 60sp、航班号 30sp Heavy（`FlightNumber`）、计划/到位时间 28sp Bold，数字统一 tabular-nums；页面标题 20sp Semibold、正文 15/16sp、辅助 12/13sp。
3. 圆角/阴影/间距：chip 999、按钮 14、卡片 16 dp；三级阴影（列表卡 / 当前任务卡 / hero 与浮动按钮，藏青底色）；4dp 基准网格；卡片顶部 1px 内高光；状态变化统一 200ms ease-out（`AirShiftMotion`）。
4. 图标：统一 1.5px 线性自绘（`ui/components/DesignComponents.kt` 的 `linearIcon` 与 `LinearIcons`），不再混用填充图标。
5. 底部导航：白底 + 顶部 1px 浅灰线，中央“当前执勤”为东航红浮动圆形按钮（三级阴影）。
6. 全部执勤页：藏青渐变页头（问候缩小、日期放大、位置 chip）、红实心主按钮 + 藏青描边次按钮、琥珀“需要留意”提示条（左边条 + 浅琥珀底）；任务卡左侧 4px 类型色条（出港红/进港蓝/接续上蓝下红）、FIDS 三字码航线、计划时间右侧大号对齐、图标 meta 行、接续航班登机牌撕线虚线分隔、`--` 占位改为浅灰骨架短横线。
7. 当前执勤页：顶部倒计时 hero 卡——常态藏青渐变底 + 白色大数字，紧急态（应立即到位）东航红底白字 + 1.2s 呼吸脉冲光晕；到位时间与下一任务预览为卡内两行辅助信息；航班信息卡与列表页同一套卡片语言。

0.6.1 迭代修订：紧急态“应立即到位”强制单行并随宽度自适应字号（40–64sp，0.02em 字距）；航段航线区改为左右对称双列（左列：三字码/中文站名/登机口/出发机位，右列镜像：三字码/中文站名/到达登机口/到达机位，箭头垂直居中），“到达登机口”暂无数据源、以骨架占位；“登机口关闭/实际离位”并入末段撕线后的 meta 区（时钟图标，与机号/机型并列）；底部导航选中项加全圆角浅红 pill 背景（11% 东航红、32dp 高、200ms ease-out），中央浮动按钮选中时加 2px 白描边 + 外扩 4px 20% 红环并放大 1.05 倍。

0.6.2 迭代修订：航线区改为逐行共享基线的统一网格（1fr｜箭头列｜1fr，箭头与行1 三字码同轴），两侧标签统一精简为「登机口」「机位」；meta 区改为 2×2 等宽网格（登机口关闭｜实际离位／机号｜机型，单元格左对齐）；时间块主从调换——实时时间 28sp Heavy 大字（正常/提前墨绿 `#0F7B5F`、延误东航红 `#C8102E`、无计划可对比时藏青），其下 12sp 灰「计划 HH:mm」，无实时数据时计划时间顶替大字位且小字行隐藏。

0.6.3 迭代修订：标题行航班号与时间块底部基线对齐、最小间距 24dp；实时时间延误判定改为晚于计划 15 分钟且颜色改琥珀 `#D97706`（覆盖 0.6.2 的红色规则），正点/提前统一墨绿；航线网格「登机口/机位」标签容器固定 38dp 等宽，左列数值同一起点、右列数值右缘对齐；新增飞行状态 chip（未起飞：浅灰底 + 停机小飞机；已起飞：10% 墨绿底 + 墨绿字 + 起飞小飞机），判定以实际时间或实际离位为准，无任何航班数据时不渲染；方向 chip 与状态 chip 统一 999 圆角 12sp Semibold。

0.6.4 迭代修订：标题区拆为两行——行1 为 chip 行（方向 + 飞行状态，左对齐间距 8dp），行2 为主信息行（航班号左 + 实时时间块右，基线对齐）；航班号不再参与弹性压缩，不截断、不省略（`softWrap=false`、无 ellipsis），与时间的间距由弹性空隙吸收（最小 24dp），修复 chips 挤压导致航班号消失的问题。

0.6.5 迭代修订：修复时间块对齐——时间块内部改为竖排两行且顺序对调（上「计划 HH:mm」12sp 灰、下实时 28sp Heavy 大字），主信息行 flex-end 底边对齐（数字无下伸部，底边对齐即视觉基线对齐）；卡片上下内边距 16→20dp，主信息行与航线区间距 16→20dp。

0.6.6 迭代修订：修复跨午夜航班实时时间误标延误——延误判定由完整时间戳 `isAfter` 改为分钟差区间，仅 15 分钟～12 小时的正差判延误（琥珀），≥12 小时视为跨日数据不可比，与正点/提前一样显示墨绿。

0.6.7 迭代修订：桌面小组件集合容器由 ListView 改为系统 StackView，上下拨动一次切换一个完整执勤页，首尾不循环、不自动播放；初始页改用 `setDisplayedChild` 定位，卡片内容与视觉样式保持不变。

0.6.8 迭代修订：桌面小组件取消翻页与历史/后续执勤浏览，移除 StackView、RemoteViewsService 与集合适配链路；改为单个完整大卡片固定显示当前未完成执勤，数据变化时直接重绘。

0.6.9 迭代修订：桌面小组件取消 Hero 区弹性撑高，改为紧凑主信息区、浅灰分隔线和均衡留白；新增红色“完成”按钮，与 App 共用防重复完成的原子进度操作，并仅补查新进入当前窗口的航班。

0.7.0 迭代修订：桌面小组件视觉重构，仅调整布局与样式（RemoteViews），数据逻辑与 PendingIntent 行为不变——改为 meta 行 / hero 区 / 撕线虚线 + 底部信息行的三区结构，消灭大片留白；倒计时放大为全件最大元素（36sp Heavy + `tnum`），到位时间紧跟其下；方向标签改为浅红/浅蓝底 tonal chip（999dp 圆角，文字色随方向配套）；细分隔线改登机牌撕线式虚线（#E2E6EC；注意 RemoteViews 白名单不允许裸 `android.view.View`，虚线由 1dp 高 TextView 承载并置 software 层保证虚线渲染）；“完成”按钮加大至最小 96×56dp、16dp 圆角、2dp 投影，背景改 ripple drawable（按压叠加 12% 黑色遮罩压暗）；整卡背景叠加系统涟漪；登机口改为图标 + 值；小组件用色全面对齐 App 设计令牌；移除左侧东航红竖条。

0.7.1 迭代修订：修复小组件整卡空白——撕线虚线误用 RemoteViews inflate 白名单之外的裸 `android.view.View`，launcher 进程加载布局失败；改由 1dp 高 TextView 承载虚线。

0.7.2 迭代修订：小组件底部航段行信息增强——机场显示改为中文名 + 三字码并列；进港行尾改为到达站（本站）机位，出港行尾在登机口缺失时回退出发机位（机位值加「机位 」前缀与登机口区分）；无航段的执勤隐藏悬空虚线。

0.7.3 迭代修订：修复小组件底部虚线与航段行在 vivo 桌面不显示——根因不是渲染失败而是格子高度不足，launcher 对超出格子的内容自顶向下布局并裁剪底部（虚线与航段行恰好位于裁剪区）；三区间距 12→10dp、主次航段行距 8→6dp、虚线 1→2dp，小组件默认尺寸由 4x2 调整为 4x3（`targetCellHeight=3`、`minHeight=160dp`），保证默认尺寸下三区完整可见。已有 4x2 实例需调整高度或重新添加方可完整显示。

0.7.4 迭代修订：小组件叠加品牌装饰层，布局结构、字号与按钮保持不变——左缘 4dp 东航红竖条并入卡片背景 layer-list（左侧圆角随系统挂件，z 序天然最低）；右下角藏青燕尾双弧线水印（6% 不透明，`clipToOutline` 随卡片圆角裁剪出血边缘）；hero 与航班行之间的穿孔虚线上叠加起飞姿态小飞机（14% 不透明藏青灰、白底圆形 halo 在虚线上打出镂空、机头上仰 30°、定位于「左 padding → 完成按钮左缘前 16dp」区间中点），虚线带高度仍为 2dp，飞机经 `clipChildren=false` 溢出到上下留白，不增加内容高度，并随虚线一同在无航段时隐藏；meta 行下方新增 #EEF0F4 撕线虚线；根容器由 LinearLayout 改为 FrameLayout 以承载装饰层，装饰元素均位于内容层之下。小组件固定浅色白卡不随深色模式变化（见 6.10-9），故装饰透明度深色减半规则不适用。

0.7.5 迭代修订：装饰层加料（航空票据主题，全部为覆盖层/背景 drawable，不占布局高度）——底边全宽红蓝航空邮件斜纹条（纯色对撞，让开左缘红竖条）；右上角藏青/东航红双三角撞色块，出血随卡片圆角裁剪；左下角装饰性条码纹理（12% 藏青，登机牌肌理）；倒计时数字加 5% 浅红圆角 wash 锚定 hero 视觉重心；燕尾水印增为三线并加入红色 accent 细弧，不透明度 6%→8%。

0.7.6 迭代修订：按反馈移除倒计时浅红 wash；上半部分补充结构感元素（均不增加内容高度）——「距离到位」标签行升级为 时钟小图标 + 标签 + 红刻度段（2dp）+ 细引导线（1dp，weight=1 延伸至按钮前），hero 区与「完成」按钮之间加 1dp 竖向细分隔线划出操作区，右上撞色角块加大至 108×84dp 并提高不透明度（藏青 17% / 红 20%）。

0.7.7 迭代修订：按反馈移除标签行红刻度段、细引导线与按钮前竖线；顶部与中部装饰重设计——meta 行升级为浅灰（#F0F2F5）圆角头带（右端 mini 票据码纹理），卡片背景加红色 L 形拐角（左竖条向顶边延伸 36dp），hero 区衬底改为藏青/红双弧线条对撞（背景 drawable，零高度成本），航线带加两端锚点（左端藏青实心圆点、右端红色空心环收笔于按钮左缘前 16dp）且小飞机不透明度 14%→20%；行距微调补偿头带高度，内容总高不变（两段航段 222dp），无裁剪风险。

## 7. 数据、持久化与安全

### 7.1 本地存储

| SharedPreferences | 内容 | 保护方式 |
|---|---|---|
| `air_shift` | `user_name`、`last_live_refresh`、`assignments`；CEA 另含 `duty_progress_date`、`duty_index` | 应用私有；JSON/标量 |
| `air_shift_secrets` | API Key IV 与密文 | Android Keystore AES-GCM、128-bit tag、AAD |
| `air_shift_special_services` | version 1–3 兼容的结构化状态、32 字节随机 HMAC key | 应用私有；不含通知正文 |

旧 supplement、旧 gateway URL 和旧 gateway 凭证在初始化时删除。

`RosterStore.loadAssignments()` 当前以整份 JSON 为容错边界：任一条目抛错会使整个列表退化为空。MUC JSON Codec 则逐项跳过损坏记录；两者容错策略不同。

### 7.2 备份与迁移

- Manifest 设置 `allowBackup=false` 和 `fullBackupContent=false`。
- `data_extraction_rules.xml` 对云备份和设备迁移都排除 SharedPreferences、数据库和文件。
- API Key 明文不进入 saved-instance-state。

### 7.3 权限与组件

共同 Manifest 权限：

- `INTERNET`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_FINE_LOCATION`
- `POST_NOTIFICATIONS`
- `SCHEDULE_EXACT_ALARM`
- `RECEIVE_BOOT_COMPLETED`

共同组件：

- 导出的 launcher `MainActivity`；
- 非导出的 `MucNotificationListenerService`，受 `BIND_NOTIFICATION_LISTENER_SERVICE` 保护；
- 非导出的 `ReminderReceiver`；
- 非导出的、监听 `BOOT_COMPLETED` 的 `BootReceiver`；
- 非导出的 `widget/DutyWidgetActionReceiver`，仅接收小组件显式 `PendingIntent` 的完成动作；
- 导出的 `widget/DutyWidgetProvider`（仅 `APPWIDGET_UPDATE`，供桌面 launcher 绑定）。

WPS 分支让 `MainActivity` 同时成为 Excel `ACTION_SEND` 分享目标，因此所有外部 Intent 都必须经过 MIME、action、项目数和 URI scheme 校验。

### 7.4 隐私约束

1. 图片、Excel 和解析后的完整人员栏不得上传。
2. 飞常准请求只发送航班号、日期和 MCP 协议字段。
3. API Key 不得写入日志、错误消息、源码、资源或测试样例。
4. Debug OCR 日志只记录引擎、行数、token 数和耗时，不记录识别文本。
5. VIP 原文不得持久化，只保存与当前用户航段相关的布尔值。
6. MUC 原文只在内存中解析；持久化只允许结构化航班/日期/服务/变更/取消字段和不可逆指纹。
7. 定位只在本机匹配排班相关机场。

## 8. 技术栈、构建与许可证

### 8.1 工程配置

- 单模块 Android 工程：`:app`。
- Kotlin `2.4.10`，Java `17`。
- Android Gradle Plugin `9.3.0`。
- Gradle Wrapper `9.5.0`，华为云镜像并配置官方 SHA-256。
- `compileSdk = 37`，`targetSdk = 37`，`minSdk = 33`。
- Jetpack Compose + Material 3，浅色/深色双主题（design token 集中于 `ui/theme/AirShiftTheme.kt`）。
- 可在未跟踪的 `local.properties` 中设置 `airshift.buildDir`，将 OneDrive 中的构建产物移到本地目录。

### 8.2 主要依赖

| 依赖 | 版本/来源 | 用途 |
|---|---|---|
| Activity Compose | `1.13.0` | Activity/Compose 接入 |
| Lifecycle Runtime | `2.10.0` | 生命周期与 StateFlow 收集 |
| WorkManager | `2.11.2` | 15 分钟后台刷新 |
| Compose BOM | `2026.08.00` | Compose 版本对齐 |
| Google Play Services Location | `21.4.0` | 融合定位 |
| ONNX Runtime Android | `1.21.1` | OCR 模型推理 |
| OpenCV | `4.12.0` | 图像预后处理 |
| Apache POI | `5.5.1` | `.xls` HSSF 事件流 |
| Kotlin Coroutines Android | `1.9.0` | 异步解析与 OCR |
| Material Icons Core | CEA 分支新增 | 三 Tab 和按钮图标 |

### 8.3 构建和验证命令

仓库声明的完整验证流程：

```powershell
$env:JAVA_HOME = 'C:\Users\BradJ\AppData\Local\Programs\android-studio\jbr'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

设备测试需要连接 Android 设备或启动模拟器。可选真实 `.xls` fixture 测试依赖 `AIRSHIFT_XLS_FIXTURES_DIR` 和 `AIRSHIFT_XLS_TEST_NAME` 环境变量，默认不执行真实文件回归。

### 8.4 许可证与发布

- 项目代码：Apache License 2.0。
- PaddleOCR SDK 和 PP-OCRv6 模型：Apache License 2.0。
- ONNX Runtime：MIT。
- OpenCV、Apache POI：Apache License 2.0。
- 第三方服务和数据仍受各自条款约束。
- Release 当前未配置发布签名，且 `isMinifyEnabled=false`；现有 APK 只应视为开发/个人验证产物。

## 9. 测试规格与覆盖状态

### 9.1 分支测试规模

| 分支 | JVM 测试 | Android 测试 | 合计 |
|---|---:|---:|---:|
| `main` | 54 | 1 | 55 |
| CEA | 62 | 1 | 63 |
| WPS | 62 | 7 | 69 |

### 9.2 已有自动化覆盖

- OCR 表格几何恢复、姓名隔离、航班清洗、跨日和 VIP。
- 设备端 PP-OCRv6 合成排班图完整链路。
- `.xlsx` 语义列、共享字符串、数值日期/时间、模板变体和非法表头。
- `.xls` 生成工作簿事件流；可选真实 fixture。
- 飞常准字段、JSON-RPC/SSE、请求体、安全错误、缓存、限流和并发合并。
- MUC 类别、数量隔离、航班匹配、变更、取消、乱序、去重、过期、JSON 兼容和隐私。
- 提醒类型和任务稳定 ID。
- CEA：8 个 `DutyTimeline` 时间规则测试，以及低置信直接忽略的状态测试。
- WPS：8 个任务完成测试；6 个分享 Intent/Manifest/队列 Android 测试。

### 9.3 主要测试缺口

- Compose 页面、三 Tab、设置、人工进度和状态恢复。
- `MainActivity` 生命周期及前台自动刷新循环。
- WorkManager 真正执行、自取消、重试和并发导入。
- AlarmManager、开机恢复、通知权限和精确闹钟特殊访问。
- Android Keystore 与排班 SharedPreferences 的设备集成。
- GPS 及 15 km 机场匹配。
- 真实 MUC 通知样式和企业设备策略。
- 超大图片的内存边界。
- 真实 WPS 版本、真实 `ContentProvider` URI 授权、进程死亡和连续分享。
- CEA 进度日期翻转、导入重置、误触/双击和变更值 UI。
- 仓库没有 CI 工作流或可复核的构建产物。

## 10. 分支兼容、待决策项与已知偏差

### 10.1 集成状态

已完成合并。`MainActivity.kt` 的实际冲突已人工解决：采用 CEA 分层 UI，接入 WPS 分享队列和完成规则；保留扩展机位字段、低置信消息直接忽略策略及手动刷新。新增排班 generation 和条件保存防止旧刷新覆盖新排班，旧 Worker 仅取消自身 ID，不再取消新排班的同名任务。最终代码无未解决冲突。

### 10.2 已采用的产品决策

| 主题 | 合并前 `main` / WPS | CEA | 集成结果 |
|---|---|---|---|
| 低置信 MUC | 显示人工确认/忽略 | 直接丢弃 | 按 feat 优先采用 CEA，不恢复旧人工确认界面 |
| 执勤完成 | WPS 根据 actual 或 3 小时宽限自动判断 | 用户按任务逐项手动推进 | 两者共同驱动当前页和刷新停止；手动刷新仍可用 |
| 当前执勤 | 无独立页面 | 三 Tab + 当前任务倒计时 | 持久化索引表示手动完成前缀；其后自动跳过已完成任务，下一任务也使用同一规则 |
| 版本 | `0.1.0` / code 1 | `0.3.0` / code 3 | `0.4.0` / code 4 |
| 航段机位 | 只保存进港到达、出港出发 | 另存进港始发和出港到达 | 全部保留；映射、JSON 往返及旧数据缺字段兼容已测 |

自动跳过不会写入人工完成索引，因此未被人工确认完成的任务在预计时间修正后可以恢复；点击完成时推进到当前实际显示任务之后。新排班原子重置人工索引并增加 generation，普通刷新保留进度。前台刷新仅随前台资格、generation 或完成状态变化重启，普通实时字段更新不触发紧密请求循环。

### 10.3 已确认的实现偏差和风险

以下是代码扫描确认的事实，不是尚未实现的新需求：

1. **已修正文档**：README 已删除“单字 OCR 容错”声明；实际仍按完整规范化姓名匹配，没有新增模糊匹配。
2. **MUC 跨承运人数字简写可能误配**：同数字、同日存在多个承运人时会按稳定排序自动选一个，不会进入人工歧义确认。
3. **图片无输入上限**：Excel 有明确大小限制，图片解码没有像素或内存上限。
4. **外部载荷解析脆弱**：飞常准内层载荷以正则读取 Python-repr 风格字段，并非严格 JSON；格式或转义变化可能导致失败。
5. **排班 JSON 整体失败**：一个损坏条目会使整份排班加载为空。
6. **提醒 ID 碰撞理论风险**：PendingIntent 请求码使用 32 位 `stableId.hashCode()`。
7. **未消费字段**：`arrivalBridge` 在 `main` 被解析和持久化，但当前主分支 UI 未展示。
8. **CEA 到位提示边界**：集成后整个任务符合 WPS 完成规则即自动跳过；但尚未整体完成的过站任务仍沿用 CEA `DutyTimeline` 的到位时间显示规则。
9. **CEA 人工进度不可撤销**：完成按钮无确认、回退或撤销，误触会跳过任务。
10. **已修复前台恢复**：effect 纳入 generation 和完成状态；新非空排班恢复、普通重组不重启、busy 恢复已有设备回归。
11. **已修复 saved-state 队列缺失**：使用 SavedStateHandle 保存 FIFO 事件和编号，旧 attempt/已销毁页面不能提交；恢复能力仍受第 2.5 节 Android 和 URI 授权边界约束。
12. **已修复跨排班 Worker 竞态**：generation 条件保存及自身 ID 取消保护新排班。同一 generation 的前后台实时响应仍按最后保存生效，不新增响应时间排序协议。
13. **已统一设置路径**：保存 API Key 时也使用联合完成状态决定后台刷新资格。
14. **保留 WPS 三小时规则及其局限**：按 feat 优先保留；陈旧预计时间或超长延误仍可能过早完成，README 提示手动刷新核对。
15. **真实 WPS 兼容尚未证明**：测试使用通用 Android Intent，没有真实 WPS 版本和 MIME/ClipData 样本。

### 10.4 合并验收门槛

本次代码合并的验收项如下；第 7 项仍待具备真实设备及应用环境后执行，不因模拟器测试通过而标为完成：

1. 明确第 10.2 节的产品选择并更新本文。
2. 手工整合 `MainActivity`，保留 CEA UI 分层，同时接入 WPS Intent 队列。
3. 统一 `RosterAssignment` 的 CEA 机位字段和 WPS 完成扩展。
4. 修复 WPS 前台恢复、进程恢复和 Worker 取消竞态。
5. 为执勤索引与自动完成之间建立单一状态语义。
6. 运行 `test lintDebug assembleDebug connectedDebugAndroidTest`。
7. 在真实设备验证图片、`.xls`、`.xlsx`、WPS 分享、通知、精确闹钟、定位和 MUC。
8. 检查最终 diff、版本号、迁移兼容和隐私约束。

## 11. 验收标准

### 11.1 共同基线

- 输入有效姓名并导入包含该姓名的图片或 Excel 后，只生成属于该用户的有效航班任务。
- 不同员工姓名子串、无航班行、无机号 Excel 行和无效时间不得生成错误任务。
- `CES`、带符号航班号和 `+` 次日时间按本规格规范化。
- 飞常准无 Key、网络失败、部分失败和全部失败均保持可理解、脱敏且不丢失已有排班。
- 进港/纯出港/过站提醒符合第 5.7 节，实时变化后旧提醒被替换。
- 定位拒绝或失败不阻断排班；成功匹配必须在 15 km 内。
- 只有 MUC 白名单包可写入特服状态，持久化 JSON 不含原文和敏感个人字段。
- API Key 清除后缓存清除、后台实时刷新停止，已保存排班仍可离线查看。

### 11.2 CEA 分支

- 三个 Tab 可切换且全部执勤功能不回退。
- 当前执勤倒计时、下一任务、登机开始/关闭时间符合 `DutyTimeline` 测试规则。
- 导入新排班重置进度，实时刷新不重置，日期变化回到索引 0。
- 全部执勤只显示特服/变更摘要，当前执勤显示完整详情。
- CEA 低置信策略按最终产品决策验收，不能同时存在“直接忽略”和“要求人工确认”的模糊文案。

### 11.3 WPS 分支

- 真实 Android/WPS 的单个 `.xls` 与 `.xlsx` `content://` 分享可在冷启动、热启动和首次 onboarding 后完成导入。
- 错误 MIME、多文件、无 stream、`file://`、云链接和错误 action 被明确拒绝或忽略。
- 分享队列逐项消费，同 URI 重复分享不会被错误折叠。
- 实际时间、预计/计划加 3 小时、过站两段和空列表的完成判断与测试一致。
- 全部完成后前后台自动刷新停止，手动刷新仍可用；导入新未完成排班后前后台都必须可靠恢复。

## 12. 源码追踪索引

### 12.1 共同核心

| 规格领域 | 主要证据 |
|---|---|
| 应用流程与主 UI | `main:app/src/main/java/com/bradj/airshift/MainActivity.kt` |
| 排班模型 | `main:app/src/main/java/com/bradj/airshift/model/RosterAssignment.kt` |
| 图片 OCR 接入 | `main:app/src/main/java/com/bradj/airshift/parser/OcrRosterReader.kt` |
| OCR 表格恢复 | `main:app/src/main/java/com/bradj/airshift/parser/RosterTableParser.kt` |
| Excel 分流与限制 | `main:app/src/main/java/com/bradj/airshift/parser/ExcelRosterReader.kt` |
| `.xlsx` 解析 | `main:app/src/main/java/com/bradj/airshift/parser/ExcelRosterParser.kt` |
| `.xls` 解析 | `main:app/src/main/java/com/bradj/airshift/parser/XlsRosterParser.kt` |
| 飞常准协议 | `main:app/src/main/java/com/bradj/airshift/api/VariFlightClient.kt` |
| 实时数据合并 | `main:app/src/main/java/com/bradj/airshift/api/FlightInfo.kt` |
| 后台刷新 | `main:app/src/main/java/com/bradj/airshift/api/FlightRefreshWorker.kt` |
| 本地存储 | `main:app/src/main/java/com/bradj/airshift/data/RosterStore.kt` |
| API Key | `main:app/src/main/java/com/bradj/airshift/data/VariFlightApiKeyStore.kt` |
| 提醒 | `main:app/src/main/java/com/bradj/airshift/reminder/` |
| 定位 | `main:app/src/main/java/com/bradj/airshift/location/AirportLocator.kt` |
| MUC 全链路 | `main:app/src/main/java/com/bradj/airshift/specialservice/` |
| Manifest/隐私 | `main:app/src/main/AndroidManifest.xml`、`main:app/src/main/res/xml/data_extraction_rules.xml` |
| 依赖与版本 | `main:app/build.gradle.kts`、`main:build.gradle.kts`、`main:gradle/wrapper/gradle-wrapper.properties` |

### 12.2 分支专属

| 分支能力 | 主要证据 |
|---|---|
| CEA 主题/导航 | `feat/cea-ui-rewrite:app/src/main/java/com/bradj/airshift/ui/` |
| CEA 时间线 | `feat/cea-ui-rewrite:app/src/main/java/com/bradj/airshift/model/DutyTimeline.kt` |
| CEA 进度持久化 | `feat/cea-ui-rewrite:app/src/main/java/com/bradj/airshift/data/RosterStore.kt` |
| CEA 低置信替换 | `feat/cea-ui-rewrite:app/src/main/java/com/bradj/airshift/specialservice/SpecialServiceReducer.kt` |
| WPS Intent 与队列 | `feat/wps-excel-share-import:app/src/main/java/com/bradj/airshift/SharedExcelImport.kt` |
| WPS Manifest 分享入口 | `feat/wps-excel-share-import:app/src/main/AndroidManifest.xml` |
| WPS 完成规则 | `feat/wps-excel-share-import:app/src/main/java/com/bradj/airshift/model/RosterAssignment.kt` |
| WPS Worker 停止 | `feat/wps-excel-share-import:app/src/main/java/com/bradj/airshift/api/FlightRefreshWorker.kt` |

### 12.3 仓库级文件

- `README.md`：用户说明与已实现功能声明。
- `PLAN.md`：`main` 为 MUC 方案记录，CEA 分支改为 UI 重写计划。
- `LICENSE`：项目许可证全文。
- `THIRD_PARTY_NOTICES.md`：内嵌代码、模型和依赖许可。
- `AGENTS.md`：仓库协作约束，不属于运行时产品。
- `.gitignore`：忽略 IDE、构建、签名、环境和本机配置。
- `settings.gradle.kts`、`build.gradle.kts`、`app/build.gradle.kts`、`gradle.properties`、`gradle/wrapper/`：构建系统。
- `testdata/synthetic_roster.png`、`tools/generate_synthetic_roster.ps1`：无真实个人信息的 OCR 回归资产。

---

原始扫描事实基准保留于历史段落；当前集成基准为第 2.5 节的本地 main。后续提交应继续更新集成基线、风险状态和验收记录，避免把历史分支声明当作现状。
