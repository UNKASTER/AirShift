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

/** 把 [WidgetPage] 绑到藏青板面布局上；文案全部取字符串资源。 */
internal object DutyWidgetRenderer {
    private const val MISSING_STAND = "--"

    fun render(context: Context, page: WidgetPage, builtAt: LocalDateTime): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_duty_item)
        when (page) {
            is WidgetPage.Message -> bindMessage(context, views, page, builtAt)
            is WidgetPage.Duty -> bindDuty(context, views, page, builtAt)
        }
        return views
    }

    private fun bindMessage(
        context: Context,
        views: RemoteViews,
        page: WidgetPage.Message,
        builtAt: LocalDateTime,
    ) {
        views.setViewVisibility(R.id.widget_duty_content, View.GONE)
        views.setViewVisibility(R.id.widget_message_content, View.VISIBLE)
        val appName = context.getString(R.string.app_name)
        views.setTextViewText(
            R.id.widget_message_head,
            context.getString(R.string.duty_widget_message_head, appName, boardDate(builtAt)),
        )
        views.setTextViewText(R.id.widget_message_title, page.title)
        views.setTextViewText(R.id.widget_message_detail, page.detail)
    }

    private val WEEKDAYS = listOf("一", "二", "三", "四", "五", "六", "日")

    /** "9月5日 周六"，与 App 板头同一种写法。 */
    private fun boardDate(at: LocalDateTime): String =
        "${at.monthValue}月${at.dayOfMonth}日 周${WEEKDAYS[at.dayOfWeek.value - 1]}"

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
        // 无任何航段（如仅有机号/时刻的备份执勤）时隐藏悬空行线。
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
                views.setTextViewText(R.id.widget_status_label, statusLabel(context))
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.GONE)
                showStatusText(context, views, R.string.duty_widget_status_completed, R.color.widget_ok)
            }
            WidgetDutyStatus.COUNTDOWN -> {
                views.setTextViewText(
                    R.id.widget_status_label,
                    context.getString(R.string.duty_widget_status_countdown),
                )
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
                views.setTextViewText(R.id.widget_status_label, statusLabel(context))
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.VISIBLE)
                views.setTextViewText(R.id.widget_gate_arrival, page.gateArrivalClock)
                views.setTextColor(R.id.widget_gate_arrival, ContextCompat.getColor(context, R.color.on_board_alert))
                showStatusText(context, views, R.string.duty_widget_status_overdue, R.color.on_board_alert)
            }
            WidgetDutyStatus.NO_TIME -> {
                views.setTextViewText(R.id.widget_status_label, statusLabel(context))
                views.setViewVisibility(R.id.widget_countdown, View.GONE)
                views.setViewVisibility(R.id.widget_due_column, View.GONE)
                showStatusText(context, views, R.string.duty_widget_status_no_time, R.color.on_board_secondary)
            }
        }
    }

    private fun statusLabel(context: Context): String = context.getString(R.string.duty_widget_status_label)

    private fun showStatusText(context: Context, views: RemoteViews, textRes: Int, colorRes: Int) {
        views.setViewVisibility(R.id.widget_status_text, View.VISIBLE)
        views.setTextViewText(R.id.widget_status_text, context.getString(textRes))
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
        // 夹条颜色随方向：背景是 3dp 竖条，文字色与之配套。
        views.setInt(
            ids.tag,
            "setBackgroundResource",
            if (inbound) R.drawable.widget_tag_inbound else R.drawable.widget_tag_outbound,
        )
        views.setTextColor(
            ids.tag,
            ContextCompat.getColor(context, if (inbound) R.color.widget_arrival else R.color.widget_departure),
        )
        views.setTextViewText(ids.flight, leg.flight)
        views.setTextViewText(ids.place, leg.place)
        val standMissing = leg.stand == MISSING_STAND
        views.setTextViewText(
            ids.stand,
            if (standMissing) context.getString(R.string.duty_widget_stand_missing) else leg.stand,
        )
        views.setTextColor(
            ids.stand,
            ContextCompat.getColor(context, if (standMissing) R.color.on_board_secondary else R.color.on_board),
        )
    }
}
