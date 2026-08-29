package com.bradj.airshift.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.CeaRedSoft
import com.bradj.airshift.ui.theme.OnCeaRedSoft

/** 特服小角标：仅标记“有特服”，浅红底小标签，不显示数量明细。 */
@Composable
fun SpecialServiceBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = CeaRedSoft,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            "特服",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = OnCeaRedSoft,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
