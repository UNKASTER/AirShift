package com.bradj.airshift.ui.theme

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.R
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 时间列对齐依赖 Barlow 的 tabular figures：开启 tnum 后"1"必须明显向"0"的宽度靠拢
 * （实测 Barlow ≈0.98，Barlow Semi Condensed ≈0.93——该字体的 tabular 字形并非完全等宽）。
 * 逐位翻牌的倒计时不靠它，OdometerText 用固定位宽槽位兜底。
 */
@RunWith(AndroidJUnit4::class)
class FontFeaturesInstrumentedTest {
    /** 大字号 + 线性文本，避免逐字符像素取整把 1–2px 的差异放大成假阳性。 */
    private fun paintFor(fontRes: Int, features: String?) = Paint().apply {
        typeface = InstrumentationRegistry.getInstrumentation().targetContext.resources.getFont(fontRes)
        textSize = 200f
        isLinearText = true
        if (features != null) fontFeatureSettings = features
    }

    private fun assertTabular(fontRes: Int) {
        val proportional = paintFor(fontRes, null)
        val tabular = paintFor(fontRes, "tnum")
        val narrowDefault = proportional.measureText("1111") / proportional.measureText("0000")
        val narrowTabular = tabular.measureText("1111") / tabular.measureText("0000")
        assertTrue("默认字形里 1 应明显窄于 0（否则测不出特性是否生效）: $narrowDefault", narrowDefault < 0.8f)
        assertTrue("tnum 后 1 与 0 应接近等宽: $narrowTabular", narrowTabular > 0.9f)
    }

    @Test
    fun barlowSemiCondensedDigitsAreTabular() = assertTabular(R.font.barlow_semicondensed_bold)

    @Test
    fun barlowDigitsAreTabular() = assertTabular(R.font.barlow_semibold)
}
