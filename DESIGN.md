# 航勤智排 · 设计系统（0.11.1）

> 从已构建的界面记录，不是意图稿。源码真相在 `ui/theme/AirShiftTheme.kt`、`ui/theme/AirShiftMotion.kt`、`ui/components/`；本文与代码冲突时以代码为准并同轮修正。

## 世界：航显板 × 进程单

地面保障的一天是一条按时间排队的航班队列。界面是"一个人的航显板"：每页顶部是贯通到状态栏之下的藏青**板面**（实时钟、日期、倒计时或班次），下面是冷白**条架**；每项任务是一条固定列的**信息条**（时间｜航班｜航线｜机位｜状态灯），左侧 6dp **夹条**给方向；当前任务像从进程单架上抽出的那条被抬起、展开，完成后滑入"已完成"栏位变暗。它拒绝"登机牌卡片堆叠 + 灰色骨架"的航司 App 默认排布。

使用场景：白天在机坪强光下两秒看清"去哪、几点、哪班"；夜班到凌晨 01:35，深色是真正的夜间航显配色，不是反色。

## 色彩（`AirShiftPalette`）

| Token | 白班 | 夜间航显 | 用途 |
|---|---|---|---|
| `board` | `#14284B` | `#0B1526` | 板面、Onboarding 全屏、小组件底 |
| `boardRule` | 白 10% | 白 10% | 板面行线 |
| `onBoard` / `onBoardSecondary` / `onBoardTertiary` | `#FFFFFF` / `#A9B6CC` / `#6F7F9C` | `#EDF1F7` / `#93A3BF` / `#5E6F8C` | 板面文字三级（次文字带藏青调，不用纯灰） |
| `onBoardAlert` | `#FF8A98` | `#FF8A98` | 板面上的"应立即到位" |
| `ground` | `#F1F3F7` | `#0B1526` | 页面底（条架） |
| `strip` / `rule` / `ruleStrong` | `#FFFFFF` / `#E4E8EF` / `#D5DAE2` | `#122036` / `#1F2F4A` / `#2A3C5C` | 信息条、线 |
| `ink` / `inkSecondary` / `hint` | `#14284B` / `#4A5568` / `#8A94A6` | `#EDF1F7` / `#9AA7BD` / `#6B7A94` | 文字三级；缺失值用 `hint` |
| `departure` / `departureSoft` / `departureText` | `#C8102E` / `#FDECEE` / `#9C0B22` | `#C8102E` / 红 14% / `#FF8A98` | 出港夹条、主操作、出港灯 |
| `arrival` / `arrivalSoft` / `arrivalText` | `#2B5EA7` / `#EAF1FB` / `#1D4B8A` | `#7FA6E6` / 蓝 14% / `#A8C4F0` | 进港夹条、进港灯、班次灯 |
| `ok` / `okSoft` | `#0F7B5F` / 绿 10% | `#4CC38A` / 绿 14% | 已起飞 / 已到达 / 已完成 / 实际时间 |
| `estimate` / `estimateSoft` | `#B45309` / 琥珀 10% | `#F5B233` / 琥珀 14% | 预计（晚点）时间、变更、交接班、通知条 |
| `alert` / `alertSoft` | `#C8102E` / 红 10% | `#FF6B7A` / 红 14% | 已取消、未授权 |
| `vipSoft` / `vipText` | `#FCEBC8` / `#6E4200` | 金 16% / `#F5CB7A` | VIP 灯 |
| `neutralSoft` | 藏青 6% | 白 8% | 中性灯底、小按钮底 |
| `nav` / `field` | `#FFFFFF` / `#F1F3F7` | `#0F1B31` / `#0B1526` | 底栏、输入框 |

规则：色彩策略是 Restrained，板面是唯一的大面积色。东航红只给出港与主操作；琥珀与墨绿只做状态灯；没有渐变；阴影只有一级（`currentCardShadow`，当前条抬起）。M3 `colorScheme` 由同一套 palette 映射，`error` 系列已补齐，不会泄漏默认紫色。

## 字体与字阶

- **Barlow**（`AirShiftFonts.Text`，Regular/Medium/SemiBold/Bold）：所有 Latin 与数字；汉字由系统字体逐字回落。
- **Barlow Semi Condensed**（`AirShiftFonts.Board`，SemiBold/Bold）：板面大数字、时钟、航班号、机位号——与航显屏的紧排一致。
- 数字样式全部 `tnum`：`BoardNumeric` 68/Bold、`BoardValue` 26/SemiBold、`BoardClock` 22/SemiBold、`FlightNumberLarge` 26/Bold、`FlightNumber` 16/Bold、`StripTime` 16/SemiBold、`NumericValue` 17/SemiBold、`NumericSmall` 15/SemiBold。
- Material 字阶（sp）：display 68 / 44 / 34，headline 30 / 26 / 22，title 20 / 17 / 15，body 15 / 13 / 12，label 13 / 12 / 11。汉字大字（"晚二"、"应立即到位"）走 display 槽位。

## 形状与间距

- 圆角：灯 4、输入框与小按钮 8、信息条 10、按钮 12、板面底边 0（贯通）。
- 间距 4dp 网格：条内 10–14，条间 8，栏位标题上 6，页边 16。折叠航段行 44dp，日历行 ≥56dp，主按钮 52dp，小按钮 32–36dp，触控目标 ≥44dp。

## 组件词汇

| 组件 | 文件 | 说明 |
|---|---|---|
| `BoardHeader` / `BoardClock` | `BoardHeader.kt` | 板面：`statusBarsPadding` 在板内；顶行分区名 + 副标题 + 翻牌时钟与日期；`content` 放板面主体，`footer` 上方一条 `boardRule` |
| `DutyStrip` | `DutyStrip.kt` | 一项任务：夹条 + 每航段一行（折叠）或一块（展开）；`emphasized` 抬起、`completed` 60% 透明、`onClick` 切换展开；`AnimatedContent` + `SizeTransform`（弹簧） |
| `StatusLamp` / `LampKind` | `StatusLamp.kt` | 22dp 小矩形灯（不是胶囊）；`dot` 给飞行状态，`icon` 给轮椅 |
| `Modifier.directionHolder` / `holderColors` / `HolderBar` | `DirectionHolder.kt` | 6dp 夹条：沿信息条左边缘绘制，进港蓝、出港红、过站上蓝下红；`HolderBar` 给日历按日型着色 |
| `BayTitle` | `Bay.kt` | 栏位标签：小字 + 数量 + 向右延伸的线 |
| `OdometerText` | `OdometerText.kt` | 逐位翻牌：方向由整串数值决定（时钟向上、倒计时向下），220 ms 减速入场、130 ms 退场；非数字字符静止 |
| `PinnedActionBar` | `PinnedActionBar.kt` | 钉底主操作（执勤完成 / 保存） |
| `EmptyBay` / `NoticeStrip` | 同名文件 | 空态；提示条分 `NoticeTone.Warning`（琥珀底，警告与权限）与 `Neutral`（条底 + 线，状态说明） |
| `LinearIcons` | `DesignComponents.kt` | 1.5px 线性图标 |
| 纯计算 | `LegPresentation.kt`、`DutyBays.kt`、`BoardFormats.kt`、`OdometerSlot.kt` | 状态灯规则、本站/对方机位、分栏、日期与剩余时长文案、翻牌槽位 |

缺失数据：显示"—"或省略该格，不用骨架。登机口不在条上；轮椅只给图标 + 等级字母。

折叠行的固定列按 sp 折算（16 / 46 / 58），随系统字体放大；字体 ≥1.15 倍时航段改为两行（向/时间/航班/灯 + 航线/机位），航线永远不被挤成省略号。板面在"全部完成 / 没有排班"时仍回答"接下来"：下一班的日期、班次、班车与到位时间。

## 动效（`AirShiftMotion` · Material 3 MotionScheme）

原则：第一帧就动、退场比入场快、可中断。两类驱动：一次性入退场与翻牌用显式曲线的 tween（`EmphasizedDecelerate` 第一帧就走约 30%）；会反复重触发的尺寸 / 位移 / 颜色用弹簧，弹簧刚度 / 阻尼镜像 M3 standard MotionScheme（spatial 阻尼 0.9 · 刚度 700 / 1400 / 300，effects 阻尼 1.0 · 刚度 1600 / 3800 / 800），只在 `AirShiftMotion` 一处定义（material3 1.4.0 未公开 `MotionScheme`），业务代码里没有散落的弹簧常量。

| Token | 值 | 用途 |
|---|---|---|
| Exit | 70 ms · Linear | 旧页、折叠前内容、移除的条淡出 |
| Content | 120 ms · Linear（可带 35 ms RevealDelay） | 展开内容、新增的条淡入 |
| Enter | 180 ms · EmphasizedDecelerate | 新页滑入 **与** 淡入（同曲线） |
| Flip / FlipExit | 220 / 130 ms | 翻牌位移与新数字淡入 / 旧数字淡出（130 ms 由后续翻牌批次启用，当前仍为 220 ms） |
| Breath | 600 ms 往返 · Standard | "应立即到位"红灯光晕；`LocalReduceMotion` 为 true 时静止在 0.25 |
| fastSpatial | M3 0.9 / 1400（约 140 ms 静止） | 条的展开 / 折叠高度、底栏红灯横移 |
| defaultSpatial | M3 0.9 / 700（约 190 ms 静止） | 条在栏位间移动、被挤开 |
| defaultEffects | M3 1.0 / 1600（约 115 ms 静止） | 灯色、底栏着色 |
| SectionOffset / PressedScale / StaggerStep | 16dp / 0.97 / 40 ms | 切页位移 / 按下缩放 / 稀有时刻逐行延迟 |

- 分区切换 shared-axis：新页按标签方向滑入并淡入（Enter 档，同曲线）；旧页只淡出（Exit 档，不带位移）。藏青板面是常驻背板（`BoardBackdrop` + `LocalBoardBackdrop`），放在切页动画之外，按 fastSpatial 弹簧变高变矮；板上内容按背板动画高度裁剪、随板面揭开，不参与切页的位移与淡出淡入。
- `LocalReduceMotion` 由 `AirShiftTheme` 提供，实时跟随系统 `ANIMATOR_DURATION_SCALE`（与 Compose 自身同源）；有限时长动画与弹簧由 Compose 自动缩放，token 只对无限循环、延迟与位移型转场做降级。
- 时长是 0.11.1 真机手调值（在 emil / ui-animation 区间内），不吸附 M3 时长网格；空间弹簧接口带 `visibilityThreshold`，弹簧在肉眼看不见时就停。
- 触控反馈：所有可按元素共用 `PressIndication`（`ui/theme/AirShiftIndication.kt`）——按钮 / 小按钮 / 底栏项在 draw 阶段缩到 0.97，按下 fast effects 即时、抬手 fast spatial 回弹；整宽信息条只着色（主文字色 6%）不缩放。M3 ripple 已关闭（`LocalRippleConfiguration = null`）。

## 系统栏与主题

- `enableEdgeToEdge(statusBarStyle = dark(TRANSPARENT), navigationBarStyle = auto(TRANSPARENT, TRANSPARENT))`：状态栏图标恒为浅色（板面在下），导航栏跟随底栏。
- `Scaffold(contentWindowInsets = 0)`；板面内部处理状态栏 inset，底栏 `navigationBarsPadding()`。
- `values/themes.xml` 与 `values-night/themes.xml` 只给窗口底色，避免启动闪白。

## 小组件

固定藏青板面（不随系统深色切换，浅深壁纸都可读）：头行 · VIP 灯 / 倒计时 + 到位时间 + 72×40dp 描边"完成" / 板面行线 / 两行航段（3dp 夹条）。空态（无排班 / 全部完成）保留板头"航勤智排 · 日期"与板脚提示，标题居中。颜色在 `res/values/colors.xml` 镜像 palette；文案全部在 `strings.xml`；`@font/barlow_*` 由 launcher 进程解析（真机 OriginOS 已验证）。没有装饰层。

## 不做的事

胶囊 chip、灰色骨架、渐变、多级阴影、浮动圆形按钮、彩色左边条当作卡片装饰（夹条是进程单的实物部件，只出现在信息条上）、纯黑背景、图标里混用填充与线性。
