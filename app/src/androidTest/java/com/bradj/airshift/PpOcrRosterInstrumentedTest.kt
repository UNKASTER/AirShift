package com.bradj.airshift

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.parser.OcrRosterReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class PpOcrRosterInstrumentedTest {
    @Test
    fun ppOcrV6ReadsSyntheticRosterWithoutLeakingSupplementDetails() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val bitmap = instrumentation.context.assets.open("synthetic_roster.png").use(BitmapFactory::decodeStream)
        val clock = Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneId.of("Asia/Shanghai"))

        val result = try {
            OcrRosterReader.readBitmap(context, bitmap, "TESTUSER", clock)
        } finally {
            bitmap.recycle()
        }

        assertEquals(LocalDate.of(2026, 8, 22), result.rosterDate)
        assertTrue(result.assignments.toString(), result.assignments.any { it.outboundFlight == "ZZ1002" })
        assertTrue(result.assignments.none { it.hasVip })
    }
}
