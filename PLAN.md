# AirShift 前端 UI / 交互重写计划

## 1. 目标与约束
- **约束（最高优先级）**：严禁对逻辑层（`parser/`、`api/`、`reminder/`、`specialservice/`、`location/`、`model/RosterAssignment`）做大量修改；仅允许为 UI 新交互做小幅度功能添加（见 §4）。
- UI 全面重写为**中国东方航空 VI 风格**：主色采用东航红（约 `#C8102E`），白色卡片、浅灰背景、简洁商务；圆角与阴影保持克制。
- 整体结构改为**底部三段导航**：
  - 左：**全部执勤**（当日全部任务，结构与现 UI 相仿）
  - 中：**当前执勤**（正在执行的任务，导航按钮视觉凸显）
  - 右：**设置**（现设置对话框扩展为独立页面，功能不变）
- 现状：`MainActivity.kt`（1332 行）承载全部 UI + 状态编排；无导航、无深色模式、字符串硬编码。本次重写 UI 层，逻辑层接口全部复用。

## 2. 逻辑层小幅度新增（仅 2 处）
### 2.1 `data/RosterStore.kt`：执勤进度持久化（新增，约 30 行）
- `dutyProgressDate: LocalDate?` + `currentDutyIndex: Int`，存 SharedPreferences。
- 语义：
  - 导入新排班时重置为 0 并记录当天日期（经确认：仅导入路径调用 `resetDutyProgress()`，实时刷新复用 `saveAssignments` 不重置，避免前台 5 分钟轮询刷掉执勤进度）；
  - 读取时若 `dutyProgressDate != 今天` 则视为 0（每天执勤从第一个任务开始，不做自动判断逻辑）；
  - `advanceDutyIndex()`：执勤完成按钮 +1。
### 2.2 新增纯函数助手 `model/DutyTimeline.kt`（约 60 行，无副作用，可单测）
为当前执勤页计算时间（规则与 `ReminderPolicy` 一致，但不改动它）：
- `gateArrivalTime(assignment): LocalDateTime?` —— 须到达登机口时间：
  - 进港（含接续）：实时到达（`actualArrival ?: estimatedArrival ?: scheduledArrival`）− 10 分钟；
  - 仅出港：实时起飞（`actualDeparture ?: estimatedDeparture ?: scheduledDeparture`）− 1 小时。
- `boardingStartTime(assignment): LocalDateTime?` —— 出港航段预计登机开始 = 实时起飞 − 40 分钟。
- `gateCloseTime(assignment): LocalDateTime?` —— 预计登机口关闭 = 实时起飞 − 15 分钟。
  - 用户要求"以实际起飞时间为准"：优先 `actualDeparture`，未起飞时回退 `estimatedDeparture`，再回退计划时间。
> 以上是对逻辑层的全部改动；其余一律复用现有接口。

## 3. UI 层新结构（新增 `ui/` 包，MainActivity 重写）
```
app/src/main/java/com/bradj/airshift/
├── MainActivity.kt              ← 重写：保留依赖注入与业务编排，界面托管给 ui 层
└── ui/
    ├── theme/AirShiftTheme.kt   ← 东航 VI 配色（东航红主色）+ Typography
    ├── AirShiftRoot.kt          ← Scaffold + 底部三段导航 + 各 section 切换；状态自 MainActivity 下传
    ├── components/
    │   ├── AssignmentCard.kt    ← 任务卡片（全部执勤用，精简版）
    │   ├── SpecialServiceBadge.kt ← 特服小角标（仅标记有无，不显示数量明细）
    │   └── ChangeIndicator.kt   ← 登机口/机位变更的最小提醒元素（小圆点+"变更"字样）
    ├── all/AllDutyScreen.kt     ← 全部执勤页
    ├── current/CurrentDutyScreen.kt ← 当前执勤页
    ├── settings/SettingsScreen.kt   ← 设置页（独立页面）
    └── onboarding/OnboardingScreen.kt ← 首启姓名页（沿用现逻辑，套用新主题）
```
- **状态管理**：维持现有架构（状态集中在根 Composable / MainActivity 编排函数中），不引入 ViewModel/导航库，保持最小改动。section 切换用一个 `enum class DutySection { ALL, CURRENT, SETTINGS }` + `rememberSaveable` 状态即可。
- **底部导航**：Material3 `NavigationBar`，中间项使用凸显样式——红色实心圆形大图标（类似 FAB 嵌入），选中/未选中均比两侧大，两侧为标准 `NavigationBarItem`。三段名称：**全部执勤 / 当前执勤 / 设置**。
- **主题**：`lightColorScheme` 定制（东航红 primary `#C8102E`、白 surface、浅灰背景 `#F5F6F8`、辅助深蓝灰），VIP 仍用琥珀色强调以保留辨识度。不需要新依赖（`material3` 已含 `NavigationBar` 与基础 Icons）。

## 4. 各页面详细设计
### 4.1 全部执勤（AllDutyScreen）
沿用现 UI 结构与信息架构：
- 顶部：问候 + 当前机场定位文本（去掉右上角"设置"入口，改由底部导航承担）。
- 导入卡片：上传排班图片 / 导入 Excel（处理中态不变）。
- 状态消息、精确闹钟提醒条、识别警告卡。（已取消：待确认特服区——低置信结果在逻辑层直接忽略，UI 不再展示。）
- 任务列表 `AssignmentCard`（相对现 UI 的变化）：
  - **特服**：不再列出徽章明细与说明文字，只在卡片角落显示**小角标**（如红色小圆点或"特服"小 pill，无论数量均一个标记）。
  - **登机口/机位变更**：仅显示**最小提醒元素**（如登机口文字旁的橙色"变更"小点），不显示变更后的实际值；实际值只在当前执勤页展示。
  - 其余（机号机型、进出港行、实时/计划时间、机场三字码、取消标记）保持现有展示。
- 下拉刷新保留（PullToRefreshBox 移入本页）。
### 4.2 当前执勤（CurrentDutyScreen）
- 数据源：`assignments[dutyIndex]`；`dutyIndex` 来自 §2.1 的持久化进度。
- **倒计时卡（页面顶部，最醒目）**：
  - 主倒计时：当前任务的"须在 X 小时 Y 分钟后到达登机口（HH:mm）"，目标时间由 `DutyTimeline.gateArrivalTime` 计算，`LaunchedEffect` 每分钟 tick 刷新；已过时显示"应立即到位"。
  - 副行：下一任务预告——"下一任务 MUxxxx：HH:mm 前到位（还有 X 小时 Y 分钟）"。
- **当前任务详情卡**（比全部执勤更详细）：
  - 机号、机型、任务类型、VIP 标记；
  - 进出港航段完整信息（沿用现 FlightRow 全部字段：三字码+机场名、计划/预计/实际时间）；
  - **登机口/机位变更的实际变更情况**：如"登机口 12 → 25（MUC 更新于 HH:mm）"，新旧值对比展示；
  - 出港航段新增两行：**预计登机开始**（起飞前 40 分钟）与**预计登机口关闭**（起飞前 15 分钟），基于实时起飞时间；
  - **特服详情区**：完整列出每条特服的类型、轮椅等级、数量、置信度、确认状态、更新时间（即现 UI 的详细形式）。
- **底部"执勤完成"大按钮**：点击 → `advanceDutyIndex()` → 自动切到下一条任务；最后一条完成后显示"今日执勤全部完成"收尾页（含返回全部执勤入口）。
- 边界：无排班 → 空态引导去导入；`dutyIndex` 越界 → 钳制到末位。
### 4.3 设置（SettingsScreen）
现 `SettingsDialog` 的所有功能原样平铺为独立页面（LazyColumn 分区）：
- MUC 通知读取状态卡（授权状态、最近成功识别、最近处理结果、跳转系统授权页）；
- 姓名编辑；
- 飞常准 API Key（Keystore 加密保存说明、密码输入框、测试连接、清除 API Key）；
- 保存按钮。
行为与回调与现状完全一致（API Key 明文仍不得进入 saved-instance-state）。
### 4.4 首启 Onboarding
逻辑不变，套用东航红主题。

## 5. MainActivity / 根 Composable 调整
- `MainActivity.onCreate` 的依赖注入、`refreshLive`、`openExactAlarmSettings` 等保持不变。
- `AirShiftApp` 中现有的编排逻辑（导入流程 `finishImport`、前台 5 分钟轮询、权限请求、生命周期 ON_START 同步、MUC state 收集）**原样保留**，仅：
  - 界面部分替换为 `AirShiftRoot` + 三个 Screen；
  - 新增 `dutyIndex` 状态（从 store 读、导入后重置）；
  - `showSettings` 布尔状态改为 section 枚举；
  - `SettingsDialog` → `SettingsScreen`。
- 预计 MainActivity 净瘦身到约 400 行以内（纯编排），UI 组件全部移入 `ui/` 包。

## 6. 测试与验证
1. 新增 `DutyTimelineTest`（JVM 单测，覆盖进港/出港/接续、actual/estimated/scheduled 回退、空值）。
2. 现有测试全部不受影响，必须保持通过。
3. 构建验证：`gradlew.bat test lintDebug assembleDebug`（按 README 配置 JAVA_HOME）。
4. 人工走查清单：首启 → 导入 → 三 tab 切换 → 当前执勤倒计时与登机时间 → 执勤完成推进 → 设置各项功能 → 下拉刷新。

## 7. 实施顺序
1. 生成 `PLAN.md` 到项目根目录（本计划内容）。
2. `RosterStore` 进度持久化 + `DutyTimeline` + 单测。
3. `ui/theme` + 底部导航骨架 + section 切换。
4. `AllDutyScreen`（含精简卡片/角标/变更最小提醒）。
5. `CurrentDutyScreen`（倒计时、详情、特服、变更对比、执勤完成）。
6. `SettingsScreen` + `OnboardingScreen`。
7. MainActivity 接线与清理；`README.md`「已实现」清单同步更新 UI 描述。
8. 运行 §6 全部测试与构建，修复 lint 告警。

## 8. 风险与注意
- 不引入导航库等重型依赖（导航用手写枚举切换）；底部导航图标所需的 `material-icons-core` 并未随当前 Compose BOM 的 material3 传递进来，因此显式加入该官方轻量 artifact。若后续需要更丰富图标再评估 `material-icons-extended`。
- 待确认特服流程已取消：低置信结果在 `MucMessageReducer` 中直接忽略（计数仍计入“最近处理结果”），`SpecialServiceRepository.confirmReview` / `ignoreReview` 及 UI 确认区已删除。
- 倒计时仅 UI 展示，不改动 `ReminderPolicy` / 闹钟调度。
- OneDrive 目录构建产物仍走 `%LOCALAPPDATA%/CodexBuild/AirShift`，不受影响。
