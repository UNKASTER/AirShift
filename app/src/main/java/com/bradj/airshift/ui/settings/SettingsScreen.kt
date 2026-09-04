package com.bradj.airshift.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bradj.airshift.model.shift.ShiftBusPlan
import com.bradj.airshift.ui.components.BoardHeader
import com.bradj.airshift.ui.components.NoticeStrip
import com.bradj.airshift.ui.components.PinnedActionBar
import com.bradj.airshift.ui.components.StatusDot
import com.bradj.airshift.ui.components.boardDateText
import com.bradj.airshift.ui.components.formatEpoch
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import java.time.LocalDateTime

private const val NAME_MAX_LENGTH = 20
private const val NAME_MIN_LENGTH = 2

/** 设置页：紧凑板头 + 分组信息条；保存钉在底部。 */
@Composable
fun SettingsScreen(
    currentName: String,
    currentApiKey: String,
    hasStoredApiKey: Boolean,
    notificationAccessGranted: Boolean,
    lastSuccessfulRecognitionEpochMillis: Long?,
    lastProcessingResult: String?,
    shiftGroupId: Int?,
    shiftGroupAutoDetected: Boolean,
    shiftGroupOptions: List<Int>,
    shiftReportMarginMinutes: Int,
    now: LocalDateTime,
    onShiftGroupSelected: (Int?) -> Unit,
    onShiftReportMarginSelected: (Int) -> Unit,
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

    Column(modifier = modifier.fillMaxSize()) {
        BoardHeader(
            title = "设置",
            subtitle = listOfNotNull(currentName, shiftGroupId?.let { "第 $it 组" }).joinToString(" · "),
            now = now,
            dateText = now.toLocalDate().boardDateText(),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = AirShiftSpacing.M,
                end = AirShiftSpacing.M,
                top = 12.dp,
                bottom = AirShiftSpacing.M,
            ),
            verticalArrangement = Arrangement.spacedBy(AirShiftSpacing.S),
        ) {
            item(key = "muc") {
                MucSection(
                    notificationAccessGranted = notificationAccessGranted,
                    lastSuccessfulRecognitionEpochMillis = lastSuccessfulRecognitionEpochMillis,
                    lastProcessingResult = lastProcessingResult,
                    onOpenNotificationAccessSettings = onOpenNotificationAccessSettings,
                )
            }
            item(key = "calendar") {
                ShiftCalendarSection(
                    shiftGroupId = shiftGroupId,
                    autoDetected = shiftGroupAutoDetected,
                    options = shiftGroupOptions,
                    reportMarginMinutes = shiftReportMarginMinutes,
                    onGroupSelected = onShiftGroupSelected,
                    onMarginSelected = onShiftReportMarginSelected,
                )
            }
            item(key = "profile") {
                SectionStrip(title = "个人信息") {
                    SettingsField(
                        value = name,
                        onValueChange = { name = it.take(NAME_MAX_LENGTH) },
                        label = "排班表中的姓名",
                    )
                    HintLine("修改后只影响之后的导入，已保存的排班需要重新导入。")
                }
            }
            item(key = "api") {
                ApiKeySection(
                    apiKey = apiKey,
                    hasStoredApiKey = hasStoredApiKey,
                    isTesting = isTesting,
                    connectionMessage = connectionMessage,
                    onApiKeyChange = {
                        apiKey = it
                        connectionMessage = null
                    },
                    onTest = {
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
                    onClear = {
                        onClearApiKey()
                        apiKey = ""
                        connectionMessage = "API Key 已清除"
                    },
                )
            }
        }
        PinnedActionBar(
            text = "保存",
            onClick = { onSave(name.trim(), apiKey.trim()) },
            enabled = name.trim().length >= NAME_MIN_LENGTH && !isTesting,
            testTag = "settings_save",
        )
    }
}

/** 分组信息条：白底、1dp 线、10dp 圆角，标题在顶部。 */
@Composable
private fun SectionStrip(
    title: String,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val c = AirShiftTokens.colors
    val shape = RoundedCornerShape(AirShiftRadius.Strip)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.strip)
            .border(1.dp, c.rule, shape),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
            trailing?.invoke()
        }
        content()
    }
}

@Composable
private fun HintLine(text: String) {
    Text(
        text,
        modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = AirShiftTokens.colors.hint,
    )
}

@Composable
private fun SettingRow(label: String, value: String? = null) {
    val c = AirShiftTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
        if (value != null) {
            Text(value, style = MaterialTheme.typography.titleSmall, color = c.ink)
        }
    }
}

/** MUC 通知读取：状态灯 + 最近识别 + 权限入口。 */
@Composable
private fun MucSection(
    notificationAccessGranted: Boolean,
    lastSuccessfulRecognitionEpochMillis: Long?,
    lastProcessingResult: String?,
    onOpenNotificationAccessSettings: () -> Unit,
) {
    val c = AirShiftTokens.colors
    SectionStrip(
        title = "MUC 通知读取",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    color = if (notificationAccessGranted) c.ok else c.alert,
                    modifier = Modifier.width(8.dp).height(8.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (notificationAccessGranted) "已授权" else "未授权",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (notificationAccessGranted) c.ok else c.alert,
                )
            }
        },
    ) {
        HintLine(
            buildString {
                append("最近成功识别：${lastSuccessfulRecognitionEpochMillis?.formatEpoch("MM-dd HH:mm") ?: "暂无"}")
                if (lastProcessingResult != null) append("。$lastProcessingResult")
            },
        )
        OutlinedButton(
            onClick = onOpenNotificationAccessSettings,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp).height(44.dp),
            shape = RoundedCornerShape(AirShiftRadius.Small),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.ruleStrong),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = c.ink),
        ) {
            Text(
                if (notificationAccessGranted) "管理通知读取权限" else "授予通知读取权限",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** 排班日历分区：班组与到位余量。到位要求本身是硬规定，这里只调富余量。 */
@Composable
private fun ShiftCalendarSection(
    shiftGroupId: Int?,
    autoDetected: Boolean,
    options: List<Int>,
    reportMarginMinutes: Int,
    onGroupSelected: (Int?) -> Unit,
    onMarginSelected: (Int) -> Unit,
) {
    SectionStrip(title = "排班日历") {
        SettingRow(
            label = "我的班组",
            value = when {
                shiftGroupId == null -> "未匹配"
                autoDetected -> "第 $shiftGroupId 组 · 按姓名自动识别"
                else -> "第 $shiftGroupId 组 · 手动指定"
            },
        )
        if (!autoDetected) {
            FlowRow(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                options.forEach { option ->
                    SelectLamp(
                        text = "第 $option 组",
                        selected = option == shiftGroupId,
                        onClick = { onGroupSelected(option.takeIf { it != shiftGroupId }) },
                    )
                }
            }
            HintLine("导入一份带“候机早班/中班/夜班”的 Excel 后，应用会自动校正班组表。")
        }
        SettingRow(label = "到位余量")
        SegmentedLamps(
            options = ShiftBusPlan.REPORT_MARGIN_OPTIONS,
            selected = reportMarginMinutes,
            label = { minutes -> if (minutes == 0) "不留余量" else "$minutes 分钟" },
            onSelect = onMarginSelected,
        )
        HintLine("在“最晚到位时间”之前再留出的富余，越大则推荐更早一班班车。")
    }
}

/** 分段选择器：一条边框里的等宽格，选中格填板面色。 */
@Composable
private fun SegmentedLamps(
    options: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
) {
    val c = AirShiftTokens.colors
    val shape = RoundedCornerShape(AirShiftRadius.Small)
    Row(
        modifier = Modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, c.ruleStrong, shape),
    ) {
        options.forEachIndexed { index, option ->
            val on = option == selected
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .then(if (index > 0) Modifier.border(width = 0.dp, color = Color.Transparent) else Modifier)
                    .background(if (on) (if (c.isDark) c.arrival else c.board) else Color.Transparent)
                    .clickable(role = Role.RadioButton, onClick = { onSelect(option) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelLarge,
                    color = when {
                        on && c.isDark -> c.ground
                        on -> c.onBoard
                        else -> c.inkSecondary
                    },
                )
            }
        }
    }
}

/** 可选中的小灯：用于班组的手动指定。 */
@Composable
private fun SelectLamp(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = AirShiftTokens.colors
    val shape = RoundedCornerShape(AirShiftRadius.Small)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) c.board else c.neutralSoft)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) c.onBoard else c.inkSecondary,
        )
    }
}

@Composable
private fun ApiKeySection(
    apiKey: String,
    hasStoredApiKey: Boolean,
    isTesting: Boolean,
    connectionMessage: String?,
    onApiKeyChange: (String) -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit,
) {
    val c = AirShiftTokens.colors
    SectionStrip(
        title = "飞常准 API Key",
        trailing = {
            if (hasStoredApiKey) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(color = c.ok, modifier = Modifier.width(8.dp).height(8.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("已保存", style = MaterialTheme.typography.labelLarge, color = c.ok)
                }
            }
        },
    ) {
        SettingsField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = "API Key",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
        )
        connectionMessage?.let { message ->
            NoticeStrip(
                lines = listOf(message),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = onTest,
                enabled = apiKey.isNotBlank() && !isTesting,
                colors = ButtonDefaults.textButtonColors(contentColor = c.arrivalText),
            ) { Text(if (isTesting) "测试中…" else "测试连接", style = MaterialTheme.typography.labelLarge) }
            Spacer(Modifier.weight(1f))
            if (hasStoredApiKey || apiKey.isNotBlank()) {
                TextButton(
                    onClick = onClear,
                    enabled = !isTesting,
                    colors = ButtonDefaults.textButtonColors(contentColor = c.departureText),
                ) { Text("清除 API Key", style = MaterialTheme.typography.labelLarge) }
            }
        }
        HintLine("只作为请求头发送给飞常准，用 Android Keystore 加密保存在本机。")
    }
}

/** 输入框：条底色、1dp 线、8dp 圆角，无下划线指示条。 */
@Composable
private fun SettingsField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val c = AirShiftTokens.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        label = { Text(label) },
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        singleLine = true,
        shape = RoundedCornerShape(AirShiftRadius.Small),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = c.field,
            unfocusedContainerColor = c.field,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = c.departure,
            focusedLabelColor = c.inkSecondary,
            unfocusedLabelColor = c.hint,
            focusedTextColor = c.ink,
            unfocusedTextColor = c.ink,
        ),
    )
}
