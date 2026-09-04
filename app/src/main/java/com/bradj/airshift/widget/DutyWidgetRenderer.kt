package com.bradj.airshift.widget

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import com.bradj.airshift.R
import com.bradj.airshift.model.LegDirection
import java.time.Duration
import java.time.LocalDateTime

internal object DutyWidgetRenderer {
    fun render(context: Context, page: WidgetPage, builtAt: LocalDateTime): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_duty_item)
        when (page) {
            is WidgetPage.Message -> bindMessage(views, page)
            is WidgetPage.Duty -> bindDuty(context, views, page, builtAt)
        }
        return views
    }

    private fun bindMessage(views: RemoteViews, page: WidgetPage.Message) {
        views.setViewVisibility(R.id.widget_duty_content, View.GONE)
        views.setViewVisibility(R.id.widget_message_content, View.VISIBLE)
        views.setTextViewText(R.id.widget_message_title, page.title)
        views.setTextViewText(R.id.widget_message_detail, page.detail)
    }

    private fun bindDuty(
        context: Context,
        views: RemoteViews,
        page: WidgetPage.Duty,
        builtAt: LocalDateTime,
    ) {
        views.setViewVisibility(R.id.widget_message_content, View.GONE)
        views.setViewVisibility(R.id.widget_duty_content, View.VISIBLE)
        views.setTextViewText(R.id.widget_header, page.header)
        views.setViewVisibility(R.id.widget_vip, if (page.hasVip) View.VISIBLE else View.GONE)
        bindStatus(context, views, page, builtAt)
        bindLeg(context, views, page.legs.getOrNull(0), PRIMARY_LEG)
        bindLeg(context, views, page.legs.getOrNull(1), SECONDARY_LEG)
        // 无任何航段（如仅有机号/时刻的备份执勤）时隐藏悬空虚线。
        views.setViewVisibility(R.id.widget_divider, if (page.legs.isEmpty()) View.GONE else View.VISIBLE)
    }

    private fun bindStatus(
        context: Context,
        views: RemoteViews,
        page: WidgetPage.Duty,
        builtAt: LocalDateTime,
    ) {
        when (page.status) {
            WidgetDutyStatus.COMPLETED -> {
                views.setTextViewText(R.id.widget_status_label, "保障状态")
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.GONE)
                showStatusText(context, views, "已完成", R.color.success_green)
            }
            WidgetDutyStatus.COUNTDOWN -> {
                views.setTextViewText(R.id.widget_status_label, "距离到位")
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
                views.setTextViewText(R.id.widget_status_label, "保障状态")
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.VISIBLE)
                views.setTextViewText(R.id.widget_gate_arrival, page.gateArrivalClock)
                showStatusText(context, views, "应立即到位", R.color.cea_red)
            }
            WidgetDutyStatus.NO_TIME -> {
                views.setTextViewText(R.id.widget_status_label, "保障状态")
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.GONE)
                showStatusText(context, views, "暂无到位时间信息", R.color.text_hint)
            }
        }
    }

    private fun showStatusText(context: Context, views: RemoteViews, text: String, colorRes: Int) {
        views.setViewVisibility(R.id.widget_status_text, View.VISIBLE)
        views.setTextViewText(R.id.widget_status_text, text)
        views.setTextColor(R.id.widget_status_text, ContextCompat.getColor(context, colorRes))
    }

    /** 一行航段用到的视图 id；小组件固定两行（主/次）。 */
    private class LegViewIds(val block: Int, val tag: Int, val flight: Int, val place: Int, val stand: Int)

    private val PRIMARY_LEG = LegViewIds(
        block = R.id.widget_leg_primary,
        tag = R.id.widget_leg_primary_tag,
        flight = R.id.widget_leg_primary_flight,
        place = R.id.widget_leg_primary_place,
        stand = R.id.widget_leg_primary_stand,
    )

    private val SECONDARY_LEG = LegViewIds(
        block = R.id.widget_leg_secondary,
        tag = R.id.widget_leg_secondary_tag,
        flight = R.id.widget_leg_secondary_flight,
        place = R.id.widget_leg_secondary_place,
        stand = R.id.widget_leg_secondary_stand,
    )

    private fun bindLeg(context: Context, views: RemoteViews, leg: WidgetFlightLeg?, ids: LegViewIds) {
        if (leg == null) {
            views.setViewVisibility(ids.block, View.GONE)
            return
        }
        val inbound = leg.direction == LegDirection.INBOUND
        views.setViewVisibility(ids.block, View.VISIBLE)
        views.setTextViewText(ids.tag, leg.direction.shortLabel)
        views.setInt(
            ids.tag,
            "setBackgroundResource",
            if (inbound) R.drawable.widget_tag_inbound else R.drawable.widget_tag_outbound,
        )
        // tonal chip：浅底深字，文字色随方向与底色配套。
        views.setTextColor(
            ids.tag,
            ContextCompat.getColor(context, if (inbound) R.color.inbound_blue else R.color.on_cea_red_soft),
        )
        views.setTextViewText(ids.flight, leg.flight)
        views.setTextViewText(ids.place, leg.place)
        views.setTextViewText(ids.stand, leg.stand)
        views.setTextColor(
            ids.stand,
            ContextCompat.getColor(context, if (leg.stand == "--") R.color.text_hint else R.color.text_body),
        )
    }
}
