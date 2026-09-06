# 航勤智排（AirShift）当前实现规格

> - 文档类型：当前 `main` 的 as-built specification
> - 审查日期：2026-09-06（Asia/Shanghai）
> - 源码基线：`c5e02cd` 之后的 0.11.0 界面重设计、0.11.1 动效调整与 0.11.2 排班日跟踪
> - 应用版本：`0.11.2` / version code `50`
> - 支持平台：Android 13（API 33）及以上

## 1. 文档定位

本规格描述当前主线代码已经实现的行为、状态所有权、外部边界、验证覆盖和已知风险。它不再要求读者理解早期 `feat/cea-ui-rewrite` 或 `feat/wps-excel-share-import` 的分叉历史；这些能力已经进入 `main`，简要演进记录见第 16 节。

- [README.md](README.md) 面向使用者和首次接触项目的开发者，回答“它做什么、如何使用、如何构建”。
- 本文面向维护者，回答“规则到底是什么、状态在哪里、调用如何流转、哪些边界尚未验证”。
- 当文档与代码冲突时，以当前代码和实际测试结果为准，并应在同一次迭代中修正文档。
- “当前实现”不等于“理想需求”。第 13 节明确列出代码中已经存在但可能需要后续产品决策的限制。

## 2. 产品定义与边界

### 2.1 目标

AirShift 是航司地面服务保障人员的单用户日排班助手，核心目标是：

1. 从多人排班图片或 Excel 中只生成与当前姓名匹配的保障任务；
2. 把进港、出港或过站任务按时间组织成可执行序列；
3. 以当前和下一项未完成执勤为实时跟踪窗口，限制付费航班查询；
4. 在设备本机完成 OCR、排班解析、进度保存、提醒和 MUC 通知识别；
5. 由手机直接调用飞常准 Aviation MCP，不建设自有中转服务；
6. 按上三休三周期在本机推算上班日、班次槽位和应乘班车，不依赖当天是否已导入排班。
7. 自动刷新与提醒只在排班日进行：排班日之外（休息、请假、提前导入的当晚）不联网、不提醒；同一航班号别的日子的动态不当成排班里的这一班。

### 2.2 运行前提与降级能力

| 能力 | 前提 | 缺失时的行为 |
|---|---|---|
| 排班导入与离线查看 | Android 13+、已设置姓名 | 核心功能不可用 |
| 图片识别 | 系统图片选择器返回可读 URI | 可改用 Excel |
| 实时航班 | 用户自己的飞常准 API Key、网络 | 保留导入或上次保存的数据 |
| 系统通知提醒 | 通知运行时权限 | 闹钟可安排，但不会显示通知 |
| 精确提醒 | 精确闹钟特殊访问 | 使用非精确 `setAndAllowWhileIdle` |
| 自动机场判断 | 粗略或精确定位权限、刷新结果含坐标 | 不影响排班和提醒 |
| MUC 识别 | 通知读取特殊访问、MUC 发出可读新通知 | 其他功能不受影响 |
| 后台刷新 | API Key、未完成排班、系统允许 WorkManager 运行 | 前台或手动刷新仍可用 |

### 2.3 明确不在范围内

- 自建后端、云函数、数据库、账号体系、跨设备同步或团队调度；
- iOS、Web、Android 12 及以下；
- 读取 MUC 数据库、历史聊天、通知移除事件、附件或无障碍页面；
- 上传原始排班、图片、Excel 或 MUC 正文到自建服务；
- 预置、共享或代管飞常准 API Key；
- 发布签名、应用商店上架、生产遥测或 CI/CD 基础设施；
- 自动撤销人工完成、多份排班并存、多人账户切换。

### 2.4 完整用户流程

1. `MainActivity` 初始化提醒频道、一次性迁移和后台刷新资格，并通过 `AppDutyPorts.create` 装配 `DutyViewModel` 的全部依赖。
2. 如果没有姓名，先显示 Onboarding；WPS 分享事件会等待姓名保存。
3. 用户在“全部执勤”选择图片/Excel，或从外部应用分享 Excel。
4. Reader 在后台读取，Parser 返回 `RosterParseResult`。
5. 解析成功后整体替换排班、generation 加一、人工进度归零，并扇出提醒、MUC 重匹配、WorkManager 配置和小组件重绘。
6. 如果有 API Key、排班日的跟踪时段已开始（首个任务前 3 小时）且当前执勤窗非空，立即刷新当前和下一项未完成执勤；提前导入时只保存并提示自动跟踪的起点。
7. 用户在“当前执勤”执行任务；自动完成规则与人工完成前缀共同决定当前/下一项。
8. 全部完成后自动刷新停止；用户仍可在“全部执勤”显式下拉，查询全排班并修正数据。

## 3. 技术架构

### 3.1 总体数据流

```text
系统图片选择器
  └─ ImageDecoder → PP-OCRv6 / ONNX Runtime / OpenCV → OcrToken → RosterTableParser ┐
系统文件选择器 / ACTION_SEND                                                        │
  └─ 文件签名 → OLE/XLS 事件流 或 ZIP/XLSX SAX → ExcelRosterParser ────────────────┤
                                                                                   ↓
                                                                        RosterParseResult
                                                                                   ↓
                                                               RosterStore.replaceAssignments
                                                                 generation++ / duty_index=0
                                                          ┌────────┬────────┬────────┬────────┐
                                                          ↓        ↓        ↓        ↓        ↓
                                                      Compose   MUC重匹配  Reminder  Worker   Widget

飞常准 HTTPS MCP → 进程级限流/缓存 → batch → generation/scope 复查 → RosterStore 原子合并
MUC 新通知 → 包白名单 → 文本提取 → parser/reducer → SpecialServiceRepository → Compose
App/Widget 完成 → generation+index 原子校验 → 进度推进 → 新窗口补查/调度/重绘
```

### 3.2 模块职责

| 模块 | 主要路径 | 职责 |
|---|---|---|
| Composition root | `MainActivity.kt` | 装配依赖（`duty/DutyPorts.kt` 的 `AppDutyPorts`）、接收分享 Intent、转发生命周期；不含业务流程 |
| 编排层 | `duty/DutyViewModel.kt`、`duty/DutyUiState.kt`、`duty/DutyPorts.kt` | 导入、实时刷新、人工完成、设置保存、权限跟进；只依赖端口接口（`RosterRepository`、`LiveFlightRefresher`、`ReminderPort`、`SpecialServicePort` 等），可在 JVM 上用假实现测试 |
| 四页装配 | `ui/AirShiftApp.kt`、`ForegroundFlightRefreshEffect.kt` | 渲染 `DutyUiState`，把用户动作、生命周期、权限结果、分钟 tick 与分享队列转发给 ViewModel |
| 外部分享队列 | `SharedExcelImport.kt` | 校验分享 Intent、FIFO、attempt ownership、saved-state 恢复 |
| 领域模型 | `model/` | 排班字段、任务类型、自动完成、人工前缀、两项执勤窗、时间线 |
| 排班周期 | `model/shift/` | 六天周期、班组环形轮转、班次槽位、交接班到岗、班车选择（纯 Kotlin） |
| 导入解析 | `parser/` | OCR 表格恢复、XLS/XLSX 安全读取、姓名/日期/航班/VIP 提取 |
| OCR 引擎 | `com/paddle/ocr/`、`assets/models/` | PaddleOCR 检测/识别、预后处理、ONNX 会话和模型资产 |
| 实时航班 | `api/` | MCP 客户端、响应解析、多经停映射、缓存/限流、批次和 WorkManager |
| 本地数据 | `data/` | 排班/进度 JSON、generation、Keystore API Key |
| MUC | `specialservice/` | 通知提取、结构化解析、航班匹配、时序归并、去重和持久化 |
| 提醒 | `reminder/` | 提醒策略、AlarmManager、通知频道和开机恢复 |
| 定位 | `location/` | Fused Location、候选机场和 15 km 匹配 |
| UI | `ui/` | 四页 Compose 界面、自定义底栏、组件和 design token |
| 小组件 | `widget/`、`res/layout/widget_duty_item.xml` | 当前任务模型、RemoteViews、完成广播和重绘 |
| 测试/样本 | `app/src/test/`、`app/src/androidTest/`、`testdata/`、`tools/` | JVM/Android 回归、合成 OCR 图片及生成脚本 |

项目没有导航框架、数据库 ORM 或依赖注入框架。依赖在 `AppDutyPorts.create` 手工装配；新增全局业务流程应加在 `DutyViewModel` 并先补 `DutyViewModelTest`，不要回到 Composable 里。`RosterStore` 实现 `RosterRepository`，是编排层看到的唯一排班存储。

### 3.3 状态所有权

| 状态 | 所有者 | 生命周期/存储 |
|---|---|---|
| 姓名、排班、实时字段、刷新时间、人工进度、generation | `RosterStore` | `air_shift` SharedPreferences，跨进程重建 |
| 飞常准 API Key | `VariFlightApiKeyStore` | `air_shift_secrets` 密文 + Android Keystore 密钥 |
| 班组校准、到位余量、手动班组 | `RosterStore` | `air_shift` SharedPreferences，独立于 generation 不变量 |
| 特服、变更、取消、指纹、处理状态 | `SpecialServiceRepository` | `air_shift_special_services` JSON + `StateFlow` |
| 分享事件 FIFO、递增 ID、attempt token | `SharedExcelImportQueueViewModel` | `SavedStateHandle` Bundle，尽力恢复 |
| 当前底栏页面 | `DutyNavigationViewModel` | 配置变化内保留；真正重新前台时回当前执勤 |
| 工作中、状态消息、警告、当前机场、待补查窗口、前台标志、当前时刻 | `DutyViewModel`（`DutyUiState`） | 随 ViewModel 跨配置变化保留；进程重建后重新从存储恢复 |
| 航班成功缓存、限流时间窗 | `VariFlightClient` companion | 进程内共享，进程重启清空 |

### 3.4 一致性与竞态保护

- `RosterStore` 使用进程内 `rosterLock` 串行化排班、generation 和人工进度变更。
- 新排班整体替换时 generation 加一；前台回调、Worker 和小组件完成都必须携带并复查 generation。
- 网络响应写回时重新读取最新 snapshot，只合并当前 scope 允许的索引和 lookup，旧排班响应不能覆盖新排班。
- 每个 HTTP 请求前复查 generation、API Key 和最新窗口；等待同 key 缓存锁之后、真正请求上游之前再次复查。
- App 完成操作还校验“调用者看到的当前索引”，旧卡片或重复点击变为 no-op。
- 分享队列只有队首可取得 attempt token；旧页面回调或旧 token 不能消费/提交新 attempt。
- 导入与刷新在 `viewModelScope` 内执行；ViewModel 被清理（进程重建）后，未完成的读取或响应不再落库，分享队列事件也不会被消费，由新 ViewModel 重新发起。
- 同一 generation 的重叠刷新没有服务端事件时间排序；相同字段仍以最后一次成功写入为准。

## 4. 领域模型与进度

### 4.1 `RosterAssignment`

一项任务保存以下字段组：

- 排班身份：机号、机型、进港航班、前站、计划到达、出港航班、后站、计划出发、匹配行的人员栏；
- 实时进港：预计/实际到达、始发登机口、始发机位、本站到达机位、登机口关闭观察、实际离位、廊桥；
- 实时出港：预计/实际出发、本站登机口、本站出发机位、目的地到达机位、登机口关闭观察、实际离位；
- 机场：前站/后站代码与名称、本场代码与名称；
- 标记：进港 VIP、出港 VIP。

任务类型由航段存在性派生：

- `ARRIVAL_ONLY`：只有进港；
- `DEPARTURE_ONLY`：只有出港；
- `TURNAROUND`：同时有进港和出港。

`stableId` 由机号、进港航班、出港航班以及计划到达优先/计划出发回退的日期拼接。它同时用于列表 key、提醒请求码的输入和任务身份。

### 4.2 航班与时间规范化

- 航班号转大写并去除非字母数字字符，再提取首个 `2–3` 个字母 + `3–4` 位数字片段。
- `CES` 前缀转换为 `MU`。
- 排班时间接受 3/4 位 `HHmm`；包含 `+` 时落在次日。
- 无年份的月日从前一年、当年、后一年中选择离设备当天最近的合法日期。
- 查实时航班时，lookup key 是规范化航班号与运行日期。飞常准的 `date` 是**出发日**（真机核对，见 §12.2）：出港航段取计划出发日；进港航段取计划到达时间的运行日（`FlightOperation.operationDateOfArrival`，06:00 前到达的夜班航班算前一天，与 `DutyProgressDay` 共用边界）；航段没有计划日期时先用同一任务另一航段的日期，再回退排班日（最早计划时间所在日，`rosterDate()`），只有整份排班都没有日期时才用执行时当天（`lookupFallbackDate`）。

### 4.3 自动完成、人工完成与窗口

单航段满足任一条件即自动完成：

1. 已有对应实际到达/起飞时间；
2. 当前时间不早于“预计时间优先、计划时间回退”加 3 小时；预计时间只在与计划时间相差不超过 12 小时时采信（`FlightOperation.trusted`），否则按计划时间；
3. 航段完全没有实际、预计或计划时间，因无法跟踪而视为完成。

不存在的方向天然完成，过站任务必须进、出港都完成。排班列表为空不算“全部执勤完成”。

**同一班归属（`model/FlightOperation.kt`）**：同一航班号每天都执行，排班里的那一班只有一个。与计划时间相差不超过 `MAX_DEVIATION = 12h` 的动态才属于这一班（相邻两天相隔 24 小时，取一半）；相差更多的是别的日子的同号航班。实时合并（§7.4）、自动完成与提醒（§8.1）三处共用这一规则，因此别的日子的动态既不能让任务永远完不成，也不能把提醒挪到休息日。

**排班日跟踪时段（`model/RosterTracking.kt`）**：App 只保存一份排班，它就是用户上班那天的进程单；排班日之外的日子——休息、请假，或提前一天导入——用户都不上班。自动跟踪的起点 `startsAt` = 最早航段时间（计划优先，缺计划时回退预计/实际）− `LEAD = 3h`，与收尾的 3 小时宽限对称；终点仍由逐项自动完成决定。`hasStarted` 为假时，所有 `DUTY_WINDOW` 入口（导入后首刷、前台自动、后台周期、完成补查）的下标与 lookup 集合都为空；`ALL_ROSTER`（显式手动下拉）不受限。跟踪时段不看排班日历的到岗判断，因为请假日日历仍会显示上班。

人工进度是一个按日保存的前缀计数 `duty_index`：

- 当前有效起点为人工前缀末尾；
- 从该位置继续跳过自动完成项，得到当前未完成任务；
- 再从当前之后跳过自动完成项，得到下一项未完成任务；
- 前缀随执勤日保存：`DutyProgressDay` 以 06:00 为界（夜班跨零点后的凌晨仍属前一天），执勤日变化时前缀视为 0；新排班导入显式写 0；实时刷新不改变它；`RosterStore` 通过注入的 `Clock` 取“现在”；
- 自动跳过不会写大人工前缀，因此后续实时数据修正后，任务可以重新变为未完成。

### 4.4 成功导入的原子语义

所有导入来源共享 `finishImport`。只要 Parser 成功返回 `RosterParseResult`，即使任务列表为空，也会：

1. 以新列表整体替换旧排班；
2. generation 加一，人工进度重置为当天的 0；
3. 取消旧排班 stable ID 对应的提醒；
4. 重新读取已保存 snapshot 并更新 Compose；
5. 让 MUC 状态对新排班重新匹配；
6. 根据 API Key 与完成状态重配 WorkManager；
7. 按新排班重排提醒并重绘小组件；
8. 对分享导入，在排班落盘后才消费队列事件；
9. 有 API Key 时只刷新新的两项执勤窗。

文件无法打开、签名无效、工作簿损坏或 Parser 抛错时不替换旧排班。当前没有导入预览、替换确认或回滚。

### 4.5 排班周期与班次轮转

排班日历的全部规则由 2026-08-24 至 2026-09-01 的六份真实排班表反推，样本内零反例。实现集中在 `model/shift/`，为纯 Kotlin、无 Android 依赖。

#### 六天周期

锚点 `ShiftCycle.ANCHOR = 2026-08-23` 为周期第 1 天，`floorMod` 保证锚点之前的日期同样有效：

| 周期内序号 | `ShiftDayKind` | 当天形态 |
|---|---|---|
| 0 | `WORK_FIRST` | 接班日，上午由上一班交出，本班约 10:40 起接手，干到次日凌晨 |
| 1 | `WORK_SECOND` | 整班，07:10 起到次日凌晨 |
| 2 | `WORK_THIRD` | 整班，07:10 起到次日凌晨 |
| 3 | `HANDOVER` | 交接班日，只上上午 07:10–10:00 |
| 4、5 | `REST` | 休息 |

#### 班组轮转

- 10 个在编班组（7、12 号空缺）按固定环形顺序 `1 → 5 → 11 → 8 → 9 → 2 → 6 → 4 → 10 → 3` 排列。
- 序列**只在整班工作日**左移 `ROTATION_STEP = 3`；交接班日与休息日不推进，因此 `workOrdinal` 只数 D1/D2/D3。
- `rotationOffset = floorMod((workOrdinal - 1) * 3, 10)`，已校准到 2026-08-24 偏移为 0。
- 按日历日推进无解（`4k ≡ 3 (mod 10)` 无整数解），故“仅工作日推进”是唯一自洽解释。
- 某组当天位置 = `floorMod(cycleIndex - rotationOffset, size)`。

#### 班次槽位

旋转后的位置经 `ShiftTierSizes` 映射为槽位：位置 0–2 是早一–早三，3–6 是中一–中四，7–9 是晚一–晚三（模板本身是 4/4/4，当前 10 组填成 3/4/3）。实测下班时间随槽位号单调递增：早班约 17:15–17:35、中班约 19:20–21:55、晚班约 23:55–次日 01:35。

#### 交接班日到岗

交接班日的槽位继承 `previousFullWorkday`（即前一个整班工作日）的轮转结果，`attends = (tier != 晚)`：那天排到晚班的组干到次日凌晨，交接班上午不到岗。2026-09-01 的表格中出现的 7 个组恰为 08-31 的早班 + 中班，且各组首个航班的先后顺序与 08-31 的班次顺序逐位吻合。

#### 班车

- 发车时刻 `04:50`、`05:25`、`05:55`，其后自 `08:00` 起每两小时一班；`WORK_FIRST` 与 `HANDOVER` 额外一班 `09:00`。`RIDE_MINUTES = 5`。
- 到位时间沿用 `DutyTimeline` 规则：出港提前 70 分钟、进港提前 15 分钟，不另立规则。
- 选车 = 满足「发车 + 车程 ≤ 到位时间 − 到位余量」的最晚一班；余量可选 0/15/30，默认 15。
- 固定习惯优先于推算：`WORK_FIRST` 早班/晚班坐 `09:00`、`WORK_FIRST` 中班坐 `12:00`；整班工作日的中二至中四坐 `12:00`。
- 首个任务的时间与进出港方向都取自实测，方向在样本内完全一致：清晨上岗的槽位首个任务是出港，下午上岗的中二至中四以及接班日的早班/中班是过站进港。把它们误标为出港会让推荐班车晚于到位时间，`ShiftBusPlanTest` 有对应不变量断言。

#### 校准

`ShiftCalibration` 承载一次真实观测（日期 + 有序分组）。有校准数据时以观测当天的顺序为相位基准，其余日期相对它旋转；成员按“观测优先、未被任何观测行认领的基表成员保留”合并（`ShiftGroupTable.from(observed, base)`，基表默认为内置表），因此病假缺席的人不会丢失归属，真正换组的人也不会留在旧组。只有整班工作日的表格带班次行，故只有这类日期能作为校准点。

内置表 `ShiftGroupTable.DEFAULT` 只含环形顺序与 3/4/3 槽位分层，成员名单为空：仓库公开，真实姓名只存在于用户设备上的校准数据。校准前 `findGroupIdForName` 恒为 null，排班日历依赖设置中的手动班组。

## 5. 排班导入规格

### 5.1 姓名

- Onboarding 和设置页都将输入限制为最多 20 个字符；保存按钮要求去除首尾空格后至少 2 个字符。
- 姓名保存在应用私有 SharedPreferences，可在设置页修改。
- 修改姓名不会重新解析已保存任务；需要重新导入原排班。
- 没有姓名时 `AirShiftApp` 只显示 Onboarding，不启动后续导入消费流程。

### 5.2 Excel 导入

#### 格式分流

- 系统文件选择器传入标准 `.xls/.xlsx` MIME，但 Reader 仍检查文件内容。
- OLE 8 字节签名进入 `.xls`；`PK` ZIP 签名进入 `.xlsx`；其他签名拒绝。
- `.xls` 复制到应用私有 cache 临时文件，最多 256 MiB，并在 `finally` 删除。
- `.xls` 用 Apache POI HSSF 事件模型读取 SST、Label、Number、RK/MulRK、公式缓存字符串/数值/布尔值和 1904 日期标志。
- `.xlsx` 读取 workbook、shared strings 和 worksheet XML 后用 SAX 解析，不构建 POI 完整工作簿模型；DOCTYPE 和外部实体被禁用/空解析。

#### 安全边界

| 路径 | 当前限制 |
|---|---|
| `.xls` 输入文件 | 256 MiB |
| `.xls` 工作表 | 最多 64 张；累计唯一单元格最多 100,000/表 |
| `.xls` 行列 | 行号 10,000 以后、列号 255 以后被静默忽略，不是整表报错 |
| `.xlsx` 单个相关 XML | 16 MiB |
| `.xlsx` 相关 XML 合计 | 32 MiB |
| `.xlsx` worksheet | 最多 64 张 |

#### 语义解析

- 每张表搜索最佳表头行；必须包含机号、人员、至少一个进/出港航班列，并识别至少 6 个语义列。
- 支持机号/机尾号/飞机注册号、进出港航班变体、前站/到站变体、计划/预计时间变体、接送机人员/送机人员/保障人员等别名。
- 不可识别的 worksheet 被忽略；至少一张有效表即可继续。
- 每张有效表优先使用自身表头之前的日期；再回退第一张有效表日期，最后回退设备当天并给出警告。
- 支持 1900/1904 日期系统、完整日期、月日、Excel serial、时间小数、整数/文本 HHmm 和 `+` 次日。
- 有分隔符的人员栏先移除括号备注，再按空白、逗号、顿号、分号、斜杠或竖线切分并逐项精确匹配。
- 无分隔符时，人员栏可与姓名完全相同；组合签名只有长度至少为姓名两倍且包含完整姓名时才匹配。
- 仅生成具有合法机号、至少一个合法航班号且匹配姓名的行。
- 扫描 `VIP信息/要客信息` 区域；向下最多看 30 行，遇到班次标志或连续空行停止，只把航班号集合映射为任务布尔值。
- 多表结果按 `stableId` 去重，并按计划到达优先/计划出发回退排序。
- 另行扫描“候机早班/中班/夜班”三行，按数字串切分为有序的 (组号, 成员)，组号只作分隔、顺序才决定槽位；括号备注不进入姓名。三行缺任意一行即返回 null，`RosterParseResult.observedShiftGroups` 为可空且带默认值，图片 OCR 路径不受影响。

### 5.3 图片 OCR 导入

- 使用 `PickVisualMedia(ImageOnly)` 获取单张图片 URI，不申请通用相册/存储权限。
- `ImageDecoder` 创建软件 Bitmap；识别结束后回收。
- PP-OCRv6 tiny 检测和识别模型随 APK 打包，使用 ONNX Runtime 与 OpenCV；引擎进程内复用。
- 初始化和推理分别由 `Mutex` 串行保护；DEBUG 日志只包含引擎、行数、token 数和耗时，不记录 OCR 文本。
- OCR 结果转换为文字和四边形外接框组成的 `OcrToken`。
- 表格模板为机号、机型、进港航班、前站、预落、出港航班、到站、计离、接送机人员 9 列。
- 至少两个表头命中时拟合列几何；少于两个时回退固定比例模板。只有命中数为 1–4 时产生“表头不完整”警告，0 命中也会回退但当前不警告。
- 数据行优先以机号锚点和中位行距分组；锚点不足时按 Y 坐标聚类。
- 人员栏去除空白、标点和符号后，用 `contains(完整规范化姓名)` 匹配；不使用编辑距离或单字模糊。
- VIP 只从表格右侧、且存在 VIP/要客 anchor 的水平或垂直附加区提取；其他附加栏目不进入任务。

### 5.4 WPS/Android 分享

`MainActivity` 为 `singleTop` 并对外注册两个标准 Excel MIME 的 `ACTION_SEND`。解析条件是：

1. action 精确为 `ACTION_SEND`；
2. MIME 精确为 `application/vnd.ms-excel` 或 `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`；
3. `ClipData.itemCount` 不大于 1；
4. `EXTRA_STREAM` 中存在 `Uri`；
5. scheme 为 `content://`。

`ACTION_VIEW`、`ACTION_SEND_MULTIPLE` 不进入处理；错误 MIME、缺 stream、`file://` 或多项目产生可见拒绝消息。只放在单项 `ClipData`、未同时提供 `EXTRA_STREAM` 的来源也不会被接受。网页/文本链接被拒绝，但应用无法判断合法 ContentProvider 是否在底层按需取回云端内容。

冷启动在首次 `onCreate` 入队，热启动在 `onNewIntent` 入队；随后 Activity Intent 重置为 `ACTION_MAIN`。队列语义：

- FIFO，重复 URI 保留为不同事件；
- 每个事件有递增 ID，只有队首可开始；
- attempt token 防止旧回调消费新任务；
- URI 字符串、错误消息、next ID 与 next token 写入 `SavedStateHandle`；
- 恢复后旧 attempt 失效并重新处理队首；
- 未调用 `takePersistableUriPermission`，也没有队列长度上限；恢复受 Activity saved state、Bundle 大小和来源 URI 授权约束。

### 5.5 解析结果与警告

`RosterParseResult` 包含任务列表、解析出的排班日期和警告。未识别日期、表头不完整、无数据行或没有匹配姓名会产生用户可见警告。空任务列表仍是成功结果，不应与格式/读取失败混淆。

## 6. UI 规格

### 6.1 根导航与页面骨架

- `AirShiftRoot` 使用 Material 3 `Scaffold`（`contentWindowInsets = 0`）加四等分自定义底栏，不使用 Navigation Component 或 Material `NavigationBar`；固定四页：全部执勤、排班日历、当前执勤、设置，底栏文字标签就是这四个词（testTag `nav_all / nav_calendar / nav_current / nav_settings`；因板头标题与标签同名，仪器测试按 testTag 点底栏）。
- 底栏 64dp + 导航栏 inset，顶部 1dp 线；激活项图标与文字变主文字色，上方一枚 20×3dp 东航红"灯"在四个标签间横移到选中项（`animateDpAsState` + `AirShiftMotion.fastSpatial(Dp.VisibilityThreshold)`，约 140 ms 静止），图标与文字用 `AirShiftMotion.defaultEffects()` 变色（约 115 ms 静止）。不再有浮动圆形按钮。
- 分区切换用 shared-axis：新页按底栏标签的左右方向从 16dp 位移滑入并淡入（180 ms，emphasized decelerate，同曲线），旧页 70 ms 淡出。藏青板面是常驻背板（`BoardBackdrop` + `LocalBoardBackdrop`）：放在切页动画之外，高度由当前页 `BoardHeader` 报上来并按 fast spatial 弹簧跟随，板上内容按背板动画高度裁剪、随板面揭开；切页时板面不淡出淡入、不横移。
- 每页顶部是 `BoardHeader`：藏青"板面"贯通到状态栏之下（板内 `statusBarsPadding`），左侧分区名 + 副标题，右侧实时钟（逐位翻牌）与日期；板面主体与板脚由各页给出。因此状态栏图标恒为浅色（`MainActivity.enableEdgeToEdge(statusBarStyle = dark(TRANSPARENT))`），导航栏透明跟随底栏。
- 新 ViewModel 默认当前执勤；冷启动、进程重建和真正从后台恢复都会回到当前执勤。旋转等 `isChangingConfigurations=true` 的停止不算离开应用，保持当前页。

### 6.2 信息条（DutyStrip）

四页共用一种任务表示：`ui/components/DutyStrip.kt`。

- 左侧 6dp 方向夹条（`Modifier.directionHolder` 沿条左边缘绘制，颜色由 `holderColors(kind)` 给出）：进港藏青蓝、出港东航红、过站上蓝下红。
- **折叠态**：每个航段一行 44dp，固定列 —— 进/出字 16、时间 46、航班号 58（宽度按 sp 折算，随系统字体缩放）、航线（弹性；进港"前站 →"、出港"→ 后站"，只给三字码）、本站机位（定位钉 + 数字）、状态灯。有 VIP 或特服时条顶多一行 24dp 的头（类型小字 + 灯），不挤占航段行。系统字体 ≥1.15 倍时每航段改为两行：第一行向/时间/航班/状态灯，第二行航线/机位。
- **展开态**（`DetailLevel.FULL`）：条头为任务类型 + 特服灯 + VIP 灯；每个航段一块：方向灯 + 26sp 航班号，右侧 26sp 本站机位；航线全名"PVG 上海浦东 → LHW 兰州中川"；"计划 / 预计（或实际）"两个时间 + 状态灯；meta 行只列有值的项（对方机位、预计登机、预计登机口关闭、登机口关闭、实际离位）；MUC 登机口/机位变更各一行琥珀值；取消为红灯；特服明细逐行。条脚为机号 · 机型。
- 状态灯（`StatusLamp`，22dp 高、4dp 圆角小矩形，不是胶囊）：已完成 / 已到达 / 已起飞（墨绿带点）、晚 N 分（琥珀带点，15 分钟以内算正点，≥12 小时视为跨午夜不可比）、已取消（红带点）、未起飞（中性）、VIP（琥珀金）、进港 / 出港方向灯。
- 缺失数据一律显示"—"或省略该格，不再使用灰色骨架占位。
- 登机口不在条上显示；飞常准的 `BoardGate` 仍解析并保存，只作 MUC 登机口变更的原值回退。轮椅只显示线性图标 + 等级字母 C/R/S。
- 折叠 ↔ 展开：`AnimatedContent` + `SizeTransform`，容器高度用 `AirShiftMotion.fastSpatial(IntSize.VisibilityThreshold)` 弹簧（ζ0.9 / 刚度 1400，约 140 ms 内静止）从第一帧起步，新内容 35 ms 后 120 ms 淡入，旧内容 70 ms 淡出。条在栏位间移动、以及被展开的条挤开时的位移用另一支 `AirShiftMotion.defaultSpatial(IntOffset.VisibilityThreshold)` 弹簧（ζ0.9 / 刚度 700，`animateItem` placement）——高度与位移是两支不同的弹簧，新增 / 移除的条 120 / 70 ms 淡入淡出。夹条改为绘制，条不再用 `IntrinsicSize.Min`，动画中没有逐帧二次测量；`AllDutyScreen` 每条只接收自身的展开布尔值，点开一条不重组其余条。

### 6.3 全部执勤

- 板头：分区名 + "N 项 · M 已完成"、日期与实时钟；板脚一行：左侧本场（"PVG 上海浦东 · 本场"或"导入并刷新航班后自动识别机场"），右侧不超过 20 字的 `statusMessage`（如"实时信息已更新：16:08"）；更长的状态说明改放列表里的中性通知条（`NoticeStrip(tone = Neutral)`）。
- 列表自上而下：导入操作条（一条 48dp：图标 + 说明 + "图片" / "Excel" 两个小按钮；处理中换成进度指示）、长状态说明的中性通知条、精确闹钟提示条与解析警告条（`NoticeStrip`，琥珀底）、然后是三个栏位：**当前**（1 条，抬起有阴影）、**接下来 N**、**已完成 N**（整条 60% 透明，状态灯"已完成"）。分栏由纯函数 `splitIntoBays(manuallyCompletedCount, now)` 算出：当前 = 人工前缀后第一项未自动完成任务；当前之前的全部与当前之后已自动完成的进"已完成"；其余进"接下来"。
- 点任一条就地展开 / 折叠；展开状态跨配置变化保留。
- `PullToRefreshBox` 触发显式手动刷新，指示器着板面色；列出整份当前排班，而非只列刷新窗口。
- 无排班时给 `EmptyBay`（"还没有排班"）。
- 通知条 / 空态 / 栏位标题也用同一套 `animateItem`；导入操作条在解析中 ↔ 按钮之间交叉淡化（`AnimatedContent` + `SizeTransform`）；下拉指示器只在用户下拉时显示（UI 局部 `pulled` 标志），自动刷新只在板脚给文字。

### 6.4 当前执勤与时间线

- 板面主体是倒计时："距到位" + 68sp Barlow Semi Condensed 翻牌数字（整串数值变大向上、变小向下；旧数字更快退场；跨 60 分钟时小时组横向展开 / 收起）（≥60 分钟显示"N 小时 M 分"两组数字）+ 右侧"到位时间 HH:mm"；到位点已过时改为红灯"应立即到位"（光晕呼吸，系统"移除动画"时静止），到位时间旁给"已过 N 分"。板脚是一个文本节点："下一任务 <航班>：HH:mm 前到位（还有 …）"。
- 滚动区只有一个 `LazyColumn`：栏位"当前"里放当前条（展开、抬起），栏位"接下来"里放下一条（折叠）。
- "执勤完成"钉在底栏之上的 `PinnedActionBar`（52dp 东航红），不随内容滚动；点击先给一次 `HapticFeedbackType.Confirm` 触感再调用完成。没有确认、撤销或回退；原子 guard 只防止旧页面和重复点击完成错误任务。
- 无排班时板头 + `EmptyBay`（"还没有排班"，可去导入）；没有未完成任务时 `EmptyBay`（"今日执勤全部完成"，可返回全部执勤）。这两种状态下板面主体显示"下一班"：由 `ui/calendar/NextShift.text` 从排班日历取今天之后 7 天内第一个到岗日，格式"9/6 周日 早三 · 班车 05:55 · 到位 06:40"；没有班组时不显示。
- 到位时间：有进港航段时取 `实际到达 ?: 预计到达 ?: 计划到达` 减 15 分钟；纯出港取 `实际出发 ?: 预计出发 ?: 计划出发` 减 70 分钟。出港预计登机开始为最佳出发时间前 40 分钟；预计登机口关闭为前 15 分钟。倒计时每分钟更新。
- 页面展示完整特服、行程取消以及 MUC 机位新旧值；MUC 登机口变更是条内单独一行"登机口变更 原值 → 新值 · MUC 更新于 HH:mm"，原值优先取 MUC 消息里的记录，缺失时回退飞常准的 `BoardGate`。
- 尚未整体完成的过站任务始终按进港到位规则显示，即使进港已有实际时间而出港仍未完成。
- 执勤完成后列表项用 `animateItem`：旧的当前条 Exit 档淡出，下一条（key 不变）以 default spatial 弹簧从"接下来"槽升入"当前"槽并展开（`DutyStrip` 的 `SizeTransform`），再下一条 Content 档淡入。
- 三态（没有排班 / 全部完成 / 有任务）之间 Enter / Exit 档淡过渡；从有任务过渡到全部完成时 `EmptyBay` 四行按 40 ms 逐行淡入并上浮 8dp（`LocalReduceMotion` 时只淡入、无延迟），冷启动直接进入这两种空态时静态出现。

### 6.5 设置

- 板头：分区名 + "姓名 · 第 N 组"。分组信息条：MUC 通知读取（状态点 + 已授权/未授权、最近成功识别与最近处理结果、描边按钮打开系统授权页）、排班日历（我的班组；姓名匹配不到时才出现可点选的班组小灯；"到位余量"一行标签 + 三格分段选择器 0/15/30 分钟 + 说明）、个人信息（姓名输入框）、飞常准 API Key（密码输入框、测试连接 / 清除 API Key 文字按钮、连接结果用琥珀提示条）。
- "保存"钉在底部（`PinnedActionBar`），姓名去空格后不足 2 字或测试中禁用。
- 修改姓名只影响后续导入，不重新解析旧排班。API Key 文本状态不使用 `rememberSaveable`，明文不进入 saved-instance-state。测试连接从现有排班中选择首个带航班号的任务；无候选时直接失败。保存非空 API Key 后清缓存并重配刷新；空文本不会隐式清除已有 Key，必须点击"清除 API Key"。
- 到位余量分段器的选中填充是一个物体，按 fast spatial 弹簧在格间横移；班组小灯选中态用 effects 弹簧过渡。

### 6.6 排班日历

- 板头：分区名 + "上三休三 · 第 N 组"；板面主体是今天的班次大字（"晚二" / "休息" / "不到岗"）+ 日型说明 + 右侧班车时间；板脚给预计下班（交接班日为交班）时间与校正来源。
- 范围为今天前 7 天至后 42 天，按月给 `BayTitle`；每天一条：日期列（M/D + 周几）| 班次灯（整班藏青蓝、交接班琥珀）+ 日型说明 + "今天"灯 | 到位 / 到场 / 富余 / 预估或按当日排班 | 右侧班车时刻与下班/交班时间。夹条：今天藏青、整班东航红、交接班琥珀、休息与不到岗灰。
- 休息日与交接班日不到岗时无底无边，只显示日期与说明。到场晚于到位时间（`spareMinutes < 0`）时班车时刻变琥珀并加灯"晚 N 分 · 建议提前一班"；没有合适班车时给琥珀文字。
- 没有匹配到班组时不渲染日期行，只给 `EmptyBay` 并提供跳转设置的入口。

### 6.7 首次进入

- 整屏板面：右上实时钟；标题"航勤智排"与副标题；一条白色信息条承载姓名输入（带 N / 20 计数）；红色"保存并开始使用"；去除首尾空格后 2–20 字才可保存。

### 6.8 视觉系统

- Design token 集中于 `ui/theme/AirShiftTheme.kt`：`AirShiftPalette`（浅 / 深两套语义色，经 `LocalAirShiftPalette` 提供，`AirShiftTokens.colors` 访问）、`AirShiftRadius`（灯 4 / 输入框与小按钮 8 / 信息条 10 / 按钮 12）、`AirShiftSpacing`（4dp 网格）、`currentCardShadow`（唯一一级阴影）、数字字阶（`BoardNumeric` 68 / `BoardValue` 26 / `BoardClock` 22 / `FlightNumberLarge` 26 / `FlightNumber` 16 / `StripTime` 16 / `NumericValue` 17 / `NumericSmall` 15）与 Material3 映射；`ui/theme/AirShiftMotion.kt` 是动效 token（Exit 70 / Content 120 / Enter 180 / Flip 220 / FlipExit 130 / Breath 600 ms、RevealDelay 35 ms、StaggerStep 40 ms、SectionOffset 16dp、PressedScale 0.97、`EmphasizedDecelerate` 曲线；弹簧 `fastSpatial / defaultSpatial / slowSpatial / defaultEffects / fastEffects` 镜像 M3 standard MotionScheme 的刚度 / 阻尼（material3 1.4.0 的 `MotionScheme` 是 internal，应用读不到）；`LocalReduceMotion` 实时读系统动画比例是否为 0）。原则：第一帧就动、退场比入场快、尺寸与位移用弹簧；tween 一律显式曲线，不用起步慢的 Material standard 曲线做入退场。
- 色彩：板面藏青 `#14284B`、条架 `#F1F3F7`、信息条白；东航红 `#C8102E` 只给出港夹条与主操作，进港 `#2B5EA7`；墨绿 `#0F7B5F` 正常 / 已起飞，琥珀 `#B45309` 预计 / 变更 / 交接班，VIP 琥珀金。深色是夜间航显：板面与底 `#0B1526`、条 `#122036`、主文字 `#EDF1F7`、琥珀 `#F5B233`、进港 `#7FA6E6`、红字 `#FF8A98`。没有渐变。
- 字体：`res/font/` 内置 Barlow（OFL 1.1，Regular/Medium/SemiBold/Bold）与 Barlow Semi Condensed（SemiBold/Bold）。所有 Latin 与数字用 Barlow，汉字由系统字体逐字回落；板面大数字、时钟、航班号、机位号用 Semi Condensed；全部数字样式启用 `tnum`，倒计时不跳动（`FontFeaturesInstrumentedTest` 断言等宽）。字阶 11 / 12 / 13 / 15 / 17 / 20 / 26 / 34 / 44 / 68 sp。
- 图标：`ui/components/DesignComponents.kt` 的 `linearIcon()` 1.5px 线性图标集（`LinearIcons`）。
- 组件（`ui/components/`）：`BoardHeader` / `BoardClock`、`DutyStrip`、`StatusLamp`（`LampKind`）、`holderColors` / `Modifier.directionHolder` / `HolderBar`、`BayTitle`、`OdometerText`、`PinnedActionBar`、`EmptyBay`、`NoticeStrip`、`StatusDot`；纯计算在 `LegPresentation.kt`（状态灯规则、本站/对方机位、缺失判定）、`DutyBays.kt`（分栏）、`BoardFormats.kt`（日期与剩余时长文案）、`OdometerSlot.kt`（翻牌槽位）。
- 弹簧与有限时长动画由 Compose 跟随系统"动画时长比例"；无限循环的呼吸灯读 `LocalReduceMotion`（实时）。
- 触控反馈统一走 `PressIndication`（`IndicationNodeFactory`，draw 阶段缩放，不改布局、不重组）：按钮与底栏项缩到 0.97，按下 `fastEffects`、抬手 `fastSpatial`；`DutyStrip` 用只着色的 `AirShiftIndication.row()`；M3 `Button` 系列显式 `Modifier.indication(interactionSource, LocalIndication.current)`，ripple 通过 `LocalRippleConfiguration = null` 关闭。点击语义与 testTag 不变。
- detekt 通过 `app/detekt.yml` 对 `@Composable` 放开命名 / 长度 / 复杂度 / 魔法数字规则，并把 `ui/theme` 排除出 MagicNumber；业务代码不受影响。

## 7. 飞常准实时航班

### 7.1 刷新 scope

`FlightRefreshScope` 只有两种：

- `DUTY_WINDOW`：第 4.3 节定义的当前 + 下一项未完成执勤；
- `ALL_ROSTER`：整份排班的所有索引，包括人工或自动完成项。

| 入口 | Scope | 说明 |
|---|---|---|
| 导入后首次刷新 | `DUTY_WINDOW` | 无旧版时间区间过滤 |
| 前台自动刷新 | `DUTY_WINDOW` | 有排班、有 Key、在前台且未全部完成 |
| 后台周期刷新 | `DUTY_WINDOW` | Worker 每次执行重新计算窗口 |
| 未完成时手动下拉 | `DUTY_WINDOW` | 显式操作也不扩大付费查询范围 |
| 窗口耗尽后手动下拉 | `ALL_ROSTER` | 唯一全排班查询路径 |
| App 完成补查 | `DUTY_WINDOW` 的新 lookup | 忙碌时并入 pending 集合 |
| Widget 完成补查 | `DUTY_WINDOW` 的新 lookup | 一次性联网约束 Worker |

每项最多有两个航段，两项窗口最多产生 4 个不同 lookup；相同航班号与日期去重。窗口不再按计划时间相对现在的小时范围筛选，但受 §4.3 的排班日跟踪时段限制：跟踪起点之前所有 `DUTY_WINDOW` 入口返回空集，不发起任何查询；`ALL_ROSTER` 不受限。

### 7.2 调度与停止

- 前台 effect 在 Activity 前台、有排班和 API Key 时启动；可立即查询，之后目标间隔 5 分钟，忙碌时每 15 秒复查。
- effect key 包含 active、generation 和完成状态；普通实时字段变化不会造成紧密重启。
- 前台循环在跟踪起点之前每 5 分钟触发一次自动刷新，但窗口为空、不联网、不提示；到点后的下一次触发开始查询。
- 后台使用联网约束的 `PeriodicWorkRequest`：周期 15 分钟、generation 专属唯一名称、`KEEP`；首轮延迟为 15 分钟与“距跟踪起点的分钟数 + 1”中的较大值（`initialDelayMinutes`），提前一天导入时 Worker 直接睡到首个任务前 3 小时。`KEEP` 意味着首轮延迟在同一 generation 首次登记时定下；到点后每次执行仍以最新窗口重算。
- 15 分钟是 WorkManager 的最小请求周期，不保证墙钟准点执行。
- Scheduler 使用单线程 executor 串行配置；旧 generation 的工作按捕获 ID 取消，过期 disable 请求不能取消仍符合资格的新任务。
- Worker 在入口和每个 HTTP 前检查 API Key、generation、停止状态、排班、完成状态和最新窗口。
- 全部完成时前台循环退出、Worker 自取消；导入新的未完成排班或全量手动刷新修正完成状态后，可重新启用。
- 普通前台刷新不重建后台周期任务。

### 7.3 协议与字段映射

- 固定端点：`https://ai.variflight.com/servers/aviation/mcp`
- 方法：HTTPS `POST`
- 鉴权：`X-API-Key`
- MCP：JSON-RPC `tools/call`，工具 `searchFlightsByNumber`，参数 `fnum` 和 ISO 日期 `date`
- 超时：连接 5 秒，读取 15 秒

| 本地字段 | 飞常准字段 |
|---|---|
| 计划出发/到达 | `FlightDeptimePlanDate` / `FlightArrtimePlanDate` |
| 预计出发 | `VeryZhunReadyDeptimeDate`，回退 `FlightDeptimeReadyDate` |
| 预计到达 | `VeryZhunReadyArrtimeDate`，回退 `FlightArrtimeReadyDate` |
| 实际出发/到达 | `FlightDeptimeDate` / `FlightArrtimeDate` |
| 实际离位 | `FlightOutgateTime` |
| 登机口关闭观察 | `EstimateBoardingEndTime` |
| 登机口 | `BoardGate` |
| 出发/到达机位 | `DepStandGate` / `ArrStandGate` |
| 廊桥 | `arr_bridge`，回退 `bridge` |
| 机场代码/名称 | `FlightDepcode`、`FlightDepAirport`、`FlightArrcode`、`FlightArrAirport` |
| 机场坐标 | `DepAirportLat/Lon`、`ArrAirportLat/Lon` |

### 7.4 响应和航段选择

- 外层接受普通 JSON-RPC，或从 `data:` SSE 行中选择可解析 JSON 对象。
- `result.content[*].text` 可能各自包含一个或多个航段，客户端会合并全部文本项。
- 内层是飞常准返回的类 Python 字典文本，支持字符串时间与 `datetime.datetime(...)`。
- 多经停响应按顶层字典拆为航段；当存在逐段记录时丢弃 `StopFlag='1'` 的全程摘要。
- 航段先按同一班归属过滤（`ownLegs`）：进港侧看到达时间、出港侧看出发时间（计划优先，回退预计/实际）。任务有计划时间时，航段时间须与之相差 ≤ 12 小时；没有计划时间时只能粗判，须落在查询日期 ±1 天；航段没有任何时间时无从判断，照旧接受（它带不来会漂移的时间）。被过滤的航段不参与后续选择，全部过滤后任务保持原值。
- 入港/出港映射先选计划时间最接近排班时间的航段，再回退已存本场代码、同航班过站拓扑，最后分别回退末段/首段。
- 新响应只有非空字段才覆盖旧值；部分数据或失败不会主动清除已保存实时字段。

### 7.5 缓存、限流与失败

- 同航班同日期成功结果缓存 120 秒；失败不缓存也不共享。相同 key 的并发请求以 `CompletableFuture` 合并为一次上游调用：加载方只在 `ConcurrentHashMap.compute` 里登记自己，网络 I/O 在桶锁之外执行（同桶的其他航班不会被挂住），等待方在 future 上等待；加载失败后等待方重新竞争加载权，并在自己的 loader 里复查执勤窗口。
- 进程级滑动窗口最多接受每分钟 30 次调用。
- 容量在查询缓存之前获取，因此缓存命中也计数；测试连接绕过缓存但计数；窗口移动后在 HTTP 前跳过的调用也已占用本地容量。
- 缓存和限流状态由 App 前台、Worker 和测试连接共享，但只存在于当前进程。
- 401/403、408、429、5xx、网络、超时和安全失败映射为固定用户消息；未知异常不向 UI 暴露堆栈或原始响应。
- batch 逐项执行；取消和线程中断向外传播，其他单项失败进入错误列表。
- 部分成功会保存成功值；后台只有所有已尝试请求都失败且至少一个可重试时返回 `Result.retry()`。

### 7.6 合并规则

- 写回必须 generation 相同，并重新计算写回时的 scope 索引和允许 lookup。
- `DUTY_WINDOW` 不更新窗口外重复航班；`ALL_ROSTER` 可更新已经完成的任务。
- merge 在锁内基于最新排班执行，避免旧列表覆盖另一刷新已写入的不同任务或人工进度。
- 无计划时间的航段按排班日对应 lookup（`lookupFallbackDate`），与请求生成时一致；`fallbackDate` 参数只在整份排班没有日期时生效。
- 保存成功时更新 `last_live_refresh`，随后重匹配 MUC、重排提醒并重绘小组件。

## 8. 提醒与定位

### 8.1 提醒策略

每项任务最多安排一条提醒：

- 有进港航段（包括过站）：实际到达存在时不提醒；否则取同一班的预计到达优先/计划到达回退，提前 15 分钟；
- 纯出港：实际出发存在时不提醒；否则取同一班的预计出发优先/计划出发回退，提前 1 小时 10 分钟；
- 没有可用时间或目标时间已过：不安排。

“同一班”按 §4.3 的 `FlightOperation.trusted` 判断：预计时间与计划时间相差超过 12 小时时不采信，退回计划时间。提醒时间只来自任务自身的时间，因此只可能落在排班日；休息日、请假日或提前导入的当晚不会触发同号航班的提醒。

每次重排用 `stableId.hashCode()` 创建 PendingIntent，先取消同 ID 的旧闹钟。获得精确闹钟特殊访问时使用 `setExactAndAllowWhileIdle`，否则使用 `setAndAllowWhileIdle`。通知频道为高重要性，点击通知打开 `MainActivity`。

`BootReceiver` 只监听标准 `BOOT_COMPLETED`，负责创建频道、从排班重排提醒并重绘小组件；没有监听时区变化、系统时间变化或应用升级广播。

当前人工完成状态不参与 `ReminderPolicy`。提前人工完成的任务若提醒时间未到，App/Widget 完成后的全排班重排以及开机重排仍可能再次安排它。

### 8.2 定位

- 使用 Google Play Services Fused Location Provider；精确权限时请求高精度，只有粗略权限时请求平衡功耗精度。
- 当前位置请求允许最多 60 秒旧缓存，最长等待 20 秒；失败或空结果回退 `lastLocation`。
- 最终位置不得早于 10 分钟。
- 候选机场只来自本次成功实时刷新结果的航班两端，并要求经纬度，按机场代码去重。
- 计算设备到所有候选机场的球面距离，最近值不超过 15 km 才返回匹配。
- 当前机场只存在 `DutyViewModel` 状态；设备原始位置不写入 `RosterStore`，应用自身不把它发送给飞常准。

## 9. MUC 通知识别

### 9.1 输入边界

- 通知读取必须由用户在系统设置主动授权。
- `MucNotificationListenerService` 在提取文本前严格要求来源包名为 `com.ceair.im.muc`。
- 只处理新的 `onNotificationPosted`；通知移除不表示业务取消。
- MessagingStyle 的消息逐条读取正文和消息时间；否则合并 `BIG_TEXT`、`TEXT_LINES`、`TEXT`、标题和会话标题，并把通知时间标为不可靠 source time。
- 系统回调只做提取，后续处理进入单线程 executor。
- 只有“新消息”等摘要时记录不可读状态，不启用数据库读取或无障碍替代方案。

### 9.2 解析类别

文本先执行 NFKC、大小写、全半角和空白规范化。支持：

- 残障旅客；
- 轮椅 `WCHR`、`WCHS`、`WCHC`，以及 MUC 口语简称 `R轮`、`S轮`、`C轮`（字母与“轮”之间可有一个空格；字母前紧贴字母数字时不算，如座位 `32C` 后接“轮椅”或代码 `WCHS` 后接“轮”）。代码与简称可在同一句混用，如「WCHR改为S轮」按更正处理；
- UM 无陪伴儿童；
- MAAS 全流程陪伴；
- 客舱宠物；
- 登机口变更与原登机口；
- 机位变更；
- 放弃/取消特服；
- 取消整段行程。

数量接受 1–2 位阿拉伯数字或常见中文数量词；解析规则排除手机号、票号、座位、行李和重量等数字上下文。

### 9.3 航班匹配与置信度

- MUC 索引只包含有计划到达/出发时间的排班航段。
- 航班 token 必须有 3–4 位数字；按完整数字部分找候选。
- 消息带承运人且存在完整航班号匹配时优先该承运人；随后按明确日期或通知日期的距离、日期方向、日期和航班号稳定排序。
- 没有最大日期距离阈值；跨承运人同数字也会自动稳定选择一个候选。
- 明确服务代码/正式类别（含 R轮/S轮/C轮 简称）可形成高置信；普通“轮椅”“无随行”等弱表达为低置信。
- 低置信特服直接忽略，不提供人工确认 UI。
- 高置信特服、登机口、机位和取消候选都可在无排班匹配时暂存，并在排班变化后重试。

### 9.4 时序、取消、去重和过期

- 更晚 source timestamp 覆盖更早状态；相同时间且不同指纹的冲突保留已有值。特服、登机口、机位、取消四类记录都实现 `TimestampedRecord`，共用同一个 `applyLatest` 归并；登机口与机位的候选循环共用 `reduceFacilityChanges`，取消与特服因有级联规则各自保留。
- 所有候选与记录都实现 `Fingerprinted`（指纹 + 过期时间），过期清理与指纹保鲜只依赖这两项。已移除的旧人工确认字段（`suggestedFlights`、`ignored`）不再写入，读取时忽略。
- 行程取消会停用已有特服，并移除不晚于取消事件的登机口/机位状态；更晚的有效更新可重新激活。
- 指纹使用本机随机 32 字节密钥的 HMAC-SHA256。
- 可靠消息时间按“同指纹 + 同 source time”去重；不可靠 fallback 摘要在未过期期间对同指纹保守去重。
- 未匹配候选以处理时刻起 24 小时过期；已匹配记录以实际优先、预计回退、计划回退的航段时间后 24 小时过期。
- UI active 查询会立即隐藏逻辑过期记录；SharedPreferences 的物理清理只在下一次通知处理或排班 reconcile 时发生，没有独立定时清理器。

### 9.5 持久化隐私

原通知文本只在内存中存在。JSON version 3 保存结构化特服、变更、取消、候选、指纹和处理状态；不会保存原文、姓名、发送人、电话、票号、座位、图片或附件。Codec 可读 version 1–3，并逐项跳过损坏记录。

## 10. 桌面小组件

- `DutyWidgetProvider` 是标准 AppWidget，默认 4×3，可横纵缩放，系统周期 `updatePeriodMillis=30` 分钟。
- 直接读取 `RosterStore`，固定显示当前未完成任务；没有排班或全部完成时显示整卡提示（板头"航勤智排 · 日期"、居中的标题与说明、板面行线、板脚"打开应用查看排班日历与班车"），不留空板。
- 不使用集合容器、RemoteViewsService、翻页、轮播或独立小组件存储。
- 视觉是固定藏青板面（`res/layout/widget_duty_item.xml`，与 App 板头同色，浅深壁纸都可读，不随系统深色切换）：头行"执勤 2/8 · 类型 · 机号 · 机型"（尾部省略）+ VIP 灯；hero 行"距到位"倒计时（40sp Barlow Semi Condensed）+ 到位时间 + 72×40dp 描边"完成"；一条板面行线；两行航段（3dp 方向夹条 + 进/出 + 航班号 + 对方机场 + 本站机位）。没有条码、条纹、弧线、二维码等装饰层，总高约 205dp。文案全部来自 `strings.xml`，颜色来自 `colors.xml` 的 `board / on_board / on_board_secondary / on_board_alert / widget_*`。
- 内容包括执勤序号/总数、类型、机号/机型、VIP、到位状态、进出港航班、对方机场和本站机位。进港行使用 `arrivalStand`，出港行使用 `departureStand`；两行都显示"机位"，缺失显示"机位 —"。
- 小组件不读取 `SpecialServiceRepository`，因此不叠加 MUC 登机口/机位变更、特服或取消状态。
- 倒计时使用 launcher 进程渲染的 count-down `Chronometer`，App 进程无需每秒唤醒。
- 跨过零点后的即时格式由系统 Chronometer 决定；没有代码监听零点并主动 stop，最迟在下一次重绘后转成“应立即到位”。
- 导入、完成、成功实时合并、提醒触发、开机和系统周期都会重绘。
- 点击卡片打开 App；完成按钮发给非导出的显式 receiver，使用 generation + 当前索引原子校验。
- 完成成功后重排提醒、重配周期任务，并用一次性联网 Worker 只补查新进入窗口的航班。
- 小组件按标准 AppWidget 出现在 OriginOS"应用挂件"；第三方应用不能注册厂商专有"原子组件"。RemoteViews 不允许裸 `View`，行线用空 `FrameLayout`；字体经 `android:fontFamily="@font/…"` 引用本包资源，由 launcher 进程解析。

## 11. 持久化、安全与权限

### 11.1 本地存储

| 存储 | 内容 | 兼容/保护 |
|---|---|---|
| `air_shift` | `user_name`、`last_live_refresh`、`duty_progress_date`、`duty_index`、`roster_generation`、`assignments`、`shift_report_margin_minutes`、`shift_manual_group_id`、`shift_group_calibration`、`migration_version` | 应用私有 JSON/标量 |
| `air_shift_secrets` | API Key IV 与密文 | Android Keystore AES-GCM、128-bit tag、AAD |
| `air_shift_special_services` | version 1–3 结构化 MUC 状态、随机 HMAC key | 应用私有，不含正文 |
| `SavedStateHandle` | 分享 FIFO、URI 字符串、错误、ID、attempt token | 临时尽力恢复，不是永久业务存储 |

排班 JSON 兼容行为：

- `arrivalStand` 缺失时回退旧键 `arrivalGate`；
- 新增可空实时/机场/机位字段缺失时为 null；VIP 字段缺失时为 false；
- 非法日期字符串退化为 null；
- `aircraftRegistration` 和 `assignees` 是必需键；外层 JSON 或任一条目抛错会让整份排班加载为空，不会逐项跳过。

排班日历的三个键独立于 generation 与 `rosterLock` 不变量：`shift_report_margin_minutes` 写入时收敛到 0–120；`shift_group_calibration` 解析失败时返回 null 并回退内置班组表，不抛错；`shift_manual_group_id` 只在姓名匹配不到班组时参与判定。

API Key 读写使用随机 IV、AES/GCM/NoPadding 和固定 AAD。解密失败由 `ApiKeyDecryptFailure` 分类：GCM 标签不符、密钥失效/不可恢复（含 `KeyPermanentlyInvalidatedException`）、密文 Base64 损坏为永久失败，清除密文和对应 key；Keystore 服务暂不可用、Provider 或 I/O 错误为瞬时失败，保留密文、本次返回 null。任何情况下都不返回不可信明文。`hasVariFlightApiKey` 只检查密文是否存在，不解密、不访问 Keystore，因此瞬时故障不会让后台刷新被取消。

旧 gateway URL、supplement 和旧 gateway 凭据由 `LegacyMigrations.runOnce` 在 `MainActivity.onCreate` 一次性清理，并以 `migration_version = 1` 记录；`RosterStore` 的构造不再触发这段清理，其 API Key 存储按需惰性创建，小组件重绘、MUC 通知、后台 Worker 构造 `RosterStore` 时不会访问 Keystore。

### 11.2 Manifest 权限与组件

| 声明/特殊访问 | 目的 |
|---|---|
| `INTERNET` | 飞常准 HTTPS MCP |
| `ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` | Fused Location 机场匹配 |
| `POST_NOTIFICATIONS` | 保障通知 |
| `SCHEDULE_EXACT_ALARM` | 精确提醒特殊访问 |
| `RECEIVE_BOOT_COMPLETED` | 开机重排提醒和重绘小组件 |
| 通知监听特殊访问 | 系统授权 `MucNotificationListenerService` |

组件边界：

- `MainActivity` 导出，既是 launcher 入口，也是经过严格校验的 Excel `ACTION_SEND` 入口；
- `MucNotificationListenerService` 不导出并受 `BIND_NOTIFICATION_LISTENER_SERVICE` 保护；
- `ReminderReceiver`、`BootReceiver`、`DutyWidgetActionReceiver` 不导出；
- `DutyWidgetProvider` 为 launcher 绑定 AppWidget 而导出，只注册 `APPWIDGET_UPDATE`。

### 11.3 隐私

- Manifest 设置 `allowBackup=false`、`fullBackupContent=false`；data extraction rules 对云备份和设备迁移排除 SharedPreferences、database 和 file。
- 原图片/Excel 不持久化或上传；`.xls` 临时文件位于应用 cache 并在 `finally` 删除。
- 只保存匹配任务；匹配行的 `assignees` 原文本会随任务写入本地 JSON，可能包含同组姓名。无关行和 VIP 之外的附加栏目不保存。
- 飞常准请求只包含航班号、日期、JSON-RPC/MCP 字段和鉴权头；应用不记录 Key、完整请求头或原始错误载荷。
- 设备位置不写入持久化，也不由应用发送给飞常准；定位来源仍是 Google Play Services。
- MUC 原文不持久化，见第 9.5 节。
- 源码、测试与文档不含真实员工姓名：内置班组表只有组号与轮转顺序，测试 fixture 使用天干地支合成名；成员名单只存在于用户设备的校准数据中。

## 12. 工程、构建与验证

### 12.1 工程配置

- 单模块 `:app`；namespace/application ID 为 `com.bradj.airshift`。
- Java 17、Kotlin 2.4.10、Android Gradle Plugin 9.3.0、Gradle Wrapper 9.5.0。
- `compileSdk=37`、`targetSdk=37`、`minSdk=33`。
- Compose BOM 2026.08.00、Activity Compose 1.13.0、Lifecycle 2.10.0、WorkManager 2.11.2。
- Google Play Services Location 21.4.0、ONNX Runtime Android 1.21.1、OpenCV 4.12.0、Apache POI 5.5.1、Coroutines Android 1.9.0。
- Gradle Wrapper 使用华为云镜像并固定 SHA-256；configuration cache 开启，Gradle parallel 关闭。
- 守护进程 JVM 由 `gradle/gradle-daemon-jvm.properties` 固定为 JDK 21，缺失时经 `foojay-resolver-convention` 自动下载；本机 Android Studio 自带的 JBR 25 会让 detekt 1.23 内嵌的旧版 Kotlin 编译器崩溃，这样本机与 CI 一致。
- `local.properties` 可设置 `airshift.buildDir`，把 OneDrive 内的构建产物重定向到本地目录。
- release 未配置发布签名；`isMinifyEnabled=true`、`isShrinkResources=true`，keep 规则在 `app/proguard-rules.pro`（POI 反射构造 Record、ONNX Runtime 与 OpenCV 的 JNI）。
- Android Lint：`app/lint-baseline.xml` 记录既有 warning，`warningsAsErrors=true`，新增 warning 让 `lintDebug` 失败。
- detekt 1.23.8：默认规则集之上叠加 `app/detekt.yml`（对 `@Composable` 放开 FunctionNaming / LongMethod / LongParameterList / CyclomaticComplexMethod / NestedBlockDepth / MagicNumber / TooManyFunctions，`ui/theme` 排除出 MagicNumber），`app/detekt-baseline.xml` 记录既有发现，新增发现让 `detekt` 失败。
- 内置字体 `app/src/main/res/font/`：Barlow 与 Barlow Semi Condensed 六个静态 TTF（OFL 1.1，见 THIRD_PARTY_NOTICES.md）。
- GitHub Actions（`.github/workflows/ci.yml`）：push 到 `main` 与 PR 时在 ubuntu 上执行 `testDebugUnitTest lintDebug detekt`；真机测试仍手动。`gradlew` 在 git 里必须是 `100755`（Windows 提交默认丢失可执行位，0.10.x 的两次 CI 因 `Permission denied` 退出码 126 失败，已用 `git update-index --chmod=+x gradlew` 修正）。action 版本：`actions/checkout@v7`、`actions/setup-java@v6`、`android-actions/setup-android@v4`、`gradle/actions/setup-gradle@v5`、`actions/upload-artifact@v7`（均为 Node 24 运行时）；`gradle/actions@v6` 起缓存组件改为 Gradle 专有许可、升级即接受其 Terms of Use，因此有意停在 v5。

主要命令：

```powershell
.\gradlew.bat test lintDebug detekt assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Android 仪器测试需要 API 33+ 设备或模拟器。`XlsRosterParserRealFileTest` 只有配置 `AIRSHIFT_XLS_FIXTURES_DIR` 和 `AIRSHIFT_XLS_TEST_NAME` 时才运行真实外部 `.xls` fixture。

### 12.2 本轮验证

本轮（0.11.2）修正“休息时仍弹通知、不上班时仍在后台刷新”。用户报告：休息日收到排班里同号航班的“即将进港”提醒。代码审查得到的路径：`withLiveInfo` 原样接受飞常准返回的任何航段，若接口对过去日期的查询返回了别的日子的同号航班（休息日手动下拉走 `ALL_ROSTER` 时尤其可能），其预计时间会写进任务，`isDutyComplete` 因而重新变为未完成、`ReminderPolicy` 据此排出落在休息日的提醒、Worker 也被重新启用并逐日漂移；此外没有计划时间的航段一直按“今天”查询。修法见 §4.3（`FlightOperation` 12 小时归属、`RosterTracking` 跟踪时段）、§7.1 / §7.2 / §7.4 / §7.6 与 §8.1：合并只收同一班的航段，自动完成与提醒只信同一班的预计时间，所有自动刷新入口在排班日首个任务前 3 小时之前返回空集，Worker 首轮延迟直接睡到跟踪起点，无计划时间的航段按排班日查询。飞常准按日期查询的真实语义仍未离线验证，归属规则是本地兜底。先写纯函数测试再改实现：新增 `FlightOperationTest` 4 项、`RosterTrackingTest` 5 项、`ReminderPolicyTest` 4 项、`FlightInfoOperationGuardTest` 7 项、`FlightRefreshInitialDelayTest` 3 项，`DutyFlightWindowTest` 追加“起点前窗口为空 / 旧排班隔天不重开”2 项并把无计划时间航段的期望改为排班日，`RosterAssignmentCompletionTest` 追加“别的日子的预计时间不阻止完成”1 项，`DutyViewModelTest` 追加“头天晚上导入只保存并提示起点 / 起点前自动刷新无请求、手动下拉仍可全量”2 项。仪器测试同步改动：`DutyWindowRefreshInstrumentedTest` 的 `baseTime` 由 8 小时后改为 1 小时后、`RosterStoreInstrumentedTest` 的 upcoming 任务改为 2 小时后且“全量刷新救回自动完成项”改用相差 6 小时的预计时间、`FlightRefreshSchedulerInstrumentedTest` 默认任务改为 1 小时后并新增“明天的排班首轮延迟超过 1 小时”1 项。在 JDK 21 守护进程下执行 `:app:testDebugUnitTest :app:detekt :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug`：JVM 282 项通过、0 项失败、2 项条件跳过；detekt 首轮拦下测试辅助函数的 LongParameterList、`initialDelayMinutes` 的 ReturnCount 与两个 `Duration.ofHours` 的 MagicNumber，分别改为按航段拆分的辅助函数、单表达式与命名常量后 0 新发现；Lint 基线外零新增（仍提示基线中 22 条记录已不存在）；仪器测试编译通过；Debug APK 生成。随后在用户确认手机空闲后接入真机（vivo V2505A，Android 16 / API 36，前台为桌面）执行标准单批次 `connectedDebugAndroidTest`：覆盖安装 0.11.2（version code 50），61 项执行、0 失败、5 项按 `assumeFalse` 跳过（`FlightRefreshSchedulerInstrumentedTest` 全部 5 项，含新增的首轮延迟用例，因手机上配置了真实 API Key），用时 1 分 8 秒；改过时间假设的 duty-window 9 项、数据层 19 项与 Compose 用例全部通过，主应用与本地数据保留。

随后在用户授权花费查询额度后，用手机上已存的 Key 做了一次真实查询探针（新增可选用例 `VariFlightLiveProbeInstrumentedTest`，必须以 `airshift.liveVariFlight=true` 显式开启，明文 Key 只在进程内解密、不写日志）。手机上留存的 09-04 夜班排班本身就是故障现场：`MU2418` 计划 09-05 01:00 到达，存储里的预计到达却是 09-06 00:31，`last_live_refresh` 停在 09-05 13:42（交接班日仍在刷新）。探针结果：`MU2418@2026-09-04` 返回 PKX→LHW 计划 09-04 22:40 → 09-05 01:00、实际 09-05 00:40 到达、机位 342，即排班里的这一班；`MU2418@2026-09-05` 返回 09-05 22:40 出发、09-06 01:00 到达的下一班——App 原先按到达日 09-05 查询，拿到的正是它；`FM9211@2026-09-04`（两天前）返回正确的历史班次与实际时间。结论：`date` 是出发日，过去日期查询可靠，漂移来自跨零点到达航班的查询日期。据此把进港 lookup 改为按运行日（06:00 前到达算前一天）查询，`FlightInfoOperationGuardTest` 追加 2 项、`DutyFlightWindowTest` 追加 1 项锁定；三个仪器测试类的 lookup 日期改用 `inboundLookupDate`，避免深夜运行时日期错位。同时移除 `FlightRefreshSchedulerInstrumentedTest` 的“已配置 Key 则跳过”假设：其 tearDown 不再调用会删除 Keystore 别名的 `clearVariFlightApiKey()`，只删带前缀的隔离文件，因此在配置了真实 Key 的手机上也能安全运行；首轮延迟用例的上限断言改为与 `initialDelayMinutes` 的计算值相差不超过 2 分钟（凌晨运行时“明天 12:00”距今超过 24 小时，原上限过紧）。改动后再次执行 `:app:testDebugUnitTest :app:detekt :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug`：JVM 285 项通过、0 失败、2 项条件跳过，detekt 与 Lint 零新增；真机整批 `connectedDebugAndroidTest`：62 项执行、0 失败、1 项跳过（未开启的探针），用时 1 分 4 秒，`FlightRefreshSchedulerInstrumentedTest` 5 项在配置了真实 Key 的手机上首次实际运行并通过，运行后 Key 密文仍在。AlarmManager 实际触发仍未验证。

上一轮（0.11.1）只改动效，设计、文案与业务不变。起因：真机上切换分区与展开信息条"迟钝、不干脆"。诊断：分区切换 fade-through 有 90 ms 空档并叠 96% 缩放（合计 300 ms）；展开用 Material standard 曲线（起步慢）250 ms；条在栏位间移动 400 ms 同曲线；展开内容瞬间替换、无淡入。改法见 §6.1 / §6.2 / §6.8 与 DESIGN.md 动效节。验证：JDK 21 守护进程下 `:app:testDebugUnitTest :app:detekt :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug`：JVM 254 项通过、2 项条件跳过；detekt 0 发现；Lint 基线外零新增；APK 生成并 `adb install -r` 到 vivo V2505A（0.11.1 / 49）。四页静止态截图核对：红灯落在选中标签上方、夹条三色正确、信息条外观与 0.11.0 一致。切页帧统计（`dumpsys gfxinfo`，暖机后底栏 8 次点击）：0.11.0 卡顿帧 3.2%、50/90/95/99 分位 8/18/22/57 ms；0.11.1 两轮为 2.3% 与 2.0%，分位 7/9/24/105 与 8/9/19/101 ms。framestats 拆解显示最慢帧（57 ms）是新页第一次组合（重组约 16 ms + 测量布局绘制约 33 ms），与旧版同量级，是 Debug 构建下整页组合的固有成本；99 分位升高来自紧随其后被推迟起步的一帧，不是动画本身变慢。系统动画时长比例为 1.0。随后在手机空闲时执行标准单批次 `connectedDebugAndroidTest`：60 项、0 失败、4 项按 `assumeFalse` 跳过（真实 API Key），含点条展开的 `AllDutyScreenBaysInstrumentedTest`、按 `nav_*` 切页的 `DutyWindowRefreshInstrumentedTest` 9 项与钉底按钮的 `CurrentDutyScreenIntegrationInstrumentedTest`，1 分 25 秒完成，主应用与数据保留。动效手感由用户在真机上判断。教训：向手机注入点击前先确认 `topResumedActivity` 是本应用，否则点击会落到用户正在用的其他应用上。

上一轮（0.11.0）是整套界面的重设计（方向"航显板 × 进程单"，见第 6 节）。流程：先用 Claude Design 画布出 10 块高保真画板（当前执勤浅/深/过点、全部执勤、排班日历、设置、首次进入、小组件、完成动效分镜、设计系统一览）经用户确认，再按 B0–B10 分任务实现。新增 Barlow 字体、`AirShiftPalette` 双主题 token、`BoardHeader` / `DutyStrip` / `StatusLamp` / `OdometerText` / `PinnedActionBar` 等组件，四页与 Onboarding 全部重写，底栏改为四等分 + 红灯指示，页面切换 fade-through，`enableEdgeToEdge` 显式指定状态栏图标恒为浅色并修正深色模式下的图标颜色，小组件改为藏青板面并删除全部装饰 drawable。先写纯函数测试再写实现：`DutyBaysTest` 3 项（人工前缀 / 自动完成 / 无时间任务落入已完成栏位、全部完成、空排班）、`OdometerSlotsTest` 2 项、`AssignmentLegsTest` 追加 `liveKind` 1 项；androidTest 新增 `FontFeaturesInstrumentedTest`（tnum 等宽断言）2 项与 `AllDutyScreenBaysInstrumentedTest` 1 项；`CurrentDutyScreenIntegrationInstrumentedTest` 的完成辅助函数改为直接点击钉底按钮（按钮已不在滚动区内）。detekt 首轮拦下主题 token 的 MagicNumber、单文件多声明命名、多 return、超长行与 Composable 复杂度，分别用 `detekt.yml` 排除 `ui/theme`、拆文件（`LampKind.kt`、`OdometerSlot.kt`、`LegPresentation.kt`）、改 `when` 与折行归零；Lint 拦下 RemoteViews 不允许裸 `View`，行线改为空 `FrameLayout`。在 JDK 21 守护进程下执行 `:app:testDebugUnitTest :app:detekt :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug`：JVM 254 项通过、0 项失败、2 项条件跳过；detekt 0 新发现；Lint 基线外零新增（提示基线中 22 条记录已不存在，对应删除的旧小组件装饰与卡片文案）；Debug APK 生成。

随后接入真机（vivo V2505A，Android 16 / API 36，1440×3168 @640dpi = 360×792dp）：`adb install -r` 覆盖安装 0.11.0（version code 48），排班、校准、MUC 记录与 API 密钥保留。四页 + 深色主题 + 字体缩放 1.3 + 桌面小组件逐张截图核对，发现并修正三轮问题：(1) 360dp 屏上折叠条的航线列被挤成省略号、带 VIP 的行状态灯被裁掉 → 列宽压到 16/46/58 并按 sp 折算，机位改为定位钉 + 数字，VIP/特服灯移到单独的条头行；日历行说明缩短、到位/到场与富余分两行；设置"到位余量"改为标签 + 分段选择器 + 说明。(2) 展开态的已完成任务仍显示"未起飞" → 把 completed 传入状态灯；缺三字码的机场只显示名称。(3) 独立 finish review（disposition: fix）的 7 条：字体 ≥1.15 倍时折叠行改两行；全部执勤板脚回到一行、长状态说明改放中性通知条；小组件空态补板头与板脚；当前执勤全部完成时板面显示"下一班"（`NextShift`）；设置余量行去重；补 PRODUCT.md；概念掷骰脚本因计划模式限制未运行、无 `.impeccable` 状态文件，方向由用户在四个候选中经结构化问题选定，如实记录。仪器测试首轮 8 项失败：6 项是 `DutyWindowRefreshInstrumentedTest` 在 fade-through 过渡期内找到两个可滚动节点、且板头标题与底栏标签同名导致 `onNodeWithText` 命中两个节点 → 测试改为按 `nav_*` testTag 点底栏并推进 600 ms 时钟；2 项是 `FontFeaturesInstrumentedTest` 断言过严 → 设备诊断确认 `tnum` 生效（"1"由 22px 变 33px），但 Barlow Semi Condensed 的 tabular 字形仍有约 7% 宽差，断言改为比例 >0.9，翻牌数字改用固定位宽槽位。最终 `connectedDebugAndroidTest` 标准单批次：60 项执行、0 失败、4 项按 `assumeFalse` 跳过（手机上配置了真实 API Key）。RemoteViews 对 `@font/barlow_*` 的解析在 OriginOS 桌面上已确认（小组件板头数字为 Barlow）。测试 APK 按 `leaveApksInstalledAfterRun` 保留，主应用与本地数据未受影响。

上一轮（0.10.6）让 MUC 解析兼容轮椅口语简称 C轮/R轮/S轮，并把任务卡上的轮椅记录改为轮椅线性图标 + 等级字母。先在 `SpecialServiceParserTest` 增加简称用例（小写 `r轮` 经规范化识别、字母与“轮”之间一个空格、`旅客S 轮` 按单人计数、`WCHR改为S轮` 的代码与简称混用按更正处理、座位 `32C轮椅` 不误判为 C 轮、`WCHS轮椅` 不重复识别出 S 轮），新增 `SpecialServiceLabelsTest` 锁定角标与详情行文案只给单字母（等级缺失时只剩数量或空串），再改实现。`:app:testDebugUnitTest`（`--rerun`）：JVM 248 项通过、0 项失败、2 项条件跳过。detekt 首次运行拦下 2 条基线外发现：新增私有 Composable `WheelchairGlyph` 触发 FunctionNaming（默认规则集未豁免 `@Composable`，既有 Composable 都在基线里），以及图标路径字符串超过行宽；分别改为在两处内联 `Icon` 与拆分字符串拼接后归零。随后 `:app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug` 全部成功，Lint 基线外零新增（仍提示基线中 2 条记录未在项目中找到）。图标形状用同一路径在浏览器里按 192/48/24/14/12px 渲染核对。真机（vivo V2505A）以 `adb install -r` 覆盖安装 0.10.6（version code 47），`dumpsys package` 确认版本，拉起应用无崩溃；排班、校准、MUC 记录与 API 密钥保留。未复跑仪器测试。

上一轮（0.10.5）把到位提前量改为进港实时到达前 15 分钟、纯出港实时起飞前 70 分钟，并把提前量收敛为 `DutyTimeline` 的两个公开常量，`ReminderPolicy` 与 `ShiftBusPlan` 直接复用，因此系统提醒、当前执勤页倒计时、小组件倒计时与排班日历班车推荐同步变化。先按新规则改写 `DutyTimelineTest`、`ProjectSmokeTest`、`DutyWidgetModelTest`、`ShiftBusPlanTest`、`ShiftCalendarRowsTest` 的期望值（`ShiftCalendarRowsTest` 的“余量不改变班车”用例改用组 8 在 09-06 的早三，因原用例的早二在 30 分钟余量下已需提前一班），再改实现。在 JDK 21 守护进程下执行 `:app:testDebugUnitTest`：JVM 244 项通过、0 项失败、2 项条件跳过；随后 `:app:detekt :app:lintDebug :app:compileDebugAndroidTestKotlin :app:assembleDebug` 全部成功，detekt 0 项、Lint 基线外零新增（Lint 另提示基线中有 2 条记录未在项目中找到，未查证来源）。班车推荐因到位提前而变化的推算场景：默认 15 分钟余量下，整班工作日的早一、晚三、晚四与交接班日的早二由 05:55 改为 05:25，其余槽位不变。真机（vivo V2505A，Android 16 / API 36）：以 `adb install -r` 覆盖安装 0.10.5（version code 46），排班、校准、MUC 记录与 API 密钥保留。先用 `notClass` 排除 4 个 Compose 测试类跑 `connectedDebugAndroidTest`：40 项执行、0 失败、4 项跳过（`FlightRefreshSchedulerInstrumentedTest` 因手机上配置了真实 API Key 按 `assumeFalse` 跳过），小组件渲染/布局 3 项通过。随后单独跑 Compose 的 16 项（仍排除此前记录会持续滚动的 `importingANewNonEmptyRoster…`）：首次尝试时宿主 Activity 始终没有拉起，等待 14 分钟仍是 0/16，手动 `am force-stop` 终止；在手机设置里为 AirShift 放开“后台弹出界面”后重跑，`DutyWindowRefreshInstrumentedTest` 9 项、`ForegroundFlightRefreshEffectInstrumentedTest` 5 项、`SharedExcelImportOwnerInstrumentedTest` 1 项与当前执勤页 `manualCompletionAndAutomaticCompletion…` 1 项全部通过（16/16，57 秒）。这说明此前几轮 Compose 用例无结果的原因是该权限，而非用例本身。重跑期间有一次因 vivo 安装确认被拒（`INSTALL_FAILED_ABORTED`）而未执行任何用例，允许安装后再跑即通过。测试 APK 已卸载，主应用与本地数据保留。AlarmManager 实际触发与通知展示未复跑。

上一轮（0.10.4）把任务卡与小组件统一为只显示机位，MUC 登机口变更改为单独一行提示。`AssignmentLegsTest` 先按新规则改写并观察到 3 项失败，再改实现转绿；JVM 244 项通过、2 项条件跳过。detekt 与 Lint 各拦下一条新问题（`bindLeg` 参数过多因改名而脱离基线、小组件布局的硬编码 contentDescription），分别收成视图 id 组与字符串资源后归零。真机（vivo V2505A）跑小组件渲染/布局 3 项与当前执勤页 1 项通过；当前执勤页第 2 项 `importingANewNonEmptyRoster…` 在 `performScrollToNode` 处持续滚动直至 10 分钟超时，属本节此前记录的 vivo 滚动问题，与本次改动无关，未再复跑。教训：中断的 `connectedDebugAndroidTest` 走清理流程时把主应用连同本地数据一起卸载了（manifest 关闭备份，无法恢复），排班、校准、MUC 记录与 API 密钥需重新录入。为此 `gradle.properties` 加了 `android.injected.androidTest.leaveApksInstalledAfterRun=true`，此后真机测试不再卸载已安装的 APK。

上一轮（0.10.3）是审查计划的收尾：MUC 归并层去重与 JSON 辅助函数收敛，属纯重构，现有 JVM 用例即回归锁。执行 `:app:testDebugUnitTest :app:detekt :app:lintDebug :app:compileDebugAndroidTestKotlin`：JVM 243 项通过、2 项条件跳过；detekt 首次运行在新代码上报出 3 条基线外发现（LongParameterList、NestedBlockDepth、ReturnCount），按规则改写为 `FacilityReducer` + `fold` 与单一 `when` 表达式后归零，说明门禁对新代码有效；Lint 基线外零新增；Android 测试源码编译通过，未在设备上重跑。

上一轮（0.10.2）是代码审查后的第四阶段（工程化）。守护进程首次自动下载 JDK 21 后执行 `:app:detekt :app:lintDebug :app:testDebugUnitTest :app:assembleRelease`：detekt 对既有代码记录 466 条基线（MagicNumber 190、MaxLineLength 72、FunctionNaming 50、ReturnCount 47、LongMethod 26、LongParameterList 22、CyclomaticComplexMethod 14 等），基线之外零新增；Lint 基线 30 条，零新增；JVM 243 项通过、2 项条件跳过；`minifyReleaseWithR8` 在 `proguard-rules.pro` 的 keep 规则下一次通过，未产生 missing rules。Debug APK 251.9 MB，R8 后的未签名 release APK 216.9 MB：体积主要来自 OpenCV/ONNX Runtime 的多 ABI 原生库与 6 MB 模型，R8 只能压缩 Java/Kotlin 代码。真机（vivo V2505A，打开“后台弹出界面”权限后）：17 项 Compose 用例经标准 `connectedDebugAndroidTest` 全部通过，加上此前 44 项非 Compose 用例，本轮 Android 用例 57 项执行、0 失败、4 项按 `assumeFalse` 跳过。git 历史已用 `git filter-repo --replace-text` 改写，30 个真实姓名在全部历史中残留为 0，当前树哈希不变。

上一轮（0.10.0–0.10.1）是代码审查后的第三阶段（结构），连接 vivo `V2505A`（Android 16 / API 36）。在 JDK 17 下执行 `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`：JVM 报告共 243 项，241 项通过、0 项失败、2 项条件跳过；新增 `DutyViewModelTest` 12 项与 `AssignmentLegsTest` 6 项（从真机 duty-window / owner 场景移植，含"清理后的 ViewModel 不落库"）。真机：非 Compose 的 44 项（数据层、迁移、调度、小组件、分享 Intent、OCR）通过，其中 `FlightRefreshSchedulerInstrumentedTest` 4 项因手机上的正式应用已配置真实 API Key 而按 `assumeFalse` 主动跳过；17 项 Compose 用例在该机仍被阻止从后台拉起宿主，改用"预启动宿主 + `--no-restart`"后又因预启动进程已初始化 kotlinx-coroutines、测试 APK 的 `ExceptionCollector` 无法经 ServiceLoader 注册而全部报错，因此本轮 Compose 用例没有得到结果，需在放开"后台弹出界面"权限后重跑。Lint 0 error、29 warning（新增 1 条为 `kotlinx-coroutines-test` 依赖的版本提示）。

上一轮（0.9.2）是代码审查后的第二阶段（性能与健壮性），没有连接设备。在 JDK 17 下执行 `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`：JVM 报告共 225 项，223 项通过、0 项失败、2 项条件跳过；新增的 `aSlowLoadForOneFlightDoesNotBlockAnotherFlightInTheSameHashBin` 先在旧实现上以 `TimeoutException` 失败，改为 future 合并后转绿；Lint 保持 0 error。Android 用例只完成编译。

上一轮（0.9.1）是代码审查后的第一阶段修复，没有连接设备。在 JDK 17 下执行 `.\gradlew.bat :app:testDebugUnitTest --rerun`：JVM 报告共 224 项，222 项通过、0 项失败、2 项因未配置真实 `.xls` fixture 而跳过；新增的 `DutyProgressDayTest`、`ApiKeyDecryptFailureTest` 与改写后的 `ShiftGroupTableTest` 均先观察到失败再转绿。`connectedDebugAndroidTest` 未执行：新增或修改的 5 个 Android 用例（执勤日跨零点、更早执勤日的进度不复用、遗留键一次性清理、已完成迁移不重跑、构造不触碰遗留键）只完成了编译，需在下次接入设备时补跑。

上一轮（0.9.0）新增排班日历，并在 JDK 17 下执行：

```powershell
.\gradlew.bat test lintDebug assembleDebug --console=plain
```

2026-09-04 的验证结果：Gradle 构建成功；JVM 报告共 212 项，210 项通过、0 项失败、2 项因未配置真实外部 `.xls` fixture 而跳过；Lint 为 0 error、28 warning（与新增功能前的基线一致，新增文件零发现）；Debug APK 成功生成。

排班算法另有两项端到端判据：

- `ShiftRotationTest` 把六份实测排班表的班次行原样固定为回归锁，纯计算出的班组顺序必须逐位一致；
- 配置 `AIRSHIFT_XLS_FIXTURES_DIR` 与 `AIRSHIFT_XLS_TEST_NAME` 后，`XlsRosterParserRealFileTest.theBuiltInRotationMatchesEveryRealShiftLine` 直接读真实 `.xls`，校验解析出的班次行与 `ShiftSchedule` 一致、交接班日不含班次行，并确认以任一份表格自校正后其余日期顺序不变。本轮已用真实文件实跑通过（212 项全部执行、0 跳过）。

真机验证使用 vivo `V2505A`、Android 16 / API 36：覆盖安装确认版本 `0.9.0 (38)`，四页底栏渲染正常，排班日历对班组 8 给出 08-29 晚一、08-30 中二、08-31 早二、09-04 晚二、09-05 中三、09-06 早三、09-07 交接班（早三、到岗），与实测表格一致；到位余量改动经强停后仍保留。`RosterStorePersistenceInstrumentedTest` 的 8 项（含班组校准 JSON 往返、余量收敛、损坏 JSON 回退、手动班组）在该机单批次通过。

需要注意：在该机上执行 `pm uninstall com.bradj.airshift.test` 会连带移除主应用及其数据，清理测试 APK 时应改用其他方式。

真机验证使用 vivo `V2505A`、Android 16 / API 36：Debug APK 覆盖安装成功，设备包管理器确认版本 `0.8.1 (37)`，`MainActivity` 冷启动成功。`connectedDebugAndroidTest` 能发现 48 项测试，但该设备会阻止 AndroidJUnitRunner 从后台拉起 Compose ActivityScenario，整批运行停在 `0/48`。首次按类/方法拆分时，31 项非 Compose 测试和 12 项 Compose 测试通过，共 43/48；其余 5 项没有得到可信的业务断言结果。

复测发现这 5 项均卡在测试辅助函数对已显示的“执勤完成”按钮执行 `performScrollToNode`，vivo 上产生持续滚动而没有进入后续断言；改为直接触发目标节点的 Compose 点击语义后，5 项逐项全部通过，完整 `DutyWindowRefreshInstrumentedTest` 也连续通过 9/9（19.596 秒）。因此 48 个 Android 测试场景均已在该机获得通过结果；但这不是标准 `connectedDebugAndroidTest` 单批次通过，仍使用了预启动 Compose 宿主、`--no-restart` 和诊断期临时 test exception collector 来绕过厂商系统的 Activity 启动与分离 APK `ServiceLoader` 限制。

临时 test exception collector 已撤回，仅保留不涉及生产逻辑的测试动作修正。最终再次执行 `test lintDebug assembleDebug assembleDebugAndroidTest`，重新安装正常 APK 并卸载测试 APK。`git diff --check` 已通过（只有 Git 的 LF→CRLF 工作区行尾提示，没有空白错误）。真实飞常准、WPS、MUC、定位、闹钟、通知、锁屏和 launcher 行为仍不能由本次自动化替代。

### 12.3 当前测试矩阵

| 范围 | `@Test` | 主要覆盖 |
|---|---:|---|
| JVM `api` | 57 | 两项窗口（含跟踪起点前为空、旧排班隔天不重开、无计划时间按排班日、跨零点到达按出发日）、batch、字段/多经停映射、同一班归属过滤与 lookup 日期 9 项、Worker 首轮延迟 3 项、JSON-RPC/SSE、脱敏错误、缓存/限流/并发（含同桶航班不互相阻塞） |
| JVM `model` | 34 | 时间线、自动完成（含别的日子的预计时间不阻止完成）、人工前缀和窗口、执勤日 06:00 边界、同一班归属 4 项、排班日跟踪时段 5 项 |
| JVM `data` | 5 | API Key 解密失败的永久/瞬时分类 |
| JVM `duty` | 14 | 编排层：两项窗口自动/手动刷新、完成后补查、忙碌时排队、全部完成停止、导入后首刷、提前导入只保存并提示起点、起点前自动刷新无请求、旧 generation 忽略、清理后不落库、设置保存 |
| JVM `reminder` | 4 | 提醒只信同一班的预计时间，别的日子的预计退回计划时间 |
| JVM `parser` | 12 | XLSX/XLS、模板变体、姓名隔离、班次行解析；含 2 个条件式真实 fixture |
| JVM `model/shift` | 85 | 周期与日型、轮转回归锁、槽位与交接班到岗、班车与余量、班组表合并（内置表无成员、合成姓名基表）、日历行装配 |
| JVM `specialservice` | 29 | MUC 解析、匹配、顺序、取消、去重、过期和 JSON 兼容 |
| JVM `ui/components` | 17 | 航段模型（进出港顺序与本站机场、SUMMARY 只打角标、FULL 展开原值 → 新值/登机时刻/特服、取消归属、机号机型落在末段、日期不符不采用、`liveKind` 预计/实际）、特服角标文案 6 项、栏位分栏 3 项、翻牌槽位 2 项 |
| JVM `widget` | 11 | 当前页选择、空/完成/倒计时、VIP、机场和机位 |
| JVM `ui` | 8 | 默认页、前后台恢复、配置变化和排班日历页选中 |
| JVM smoke | 9 | OCR 表格、姓名、VIP、提醒基础 |
| Android 数据层 | 19 | generation、进度、执勤日跨零点、scope 合并、旧 JSON、扩展机位、班组校准 JSON 往返与余量收敛 |
| Android 迁移 | 3 | 遗留键一次性清理、已完成迁移不重跑、构造 `RosterStore` 不触碰遗留键 |
| Android 刷新编排 | 14 | duty-window 9 项、foreground effect 5 项 |
| Android WorkManager | 5 | KEEP、generation、停止、旧任务迁移和明天排班的首轮延迟；不再因已配置 Key 而跳过 |
| Android 飞常准探针 | 1 | 可选的付费真实查询，默认跳过，把返回航段写入 logcat |
| Android Excel 分享 | 11 | Manifest/Intent/FIFO/恢复 10 项、owner 隔离 1 项 |
| Android 当前页 Compose | 2 | 点击完成、自动跳过和新排班恢复 |
| Android 全部执勤页 Compose | 1 | 三个栏位与点条展开 |
| Android 字体 | 2 | Barlow / Barlow Semi Condensed 开启 tnum 后"1"向"0"的宽度靠拢（比例 >0.9） |
| Android 小组件 | 3 | 单卡布局与 RemoteViews 渲染 |
| Android OCR | 1 | PP-OCRv6 合成图片端到端 |

`testdata/synthetic_roster.png` 是无真实个人信息的自动 OCR fixture，可由 `tools/generate_synthetic_roster.ps1` 重建。`testdata/mu2415_verify.png` 当前没有被自动测试引用。

### 12.4 仍缺的验证

- `VariFlightLiveProbeInstrumentedTest` 是付费的真实查询探针，默认跳过，只在显式开启时运行；06:00 之后才出发、当天凌晨前到达的红眼航班（出发与到达同为凌晨）仍会被按前一天查询，归属规则会拒绝其航段而没有实时数据，尚无真实样本；
- 标准 `connectedDebugAndroidTest` 单批次仍需在 AOSP 模拟器或无厂商后台启动限制的设备上复跑；vivo 当前只能通过预启动宿主的拆分方式完成全部场景；
- Onboarding、全部执勤、设置页的完整 Compose 交互和无障碍；
- 真实 `MainActivity` ActivityScenario 生命周期、进程强杀和多窗口；
- `FlightRefreshWorker.doWork()` 的真实网络、系统重试/backoff 与系统延迟；
- AlarmManager 实际触发、BootReceiver、通知权限、锁屏展示和时区/改时；
- Android Keystore API Key 真机往返和密钥失效；
- AirportLocator 自动化与真实 Fused Location；
- NotificationListenerService、真实 MUC 样式和企业设备策略；
- 真实 WPS MIME/ClipData/ContentProvider、URI 授权和强杀恢复；
- Excel 安全上限、损坏/加密文件、部分 1904/公式/多表边界的直接回归；
- OCR 零表头警告、短姓名前缀碰撞、超大图片；
- Widget 完成 receiver、launcher 跨零点、OriginOS 裁剪和不同桌面实现；
- 排班日历页与设置页的 Compose 交互与无障碍（当前只有真机人工核对与截图，没有 Compose 测试）；
- 当前执勤页带倒计时的板面与"执勤完成"动效只在仪器测试中驱动过，真机上排班已全部完成，未截到实时倒计时画面；
- 条展开 / 折叠与移栏的动效只有静止态截图、帧统计与仪器测试的终态断言，没有逐帧画面；
- 导入带班次行的 Excel 后自校正的真机端到端（JVM 已用真实文件覆盖解析与相位，真机只验证了存储往返）；
- 跨越 2026 年末的日期、设备改时区/改时对周期判定的影响；
- 班组人员真实调整、新增第 7/12 组后 3/4/3 变为 4/4/4 的实际表格。

## 13. 已知实现限制与风险

1. **导入替换**：成功但零匹配会清空旧排班并重置进度，没有预览、确认或回滚。
2. **OCR 姓名包含匹配**：短姓名可能命中更长姓名；Excel 的分隔符感知规则更严格。
3. **图片无上限**：图片解码没有文件、像素或内存限制，超大输入可能造成内存压力。
4. **分享恢复**：没有永久 URI 权限或队列长度上限，不保证强停后的 exactly-once。
5. **XLS 静默截断**：行号 10,000 以后和列号 255 以后忽略，可能产生不完整而非明确失败的结果。
6. **排班 JSON 整体失败**：任一损坏条目可使整份排班加载为空；MUC Codec 才是逐项容错。
7. **自动完成启发式**：陈旧预计时间或超长延误可能在 3 小时后过早完成；无时间航段直接完成。与计划相差超过 12 小时的预计时间被当成别的日子的同号航班而不采信，因此超过 12 小时的超长延误会按计划时间完成、也不会再收到提醒。
8. **人工完成不可撤销**：没有确认/回退，并且不会取消该任务未来提醒。
9. **过站到位语义**：尚未整体完成的过站始终按进港到位时间显示，可能在等待出港期间长期显示过点。
10. **后台非精确**：WorkManager 15 分钟不是准点保证，系统可延后。
11. **载荷格式脆弱**：飞常准内层类 Python 字典依赖受限文本解析，上游格式或转义变化可能失败。
12. **同代响应排序**：同一 generation 没有服务端更新时间冲突解决，相同字段最后写入者生效。
13. **限流语义**：缓存命中和跳过上游的调用也消耗本地每分钟容量。
14. **MUC 歧义**：数字简写没有最大日期距离，跨承运人同数字会确定性自动匹配而非人工确认。
15. **MUC 低置信丢弃**：没有人工确认入口；弱表达可能被直接忽略。
16. **MUC 逻辑过期**：物理删除依赖下一次通知或排班变化，没有后台清理器。
17. **小组件数据边界**：不显示 MUC 状态；Chronometer 跨零点依赖 launcher 和后续重绘。
18. **未消费字段**：`arrivalBridge` 已解析/持久化，但 UI 未展示。
19. **提醒 ID**：`stableId.hashCode()` 是 32 位，理论上存在碰撞。
20. **发布状态**：中文硬编码、无发布签名；release 虽已开启 R8，但只验证到 `assembleRelease` 成功，压缩后的 OCR/XLS 导入未在真机跑过，Debug APK 仍是唯一可安装产物。
21. **排班规律为外推**：六天周期、轮转步长 3、槽位分层和交接班到岗规则由六份实测排班表反推，样本内零反例但属外推；实际排班调整后需靠导入 Excel 的班次行自校正，应用不会主动发现漂移。
22. **日历班车多为预估**：只有与当前已导入排班同一天的那一行使用真实航班时间；其余行按实测的典型首个任务推算，个别日期观测到首任务时间离群（如 08-24 晚二 10:40、08-30 晚一 08:25）。
23. **单份排班的限制**：App 只保存一份当前排班，因此日历无法为多个日期同时提供真实数据。
24. **内置班组表无成员**：校准前无法按姓名自动识别班组，只能手动指定；这是为了不让真实姓名进入公开仓库而有意为之。
25. **执勤日固定 06:00 切换**：`DutyProgressDay.ROLLOVER_HOUR` 不可配置；延误到 06:00 之后才手动完成的夜班任务会在切换后重新显示，直到 3 小时自动完成生效。
26. **APK 体积**：Debug 约 252 MB、R8 后的 release 约 217 MB，主要是 OpenCV 与 ONNX Runtime 的多 ABI 原生库；要明显缩小需要 ABI split 或 App Bundle，不在当前范围内。
27. **排班日之外不自动跟踪**：跟踪时段以排班自身日期为准，不看排班日历。排班没有识别出日期时按导入当天处理（§5.2 的“暂按今天处理”警告），若在头天晚上导入，跟踪与提醒都会落在错误的一天，需要重新导入带日期的表。已存的旧数据若含别的日子的预计时间，升级后不再影响完成判定与提醒，但任务详情仍会显示它，直到下一次导入。
28. **凌晨到达按前一天查询**：飞常准按出发日查询，App 把 06:00 前到达的进港航班当作前一天晚上出发。凌晨出发、凌晨到达的红眼航班会被查到前一天的班次并被归属规则拒绝，只能靠排班计划时间，没有实时数据；没有按“查不到就换另一天”的重试。

## 14. 当前验收标准

### 14.1 导入与存储

- 有效图片/XLS/XLSX 能生成只属于配置姓名的合法航班任务；无关行不进入结果。
- 文件格式由签名决定；非法、加密或损坏工作簿产生可理解错误且不替换旧排班。
- 成功空结果按当前产品语义替换旧排班，并显示“无匹配姓名”警告。
- `CES`、符号航班号、日期/序列时间和 `+` 次日按本规格规范化。
- 新排班重置进度并增加 generation；实时刷新不重置进度。

### 14.2 刷新与进度

- 导入、前台、后台和未完成时手动刷新只查询当前 + 下一项未完成执勤。
- 排班日首个任务前 3 小时之前，所有自动入口（导入后首刷、前台自动、后台周期、完成补查）不发起查询；显式手动下拉不受限。
- 与计划时间相差超过 12 小时的实时航段不合并、不参与完成判定与提醒；无计划时间的航段按排班日查询。
- 进港航段按运行日查询：06:00 前到达的航班查前一天（飞常准 `date` 为出发日）；出港航段按计划出发日。
- 两项窗口按航班号+日期去重，每个请求前复查最新资格。
- 人工完成只能推进调用者所见当前任务，并只补查新进入窗口的航班。
- 全部完成后自动刷新停止；全排班手动刷新继续可用且不重置人工进度。
- 旧 generation 响应、Worker 或 Widget 卡片不能覆盖/推进新排班。
- 部分 API 失败保留成功结果和已有数据，错误不泄露敏感载荷。

### 14.3 提醒、MUC、定位与 Widget

- 进港/过站只建到达前 15 分钟提醒，纯出港只建出发前 1 小时 10 分钟提醒。
- 提醒只落在排班日：时间只来自任务的计划时间与同一班（相差 ≤ 12 小时）的预计时间。
- 无权限时功能按第 2.2 节降级，不阻断排班查看。
- 只有 MUC 白名单包的新通知可进入解析；持久化 JSON 不含原文和个人敏感字段。
- 更晚变更/取消按时序生效，旧摘要不能恢复已取消状态。
- 机场只在当前刷新候选中、15 km 内匹配，设备位置不持久化。
- Widget 固定选择当前未完成执勤；旧按钮重复点击不能完成下一项。

### 14.4 排班日历

- 六份实测排班表的班次行必须能被纯计算逐位复现，交接班日不产生班次行。
- 交接班日只有前一个整班工作日排到早班/中班的组到岗，晚班组标记为不到岗且不给班车。
- 任何日型、槽位与可选余量组合下，推荐班车的到场时间都不晚于到位时间。
- 与当前排班同一天的行使用真实航班时间并标注来源，其余行标注预估。
- 解析不到班次行、姓名匹配不到班组或校准 JSON 损坏时，一律回退内置班组表，不影响其他页面。

## 15. 源码追踪索引

| 规格领域 | 当前 `main` 证据 |
|---|---|
| Composition root | `app/src/main/java/com/bradj/airshift/MainActivity.kt` |
| 编排层与端口 | `duty/DutyViewModel.kt`、`duty/DutyUiState.kt`、`duty/DutyPorts.kt`、`data/RosterRepository.kt`、`api/LiveFlightRefresher.kt` |
| 四页装配与 fade-through | `ui/AirShiftApp.kt` |
| 信息条与航段模型 | `ui/components/DutyStrip.kt`、`ui/components/AssignmentLegs.kt`、`ui/components/LegPresentation.kt`、`ui/components/DutyBays.kt` |
| 板面、状态灯、翻牌、钉底操作 | `ui/components/BoardHeader.kt`、`StatusLamp.kt`、`LampKind.kt`、`DirectionHolder.kt`、`Bay.kt`、`OdometerText.kt`、`OdometerSlot.kt`、`PinnedActionBar.kt`、`EmptyBay.kt`、`NoticeStrip.kt`、`BoardFormats.kt`、`DesignComponents.kt`（线性图标） |
| Design token 与字体 | `ui/theme/AirShiftTheme.kt`、`ui/theme/AirShiftFonts.kt`、`ui/theme/AirShiftMotion.kt`、`res/font/`、`res/values/themes.xml`、`res/values-night/themes.xml` |
| 前台刷新 effect | `app/src/main/java/com/bradj/airshift/ForegroundFlightRefreshEffect.kt` |
| WPS 分享与队列 | `app/src/main/java/com/bradj/airshift/SharedExcelImport.kt` |
| 排班模型/完成/窗口 | `app/src/main/java/com/bradj/airshift/model/RosterAssignment.kt` |
| 执勤日边界 | `model/DutyProgressDay.kt` |
| 排班日跟踪时段与排班日期 | `model/RosterTracking.kt` |
| 同一班归属（12 小时） | `model/FlightOperation.kt` |
| 航段方向枚举 | `model/LegDirection.kt`；详情条目类型 `ui/components/Lookups.kt` 的 `DetailKind` |
| 遗留清理与解密失败分类 | `data/LegacyMigrations.kt`、`data/ApiKeyDecryptFailure.kt` |
| 当前执勤时间线 | `app/src/main/java/com/bradj/airshift/model/DutyTimeline.kt` |
| OCR 接入/表格恢复 | `parser/OcrRosterReader.kt`、`parser/RosterTableParser.kt` |
| XLS/XLSX | `parser/ExcelRosterReader.kt`、`parser/ExcelRosterParser.kt`、`parser/XlsRosterParser.kt` |
| 刷新 scope/batch/Worker | `api/DutyFlightWindow.kt`、`api/FlightRefreshBatch.kt`、`api/FlightRefreshWorker.kt` |
| 飞常准协议/保护 | `api/VariFlightClient.kt` |
| 多经停和字段合并 | `api/FlightInfo.kt` |
| 排班/进度存储 | `data/RosterStore.kt` |
| API Key | `data/VariFlightApiKeyStore.kt` |
| MUC 全链路 | `specialservice/` |
| 提醒/定位 | `reminder/`、`location/AirportLocator.kt` |
| 四页 UI 与底栏 | `ui/AirShiftRoot.kt`、`ui/all/`、`ui/calendar/`、`ui/current/`、`ui/settings/`、`ui/onboarding/` |
| 排班周期与班车 | `model/shift/ShiftCycle.kt`、`ShiftGroupTable.kt`、`ShiftSlot.kt`、`ShiftSchedule.kt`、`ShiftBusPlan.kt`、`ShiftCalendarRows.kt`、`ShiftRosterBridge.kt` |
| 小组件 | `widget/`、`res/layout/widget_duty_item.xml`、`res/xml/duty_widget_info.xml` |
| 权限/备份 | `app/src/main/AndroidManifest.xml`、`res/xml/data_extraction_rules.xml` |
| 构建/版本/依赖 | `app/build.gradle.kts`、`build.gradle.kts`、`gradle/wrapper/gradle-wrapper.properties` |

表中省略 `app/src/main/java/com/bradj/airshift/` 的重复前缀时，路径均相对于该目录。

## 16. 简要演进记录

- 早期版本建立端侧排班、PP-OCRv6、飞常准直连、Excel 和 MUC 能力。
- `feat/cea-ui-rewrite` 的三页 UI、时间线和 design token 已进入 `main`。
- `feat/wps-excel-share-import` 的 Android 分享入口和完成状态已进入 `main`。
- 0.4.x 将刷新收敛为当前 + 下一未完成执勤，并加入 generation、scope 和生命周期竞态保护。
- 0.5.x–0.6.x 完成当前执勤导航和 CEA 视觉层重构。
- 0.6.7–0.8.0 将小组件从可翻页集合收敛为固定当前任务单卡，加入原子完成，并统一进出港行显示本站机位。
- 0.8.1 将 README/spec 从历史分支记录整理为当前主线的用户与维护者文档；不改变运行时业务逻辑。
- 0.9.0 新增排班日历：`model/shift/` 纯 Kotlin 周期与轮转算法、导航第 2 页、Excel 班次行自校正、到位余量设置；既有导入、执勤窗口、实时刷新、提醒、MUC 与小组件行为不变。
- 0.11.0 界面重设计"航显板 × 进程单"：新增 Barlow 字体与 `AirShiftPalette` 双主题 token；每页顶部改为贯通状态栏的藏青板面（实时钟逐位翻牌），任务统一为带方向夹条的信息条（折叠一航段一行、点开展开），全部执勤按"当前 / 接下来 / 已完成"分栏，当前执勤的"执勤完成"钉在底部并带触感，底栏改四等分红灯指示、分区切换 fade-through，状态改为小矩形灯、缺失值显示"—"；设置与日历改为板头 + 信息条，Onboarding 改整屏板面；小组件改为藏青板面并删除装饰层；`enableEdgeToEdge` 显式指定系统栏样式并新增 `values-night` 主题；`app/detekt.yml` 对 Composable 放开规则。业务、数据与 MUC 逻辑不变，已有测试契约（底栏文字、"执勤完成"、单一滚动节点、小组件 view id）保留。
- 0.11.1 动效调整：分区切换由 fade-through 改为 shared-axis（新页 16dp 位移滑入 180 ms、旧页 70 ms 淡出、无空档）；信息条展开 / 折叠改为 `AnimatedContent` + `SizeTransform`，容器高度、条的位移与底栏红灯横移共用无回弹弹簧 `AirShiftMotion.snap`（约 200 ms 内静止），内容 120 / 70 ms 淡入淡出；底栏红灯改为在标签间横移；"执勤完成"按下缩放反馈；翻牌 220 ms；夹条改为绘制并去掉 `IntrinsicSize.Min`；`AllDutyScreen` 每条只接收自身展开布尔值。设计、文案、业务与测试契约不变。
- 0.11.2 排班日跟踪：修正休息日仍弹同号航班提醒、不上班时仍后台刷新。新增 `model/FlightOperation.kt`（与计划相差 ≤ 12 小时才算排班里的这一班）与 `model/RosterTracking.kt`（排班日首个任务前 3 小时起才自动跟踪；`rosterDate()` 收敛为排班日期的唯一定义，`ShiftRosterBridge` 委托它）。`withLiveInfo` 先按归属过滤航段，`isDutyComplete` 与 `ReminderPolicy` 只信同一班的预计时间，`refreshIndices` 的 `DUTY_WINDOW` 在跟踪起点前为空，无计划时间的航段按排班日而非“今天”查询，Worker 首轮延迟直接睡到跟踪起点，提前导入时状态栏提示起点。界面、MUC 与存储格式不变。
- 0.10.6 MUC 轮椅简称与角标：解析器兼容口语简称 `C轮/R轮/S轮`（与 WCHC/WCHR/WCHS 同为高置信，可与代码在同一句混用，如「WCHR改为S轮」）；`WheelchairLevel` 增加 `shortCode` 单字母；任务卡特服角标与详情行的轮椅记录改为轮椅线性图标 + 等级字母，不再显示 WCHR/WCHS/WCHC 全称。数据层与 JSON 不变。
- 0.10.5 到位提前量调整：进港到位与提醒改为实时到达前 15 分钟（原 10 分钟），纯出港改为实时起飞前 70 分钟（原 60 分钟）。提前量收敛为 `DutyTimeline` 的两个公开常量，`ReminderPolicy` 与 `ShiftBusPlan` 直接复用；当前执勤页、小组件倒计时、系统提醒和排班日历班车推荐随之一致变化，其余行为不变。
- 0.10.4 界面一致性：任务卡与小组件一律只显示机位，航线网格删除登机口行；MUC 登机口变更改为卡片内单独一行提示（列表页只提示有变更，当前执勤页展示原值 → 新值与更新时间）；小组件航段行字段与视图 id 由 gate 改名为 stand。数据层不变。
- 0.10.3 收尾：MUC 四类记录实现 `TimestampedRecord` 并共用 `applyLatest`，登机口/机位候选循环合并为 `reduceFacilityChanges`，过期清理基于 `Fingerprinted` 统一保鲜指纹；排班 JSON 与 MUC JSON 共用 `data/JsonSupport.kt`；移除旧人工确认流程残留的 `suggestedFlights` 与 `ProcessedFingerprint.ignored`（写入停止，读取忽略）。行为不变，现有 JVM 用例即回归锁。
- 0.10.2 代码审查后的第四阶段（工程化）：Android Lint 基线 + `warningsAsErrors`、detekt 1.23.8 默认规则集 + 基线、GitHub Actions（单元测试 / Lint / detekt）、release 开启 R8 与资源裁剪并补 POI/ONNX/OpenCV keep 规则；git 历史用 `git filter-repo --replace-text` 把真实姓名改写为合成名。
- 0.10.1 任务卡去重：全部执勤页与当前执勤页共用 `AssignmentDetailCard`，详略由 `DetailLevel` 决定；航段显示内容由纯 Kotlin 的 `legUiModels` 算出（`FlightLegUiModel`），`FlightRow` 由 16 个参数收成一个模型；新增 6 项 JVM 用例锁定两种详略的差异。
- 0.10.0 代码审查后的第三阶段（结构）：新增 `duty/DutyViewModel` 承载导入、实时刷新、人工完成、设置与权限跟进，所有外部依赖收敛为 `DutyPorts` 端口接口；`RosterStore` 实现 `RosterRepository`；两个 Reader 与实时刷新改为挂起函数（`LiveFlightRefresher`）；`MainActivity` 降到 92 行，`ui/AirShiftApp` 只渲染状态；真机 duty-window / owner 场景移植为 JVM 的 `DutyViewModelTest`，真机测试改用 `ViewModelStore.clear()` 模拟进程重建。
- 0.9.2 代码审查后的第二阶段：飞常准响应缓存改为 `CompletableFuture` 合并，网络请求移出 `ConcurrentHashMap` 桶锁；Excel/OCR/飞常准载荷/MUC 解析中逐单元格、逐字段重新编译的 Regex 全部提升为常量或按字段名缓存；小组件“完成”广播用 `goAsync()` 等待 WorkManager 配置完成；进出港方向与详情条目改为 `LegDirection` / `DetailKind` 枚举，不再按中文文案分派；MUC 可见列表按状态与分钟 tick 记忆，避免每次重组都让全部任务卡重组；删除无调用的 `fetchFlight`、`resetDutyProgress`、`advanceDutyIndex`、`ReviewStatus.IGNORED` 与 7 个未使用的主题 token。
- 0.9.1 代码审查后的第一阶段修复：人工进度改按 06:00 切换的执勤日保存（修复夜班跨零点后已完成任务重新出现）；内置班组表移除全部成员姓名，测试改用合成姓名；遗留键清理改为 `LegacyMigrations` 一次性迁移，`RosterStore` 构造不再访问 Keystore；API Key 解密区分永久/瞬时失败，`hasVariFlightApiKey` 不再解密；设置页只在进入时解密一次。
