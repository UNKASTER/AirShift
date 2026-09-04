package com.bradj.airshift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
                .background(c.board)
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
