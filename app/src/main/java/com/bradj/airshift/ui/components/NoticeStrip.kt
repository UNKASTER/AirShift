package com.bradj.airshift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens

/**
 * 通知条：一条提示（解析警告、精确闹钟未开、连接结果、刷新状态）。
 * 多行文字逐行列出；可选一个文字操作。不用彩色左边条，语气在底与图标上。
 */
@Composable
fun NoticeStrip(
    lines: List<String>,
    modifier: Modifier = Modifier,
    tone: NoticeTone = NoticeTone.Warning,
    title: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AirShiftTokens.colors
    val shape = RoundedCornerShape(AirShiftRadius.Strip)
    val warning = tone == NoticeTone.Warning
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (warning) c.estimateSoft else c.strip)
            .then(if (warning) Modifier else Modifier.border(1.dp, c.rule, shape))
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (warning) LinearIcons.Alert else LinearIcons.Clock,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (warning) c.estimate else c.hint,
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.labelLarge, color = c.ink)
            }
            lines.distinct().forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
            }
        }
        if (actionText != null && onAction != null) {
            Spacer(Modifier.width(AirShiftSpacing.S))
            TextButton(
                onClick = onAction,
                colors = ButtonDefaults.textButtonColors(contentColor = if (warning) c.estimate else c.arrivalText),
            ) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
