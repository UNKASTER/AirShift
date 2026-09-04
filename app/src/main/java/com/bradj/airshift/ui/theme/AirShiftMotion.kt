package com.bradj.airshift.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * 动效 token。有限时长的动画由 Compose 自动跟随系统"动画时长比例"；
 * 无限循环的呼吸灯需要用 [rememberAnimatorScaleEnabled] 显式门控。
 */
object AirShiftMotion {
    /** 灯色、选中态、按下缩放。 */
    const val QuickMs: Int = 150

    /** 展开 / 折叠、淡入淡出、页面切换。 */
    const val StandardMs: Int = 250

    /** 信息条在栏位间移动。 */
    const val EmphasizedMs: Int = 400

    /** 时钟与倒计时逐位翻牌。 */
    const val FlipMs: Int = 280

    /** fade-through：出场 90 ms，入场 210 ms。 */
    const val FadeOutMs: Int = 90
    const val FadeInMs: Int = 210

    /** 减速曲线（Material emphasized decelerate）。 */
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 标准曲线（Material standard）。 */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
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
