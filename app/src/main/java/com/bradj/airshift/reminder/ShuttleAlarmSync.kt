package com.bradj.airshift.reminder

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.shift.ShuttleAlarmDay
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 班车闹铃的编排：读存储 → 算序列 → [ShuttleAlarmPolicy.decide] → 落盘 → 定唤醒 → 通知 → 交给
 * [ShuttleAlarmSetActivity] 去写时钟。
 *
 * 写时钟必须由 Activity 发起：时钟的入口 Activity 是 standard 启动模式，只有不带 `FLAG_ACTIVITY_NEW_TASK`
 * 才会落在本应用的任务里、写完立刻还回前台，本应用因此全程可见；否则第一响就把整个时钟任务拉到前台，
 * 剩下几响变成后台启动被系统拦掉（真机取证：2026-09-09 只有第一响写进时钟，StrictMode 报后台启动被拦）。
 */
internal object ShuttleAlarmSync {
    enum class Launch { VISIBLE, BACKGROUND }

    /**
     * 这台时钟是否用 `setAlarmClock` 登记闹钟（决定"下一闹钟"为空 / 偏晚能否判定为没写进去）。
     * 依据：BBKClock 8.0.3.12 的 classes.dex 含 `setAlarmClock` / `AlarmClockInfo` 符号，写入后
     * `dumpsys alarm` 确实报出 "Next alarm clock information"。读不到时应改为 false，后台写入只会给"未核对"。
     */
    const val CLOCK_REPORTS_NEXT_ALARM = true

    /** 启动蹦床之后等系统报"后台启动被拦"的时间。 */
    private const val DISPATCH_CHECK_MILLIS = 400L

    private val handler = Handler(Looper.getMainLooper())

    private class Request(
        val launch: Launch,
        val reason: ShuttleAlarmReason,
        val force: Boolean,
        val onFinished: () -> Unit,
    )

    private var inFlight = false
    private var queued: Request? = null

    fun sync(
        context: Context,
        launch: Launch,
        reason: ShuttleAlarmReason,
        force: Boolean = false,
        onFinished: () -> Unit = {},
    ) {
        val appContext = context.applicationContext
        val request = Request(launch, reason, force, onFinished)
        if (inFlight) {
            queued = queued.mergedWith(request)
            return
        }
        inFlight = true
        run(appContext, request) { finish(appContext, request) }
    }

    /** 用户在时钟里关掉了旧闹铃：清掉提示与通知。 */
    fun acknowledgeStale(context: Context) {
        val store = RosterStore(context.applicationContext)
        store.shuttleAlarmState = store.shuttleAlarmState.copy(stale = emptyList())
        ShuttleAlarmNotifications.cancelStale(context)
    }

    /**
     * 蹦床进到前台后调用：在自己的任务里逐个写时钟、核对、落盘。
     * [onFinished] 给出本次写入的结果，没有需要写的返回 null。
     */
    fun write(
        activity: Activity,
        reason: ShuttleAlarmReason,
        force: Boolean,
        onFinished: (ShuttleAlarmAttempt?) -> Unit,
    ) {
        val store = RosterStore(activity.applicationContext)
        val now = LocalDateTime.now()
        val enabled = store.shuttleAlarmEnabled
        val session = Session(activity, store, ShuttleAlarmSource.window(store, now.toLocalDate()))
        val decision = ShuttleAlarmPolicy.decide(
            ShuttleAlarmRequest(now, enabled, visible = true, force = force),
            session.window,
            store.shuttleAlarmState,
        )
        session.persist(decision.state, now, wake = enabled)
        if (decision.newStale.isNotEmpty()) ShuttleAlarmNotifications.showStale(activity, decision.state.stale, now)
        val days = decision.fire.sortedBy { it.date }
        if (days.isEmpty() || !ShuttleAlarmClock(activity).isAvailable()) {
            onFinished(null)
            return
        }
        session.fire(activity, days, decision.state, reason, onFinished)
    }

    private fun run(context: Context, request: Request, done: () -> Unit) {
        val store = RosterStore(context)
        val now = LocalDateTime.now()
        val enabled = store.shuttleAlarmEnabled
        val session = Session(context, store, ShuttleAlarmSource.window(store, now.toLocalDate()))
        val decision = ShuttleAlarmPolicy.decide(
            ShuttleAlarmRequest(now, enabled, request.launch == Launch.VISIBLE, request.force),
            session.window,
            store.shuttleAlarmState,
        )
        if (decision.newStale.isNotEmpty()) ShuttleAlarmNotifications.showStale(context, decision.state.stale, now)
        if (!enabled) {
            ShuttleAlarmNotifications.cancelSetPrompt(context)
            session.persist(decision.state, now, wake = false)
            done()
            return
        }
        session.persist(session.verifyPending(decision.state, now), now, wake = true)
        if (decision.fire.isEmpty() || !ShuttleAlarmClock(context).isAvailable()) {
            done()
        } else {
            session.dispatch(request.reason, request.launch, request.force, decision.fire, done)
        }
    }

    private fun finish(context: Context, request: Request) {
        request.onFinished()
        val next = queued
        queued = null
        if (next == null) {
            inFlight = false
        } else {
            run(context, next) { finish(context, next) }
        }
    }

    private fun Request?.mergedWith(other: Request): Request {
        if (this == null) return other
        val first = onFinished
        val anyVisible = launch == Launch.VISIBLE || other.launch == Launch.VISIBLE
        return Request(
            launch = if (anyVisible) Launch.VISIBLE else Launch.BACKGROUND,
            reason = other.reason,
            force = force || other.force,
            onFinished = {
                first()
                other.onFinished()
            },
        )
    }

    /** 一轮同步里共用的三件东西：上下文、存储、今天起的序列窗口。 */
    private class Session(
        val context: Context,
        val store: RosterStore,
        val window: List<ShuttleAlarmDay?>,
    ) {
        fun persist(state: ShuttleAlarmState, now: LocalDateTime, wake: Boolean) {
            store.shuttleAlarmState = state
            ShuttleAlarmWake.schedule(context, if (wake) ShuttleAlarmWakes.nextWakeAt(now, window, state) else null)
        }

        /** 交给蹦床去写；启动蹦床本身若被系统当成后台启动拦下，就发通知让用户点一下。 */
        fun dispatch(
            reason: ShuttleAlarmReason,
            launch: Launch,
            force: Boolean,
            days: List<ShuttleAlarmDay>,
            done: () -> Unit,
        ) {
            val watch = BlockedLaunchWatch.install()
            val started = runCatching {
                context.startActivity(ShuttleAlarmSetActivity.intent(context, reason, force))
            }.isSuccess
            handler.postDelayed(
                {
                    if (watch.finish() || !started) blocked(reason, launch, days)
                    done()
                },
                DISPATCH_CHECK_MILLIS,
            )
        }

        private fun blocked(reason: ShuttleAlarmReason, launch: Launch, days: List<ShuttleAlarmDay>) {
            val at = LocalDateTime.now()
            val attempt = attempt(reason, at, ShuttleAlarmOutcome.BLOCKED, launch == Launch.BACKGROUND, days)
            persist(recorded(store.shuttleAlarmState, days, attempt), at, wake = true)
            ShuttleAlarmNotifications.showBlocked(context, days, at.toLocalDate())
        }

        fun fire(
            activity: Activity,
            days: List<ShuttleAlarmDay>,
            base: ShuttleAlarmState,
            reason: ShuttleAlarmReason,
            onFinished: (ShuttleAlarmAttempt?) -> Unit,
        ) {
            val clock = ShuttleAlarmClock(activity)
            val expectedFirst = days.first().let { it.date.atTime(it.times.first()) }
            clock.fire(days.flatMap { it.times }) { result ->
                val at = LocalDateTime.now()
                val verdict = if (result.blockedByPolicy || result.launched == 0) {
                    ShuttleAlarmVerdict.Blocked
                } else {
                    ShuttleAlarmVerification.judge(
                        clock.nextAlarmClockAt(),
                        expectedFirst,
                        base.records,
                        hops = 0,
                        clockReportsNextAlarm = CLOCK_REPORTS_NEXT_ALARM,
                    )
                }
                val attempt = attempt(reason, at, verdict.toOutcome(), background = false, days = days)
                notifyOutcome(attempt.outcome, days, at.toLocalDate(), verdict)
                persist(recorded(base, days, attempt).applyVerdict(verdict, hops = 0), at, wake = true)
                onFinished(attempt)
            }
        }

        fun recorded(
            base: ShuttleAlarmState,
            days: List<ShuttleAlarmDay>,
            attempt: ShuttleAlarmAttempt,
        ): ShuttleAlarmState {
            val write = ShuttleAlarmWrite(attempt.outcome, attempt.background, attempt.at)
            var state = base
            days.forEach { day ->
                // 记录按整段推荐序列合并，fire 里的 day 只带这次要写的那几响。
                val plan = window.firstOrNull { it?.date == day.date } ?: day
                val record = ShuttleAlarmVerification.merged(state.record(day.date), plan, day.times, write)
                state = state.withRecord(record)
            }
            val fired = days.flatMap { day -> day.times.map { ShuttleAlarm(day.date, it) } }
            return state.copy(stale = state.stale.filter { it !in fired }, lastAttempt = attempt)
        }

        /** 核对成功撤掉之前的提示；被拦发高优先级通知；无法核对且不再重试才发静默通知。 */
        fun notifyOutcome(
            outcome: ShuttleAlarmOutcome,
            days: List<ShuttleAlarmDay>,
            today: LocalDate,
            verdict: ShuttleAlarmVerdict,
        ) {
            when {
                outcome == ShuttleAlarmOutcome.CONFIRMED -> ShuttleAlarmNotifications.cancelSetPrompt(context)
                outcome == ShuttleAlarmOutcome.BLOCKED -> ShuttleAlarmNotifications.showBlocked(context, days, today)
                verdict !is ShuttleAlarmVerdict.Inconclusive ->
                    ShuttleAlarmNotifications.showUnconfirmed(context, days, today)
            }
        }

        /** 上一轮核对没有结论、约定的再看一次的时刻到了：只读"下一闹钟"，不再写。 */
        fun verifyPending(state: ShuttleAlarmState, now: LocalDateTime): ShuttleAlarmState {
            val due = state.pendingVerifyAt?.let { now >= it } ?: false
            if (!due) return state
            val unconfirmed = state.records.filter { it.outcome == ShuttleAlarmOutcome.UNCONFIRMED }
            val expectedFirst = unconfirmed.flatMap { it.alarms() }.map { it.at }.filter { it > now }.minOrNull()
            return if (expectedFirst == null) {
                state.copy(pendingVerifyAt = null, verifyHops = 0)
            } else {
                reverify(state, unconfirmed, expectedFirst, now)
            }
        }

        private fun reverify(
            state: ShuttleAlarmState,
            unconfirmed: List<ShuttleAlarmRecord>,
            expectedFirst: LocalDateTime,
            now: LocalDateTime,
        ): ShuttleAlarmState {
            val verdict = ShuttleAlarmVerification.judge(
                ShuttleAlarmClock(context).nextAlarmClockAt(),
                expectedFirst,
                state.records,
                state.verifyHops,
                CLOCK_REPORTS_NEXT_ALARM,
            )
            val outcome = verdict.toOutcome()
            var next = state
            unconfirmed.forEach { record -> next = next.withRecord(record.copy(outcome = outcome)) }
            val fallbackDeparture = expectedFirst.toLocalTime()
            val days = unconfirmed.map { ShuttleAlarmDay(it.date, it.departure ?: fallbackDeparture, it.times) }
            notifyOutcome(outcome, days, now.toLocalDate(), verdict)
            return next.applyVerdict(verdict, state.verifyHops)
        }

        private fun attempt(
            reason: ShuttleAlarmReason,
            at: LocalDateTime,
            outcome: ShuttleAlarmOutcome,
            background: Boolean,
            days: List<ShuttleAlarmDay>,
        ) = ShuttleAlarmAttempt(
            reason = reason,
            at = at,
            outcome = outcome,
            background = background,
            summary = days.joinToString("；") { ShuttleAlarmText.summary(it, at.toLocalDate()) },
        )
    }
}

private fun ShuttleAlarmVerdict.toOutcome(): ShuttleAlarmOutcome = when (this) {
    ShuttleAlarmVerdict.Confirmed -> ShuttleAlarmOutcome.CONFIRMED
    ShuttleAlarmVerdict.Blocked -> ShuttleAlarmOutcome.BLOCKED
    ShuttleAlarmVerdict.Unconfirmed, is ShuttleAlarmVerdict.Inconclusive -> ShuttleAlarmOutcome.UNCONFIRMED
}

private fun ShuttleAlarmState.applyVerdict(verdict: ShuttleAlarmVerdict, hops: Int): ShuttleAlarmState =
    if (verdict is ShuttleAlarmVerdict.Inconclusive) {
        copy(pendingVerifyAt = verdict.retryAt, verifyHops = hops + 1)
    } else {
        copy(pendingVerifyAt = null, verifyHops = 0)
    }
