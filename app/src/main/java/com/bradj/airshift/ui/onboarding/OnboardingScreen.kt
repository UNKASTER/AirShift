package com.bradj.airshift.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.bradj.airshift.ui.components.BoardClock
import com.bradj.airshift.ui.components.boardDateText
import com.bradj.airshift.ui.theme.AirShiftRadius
import com.bradj.airshift.ui.theme.AirShiftSpacing
import com.bradj.airshift.ui.theme.AirShiftTokens
import com.bradj.airshift.ui.theme.NumericSmall
import java.time.LocalDateTime

private const val NAME_MAX_LENGTH = 20
private const val NAME_MIN_LENGTH = 2

/** 首启姓名页：整屏板面，中间一条白色信息条承载姓名输入。校验规则不变（去空格后 2–20 字）。 */
@Composable
fun OnboardingScreen(now: LocalDateTime, onSave: (String) -> Unit) {
    val c = AirShiftTokens.colors
    var name by rememberSaveable { mutableStateOf("") }
    val valid = name.trim().length >= NAME_MIN_LENGTH
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.board)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            BoardClock(now = now, dateText = now.toLocalDate().boardDateText())
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.departure),
            )
            Spacer(Modifier.width(12.dp))
            Text("航勤智排", style = MaterialTheme.typography.displaySmall, color = c.onBoard)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "中国东方航空 · 地面服务保障助手",
            style = MaterialTheme.typography.bodyMedium,
            color = c.onBoardSecondary,
        )
        Spacer(Modifier.height(AirShiftSpacing.L))
        Text(
            "从多人排班截图或 Excel 里，只留下分配给你的进港、出港和过站任务，按时间排成一条队列。",
            style = MaterialTheme.typography.bodyLarge,
            color = c.onBoardSecondary,
        )
        Spacer(Modifier.height(AirShiftSpacing.L))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(AirShiftRadius.Strip))
                .background(c.strip)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("排班表中的姓名", style = MaterialTheme.typography.bodySmall, color = c.hint)
                Text("${name.length} / $NAME_MAX_LENGTH", style = NumericSmall.copy(fontSize = 12.sp), color = c.hint)
            }
            TextField(
                value = name,
                onValueChange = { name = it.take(NAME_MAX_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                textStyle = MaterialTheme.typography.titleLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = c.departure,
                    focusedTextColor = c.ink,
                    unfocusedTextColor = c.ink,
                ),
            )
            Text(
                "去除首尾空格后 2–20 个字符；只保存在本机，可在设置中修改。",
                style = MaterialTheme.typography.bodySmall,
                color = c.hint,
            )
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { onSave(name.trim()) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(AirShiftRadius.Button),
            enabled = valid,
            colors = ButtonDefaults.buttonColors(
                containerColor = c.departure,
                contentColor = Color.White,
                disabledContainerColor = Color.White.copy(alpha = 0.12f),
                disabledContentColor = c.onBoardTertiary,
            ),
        ) { Text("保存并开始使用", style = MaterialTheme.typography.titleMedium) }
        Spacer(Modifier.height(AirShiftSpacing.S))
        Text(
            "不需要账号，也不会上传排班原件。",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = c.onBoardTertiary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

private val Int.sp get() = androidx.compose.ui.unit.TextUnit(toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)
