package com.bradj.airshift.ui.components

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens

/**
 * 钉在页面底部（底栏之上）的主操作条：顶部一条线 + 52dp 红色主按钮。
 * 不随内容滚动，始终在拇指区；按下即时缩到 97%（fast effects），抬手用 fast spatial 弹簧回弹。
 */
@Composable
fun PinnedActionBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
) {
    val c = AirShiftTokens.colors
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = modifier.fillMaxWidth().background(c.ground)) {
        HorizontalDivider(thickness = 1.dp, color = c.rule)
        Button(
            onClick = onClick,
            enabled = enabled,
            interactionSource = interaction,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AirShiftSpacing.M, vertical = 10.dp)
                .height(52.dp)
                .indication(interaction, LocalIndication.current)
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier),
            shape = RoundedCornerShape(AirShiftRadius.Button),
            colors = ButtonDefaults.buttonColors(
                containerColor = c.departure,
                contentColor = Color.White,
                disabledContainerColor = c.neutralSoft,
                disabledContentColor = c.hint,
            ),
        ) {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}
