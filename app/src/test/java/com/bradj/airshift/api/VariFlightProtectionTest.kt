package com.bradj.airshift.api

import com.bradj.airshift.model.RosterAssignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class VariFlightProtectionTest {
    @Test
    fun rateLimiterReleasesCapacityAfterTheWindow() {
        var now = 1_000L
        val limiter = SlidingWindowRateLimiter(limit = 30, windowMillis = 100L) { now }

        repeat(30) { assertTrue(limiter.tryAcquire()) }
        assertFalse(limiter.tryAcquire())
        now += 100L
        assertTrue(limiter.tryAcquire())
    }

    @Test
    fun cacheDeduplicatesMatchingFlightAndDateUntilExpiry() {
        var now = 1_000L
        var upstreamCalls = 0
        val cache = FlightResponseCache(ttlMillis = 100L) { now }
        val lookup = FlightLookup.of("MU1234", LocalDate.of(2026, 8, 23))

        assertEquals("1", cache.getOrFetch(lookup) { listOf(flight("1")).also { upstreamCalls++ } }.first().arrivalBridge)
        assertEquals("1", cache.getOrFetch(lookup) { listOf(flight("2")).also { upstreamCalls++ } }.first().arrivalBridge)
        assertEquals(1, upstreamCalls)

        now += 100L
        assertEquals("2", cache.getOrFetch(lookup) { listOf(flight("2")).also { upstreamCalls++ } }.first().arrivalBridge)
        assertEquals(2, upstreamCalls)
    }

    @Test
    fun cacheSeparatesDatesAndDoesNotRetainFailures() {
        val cache = FlightResponseCache(ttlMillis = 100L)
        val firstDate = FlightLookup.of("MU1234", LocalDate.of(2026, 8, 23))
        val secondDate = FlightLookup.of("MU1234", LocalDate.of(2026, 8, 24))
        var calls = 0

        assertThrows(IllegalStateException::class.java) {
            cache.getOrFetch(firstDate) {
                calls++
                error("temporary failure")
            }
        }
        cache.getOrFetch(firstDate) { listOf(flight("first")).also { calls++ } }
        cache.getOrFetch(secondDate) { listOf(flight("second")).also { calls++ } }

        assertEquals(3, calls)
    }

    @Test
    fun cacheCoalescesConcurrentMatchingRequests() {
        val cache = FlightResponseCache(ttlMillis = 1_000L)
        val lookup = FlightLookup.of("MU1234", LocalDate.of(2026, 8, 23))
        val calls = AtomicInteger()
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val futures = (1..4).map {
                pool.submit<List<FlightInfo>> {
                    start.await()
                    cache.getOrFetch(lookup) {
                        calls.incrementAndGet()
                        listOf(flight("shared"))
                    }
                }
            }
            start.countDown()
            futures.forEach { assertEquals("shared", it.get(2, TimeUnit.SECONDS).first().arrivalBridge) }
            assertEquals(1, calls.get())
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun aSlowLoadForOneFlightDoesNotBlockAnotherFlightInTheSameHashBin() {
        // 一个航班的上游请求最长 20 秒；它不能把与之同桶的其他航班一起挂住。
        val cache = FlightResponseCache(ttlMillis = 1_000L)
        val date = LocalDate.of(2026, 8, 23)
        val slow = FlightLookup.of("MU1234", date)
        val neighbour = (1000..9999).asSequence()
            .map { FlightLookup.of("MU$it", date) }
            .first { it != slow && hashBin(it) == hashBin(slow) }
        val slowStarted = CountDownLatch(1)
        val releaseSlow = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val slowFuture = pool.submit<List<FlightInfo>> {
                cache.getOrFetch(slow) {
                    slowStarted.countDown()
                    assertTrue(releaseSlow.await(5, TimeUnit.SECONDS))
                    listOf(flight("slow"))
                }
            }
            assertTrue(slowStarted.await(2, TimeUnit.SECONDS))
            val neighbourFuture = pool.submit<List<FlightInfo>> {
                cache.getOrFetch(neighbour) { listOf(flight("neighbour")) }
            }
            assertEquals("neighbour", neighbourFuture.get(1, TimeUnit.SECONDS).first().arrivalBridge)
            releaseSlow.countDown()
            assertEquals("slow", slowFuture.get(2, TimeUnit.SECONDS).first().arrivalBridge)
        } finally {
            releaseSlow.countDown()
            pool.shutdownNow()
        }
    }

    /** ConcurrentHashMap 默认 16 个桶，桶号 = spread(hash) & 15，spread(h) = (h ^ (h >>> 16)) & 0x7fffffff。 */
    private fun hashBin(lookup: FlightLookup): Int {
        val h = lookup.hashCode()
        return ((h xor (h ushr 16)) and 0x7fffffff) and 15
    }

    @Test
    fun protectionCountsCacheHitsBeforeServingCachedResponses() {
        val protection = VariFlightRequestProtection(
            rateLimiter = SlidingWindowRateLimiter(limit = 1),
            cache = FlightResponseCache(ttlMillis = 1_000L),
        )
        val lookup = FlightLookup.of("MU1234", LocalDate.of(2026, 8, 23))

        protection.fetch(lookup) { listOf(flight("cached")) }
        val error = assertThrows(VariFlightClientException::class.java) {
            protection.fetch(lookup) { listOf(flight("unused")) }
        }

        assertTrue(error.message.orEmpty().contains("每分钟 30 次"))
    }

    @Test
    fun requestWaitingForAFailedCacheLoadRechecksTheWindowBeforeCallingUpstream() {
        val protection = VariFlightRequestProtection(
            rateLimiter = SlidingWindowRateLimiter(limit = 30),
            cache = FlightResponseCache(ttlMillis = 120_000L),
        )
        val lookup = FlightLookup.of("MU1234", LocalDate.of(2026, 8, 23))
        val firstFailure = VariFlightClientException("连接飞常准超时，请稍后重试", retryable = true)
        val firstStarted = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondCheckedWindow = CountDownLatch(1)
        val secondThread = AtomicReference<Thread>()
        val stillCurrent = AtomicBoolean(true)
        val upstreamCalls = AtomicInteger()
        val pool = Executors.newFixedThreadPool(2)
        try {
            val first = pool.submit<List<FlightInfo>> {
                protection.fetch(lookup) {
                    upstreamCalls.incrementAndGet()
                    firstStarted.countDown()
                    assertTrue(releaseFirst.await(5, TimeUnit.SECONDS))
                    throw firstFailure
                }
            }
            assertTrue(firstStarted.await(2, TimeUnit.SECONDS))
            val second = pool.submit<FlightRefreshBatchResult> {
                secondThread.set(Thread.currentThread())
                refreshFlightBatch(
                    setOf(lookup),
                    isCurrent = {
                        stillCurrent.get().also { current ->
                            assertTrue(current)
                            secondCheckedWindow.countDown()
                        }
                    },
                ) { request ->
                    protection.fetch(request, isCurrent = stillCurrent::get) {
                        upstreamCalls.incrementAndGet()
                        listOf(flight("must not be queried"))
                    }
                }
            }
            assertTrue(secondCheckedWindow.await(2, TimeUnit.SECONDS))
            // Confirm the second caller is really parked on the in-flight load, rather than only racing two submissions.
            val parkedStates = setOf(Thread.State.BLOCKED, Thread.State.WAITING, Thread.State.TIMED_WAITING)
            val blockedDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
            while (secondThread.get().state !in parkedStates && System.nanoTime() < blockedDeadline) {
                Thread.yield()
            }
            assertTrue(secondThread.get().state in parkedStates)
            stillCurrent.set(false)
            releaseFirst.countDown()

            val failure = assertThrows(ExecutionException::class.java) { first.get(2, TimeUnit.SECONDS) }
            assertSame(firstFailure, failure.cause)
            val result = second.get(2, TimeUnit.SECONDS)
            assertEquals(1, upstreamCalls.get())
            assertEquals(0, result.attemptedCount)
            assertTrue(result.live.isEmpty())
            assertTrue(result.errors.isEmpty())
            assertEquals(0, result.retryableFailures)
        } finally {
            releaseFirst.countDown()
            pool.shutdownNow()
        }
    }

    @Test
    fun liveInfoUsesFlightNumberAndDateAsTheLookupKey() {
        val firstDate = LocalDate.of(2026, 8, 23)
        val secondDate = firstDate.plusDays(1)
        val firstArrival = LocalDateTime.of(2026, 8, 23, 12, 10)
        val secondArrival = LocalDateTime.of(2026, 8, 24, 13, 20)
        val live = mapOf(
            FlightLookup.of("MU1234", firstDate) to listOf(flight(actualArrival = firstArrival)),
            FlightLookup.of("MU1234", secondDate) to listOf(flight(actualArrival = secondArrival)),
        )

        assertEquals(firstArrival, assignment(firstDate).withLiveInfo(live, firstDate).actualArrival)
        assertEquals(secondArrival, assignment(secondDate).withLiveInfo(live, secondDate).actualArrival)
    }

    private fun flight(
        bridge: String? = null,
        actualArrival: LocalDateTime? = null,
    ) = FlightInfo(
        flightNumber = "MU1234",
        origin = null,
        destination = null,
        plannedDeparture = null,
        estimatedDeparture = null,
        actualDeparture = null,
        plannedArrival = null,
        estimatedArrival = null,
        actualArrival = actualArrival,
        actualOffBlock = null,
        gateClosedObservedAt = null,
        boardingGate = null,
        departureStand = null,
        arrivalStand = null,
        arrivalBridge = bridge,
    )

    private fun assignment(date: LocalDate) = RosterAssignment(
        aircraftRegistration = "B0001",
        aircraftType = "320",
        inboundFlight = "MU1234",
        origin = "出发地",
        scheduledArrival = date.atTime(12, 0),
        outboundFlight = null,
        destination = null,
        scheduledDeparture = null,
        assignees = "测试甲",
    )
}
