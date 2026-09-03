# 航勤智排（AirShift）当前实现规格

> - 文档类型：当前 `main` 的 as-built specification
> - 审查日期：2026-09-04（Asia/Shanghai）
> - 源码基线：`cd811b8` 之后的排班日历迭代
> - 应用版本：`0.9.0` / version code `38`
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

1. `MainActivity` 初始化提醒频道、存储、MUC Repository 和后台刷新资格。
2. 如果没有姓名，先显示 Onboarding；WPS 分享事件会等待姓名保存。
3. 用户在“全部执勤”选择图片/Excel，或从外部应用分享 Excel。
4. Reader 在后台读取，Parser 返回 `RosterParseResult`。
5. 解析成功后整体替换排班、generation 加一、人工进度归零，并扇出提醒、MUC 重匹配、WorkManager 配置和小组件重绘。
6. 如果有 API Key 且当前执勤窗非空，立即刷新当前和下一项未完成执勤。
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
| Composition root / 编排 | `MainActivity.kt`、`ForegroundFlightRefreshEffect.kt` | 初始化依赖、导入、权限、前台刷新、状态同步和四页组装 |
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

项目没有导航框架、数据库 ORM 或依赖注入框架。`MainActivity` / `AirShiftApp` 直接构造和协调各子系统，因此新增全局业务流程时必须审查这里的生命周期与副作用。

### 3.3 状态所有权

| 状态 | 所有者 | 生命周期/存储 |
|---|---|---|
| 姓名、排班、实时字段、刷新时间、人工进度、generation | `RosterStore` | `air_shift` SharedPreferences，跨进程重建 |
| 飞常准 API Key | `VariFlightApiKeyStore` | `air_shift_secrets` 密文 + Android Keystore 密钥 |
| 班组校准、到位余量、手动班组 | `RosterStore` | `air_shift` SharedPreferences，独立于 generation 不变量 |
| 特服、变更、取消、指纹、处理状态 | `SpecialServiceRepository` | `air_shift_special_services` JSON + `StateFlow` |
| 分享事件 FIFO、递增 ID、attempt token | `SharedExcelImportQueueViewModel` | `SavedStateHandle` Bundle，尽力恢复 |
| 当前底栏页面 | `DutyNavigationViewModel` | 配置变化内保留；真正重新前台时回当前执勤 |
| 工作中、警告、当前机场、刷新候选 | `AirShiftApp` Compose 状态 | 进程内；部分使用 `rememberSaveable` |
| 航班成功缓存、限流时间窗 | `VariFlightClient` companion | 进程内共享，进程重启清空 |

### 3.4 一致性与竞态保护

- `RosterStore` 使用进程内 `rosterLock` 串行化排班、generation 和人工进度变更。
- 新排班整体替换时 generation 加一；前台回调、Worker 和小组件完成都必须携带并复查 generation。
- 网络响应写回时重新读取最新 snapshot，只合并当前 scope 允许的索引和 lookup，旧排班响应不能覆盖新排班。
- 每个 HTTP 请求前复查 generation、API Key 和最新窗口；等待同 key 缓存锁之后、真正请求上游之前再次复查。
- App 完成操作还校验“调用者看到的当前索引”，旧卡片或重复点击变为 no-op。
- 分享队列只有队首可取得 attempt token；旧页面回调或旧 token 不能消费/提交新 attempt。
- Compose operation owner 在页面销毁后失效，避免异步导入或刷新回调写入已销毁界面。
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
- 查实时航班时，lookup key 是规范化航班号与运行日期；没有计划日期时回退执行时当天。

### 4.3 自动完成、人工完成与窗口

单航段满足任一条件即自动完成：

1. 已有对应实际到达/起飞时间；
2. 当前时间不早于“预计时间优先、计划时间回退”加 3 小时；
3. 航段完全没有实际、预计或计划时间，因无法跟踪而视为完成。

不存在的方向天然完成，过站任务必须进、出港都完成。排班列表为空不算“全部执勤完成”。

人工进度是一个按日保存的前缀计数 `duty_index`：

- 当前有效起点为人工前缀末尾；
- 从该位置继续跳过自动完成项，得到当前未完成任务；
- 再从当前之后跳过自动完成项，得到下一项未完成任务；
- 跨日读取时前缀视为 0；新排班导入显式写 0；实时刷新不改变它；
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
- 到位时间沿用 `DutyTimeline` 规则：出港提前 60 分钟、进港提前 10 分钟，不另立规则。
- 选车 = 满足「发车 + 车程 ≤ 到位时间 − 到位余量」的最晚一班；余量可选 0/15/30，默认 15。
- 固定习惯优先于推算：`WORK_FIRST` 早班/晚班坐 `09:00`、`WORK_FIRST` 中班坐 `12:00`；整班工作日的中二至中四坐 `12:00`。
- 首个任务的时间与进出港方向都取自实测，方向在样本内完全一致：清晨上岗的槽位首个任务是出港，下午上岗的中二至中四以及接班日的早班/中班是过站进港。把它们误标为出港会让推荐班车晚于到位时间，`ShiftBusPlanTest` 有对应不变量断言。

#### 校准

`ShiftCalibration` 承载一次真实观测（日期 + 有序分组）。有校准数据时以观测当天的顺序为相位基准，其余日期相对它旋转；成员按“观测优先、未被任何观测行认领的内置成员保留”合并，因此病假缺席的人不会丢失归属，真正换组的人也不会留在旧组。只有整班工作日的表格带班次行，故只有这类日期能作为校准点。

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

### 6.1 根导航

- `AirShiftRoot` 使用 Material 3 `Scaffold` 加自定义 `Surface/Row` 底栏，不使用 Navigation Component 或 Material `NavigationBar`。
- 固定四页：全部执勤、排班日历、当前执勤、设置；中央当前执勤为红色圆形主入口。
- 底栏左侧两项各占 1 份权重、右侧设置占 2 份，浮动按钮的 88dp 缺口才落在正中；设置在自己的双宽格内居中。
- 新 ViewModel 默认当前执勤；冷启动、进程重建和真正从后台恢复都会回到当前执勤。
- 旋转等 `isChangingConfigurations=true` 的停止不算离开应用，保持当前页。

### 6.2 全部执勤

- 显示问候、日期、当前机场、图片/Excel 导入、状态消息、解析/刷新警告和精确闹钟提示。
- `PullToRefreshBox` 触发显式手动刷新；刷新中的视觉状态与一般工作状态分开。
- 列出整份当前排班，而非只列刷新窗口。
- 卡片展示任务类型、机号/机型、进出港航班、机场、计划/实时状态、登机口/机位和 VIP。
- 特服只显示摘要角标；MUC 登机口/机位变化在该页只显示最小“变更”提示，完整新旧值在当前执勤页。

### 6.3 当前执勤与时间线

- 无排班时显示导入引导；没有未完成任务时显示今日完成态。
- 页面使用第 4.3 节的窗口算法，展示当前与下一项未完成任务。
- 到位时间：有进港航段时取 `实际到达 ?: 预计到达 ?: 计划到达` 减 10 分钟；纯出港取 `实际出发 ?: 预计出发 ?: 计划出发` 减 60 分钟。
- 出港预计登机开始为最佳出发时间前 40 分钟；预计登机口关闭为前 15 分钟。
- 倒计时每分钟更新；到位点已过时显示“应立即到位”。
- 页面展示完整特服、行程取消以及 MUC 登机口/机位新旧值。
- “执勤完成”没有确认、撤销或回退；原子 guard 只防止旧页面和重复点击完成错误任务。
- 尚未整体完成的过站任务始终按进港到位规则显示，即使进港已有实际时间而出港仍未完成。

### 6.4 设置

- 修改姓名；保存后只影响后续导入，不重新解析旧排班。
- API Key 文本状态不使用 `rememberSaveable`，明文不进入 saved-instance-state。
- 测试连接从现有排班中选择首个带航班号的任务；无候选时直接失败。
- 保存非空 API Key 后清缓存并重配刷新；空文本不会隐式清除已有 Key，必须点击“清除 API Key”。
- 显示 MUC 通知读取授权状态、最近成功识别时间和最近处理结果，并打开系统授权页。
- 排班日历分区显示生效的班组（按姓名自动识别或手动指定），姓名匹配不到时才出现手动选组的胶囊；到位余量可选 0/15/30 分钟。

### 6.5 视觉系统

- Design token 集中于 `ui/theme/AirShiftTheme.kt`；主色为东航红 `#C8102E` 和深藏青 `#14284B`。
- App 支持随系统切换的浅/深色主题；小组件固定浅色。
- 任务卡使用统一的方向色条、航班号、航线网格、实时/计划时间、状态 chip、meta 行和骨架占位。
- 当前执勤 hero 正常为藏青，过点为红色并使用呼吸动效；数字采用等宽样式避免跳动。
- 状态颜色和导航状态变化使用 200 ms ease-out token。

### 6.6 排班日历页

- `ShiftCalendarScreen` 与全部执勤页同构：`LazyColumn` + 16dp contentPadding + 16dp 间距，页头复用深藏青渐变卡与燕尾弧线装饰。
- 范围为今天前 7 天至后 42 天，按月给出小标题；今天那一行用 `QuietCard(vip = true)` 的琥珀描边高亮。
- 每天一张卡：左侧 `AccentBar` 按日型着色（整班东航红、交接班琥珀、休息弱提示灰），右上角班次胶囊，正文给出日型说明、班车、到场/到位/富余分钟数，以及预计下班或交班时间。
- 休息日与交接班日不到岗时只显示日期与说明，不显示班次胶囊、班车和下班时间。
- 班车明细在到场晚于到位时间（`spareMinutes < 0`）时改用琥珀色并提示“建议提前一班”，异常不会混在灰字里。
- 没有匹配到班组时不渲染任何日期行，只给一张说明卡并提供跳转设置的入口。
- 只使用 `ui/theme/AirShiftTheme.kt` 既有 token 与 `ui/components/` 既有组件，未引入新颜色、圆角或阴影。

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

每项最多有两个航段，两项窗口最多产生 4 个不同 lookup；相同航班号与日期去重。窗口不再按计划时间相对现在的小时范围筛选。

### 7.2 调度与停止

- 前台 effect 在 Activity 前台、有排班和 API Key 时启动；可立即查询，之后目标间隔 5 分钟，忙碌时每 15 秒复查。
- effect key 包含 active、generation 和完成状态；普通实时字段变化不会造成紧密重启。
- 后台使用联网约束的 `PeriodicWorkRequest`：周期 15 分钟、首轮延迟 15 分钟、generation 专属唯一名称、`KEEP`。
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
- 入港/出港映射先选计划时间最接近排班时间的航段，再回退已存本场代码、同航班过站拓扑，最后分别回退末段/首段。
- 新响应只有非空字段才覆盖旧值；部分数据或失败不会主动清除已保存实时字段。

### 7.5 缓存、限流与失败

- 同航班同日期成功结果缓存 120 秒；失败不缓存；相同 key 的并发 loader 由 `ConcurrentHashMap.compute` 合并。
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
- 保存成功时更新 `last_live_refresh`，随后重匹配 MUC、重排提醒并重绘小组件。

## 8. 提醒与定位

### 8.1 提醒策略

每项任务最多安排一条提醒：

- 有进港航段（包括过站）：实际到达存在时不提醒；否则取预计到达优先/计划到达回退，提前 10 分钟；
- 纯出港：实际出发存在时不提醒；否则取预计出发优先/计划出发回退，提前 1 小时；
- 没有可用时间或目标时间已过：不安排。

每次重排用 `stableId.hashCode()` 创建 PendingIntent，先取消同 ID 的旧闹钟。获得精确闹钟特殊访问时使用 `setExactAndAllowWhileIdle`，否则使用 `setAndAllowWhileIdle`。通知频道为高重要性，点击通知打开 `MainActivity`。

`BootReceiver` 只监听标准 `BOOT_COMPLETED`，负责创建频道、从排班重排提醒并重绘小组件；没有监听时区变化、系统时间变化或应用升级广播。

当前人工完成状态不参与 `ReminderPolicy`。提前人工完成的任务若提醒时间未到，App/Widget 完成后的全排班重排以及开机重排仍可能再次安排它。

### 8.2 定位

- 使用 Google Play Services Fused Location Provider；精确权限时请求高精度，只有粗略权限时请求平衡功耗精度。
- 当前位置请求允许最多 60 秒旧缓存，最长等待 20 秒；失败或空结果回退 `lastLocation`。
- 最终位置不得早于 10 分钟。
- 候选机场只来自本次成功实时刷新结果的航班两端，并要求经纬度，按机场代码去重。
- 计算设备到所有候选机场的球面距离，最近值不超过 15 km 才返回匹配。
- 当前机场只存在 Compose 状态；设备原始位置不写入 `RosterStore`，应用自身不把它发送给飞常准。

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
- 轮椅 `WCHR`、`WCHS`、`WCHC`；
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
- 明确服务代码/正式类别可形成高置信；普通“轮椅”“无随行”等弱表达为低置信。
- 低置信特服直接忽略，不提供人工确认 UI。
- 高置信特服、登机口、机位和取消候选都可在无排班匹配时暂存，并在排班变化后重试。

### 9.4 时序、取消、去重和过期

- 更晚 source timestamp 覆盖更早状态；相同时间且不同指纹的冲突保留已有值。
- 行程取消会停用已有特服，并移除不晚于取消事件的登机口/机位状态；更晚的有效更新可重新激活。
- 指纹使用本机随机 32 字节密钥的 HMAC-SHA256。
- 可靠消息时间按“同指纹 + 同 source time”去重；不可靠 fallback 摘要在未过期期间对同指纹保守去重。
- 未匹配候选以处理时刻起 24 小时过期；已匹配记录以实际优先、预计回退、计划回退的航段时间后 24 小时过期。
- UI active 查询会立即隐藏逻辑过期记录；SharedPreferences 的物理清理只在下一次通知处理或排班 reconcile 时发生，没有独立定时清理器。

### 9.5 持久化隐私

原通知文本只在内存中存在。JSON version 3 保存结构化特服、变更、取消、候选、指纹和处理状态；不会保存原文、姓名、发送人、电话、票号、座位、图片或附件。Codec 可读 version 1–3，并逐项跳过损坏记录。

## 10. 桌面小组件

- `DutyWidgetProvider` 是标准 AppWidget，默认 4×3，可横纵缩放，系统周期 `updatePeriodMillis=30` 分钟。
- 直接读取 `RosterStore`，固定显示当前未完成任务；没有排班或全部完成时显示整卡提示。
- 不使用集合容器、RemoteViewsService、翻页、轮播或独立小组件存储。
- 内容包括执勤序号/总数、类型、机号/机型、VIP、到位状态、进出港航班、对方机场和本站机位。进港行使用 `arrivalStand`，出港行使用 `departureStand`；两行都显示“机位”。
- 小组件不读取 `SpecialServiceRepository`，因此不叠加 MUC 登机口/机位变更、特服或取消状态。
- 倒计时使用 launcher 进程渲染的 count-down `Chronometer`，App 进程无需每秒唤醒。
- 跨过零点后的即时格式由系统 Chronometer 决定；没有代码监听零点并主动 stop，最迟在下一次重绘后转成“应立即到位”。
- 导入、完成、成功实时合并、提醒触发、开机和系统周期都会重绘。
- 点击卡片打开 App；完成按钮发给非导出的显式 receiver，使用 generation + 当前索引原子校验。
- 完成成功后重排提醒、重配周期任务，并用一次性联网 Worker 只补查新进入窗口的航班。
- 小组件固定浅色，并按标准 AppWidget 出现在 OriginOS“应用挂件”；第三方应用不能注册厂商专有“原子组件”。

## 11. 持久化、安全与权限

### 11.1 本地存储

| 存储 | 内容 | 兼容/保护 |
|---|---|---|
| `air_shift` | `user_name`、`last_live_refresh`、`duty_progress_date`、`duty_index`、`roster_generation`、`assignments`、`shift_report_margin_minutes`、`shift_manual_group_id`、`shift_group_calibration` | 应用私有 JSON/标量 |
| `air_shift_secrets` | API Key IV 与密文 | Android Keystore AES-GCM、128-bit tag、AAD |
| `air_shift_special_services` | version 1–3 结构化 MUC 状态、随机 HMAC key | 应用私有，不含正文 |
| `SavedStateHandle` | 分享 FIFO、URI 字符串、错误、ID、attempt token | 临时尽力恢复，不是永久业务存储 |

排班 JSON 兼容行为：

- `arrivalStand` 缺失时回退旧键 `arrivalGate`；
- 新增可空实时/机场/机位字段缺失时为 null；VIP 字段缺失时为 false；
- 非法日期字符串退化为 null；
- `aircraftRegistration` 和 `assignees` 是必需键；外层 JSON 或任一条目抛错会让整份排班加载为空，不会逐项跳过。

排班日历的三个键独立于 generation 与 `rosterLock` 不变量：`shift_report_margin_minutes` 写入时收敛到 0–120；`shift_group_calibration` 解析失败时返回 null 并回退内置班组表，不抛错；`shift_manual_group_id` 只在姓名匹配不到班组时参与判定。

API Key 读写使用随机 IV、AES/GCM/NoPadding 和固定 AAD。Keystore/密文损坏或解密失败时清除密文和对应 key，不返回不可信明文。旧 gateway URL、supplement 和旧 gateway 凭据在初始化时清理。

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

## 12. 工程、构建与验证

### 12.1 工程配置

- 单模块 `:app`；namespace/application ID 为 `com.bradj.airshift`。
- Java 17、Kotlin 2.4.10、Android Gradle Plugin 9.3.0、Gradle Wrapper 9.5.0。
- `compileSdk=37`、`targetSdk=37`、`minSdk=33`。
- Compose BOM 2026.08.00、Activity Compose 1.13.0、Lifecycle 2.10.0、WorkManager 2.11.2。
- Google Play Services Location 21.4.0、ONNX Runtime Android 1.21.1、OpenCV 4.12.0、Apache POI 5.5.1、Coroutines Android 1.9.0。
- Gradle Wrapper 使用华为云镜像并固定 SHA-256；configuration cache 开启，Gradle parallel 关闭。
- `local.properties` 可设置 `airshift.buildDir`，把 OneDrive 内的构建产物重定向到本地目录。
- release 未配置发布签名，`isMinifyEnabled=false`。

主要命令：

```powershell
.\gradlew.bat test lintDebug assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

Android 仪器测试需要 API 33+ 设备或模拟器。`XlsRosterParserRealFileTest` 只有配置 `AIRSHIFT_XLS_FIXTURES_DIR` 和 `AIRSHIFT_XLS_TEST_NAME` 时才运行真实外部 `.xls` fixture。

### 12.2 本轮验证

本轮新增排班日历，并在 JDK 17 下执行：

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
| JVM `api` | 41 | 两项窗口、batch、字段/多经停映射、JSON-RPC/SSE、脱敏错误、缓存/限流/并发 |
| JVM `model` | 19 | 时间线、自动完成、人工前缀和窗口 |
| JVM `parser` | 12 | XLSX/XLS、模板变体、姓名隔离、班次行解析；含 2 个条件式真实 fixture |
| JVM `model/shift` | 83 | 周期与日型、轮转回归锁、槽位与交接班到岗、班车与余量、班组表合并、日历行装配 |
| JVM `specialservice` | 29 | MUC 解析、匹配、顺序、取消、去重、过期和 JSON 兼容 |
| JVM `widget` | 11 | 当前页选择、空/完成/倒计时、VIP、机场和机位 |
| JVM `ui` | 8 | 默认页、前后台恢复、配置变化和排班日历页选中 |
| JVM smoke | 9 | OCR 表格、姓名、VIP、提醒基础 |
| Android 数据层 | 18 | generation、进度、scope 合并、旧 JSON、扩展机位、班组校准 JSON 往返与余量收敛 |
| Android 刷新编排 | 14 | duty-window 9 项、foreground effect 5 项 |
| Android WorkManager | 4 | KEEP、generation、停止和旧任务迁移 |
| Android Excel 分享 | 11 | Manifest/Intent/FIFO/恢复 10 项、owner 隔离 1 项 |
| Android 当前页 Compose | 2 | 点击完成、自动跳过和新排班恢复 |
| Android 小组件 | 3 | 单卡布局与 RemoteViews 渲染 |
| Android OCR | 1 | PP-OCRv6 合成图片端到端 |

`testdata/synthetic_roster.png` 是无真实个人信息的自动 OCR fixture，可由 `tools/generate_synthetic_roster.ps1` 重建。`testdata/mu2415_verify.png` 当前没有被自动测试引用。

### 12.4 仍缺的验证

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
- 排班日历页的 Compose 交互与无障碍（当前只有真机人工核对，没有 Compose 测试）；
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
7. **自动完成启发式**：陈旧预计时间或超长延误可能在 3 小时后过早完成；无时间航段直接完成。
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
20. **发布状态**：中文硬编码、无 CI、无发布签名、release 未压缩，只适合开发与个人验证。
21. **排班规律为外推**：六天周期、轮转步长 3、槽位分层和交接班到岗规则由六份实测排班表反推，样本内零反例但属外推；实际排班调整后需靠导入 Excel 的班次行自校正，应用不会主动发现漂移。
22. **日历班车多为预估**：只有与当前已导入排班同一天的那一行使用真实航班时间；其余行按实测的典型首个任务推算，个别日期观测到首任务时间离群（如 08-24 晚二 10:40、08-30 晚一 08:25）。
23. **单份排班的限制**：App 只保存一份当前排班，因此日历无法为多个日期同时提供真实数据。

## 14. 当前验收标准

### 14.1 导入与存储

- 有效图片/XLS/XLSX 能生成只属于配置姓名的合法航班任务；无关行不进入结果。
- 文件格式由签名决定；非法、加密或损坏工作簿产生可理解错误且不替换旧排班。
- 成功空结果按当前产品语义替换旧排班，并显示“无匹配姓名”警告。
- `CES`、符号航班号、日期/序列时间和 `+` 次日按本规格规范化。
- 新排班重置进度并增加 generation；实时刷新不重置进度。

### 14.2 刷新与进度

- 导入、前台、后台和未完成时手动刷新只查询当前 + 下一项未完成执勤。
- 两项窗口按航班号+日期去重，每个请求前复查最新资格。
- 人工完成只能推进调用者所见当前任务，并只补查新进入窗口的航班。
- 全部完成后自动刷新停止；全排班手动刷新继续可用且不重置人工进度。
- 旧 generation 响应、Worker 或 Widget 卡片不能覆盖/推进新排班。
- 部分 API 失败保留成功结果和已有数据，错误不泄露敏感载荷。

### 14.3 提醒、MUC、定位与 Widget

- 进港/过站只建到达前 10 分钟提醒，纯出港只建出发前 1 小时提醒。
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
| 应用生命周期与总编排 | `app/src/main/java/com/bradj/airshift/MainActivity.kt` |
| 前台刷新 effect | `app/src/main/java/com/bradj/airshift/ForegroundFlightRefreshEffect.kt` |
| WPS 分享与队列 | `app/src/main/java/com/bradj/airshift/SharedExcelImport.kt` |
| 排班模型/完成/窗口 | `app/src/main/java/com/bradj/airshift/model/RosterAssignment.kt` |
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
| 四页 UI | `ui/AirShiftRoot.kt`、`ui/all/`、`ui/calendar/`、`ui/current/`、`ui/settings/` |
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
