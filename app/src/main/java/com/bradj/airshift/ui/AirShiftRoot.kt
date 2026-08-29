package com.bradj.airshift.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.theme.NavyGrey

enum class DutySection {
    ALL,
    CURRENT,
    SETTINGS,
}

/**
 * 根布局：Scaffold + 标准三段式底部导航（全部执勤 / 当前执勤 / 设置）。
 * 三个 Tab 样式统一，当前 Tab 以东航红强调。
 */
@Composable
fun AirShiftRoot(
    section: DutySection,
    onSectionSelected: (DutySection) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                val itemColors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = NavyGrey,
                    unselectedTextColor = NavyGrey,
                )
                NavigationBarItem(
                    selected = section == DutySection.ALL,
                    onClick = { onSectionSelected(DutySection.ALL) },
                    icon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "全部执勤") },
                    label = { Text("全部执勤") },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = section == DutySection.CURRENT,
                    onClick = { onSectionSelected(DutySection.CURRENT) },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "当前执勤") },
                    label = { Text("当前执勤") },
                    colors = itemColors,
                )
                NavigationBarItem(
                    selected = section == DutySection.SETTINGS,
                    onClick = { onSectionSelected(DutySection.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "设置") },
                    label = { Text("设置") },
                    colors = itemColors,
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(padding)
        }
    }
}
