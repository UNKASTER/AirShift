package com.bradj.airshift.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.bradj.airshift.MainActivity
import com.bradj.airshift.R
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.nextIncompleteDutyIndex
import java.time.LocalDateTime

object DutyWidgetUpdater {
    /** 排班或执勤进度变化后，直接重绘全部小组件实例的当前执勤。 */
    fun notifyRosterChanged(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, DutyWidgetProvider::class.java))
        updateWidgets(context, manager, ids)
    }

    internal fun updateWidgets(
        context: Context,
        manager: AppWidgetManager,
        ids: IntArray,
    ) {
        if (ids.isEmpty()) return
        val snapshot = RosterStore(context).loadSnapshot()
        val builtAt = LocalDateTime.now()
        val currentDutyIndex = snapshot.assignments.nextIncompleteDutyIndex(
            snapshot.manuallyCompletedCount,
            builtAt,
        )
        val page = snapshot.assignments.toCurrentWidgetPage(snapshot.manuallyCompletedCount, builtAt)
        val views = DutyWidgetRenderer.render(context, page, builtAt)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_duty_item_root, openApp)
        if (page is WidgetPage.Duty) {
            val completeDuty = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, DutyWidgetActionReceiver::class.java).apply {
                    action = DutyWidgetActionReceiver.ACTION_COMPLETE_DUTY
                    putExtra(DutyWidgetActionReceiver.EXTRA_ROSTER_GENERATION, snapshot.generation)
                    putExtra(DutyWidgetActionReceiver.EXTRA_DUTY_INDEX, currentDutyIndex)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_complete_duty, completeDuty)
        }
        manager.updateAppWidget(ids, views)
    }
}
