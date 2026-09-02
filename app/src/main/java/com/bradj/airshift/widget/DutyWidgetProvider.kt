package com.bradj.airshift.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

/**
 * 当前执勤小组件：固定显示当前未完成执勤；卡片打开应用，完成按钮推进执勤。
 */
class DutyWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        DutyWidgetUpdater.updateWidgets(context, appWidgetManager, appWidgetIds)
    }
}
