package com.bradj.airshift.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.components.QuietCard
import com.bradj.airshift.ui.components.StatusDot
import com.bradj.airshift.ui.components.formatEpoch
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.SuccessGreen
import com.bradj.airshift.ui.theme.TextPrimary
import com.bradj.airshift.ui.theme.TextSecondary

/** 设置页：卡片化分区；保存为东航红主按钮，清除 API Key 降级为红色文字按钮。 */
@Composable
fun SettingsScreen(
    currentName: String,
    currentApiKey: String,
    hasStoredApiKey: Boolean,
    notificationAccessGranted: Boolean,
    lastSuccessfulRecognitionEpochMillis: Long?,
    lastProcessingResult: String?,
    onOpenNotificationAccessSettings: () -> Unit,
    onSave: (String, String) -> Unit,
    onClearApiKey: () -> Unit,
    onTestConnection: (String, (Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }
    // The plaintext API key must never enter Android's saved-instance-state bundle.
    var apiKey by remember(currentApiKey) { mutableStateOf(currentApiKey) }
    var isTesting by remember { mutableStateOf(false) }
    var connectionMessage by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(all = AirShiftSpacing.M),
        verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.M),
    ) {
        item {
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
        }
        item {
            MucNotificationCard(
                notificationAccessGranted = notificationAccessGranted,
                lastSuccessfulRecognitionEpochMillis = lastSuccessfulRecognitionEpochMillis,
                lastProcessingResult = lastProcessingResult,
                onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
            )
        }
        item {
            SettingsSection(title = "个人信息") {
                SettingsTextField(
                    value = name,
                    onValueChange = { name = it.take(20) },
                    label = "姓名",
                )
            }
        }
        item {
            SettingsSection(title = "飞常准 API Key") {
                SettingsTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        connectionMessage = null
                    },
                    label = "API Key",
                    supportingText = "由你手动输入，使用 Android Keystore + AES-GCM 加密保存",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                connectionMessage?.let { message ->
                    Spacer(Modifier.height(AirShiftSpacing.S))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                }
                Spacer(Modifier.height(AirShiftSpacing.S))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            isTesting = true
                            connectionMessage = null
                            onTestConnection(apiKey.trim()) { result ->
                                isTesting = false
                                connectionMessage = result.fold(
                                    onSuccess = { "连接成功" },
                                    onFailure = { error -> "连接失败：${error.message ?: "请稍后重试"}" },
                                )
                            }
                        },
                        enabled = apiKey.isNotBlank() && !isTesting,
                    ) { Text(if (isTesting) "测试中…" else "测试连接") }
                    Spacer(Modifier.weight(1f))
                    if (hasStoredApiKey || apiKey.isNotBlank()) {
                        TextButton(
                            onClick = {
                                onClearApiKey()
                                apiKey = ""
                                connectionMessage = "API Key 已清除"
                            },
                            enabled = !isTesting,
                        ) {
                            Text(
                                "清除 API Key",
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = { onSave(name.trim(), apiKey.trim()) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = CircleShape,
                enabled = name.trim().length >= 2 && !isTesting,
            ) { Text("保存", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        }
        item { Spacer(Modifier.height(AirShiftSpacing.L)) }
    }
}

/** 设置分区卡：白色大卡 + 分区标题。 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    QuietCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(AirShiftSpacing.M)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Spacer(Modifier.height(AirShiftSpacing.M))
            content()
        }
    }
}

/** MUC 通知读取状态卡："已授权"用绿色小圆点 + 文字。 */
@Composable
private fun MucNotificationCard(
    notificationAccessGranted: Boolean,
    lastSuccessfulRecognitionEpochMillis: Long?,
    lastProcessingResult: String?,
    onOpenNotificationAccessSettings: () -> Unit,
) {
    SettingsSection(title = "MUC 通知读取") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(
                color = if (notificationAccessGranted) SuccessGreen else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(8.dp),
            )
            Spacer(Modifier.size(AirShiftSpacing.S))
            Text(
                if (notificationAccessGranted) "已授权" else "未授权",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (notificationAccessGranted) SuccessGreen else MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(AirShiftSpacing.S))
        Text(
            "最近成功识别：${lastSuccessfulRecognitionEpochMillis?.formatEpoch("MM-dd HH:mm") ?: "暂无"}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
        )
        lastProcessingResult?.let { result ->
            Text(result, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
        }
        TextButton(onClick = onOpenNotificationAccessSettings) {
            Text(if (notificationAccessGranted) "管理通知读取权限" else "授予通知读取权限")
        }
    }
}

/** 浅灰填充圆角输入框（无下划线指示条）。 */
@Composable
private fun SettingsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        supportingText = supportingText?.let { text -> { Text(text, color = TextSecondary) } },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = MaterialTheme.shapes.small,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
