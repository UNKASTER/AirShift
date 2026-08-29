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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.components.formatEpoch

/** 设置页：原 SettingsDialog 的全部功能平铺为独立页面。 */
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
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("MUC 通知读取", fontWeight = FontWeight.Bold)
                    Text(if (notificationAccessGranted) "状态：已授权" else "状态：未授权")
                    Text(
                        "最近成功识别：${lastSuccessfulRecognitionEpochMillis?.formatEpoch("MM-dd HH:mm") ?: "暂无"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    lastProcessingResult?.let { result ->
                        Text(result, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = onOpenNotificationAccessSettings) {
                        Text(if (notificationAccessGranted) "管理通知读取权限" else "授予通知读取权限")
                    }
                }
            }
        }
        item {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(20) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("姓名") },
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    connectionMessage = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("飞常准 API Key") },
                supportingText = { Text("由你手动输入，使用 Android Keystore + AES-GCM 加密保存") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
        }
        connectionMessage?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    ) { Text("清除 API Key") }
                }
            }
        }
        item {
            Button(
                onClick = { onSave(name.trim(), apiKey.trim()) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = name.trim().length >= 2 && !isTesting,
            ) { Text("保存") }
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}
