package com.bradj.airshift.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 动效 token。原则：第一帧就动、退场比入场快、尺寸与位移用无回弹弹簧（可中断、不慢起步）。
 * 有限时长的动画由 Compose 自动跟随系统"动画时长比例"；无限循环的呼吸灯需要用
 * [rememberAnimatorScaleEnabled] 显式门控。
 */
object AirShiftMotion {
    /** 灯色、底栏着色、按下缩放：立刻可见的反馈。 */
    const val QuickMs: Int = 120

    /** 旧内容退场（旧页、折叠前的内容）：比入场快。 */
    const val ExitMs: Int = 70

    /** 新内容入场（新页滑入、展开内容淡入）。 */
    const val EnterMs: Int = 180

    /** 展开内容淡入相对容器起步的延迟：容器先动，内容跟上。 */
    const val RevealDelayMs: Int = 35

    /** 时钟与倒计时逐位翻牌。 */
    const val FlipMs: Int = 220

    /** 分区切换的横向位移：按底栏标签的左右方向。 */
    val SectionOffset: Dp = 16.dp

    /** 减速曲线（Material emphasized decelerate）：起步即全速，收尾柔和。 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 尺寸与位移的弹簧刚度：临界阻尼，约 150 ms 视觉到位、200 ms 内静止。 */
    private const val SNAP_STIFFNESS = 1100f

    /**
     * 无回弹弹簧：条的展开 / 折叠、底栏红灯横移、条在栏位间移动。
     * 弹簧从当前速度接续，连续点击不会从头重来。
     */
    fun <T> snap(visibilityThreshold: T? = null): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = SNAP_STIFFNESS,
        visibilityThreshold = visibilityThreshold,
    )
}

/**
 * 系统"移除动画"（动画时长比例为 0）时返回 false。
 * 只在进入组合时读取一次；用户改设置后重新进入页面即可生效。
 */
@Composable
fun rememberAnimatorScaleEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
