package com.bradj.airshift.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.R
import com.bradj.airshift.model.RosterAssignment
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 真机验证「模型 → RemoteViews 绑定 → 布局 inflate」整条链路，防止中间环节静默丢内容。 */
@RunWith(AndroidJUnit4::class)
class DutyWidgetRendererInstrumentedTest {
    private val now = LocalDateTime.of(2026, 9, 2, 12, 0)

    private fun render(page: WidgetPage): View {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return DutyWidgetRenderer.render(context, page, now).apply(context, FrameLayout(context))
    }

    @Test
    fun dutyPageRendersLegRowsWithAirportNameCodeAndStands() {
        val page = listOf(
            RosterAssignment(
                aircraftRegistration = "B-1234",
                aircraftType = "320",
                inboundFlight = "MU2471",
                origin = "成都天府",
                scheduledArrival = now.plusHours(1),
                outboundFlight = "MU2473",
                destination = "宁波栎社",
                scheduledDeparture = now.plusHours(3),
                assignees = "张三",
                originCode = "TFU",
                destinationCode = "NGB",
                arrivalStand = "105",
                boardingGate = "B12",
                departureStand = "352",
            ),
        ).toCurrentWidgetPage(manuallyCompletedCount = 0, now = now)
        val root = render(page)

        // 进港行：航班号 + 始发地中文名三字码 + 到达站机位。
        assertEquals(View.VISIBLE, root.findViewById<LinearLayout>(R.id.widget_leg_primary).visibility)
        assertEquals("MU2471", root.findViewById<TextView>(R.id.widget_leg_primary_flight).text.toString())
        assertEquals("成都天府 TFU", root.findViewById<TextView>(R.id.widget_leg_primary_place).text.toString())
        assertEquals("机位 105", root.findViewById<TextView>(R.id.widget_leg_primary_gate).text.toString())

        // 出港行：航班号 + 目的地中文名三字码 + 出发机位（与进港行一致，不用登机口）。
        assertEquals(View.VISIBLE, root.findViewById<LinearLayout>(R.id.widget_leg_secondary).visibility)
        assertEquals("MU2473", root.findViewById<TextView>(R.id.widget_leg_secondary_flight).text.toString())
        assertEquals("宁波栎社 NGB", root.findViewById<TextView>(R.id.widget_leg_secondary_place).text.toString())
        assertEquals("机位 352", root.findViewById<TextView>(R.id.widget_leg_secondary_gate).text.toString())

        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_divider).visibility)
    }

    @Test
    fun dutyWithoutFlightsHidesLegRowsAndDivider() {
        // 无航班的执勤恒为「已完成」，不会被选为当前执勤，但仍会作为已完成页参与渲染。
        val flightless = RosterAssignment(
            aircraftRegistration = "B-9999",
            aircraftType = null,
            inboundFlight = null,
            origin = null,
            scheduledArrival = null,
            outboundFlight = null,
            destination = null,
            scheduledDeparture = now.minusHours(5),
            assignees = "张三",
        )
        val upcoming = RosterAssignment(
            aircraftRegistration = "B-1234",
            aircraftType = null,
            inboundFlight = null,
            origin = null,
            scheduledArrival = null,
            outboundFlight = "MU2473",
            destination = "宁波栎社",
            scheduledDeparture = now.plusHours(3),
            assignees = "张三",
        )
        val page = listOf(flightless, upcoming).toWidgetPages(manuallyCompletedCount = 0, now = now)[0]
        val root = render(page)

        assertEquals(View.GONE, root.findViewById<LinearLayout>(R.id.widget_leg_primary).visibility)
        assertEquals(View.GONE, root.findViewById<LinearLayout>(R.id.widget_leg_secondary).visibility)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_divider).visibility)
    }
}
