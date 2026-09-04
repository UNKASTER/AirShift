package com.bradj.airshift.api

import android.content.Context
import com.bradj.airshift.data.RosterStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import kotlin.coroutines.coroutineContext

internal data class LiveRefreshResult(
    val live: Map<FlightLookup, List<FlightInfo>>,
    val airports: List<AirportPoint>,
    val errors: List<String>,
    val attemptedCount: Int,
    val refreshedCount: Int,
    val fallbackDate: LocalDate = LocalDate.now(),
)

/** 一次实时航班批量刷新。实现负责线程切换与每次请求前的窗口复查；调用方只看结果。 */
internal fun interface LiveFlightRefresher {
    suspend fun refresh(
        generation: Long,
        apiKey: String,
        lookups: Set<FlightLookup>,
        scope: FlightRefreshScope,
    ): LiveRefreshResult
}

/**
 * 生产实现：在 IO 线程上执行 [refreshFlightBatch]。每个 HTTP 请求前复查 generation、API Key 与最新窗口，
 * 协程被取消（ViewModel 清理）后不再发起新的请求。
 */
internal class VariFlightLiveRefresher(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone(),
) : LiveFlightRefresher {
    private val applicationContext = context.applicationContext

    override suspend fun refresh(
        generation: Long,
        apiKey: String,
        lookups: Set<FlightLookup>,
        scope: FlightRefreshScope,
    ): LiveRefreshResult = withContext(Dispatchers.IO) {
        val job = coroutineContext[Job]
        val fallbackDate = LocalDate.now(clock)
        val store = RosterStore(applicationContext, clock)
        val client = VariFlightClient(apiKey)
        val isCurrent: (FlightLookup) -> Boolean = { lookup ->
            val current = store.loadSnapshot()
            job?.isActive != false && current.generation == generation && store.variFlightApiKey == apiKey &&
                lookup in current.assignments.refreshLookups(current.manuallyCompletedCount, scope)
        }
        val batch = refreshFlightBatch(
            targets = lookups,
            isCurrent = isCurrent,
            fetch = { lookup -> client.fetchFlightBlocking(lookup) { isCurrent(lookup) } },
        )
        LiveRefreshResult(
            live = batch.live,
            airports = batch.live.values.flatten()
                .flatMap { listOfNotNull(it.origin, it.destination) }
                .distinctBy { it.code },
            errors = batch.errors,
            attemptedCount = batch.attemptedCount,
            refreshedCount = batch.live.size,
            fallbackDate = fallbackDate,
        )
    }
}
