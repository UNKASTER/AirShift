package com.bradj.airshift.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
class BoardBackdropState {
    /** 当前页板面的实际高度（含状态栏），由 BoardHeader 的 onSizeChanged 写入。 */
    var heightPx: Int by mutableIntStateOf(0)

    /** 背板动画中的高度，由 BoardBackdrop 每帧写入；BoardHeader 用它裁剪自己的绘制。 */
    var animatedHeightPx: Int by mutableIntStateOf(0)
}

val LocalBoardBackdrop = staticCompositionLocalOf<BoardBackdropState?> { null }

/** 常驻背板：首次报高直接落位（冷启动不"长出来"），之后用 fast spatial 弹簧跟随；高度只在 draw 阶段读，不触发布局。 */
@Composable
fun BoardBackdrop(state: BoardBackdropState, modifier: Modifier = Modifier) {
    val c = AirShiftTokens.colors
    var settled by remember { mutableStateOf(false) }
    val height by animateIntAsState(
        targetValue = state.heightPx,
        animationSpec = if (settled) AirShiftMotion.fastSpatial(visibilityThreshold = 1) else snap(),
        label = "boardBackdrop",
    )
    LaunchedEffect(state.heightPx) {
        if (state.heightPx > 0) settled = true
    }
    // 把动画值同步给裁剪方。SideEffect 在每次重组之后写，与 draw 同一帧。
    SideEffect { state.animatedHeightPx = height }
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind { drawRect(color = c.board, size = Size(size.width, height.toFloat())) },
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
                            .onSizeChanged { backdrop.heightPx = it.height }
                            // 只画到背板已经长到的高度：内容随板面揭开，不落在底色上。
                            .drawWithContent {
                                val visible = backdrop.animatedHeightPx.toFloat()
                                clipRect(bottom = minOf(size.height, visible)) { this@drawWithContent.drawContent() }
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
