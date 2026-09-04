package com.bradj.airshift.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import com.bradj.airshift.ui.theme.NumericSmall

/**
 * 栏位标题：像进程单架上的分栏标签——小字 + 可选数量 + 向右延伸的一条线。
 * 用于"当前 / 接下来 / 已完成"与日历页的月份。
 */
@Composable
fun BayTitle(title: String, modifier: Modifier = Modifier, count: Int? = null, testTag: String? = null) {
    val c = AirShiftTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, top = 6.dp, bottom = 2.dp)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = c.hint)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text(count.toString(), style = NumericSmall.copy(fontSize = 13.sp), color = c.inkSecondary)
        }
        Spacer(Modifier.width(AirShiftSpacing.S))
        HorizontalDivider(modifier = Modifier.weight(1f), thickness = 1.dp, color = c.rule)
    }
}
