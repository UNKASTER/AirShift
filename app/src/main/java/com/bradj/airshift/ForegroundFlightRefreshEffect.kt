package com.bradj.airshift

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay

@Composable
internal fun ForegroundFlightRefreshEffect(
    active: Boolean,
    rosterGeneration: Long,
    dutiesComplete: Boolean,
    isWorking: Boolean,
    onConfigure: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onStopped: () -> Unit,
    refreshIntervalMillis: Long = 5 * 60 * 1000L,
    busyRetryMillis: Long = 15 * 1000L,
    refreshDelayMillis: () -> Long = { 0L },
) {
    val latestIsWorking by rememberUpdatedState(isWorking)
    val latestOnConfigure by rememberUpdatedState(onConfigure)
    val latestOnRefresh by rememberUpdatedState(onRefresh)
    val latestOnStopped by rememberUpdatedState(onStopped)
    val latestRefreshDelayMillis by rememberUpdatedState(refreshDelayMillis)

    LaunchedEffect(active, rosterGeneration, dutiesComplete) {
        if (!active) return@LaunchedEffect
        latestOnConfigure(!dutiesComplete)
        if (dutiesComplete) {
            latestOnStopped()
            return@LaunchedEffect
        }
        while (true) {
            if (latestIsWorking) {
                delay(busyRetryMillis)
                continue
            }
            val remaining = latestRefreshDelayMillis()
            if (remaining > 0) {
                delay(remaining)
                continue
            }
            latestOnRefresh()
            delay(refreshIntervalMillis)
        }
    }
}
