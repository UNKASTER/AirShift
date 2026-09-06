package com.bradj.airshift.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftMotion
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import com.bradj.airshift.ui.theme.BoardClock
import java.time.LocalDateTime

/** 板头右侧的实时钟与日期行。 */
@Composable
fun BoardClock(now: LocalDateTime, dateText: String?, modifier: Modifier = Modifier) {
    val c = AirShiftTokens.colors
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        OdometerText(text = now.formatClock(), style = BoardClock, color = c.onBoard)
        if (dateText != null) {
            Spacer(Modifier.height(2.dp))
            Text(dateText, style = MaterialTheme.typography.bodySmall, color = c.onBoardSecondary, maxLines = 1)
        }
    }
}

/**
 * 板面底色的宿主。[AirShiftApp] 提供它之后，四页的 [BoardHeader] 不再各自画藏青底，而是把测得的高度报给它，
 * 由一块放在切页动画之外、之下的常驻背板按弹簧变高 / 变矮；板上的内容按 [animatedHeightPx] 裁剪，
 * 随板面长高而揭开，不会画到背板之外的底色上。没有宿主时（测试、Onboarding）[BoardHeader] 照旧自己画底。
 */
@Stable
class BoardBackdropState {
    /** 当前页板面的实际高度（含状态栏），由 BoardHeader 的 onSizeChanged 写入。 */
    var heightPx: Int by mutableIntStateOf(0)

    /**
     * 背板动画中的高度。Animatable 本身就是快照状态（snapshot state）：
     * [BoardBackdrop] 与 [BoardHeader] 都只在各自的 draw 阶段读它，逐帧只重绘、不重组。
     */
    internal val animatedHeight = Animatable(0, Int.VectorConverter)

    /** 供 [BoardHeader] 的 draw 读取；等价于 [animatedHeight] 的 `.value`。 */
    val animatedHeightPx: Int get() = animatedHeight.value
}

val LocalBoardBackdrop = staticCompositionLocalOf<BoardBackdropState?> { null }

/**
 * 常驻背板：高度不参与布局（背板用 drawBehind 画，板头用 drawWithContent 裁剪）。
 * 首次报高直接 `snapTo` 落位（冷启动不"长出来"）；之后每次报高都用 fast spatial 弹簧 `animateTo`，
 * 从当前值、当前速度接续——重启的 [LaunchedEffect] 会打断上一段，这正是弹簧该有的行为。
 * [BoardBackdrop] 与 [BoardHeader] 都只在 draw 里读 [BoardBackdropState.animatedHeight] 这一个
 * 快照状态：动画的每一帧只让两处重绘，两者都不会因为这支弹簧而重组。
 */
@Composable
fun BoardBackdrop(state: BoardBackdropState, modifier: Modifier = Modifier) {
    val c = AirShiftTokens.colors
    val target = state.heightPx
    LaunchedEffect(target) {
        if (state.animatedHeight.value == 0) {
            state.animatedHeight.snapTo(target)
        } else {
            val spec = AirShiftMotion.fastSpatial(visibilityThreshold = AirShiftMotion.IntPxThreshold)
            state.animatedHeight.animateTo(target, spec)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(color = c.board, size = Size(size.width, state.animatedHeight.value.toFloat()))
            },
    )
}

/**
 * 藏青板面：贯通到状态栏之下的页顶区域。
 * 顶行是分区名（可带副标题）与右侧实时钟；[content] 放板面主体（倒计时、班次摘要）；
 * [footer] 是板脚一行，上方有一条板面行线。所有子项默认用板面文字色。
 */
@Composable
fun BoardHeader(
    title: String,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    dateText: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    footer: (@Composable RowScope.() -> Unit)? = null,
) {
    val c = AirShiftTokens.colors
    CompositionLocalProvider(LocalContentColor provides c.onBoard) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    when (val backdrop = LocalBoardBackdrop.current) {
                        null -> Modifier.background(c.board)
                        else -> Modifier
                            // 切页的 70 ms 淡出期间旧板头仍在，只要它的尺寸不变就不会再报高；两页同时改高度的情况极少，若出现以后报的为准。
                            .onSizeChanged { backdrop.heightPx = it.height }
                            .drawWithContent {
                                val visible = backdrop.animatedHeightPx.toFloat()
                                if (visible <= 0f) {
                                    // 背板还没收到本页的高度（首次挂载 / 旋转后的第一帧）：先自己画底、不裁剪，
                                    // 下一帧背板 snap 到位后再交给它——两处同色，看不出接缝。
                                    drawRect(color = c.board)
                                    drawContent()
                                } else {
                                    // 只画到背板已经长到的高度：内容随板面揭开，不落在底色上。
                                    clipRect(bottom = minOf(size.height, visible)) {
                                        this@drawWithContent.drawContent()
                                    }
                                }
                            }
                    },
                )
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = AirShiftSpacing.M)
                .testTag("board_header"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                    Text(title, style = MaterialTheme.typography.titleSmall, color = c.onBoard)
                    if (subtitle != null) {
                        Spacer(Modifier.width(AirShiftSpacing.S))
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onBoardSecondary,
                            maxLines = 1,
                        )
                    }
                }
                BoardClock(now = now, dateText = dateText)
            }
            content?.invoke(this)
            if (footer != null) {
                HorizontalDivider(thickness = 1.dp, color = c.boardRule)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = footer,
                )
            }
        }
    }
}
