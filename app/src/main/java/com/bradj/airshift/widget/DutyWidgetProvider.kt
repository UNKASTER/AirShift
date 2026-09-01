package com.bradj.airshift.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bradj.airshift.MainActivity
import com.bradj.airshift.R
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.nextIncompleteDutyIndex

/**
 * 当前执勤小组件：ListView 垂直翻页，每页一个执勤占满视口，上下滑动翻看。
 * 初始定位到当前执勤页；点击任意页打开应用。
 */
class DutyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { updateWidget(context, appWidgetManager, it) }
    }

    private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_duty)
        views.setRemoteAdapter(R.id.widget_list, Intent(context, DutyWidgetService::class.java))
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setPendingIntentTemplate(R.id.widget_list, openApp)
        val snapshot = RosterStore(context).loadSnapshot()
        val currentIndex = snapshot.assignments.nextIncompleteDutyIndex(snapshot.manuallyCompletedCount)
        if (currentIndex < snapshot.assignments.size) {
            views.setScrollPosition(R.id.widget_list, currentIndex)
        }
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}
