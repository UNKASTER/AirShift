package com.bradj.airshift.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens

/**
 * 通知条：琥珀底的一条提示（解析警告、精确闹钟未开、连接结果）。
 * 多行文字逐行列出；可选一个文字操作。不用彩色左边条，颜色在底与图标上。
 */
@Composable
fun NoticeStrip(
    lines: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AirShiftTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AirShiftRadius.Strip))
            .background(c.estimateSoft)
            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = LinearIcons.Alert,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = c.estimate,
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
                colors = ButtonDefaults.textButtonColors(contentColor = c.estimate),
            ) {
                Text(actionText, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
