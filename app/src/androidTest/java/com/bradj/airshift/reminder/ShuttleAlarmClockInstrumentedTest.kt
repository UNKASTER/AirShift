package com.bradj.airshift.reminder

import android.content.Intent
import android.provider.AlarmClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** 只检查发给系统时钟的意图长什么样、以及本机时钟接不接它；不真的写闹钟。 */
@RunWith(AndroidJUnit4::class)
class ShuttleAlarmClockInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val clock = ShuttleAlarmClock(context)

    @Test
    fun setAlarmIntentCarriesTimeLabelSkipUiAndNoRepeatDays() {
        val intent = clock.intentFor(LocalTime.of(5, 25))

        assertEquals(AlarmClock.ACTION_SET_ALARM, intent.action)
        assertEquals(5, intent.getIntExtra(AlarmClock.EXTRA_HOUR, -1))
        assertEquals(25, intent.getIntExtra(AlarmClock.EXTRA_MINUTES, -1))
        assertEquals(ShuttleAlarmPlan.LABEL, intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertFalse(intent.hasExtra(AlarmClock.EXTRA_DAYS))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    /** `<queries>` 生效的证据：装了系统时钟的设备都能解析到接收方。 */
    @Test
    fun theDeviceClockAcceptsTheStandardSetAlarmIntent() {
        assertTrue(clock.isAvailable())
        assertTrue(clock.showAlarmsIntent().resolveActivity(context.packageManager) != null)
    }
}
