package com.bradj.airshift.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.bradj.airshift.R

/**
 * 字体：Barlow（SIL OFL 1.1，jpt/barlow ≥ 1.403，带 tabular figures）。
 *
 * - [Text]：所有 Latin 字母与数字的正文字体；中文字形由系统 CJK 字体逐字回落，
 *   因此 "机位 355" 里的数字与字母始终是 Barlow，汉字仍是系统字体。
 * - [Board]：Barlow Semi Condensed，只用于板面大数字、时钟、航班号、机位号，
 *   与航显屏的紧排一致。
 */
object AirShiftFonts {
    val Text: FontFamily = FontFamily(
        Font(R.font.barlow_regular, FontWeight.Normal),
        Font(R.font.barlow_medium, FontWeight.Medium),
        Font(R.font.barlow_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_bold, FontWeight.Bold),
    )

    val Board: FontFamily = FontFamily(
        Font(R.font.barlow_semicondensed_semibold, FontWeight.SemiBold),
        Font(R.font.barlow_semicondensed_bold, FontWeight.Bold),
    )
}
