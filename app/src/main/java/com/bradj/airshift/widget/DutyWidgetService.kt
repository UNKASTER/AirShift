package com.bradj.airshift.widget

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.ContextCompat
import com.bradj.airshift.R
import com.bradj.airshift.data.RosterStore
import java.time.Duration
import java.time.LocalDateTime

class DutyWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        DutyWidgetFactory(applicationContext)
}

private class DutyWidgetFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var pages: List<WidgetPage> = emptyList()
    private var builtAt: LocalDateTime = LocalDateTime.now()

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val snapshot = RosterStore(context).loadSnapshot()
        builtAt = LocalDateTime.now()
        pages = snapshot.assignments.toWidgetPages(snapshot.manuallyCompletedCount, builtAt)
    }

    override fun getCount(): Int = pages.size

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_duty_item)
        when (val page = pages.getOrNull(position)) {
            is WidgetPage.Message -> bindMessage(views, page)
            is WidgetPage.Duty -> bindDuty(views, page)
            null -> bindMessage(views, WidgetPage.Message("暂无数据", "打开航勤智排刷新排班。"))
        }
        views.setOnClickFillInIntent(R.id.widget_duty_item_root, Intent())
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = position.toLong()
    override fun hasStableIds(): Boolean = true
    override fun onDestroy() = Unit

    private fun bindMessage(views: RemoteViews, page: WidgetPage.Message) {
        views.setViewVisibility(R.id.widget_duty_content, View.GONE)
        views.setViewVisibility(R.id.widget_message_content, View.VISIBLE)
        views.setTextViewText(R.id.widget_message_title, page.title)
        views.setTextViewText(R.id.widget_message_detail, page.detail)
    }

    private fun bindDuty(views: RemoteViews, page: WidgetPage.Duty) {
        views.setViewVisibility(R.id.widget_message_content, View.GONE)
        views.setViewVisibility(R.id.widget_duty_content, View.VISIBLE)
        views.setTextViewText(R.id.widget_header, page.header)
        views.setViewVisibility(R.id.widget_vip, if (page.hasVip) View.VISIBLE else View.GONE)
        bindStatus(views, page)
        bindLeg(
            views, page.legs.getOrNull(0),
            R.id.widget_leg_primary, R.id.widget_leg_primary_tag,
            R.id.widget_leg_primary_flight, R.id.widget_leg_primary_place, R.id.widget_leg_primary_gate,
        )
        bindLeg(
            views, page.legs.getOrNull(1),
            R.id.widget_leg_secondary, R.id.widget_leg_secondary_tag,
            R.id.widget_leg_secondary_flight, R.id.widget_leg_secondary_place, R.id.widget_leg_secondary_gate,
        )
    }

    private fun bindStatus(views: RemoteViews, page: WidgetPage.Duty) {
        when (page.status) {
            WidgetDutyStatus.COMPLETED -> {
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.GONE)
                showStatusText(views, "已完成", R.color.success_green)
            }
            WidgetDutyStatus.COUNTDOWN -> {
                views.setViewVisibility(R.id.widget_status_text, View.GONE)
                views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                views.setViewVisibility(R.id.widget_due_column, View.VISIBLE)
                views.setTextViewText(R.id.widget_gate_arrival, page.gateArrivalClock)
                val target = page.countdownTarget ?: return
                val remainingMillis = Duration.between(builtAt, target).toMillis().coerceAtLeast(0L)
                views.setChronometerCountDown(R.id.widget_countdown, true)
                views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + remainingMillis, null, true)
            }
            WidgetDutyStatus.OVERDUE -> {
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.VISIBLE)
                views.setTextViewText(R.id.widget_gate_arrival, page.gateArrivalClock)
                showStatusText(views, "应立即到位", R.color.cea_red)
            }
            WidgetDutyStatus.NO_TIME -> {
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.GONE)
                showStatusText(views, "暂无到位时间信息", R.color.text_hint)
            }
        }
    }

    private fun showStatusText(views: RemoteViews, text: String, colorRes: Int) {
        views.setViewVisibility(R.id.widget_status_text, View.VISIBLE)
        views.setTextViewText(R.id.widget_status_text, text)
        views.setTextColor(R.id.widget_status_text, ContextCompat.getColor(context, colorRes))
    }

    private fun bindLeg(
        views: RemoteViews,
        leg: WidgetFlightLeg?,
        blockId: Int,
        tagId: Int,
        flightId: Int,
        placeId: Int,
        gateId: Int,
    ) {
        if (leg == null) {
            views.setViewVisibility(blockId, View.GONE)
            return
        }
        val inbound = leg.directionLabel.startsWith("进")
        views.setViewVisibility(blockId, View.VISIBLE)
        views.setTextViewText(tagId, leg.directionLabel.take(1))
        views.setInt(tagId, "setBackgroundResource", if (inbound) R.drawable.widget_tag_inbound else R.drawable.widget_tag_outbound)
        views.setTextViewText(flightId, leg.flight)
        views.setTextViewText(placeId, leg.place)
        views.setTextViewText(gateId, leg.gate)
        views.setTextColor(
            gateId,
            ContextCompat.getColor(context, if (leg.gate == "--") R.color.text_hint else R.color.text_primary),
        )
    }
}
