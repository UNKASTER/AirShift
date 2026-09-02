package com.bradj.airshift.widget

import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DutyWidgetLayoutInstrumentedTest {
    @Test
    fun widgetUsesSingleFullSizeDutyLayout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val remoteViews = RemoteViews(context.packageName, R.layout.widget_duty_item)

        val root = remoteViews.apply(context, FrameLayout(context))

        assertTrue(root is FrameLayout)
        assertEquals(R.id.widget_duty_item_root, root.id)
        assertEquals(
            context.getString(R.string.duty_widget_complete),
            root.findViewById<TextView>(R.id.widget_complete_duty).text.toString(),
        )
    }
}
