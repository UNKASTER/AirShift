package com.bradj.airshift.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.bradj.airshift.R

object DutyWidgetUpdater {
    /** 排班或执勤进度变化后刷新全部小组件实例的列表数据；ListView 保持当前滚动位置。 */
    fun notifyRosterChanged(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, DutyWidgetProvider::class.java))
        if (ids.isEmpty()) return
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }
}
