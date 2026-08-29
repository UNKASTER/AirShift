package com.bradj.airshift.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AmberAccent
import com.bradj.airshift.ui.theme.ChangeOrange

/** 登机口/机位变更的最小提醒元素：琥珀小圆点 + “变更”字样。 */
@Composable
fun ChangeIndicator(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(AmberAccent, CircleShape),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            "变更",
            style = MaterialTheme.typography.labelSmall,
            color = ChangeOrange,
            fontWeight = FontWeight.Bold,
        )
    }
}
