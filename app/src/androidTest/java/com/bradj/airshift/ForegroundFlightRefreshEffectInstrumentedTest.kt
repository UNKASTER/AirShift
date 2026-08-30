package com.bradj.airshift

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ForegroundFlightRefreshEffectInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun completedNonEmptyRosterResumesWhenDutiesBecomeIncomplete() {
        var dutiesComplete by mutableStateOf(true)
        val configured = CopyOnWriteArrayList<Boolean>()
        val refreshes = AtomicInteger()
        val stopped = AtomicInteger()
        composeRule.setContent {
            ForegroundFlightRefreshEffect(
                active = true,
                rosterGeneration = 1L,
                dutiesComplete = dutiesComplete,
                isWorking = false,
                onConfigure = { configured += it },
                onRefresh = { refreshes.incrementAndGet() },
                onStopped = { stopped.incrementAndGet() },
                refreshIntervalMillis = REFRESH_INTERVAL_MILLIS,
                busyRetryMillis = BUSY_RETRY_MILLIS,
            )
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.waitUntil { stopped.get() == 1 }
        assertEquals(listOf(false), configured.toList())
        assertEquals(0, refreshes.get())

        composeRule.runOnIdle { dutiesComplete = false }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.waitUntil { refreshes.get() == 1 }
        assertEquals(listOf(false, true), configured.toList())
    }

    @Test
    fun newRosterGenerationRestartsAnAlreadyEligibleLoop() {
        var generation by mutableStateOf(1L)
        val configured = AtomicInteger()
        val refreshes = AtomicInteger()
        composeRule.setContent {
            ForegroundFlightRefreshEffect(
                active = true,
                rosterGeneration = generation,
                dutiesComplete = false,
                isWorking = false,
                onConfigure = { configured.incrementAndGet() },
                onRefresh = { refreshes.incrementAndGet() },
                onStopped = {},
                refreshIntervalMillis = REFRESH_INTERVAL_MILLIS,
                busyRetryMillis = BUSY_RETRY_MILLIS,
            )
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.waitUntil { refreshes.get() == 1 }

        composeRule.runOnIdle { generation = 2L }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.waitUntil { refreshes.get() == 2 }
        assertEquals(2, configured.get())
    }

    @Test
    fun callbackRecompositionDoesNotRestartButTheNextTickUsesTheLatestCallback() {
        var useUpdatedCallback by mutableStateOf(false)
        val configured = AtomicInteger()
        val originalRefreshes = AtomicInteger()
        val updatedRefreshes = AtomicInteger()
        composeRule.setContent {
            val updatedCallback = useUpdatedCallback
            ForegroundFlightRefreshEffect(
                active = true,
                rosterGeneration = 1L,
                dutiesComplete = false,
                isWorking = false,
                onConfigure = { configured.incrementAndGet() },
                onRefresh = {
                    if (updatedCallback) updatedRefreshes.incrementAndGet() else originalRefreshes.incrementAndGet()
                },
                onStopped = {},
                refreshIntervalMillis = REFRESH_INTERVAL_MILLIS,
                busyRetryMillis = BUSY_RETRY_MILLIS,
            )
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.waitUntil { originalRefreshes.get() == 1 }

        composeRule.runOnIdle { useUpdatedCallback = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()

        assertEquals(1, configured.get())
        assertEquals(1, originalRefreshes.get())
        assertEquals(0, updatedRefreshes.get())

        composeRule.mainClock.advanceTimeBy(REFRESH_INTERVAL_MILLIS)
        composeRule.waitUntil { updatedRefreshes.get() == 1 }
        assertEquals(1, originalRefreshes.get())
        assertEquals(1, configured.get())
    }

    @Test
    fun busyLoopWaitsWithoutRequestingAndResumesWhenWorkFinishes() {
        var isWorking by mutableStateOf(true)
        val configured = AtomicInteger()
        val refreshes = AtomicInteger()
        composeRule.setContent {
            ForegroundFlightRefreshEffect(
                active = true,
                rosterGeneration = 1L,
                dutiesComplete = false,
                isWorking = isWorking,
                onConfigure = { configured.incrementAndGet() },
                onRefresh = { refreshes.incrementAndGet() },
                onStopped = {},
                refreshIntervalMillis = REFRESH_INTERVAL_MILLIS,
                busyRetryMillis = BUSY_RETRY_MILLIS,
            )
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.mainClock.advanceTimeBy(BUSY_RETRY_MILLIS * 3)
        assertEquals(0, refreshes.get())

        composeRule.runOnIdle { isWorking = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(BUSY_RETRY_MILLIS)

        composeRule.waitUntil { refreshes.get() == 1 }
        assertEquals(1, configured.get())
    }

    private companion object {
        const val REFRESH_INTERVAL_MILLIS = 1_000L
        const val BUSY_RETRY_MILLIS = 20L
    }
}
