package com.bradj.airshift.reminder

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * 班车闹铃真正写时钟的地方：透明、无界面，进到前台就把今明两天该写的序列逐个写进系统时钟，然后退出。
 *
 * 为什么必须是 Activity：时钟的入口 Activity 是 standard 启动模式，只有从 Activity 不带
 * `FLAG_ACTIVITY_NEW_TASK` 启动，它才会落在本任务里、写完立刻还回前台，本应用全程留在前台，
 * 后面几响才不会被当成后台启动拦掉。
 *
 * 为什么它满足后台启动限制：通知的 PendingIntent 由系统发出（豁免），前台同步时本应用本来就可见；
 * 进程随即有了可见窗口，再启动时钟就是前台启动。Android 12 的"通知蹦床"禁令只针对 Receiver / Service。
 */
class ShuttleAlarmSetActivity : ComponentActivity() {
    private var started = false

    override fun onResume() {
        super.onResume()
        if (started) return
        started = true
        val reason = runCatching { ShuttleAlarmReason.valueOf(intent.getStringExtra(EXTRA_REASON).orEmpty()) }
            .getOrDefault(ShuttleAlarmReason.NOTIFICATION)
        ShuttleAlarmSync.write(this, reason, intent.getBooleanExtra(EXTRA_FORCE, false)) { attempt ->
            if (reason == ShuttleAlarmReason.MANUAL || reason == ShuttleAlarmReason.NOTIFICATION) {
                Toast.makeText(this, resultText(attempt), Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    private fun resultText(attempt: ShuttleAlarmAttempt?): String {
        if (attempt == null) return "当前没有需要设置的班车闹铃"
        return when (attempt.outcome) {
            ShuttleAlarmOutcome.CONFIRMED -> "班车闹铃已设置：${attempt.summary}"
            ShuttleAlarmOutcome.UNCONFIRMED -> "已写入时钟（未核对）：${attempt.summary}"
            ShuttleAlarmOutcome.BLOCKED -> "时钟没有接收，请手动设置"
        }
    }

    companion object {
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_FORCE = "force"

        fun intent(context: Context, reason: ShuttleAlarmReason, force: Boolean): Intent =
            Intent(context, ShuttleAlarmSetActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_FORCE, force)
    }
}
