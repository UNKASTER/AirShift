package com.bradj.airshift.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 底部导航 Tab 状态。ViewModel 在旋转等配置变化时保留所选 Tab，
 * 进程重建时随新实例回到默认的当前执勤页。
 */
internal class DutyNavigationViewModel : ViewModel() {
    private val mutableSection = MutableStateFlow(DutySection.CURRENT)
    val section: StateFlow<DutySection> = mutableSection.asStateFlow()

    // 新进程或新 ViewModel 的首个 onStart 视为打开应用；
    // 此后只有真正退到后台再回到前台才算重新打开。
    private var stoppedInBackground = true

    fun selectSection(section: DutySection) {
        mutableSection.value = section
    }

    /** 冷启动、从后台恢复或进程重建后回到前台时，强制显示当前执勤页。 */
    fun onActivityForegrounded() {
        if (stoppedInBackground) {
            stoppedInBackground = false
            mutableSection.value = DutySection.CURRENT
        }
    }

    /** 旋转等配置变化不算离开应用。 */
    fun onActivityBackgrounded(isChangingConfigurations: Boolean) {
        if (!isChangingConfigurations) stoppedInBackground = true
    }
}
