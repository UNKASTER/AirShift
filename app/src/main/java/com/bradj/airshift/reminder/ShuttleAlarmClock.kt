package com.bradj.airshift.reminder

import android.app.Activity
import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.os.strictmode.Violation
import android.provider.AlarmClock
import com.bradj.airshift.model.shift.ShuttleAlarmPlan
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** 一轮写入的结果：发出了几条、几条没有时钟接、系统是否明确拦下了后台启动。 */
internal data class ShuttleAlarmFireResult(val launched: Int, val failed: Int, val blockedByPolicy: Boolean)

/**
 * 与系统时钟的全部交互：标准 `ACTION_SET_ALARM`（`EXTRA_SKIP_UI`，不带重复星期 = 仅一次，名称恒定以便时钟复用条目），
 * 以及用 [AlarmManager.getNextAlarmClock] 读系统"下一闹钟"做核对。
 */
internal class ShuttleAlarmClock(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())

    /** 需要 manifest 的 `<queries>` 才查得到时钟。 */
    fun isAvailable(): Boolean = intentFor(LocalTime.NOON).resolveActivity(context.packageManager) != null

    /**
     * 从 Activity 启动时**不加** `FLAG_ACTIVITY_NEW_TASK`：时钟的入口 Activity 是 standard 启动模式，
     * 不带该标志就落在本应用的任务里，写完 finish 后立刻回到我们自己的界面，本应用全程留在前台，
     * 后面几响才不会被当成后台启动拦掉（带 NEW_TASK 会把整个时钟任务拉到前台，只有第一响能写进去）。
     */
    fun intentFor(time: LocalTime): Intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_HOUR, time.hour)
        putExtra(AlarmClock.EXTRA_MINUTES, time.minute)
        putExtra(AlarmClock.EXTRA_MESSAGE, ShuttleAlarmPlan.LABEL)
        putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun showAlarmsIntent(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun nextAlarmClockAt(): LocalDateTime? = context.getSystemService(AlarmManager::class.java).nextAlarmClock
        ?.let { Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault()).toLocalDateTime() }

    /**
     * 逐个启动时钟的入口 Activity（skip_ui 时它在 onCreate 里写库并 finish），每条隔 [FIRE_SPACING_MILLIS]；
     * 全部发出再等 [VERIFY_DELAY_MILLIS] 让时钟落库，然后回调。只捕获"没有时钟接"与权限异常。
     */
    fun fire(times: List<LocalTime>, onDone: (ShuttleAlarmFireResult) -> Unit) {
        val watch = BlockedLaunchWatch.install()
        var launched = 0
        var failed = 0
        fun launch(index: Int) {
            if (index >= times.size) {
                handler.postDelayed(
                    { onDone(ShuttleAlarmFireResult(launched, failed, watch.finish())) },
                    VERIFY_DELAY_MILLIS,
                )
                return
            }
            try {
                context.startActivity(intentFor(times[index]))
                launched++
            } catch (_: ActivityNotFoundException) {
                failed++
            } catch (_: SecurityException) {
                failed++
            }
            handler.postDelayed({ launch(index + 1) }, FIRE_SPACING_MILLIS)
        }
        launch(0)
    }

    companion object {
        const val FIRE_SPACING_MILLIS = 250L
        const val VERIFY_DELAY_MILLIS = 1500L
    }
}

/**
 * Android 16 起能检测"后台启动 Activity 被系统拦下"：发射前临时加到当前 VmPolicy 上，发完还原。
 * 旧系统上什么也不做（永远报未拦截），由"下一闹钟"核对兜底。
 */
internal class BlockedLaunchWatch private constructor(private val previous: StrictMode.VmPolicy?) {
    private val blocked = AtomicBoolean(false)

    fun finish(): Boolean {
        previous?.let(StrictMode::setVmPolicy)
        return blocked.get()
    }

    companion object {
        private const val FIRST_API_WITH_DETECTION = 36

        fun install(): BlockedLaunchWatch {
            if (Build.VERSION.SDK_INT < FIRST_API_WITH_DETECTION) return BlockedLaunchWatch(null)
            val previous = StrictMode.getVmPolicy()
            val watch = BlockedLaunchWatch(previous)
            val direct = Executor { it.run() }
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder(previous)
                    .detectBlockedBackgroundActivityLaunch()
                    .penaltyListener(direct) { violation -> if (violation.isBlockedLaunch()) watch.blocked.set(true) }
                    .build(),
            )
            return watch
        }

        /** 平台没有公开对应的 Violation 子类，按类名 / 消息识别。 */
        private fun Violation.isBlockedLaunch(): Boolean =
            javaClass.simpleName.contains("BackgroundActivity", ignoreCase = true) ||
                message?.contains("background activity", ignoreCase = true) == true
    }
}
