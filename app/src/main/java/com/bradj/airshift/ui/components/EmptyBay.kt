package com.bradj.airshift.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens

/**
 * 空栏位：没有排班 / 今日全部完成 / 未匹配班组。
 * 图标 + 标题 + 一句说明 + 可选的单个操作。
 */
@Composable
fun EmptyBay(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AirShiftTokens.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = AirShiftSpacing.L, vertical = AirShiftSpacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(32.dp), tint = c.hint)
        Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink, textAlign = TextAlign.Center)
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = c.inkSecondary,
            textAlign = TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(AirShiftSpacing.S))
            val interaction = remember { MutableInteractionSource() }
            Button(
                onClick = onAction,
                interactionSource = interaction,
                modifier = Modifier.fillMaxWidth().height(48.dp).indication(interaction, LocalIndication.current),
                shape = RoundedCornerShape(AirShiftRadius.Button),
                colors = ButtonDefaults.buttonColors(containerColor = c.departure, contentColor = Color.White),
            ) {
                Text(actionText, style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
