package com.bradj.airshift.ui.theme

import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 倒计时与时间不跳动依赖 Barlow 的 tabular figures：
 * 开启 tnum 后，任意四位数字的宽度必须相同。
 */
@RunWith(AndroidJUnit4::class)
class FontFeaturesInstrumentedTest {
    private fun paintFor(fontRes: Int) = Paint().apply {
        typeface = InstrumentationRegistry.getInstrumentation().targetContext.resources.getFont(fontRes)
        textSize = 64f
        fontFeatureSettings = "tnum"
    }

    @Test
    fun barlowSemiCondensedDigitsAreTabular() {
        val paint = paintFor(R.font.barlow_semicondensed_bold)
        assertEquals(paint.measureText("0000"), paint.measureText("1111"), 0.5f)
        assertEquals(paint.measureText("0000"), paint.measureText("7777"), 0.5f)
    }

    @Test
    fun barlowDigitsAreTabular() {
        val paint = paintFor(R.font.barlow_semibold)
        assertEquals(paint.measureText("0000"), paint.measureText("1111"), 0.5f)
    }
}
