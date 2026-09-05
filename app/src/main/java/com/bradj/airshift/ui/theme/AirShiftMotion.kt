package com.bradj.airshift.ui.theme

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 动效 token。两类驱动，各司其职：
 *
 * - **tween**（本对象的 `exit / content / enter / flip`）：一次性、方向确定的入退场与"机械"动作。
 *   曲线一律显式；`EmphasizedDecelerate` 第一帧就走约 30%，这是"第一帧就动"的来源。
 * - **弹簧**（`fastSpatial / defaultSpatial / slowSpatial / defaultEffects / fastEffects`）：会被反复、
 *   快速重触发或需要速度连续的状态动画。刚度 / 阻尼镜像 Material 3 standard MotionScheme 的值——material3 1.4.0 的
 *   `MotionScheme` 与 `MaterialTheme.motionScheme` 是 internal，应用读不到，只能在这里镜像；M3 组件内部用的是同一组值。
 *   这里是项目里弹簧常量的唯一出处。
 *
 * 有限时长动画与弹簧由 Compose 自动跟随系统"动画时长比例"（比例为 0 时直接落终值）；
 * 只有无限循环、装饰性延迟与位移型转场需要看 [LocalReduceMotion]。
 */
object AirShiftMotion {
    /** 旧内容退场：旧页、折叠前的内容、移除的条。 */
    const val ExitMs: Int = 70

    /** 新内容淡入：展开后的内容、新增的条。 */
    const val ContentMs: Int = 120

    /** 新页滑入并淡入。 */
    const val EnterMs: Int = 180

    /** 翻牌：位移与新数字淡入。 */
    const val FlipMs: Int = 220

    /** 翻牌：旧数字淡出，比入场快。 */
    const val FlipExitMs: Int = 130

    /** 呼吸灯往返半周期。 */
    const val BreathMs: Int = 600

    /** 展开内容淡入相对容器起步的延迟：容器先动，内容跟上。 */
    const val RevealDelayMs: Int = 35

    /** 稀有时刻（下班、首次进入）逐行入场的延迟步长；reduce-motion 时为 0。 */
    const val StaggerStepMs: Int = 40

    /** 分区切换的横向位移：按底栏标签的左右方向。 */
    val SectionOffset: Dp = 16.dp

    /** 按下缩放：可见但不夸张（0.96–0.98 档）。 */
    const val PressedScale: Float = 0.97f

    /** 减速曲线（Material emphasized decelerate）：起步即全速，收尾柔和。入场与翻牌用它。 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 对称曲线（Material standard）。只给原地往返的呼吸用；入退场一律不用——它起步慢。 */
    val Standard: Easing = FastOutSlowInEasing

    fun <T> exit(): TweenSpec<T> = tween(durationMillis = ExitMs, easing = LinearEasing)

    fun <T> content(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = ContentMs, delayMillis = delayMillis, easing = LinearEasing)

    fun <T> enter(delayMillis: Int = 0): TweenSpec<T> =
        tween(durationMillis = EnterMs, delayMillis = delayMillis, easing = EmphasizedDecelerate)

    fun <T> flip(): TweenSpec<T> = tween(durationMillis = FlipMs, easing = EmphasizedDecelerate)

    fun <T> flipExit(): TweenSpec<T> = tween(durationMillis = FlipExitMs, easing = LinearEasing)

    // ---- 弹簧：镜像 Material 3 standard MotionScheme（material3 1.4.0 未公开该 API）。只在这里出现这组数字。----

    /** 空间属性（尺寸 / 位移）的阻尼比：M3 standard 的 0.9，过冲约 0.15%，肉眼不可见但速度曲线比临界阻尼更有重量。 */
    private const val SPATIAL_DAMPING = 0.9f

    /** 效果属性（颜色 / 透明度 / 缩放）的阻尼比：临界阻尼，不过冲。 */
    private const val EFFECTS_DAMPING = 1f

    private const val FAST_SPATIAL_STIFFNESS = 1400f
    private const val DEFAULT_SPATIAL_STIFFNESS = 700f
    private const val SLOW_SPATIAL_STIFFNESS = 300f
    private const val FAST_EFFECTS_STIFFNESS = 3800f
    private const val DEFAULT_EFFECTS_STIFFNESS = 1600f

    /**
     * 条展开 / 折叠高度、底栏红灯横移、抬手回弹、分段器填充块：约 140 ms 静止。
     * 空间属性（Dp / IntOffset / IntSize / Rect）传对应的 `VisibilityThreshold`，弹簧在肉眼看不见时就停
     * （没有它，像素级弹簧会跑到 0.01 px 才"结束"，期间 LazyColumn 每帧都在重放置）；Float 型（缩放）不传。
     */
    fun <T> fastSpatial(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = SPATIAL_DAMPING,
            stiffness = FAST_SPATIAL_STIFFNESS,
            visibilityThreshold = visibilityThreshold,
        )

    /** 条在栏位间移动、被展开的条挤开：距离长，约 190 ms 静止。 */
    fun <T> defaultSpatial(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = SPATIAL_DAMPING,
            stiffness = DEFAULT_SPATIAL_STIFFNESS,
            visibilityThreshold = visibilityThreshold,
        )

    /** 整块板面变形等大位移：约 300 ms 静止。 */
    fun <T> slowSpatial(visibilityThreshold: T? = null): SpringSpec<T> =
        spring(
            dampingRatio = SPATIAL_DAMPING,
            stiffness = SLOW_SPATIAL_STIFFNESS,
            visibilityThreshold = visibilityThreshold,
        )

    /** 颜色 / 透明度这类非空间属性：灯色、底栏着色、选中态底色，约 115 ms 静止。 */
    fun <T> defaultEffects(): SpringSpec<T> =
        spring(dampingRatio = EFFECTS_DAMPING, stiffness = DEFAULT_EFFECTS_STIFFNESS)

    /** 按下反馈：约 50 ms 到位，"指按下去就有"。 */
    fun <T> fastEffects(): SpringSpec<T> = spring(dampingRatio = EFFECTS_DAMPING, stiffness = FAST_EFFECTS_STIFFNESS)
}

/**
 * 系统"移除动画"（`ANIMATOR_DURATION_SCALE == 0`）时为 true。由 [AirShiftTheme] 提供，随系统设置实时更新。
 * Compose 自己也监听同一个设置来缩放有限时长动画；这里只给无限循环、装饰性延迟与位移型转场做降级判断。
 * `TRANSITION_ANIMATION_SCALE` 只作用于 Activity / 窗口转场，本应用没有，故不读。
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
internal fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    val scale by produceState(initialValue = remember(resolver) { animatorDurationScale(resolver) }, resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = animatorDurationScale(resolver)
            }
        }
        resolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer,
        )
        awaitDispose { resolver.unregisterContentObserver(observer) }
    }
    return scale == 0f
}

private fun animatorDurationScale(resolver: ContentResolver): Float =
    Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
