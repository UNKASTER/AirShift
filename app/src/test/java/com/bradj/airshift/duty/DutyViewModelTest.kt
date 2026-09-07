package com.bradj.airshift.duty

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.bradj.airshift.api.AirportPoint
import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.api.LiveFlightRefresher
import com.bradj.airshift.api.LiveRefreshResult
import com.bradj.airshift.api.dutyWindowLookups
import com.bradj.airshift.api.inboundLookupDate
import com.bradj.airshift.api.lookupFallbackDate
import com.bradj.airshift.api.refreshIndices
import com.bradj.airshift.api.refreshLookups
import com.bradj.airshift.api.withLiveInfo
import com.bradj.airshift.data.DutyCompletion
import com.bradj.airshift.data.RosterRepository
import com.bradj.airshift.data.RosterSnapshot
import com.bradj.airshift.location.AirportMatch
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.model.dutyWindowIndices
import com.bradj.airshift.model.shift.ManualShiftGroup
import com.bradj.airshift.model.shift.ShiftCalibration
import com.bradj.airshift.model.shift.ShiftTeam
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ScheduleSummary
import com.bradj.airshift.specialservice.SpecialServiceState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

/**
 * 从 DutyWindowRefreshInstrumentedTest / SharedExcelImportOwnerInstrumentedTest 移植的编排层场景，
 * 在 JVM 上以假端口运行，不需要设备。前台自动刷新循环仍由 ForegroundFlightRefreshEffect 的真机测试覆盖，
 * 这里直接调用 refreshCurrentAssignments(automatic = true) 代表一次循环触发。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DutyViewModelTest {
    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")
    private val baseTime: LocalDateTime = LocalDateTime.of(2026, 9, 4, 12, 0)
    private val clock: Clock = Clock.fixed(baseTime.atZone(zone).toInstant(), zone)
    private val dispatcher = StandardTestDispatcher()
    private val store = FakeRosterRepository(clock)
    private val refresher = FakeLiveFlightRefresher()
    private val configurations = mutableListOf<Boolean>()
    private val viewModelStore = ViewModelStore()
    private var fakeClockMillis = 0L

    private lateinit var viewModel: DutyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        store.userName = TEST_USER_NAME
        store.variFlightApiKey = TEST_API_KEY
        viewModel = ViewModelProvider(viewModelStore, DutyViewModel.factory(ports()))[DutyViewModel::class.java]
        viewModel.onForegrounded()
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun automaticAndManualRefreshOnlyRequestBothLegsOfTheCurrentAndNextDuty() {
        val roster = duties(3).mapIndexed { index, duty ->
            if (index == 0) duty.copy(inboundFlight = "ZZ1001", scheduledArrival = baseTime) else duty
        }
        store.replaceAssignments(roster)
        viewModel.onForegrounded()

        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()
        val expected = lookups(roster.take(2))
        assertEquals(3, expected.size)
        assertEquals(expected, refresher.requests[0].targets)
        refresher.finish(0)
        runCurrent()
        assertEquals(roster[2], store.loadSnapshot().assignments[2])
        assertEquals("TEST-STAND", store.loadSnapshot().assignments[0].departureStand)

        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()
        assertEquals(expected, refresher.requests[1].targets)
        refresher.finish(1)
        runCurrent()

        viewModel.refreshCurrentAssignments(automatic = false)
        runCurrent()
        assertEquals(3, refresher.requests.size)
        assertEquals(expected, refresher.requests[2].targets)
        assertTrue(refresher.requests.all { it.generation == store.rosterGeneration })
        assertTrue(refresher.requests.all { it.scope == FlightRefreshScope.DUTY_WINDOW })
        assertTrue(refresher.requests.all { it.apiKey == TEST_API_KEY })
    }

    @Test
    fun completingADutyOnlyFetchesTheNewEntryAndAnOrdinaryWriteDoesNotFetchAgain() {
        val roster = duties(4)
        store.replaceAssignments(roster)
        viewModel.onForegrounded()
        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()
        refresher.finish(0)
        runCurrent()

        viewModel.completeCurrentDuty()
        runCurrent()
        assertEquals(2, refresher.requests.size)
        assertEquals(lookups(listOf(roster[2])), refresher.requests[1].targets)
        assertEquals(FlightRefreshScope.DUTY_WINDOW, refresher.requests[1].scope)
        assertEquals(1, store.currentDutyIndex)
        refresher.finish(1)
        runCurrent()

        assertEquals(2, refresher.requests.size)
        assertEquals("TEST-STAND", store.loadSnapshot().assignments[2].departureStand)
        assertEquals(roster[3], store.loadSnapshot().assignments[3])

        viewModel.completeCurrentDuty()
        runCurrent()
        assertEquals(3, refresher.requests.size)
        assertEquals(lookups(listOf(roster[3])), refresher.requests[2].targets)
    }

    @Test
    fun repeatedCompletionDuringAnInFlightRefreshQueuesOnlyTheLatestWindow() {
        val roster = duties(6)
        store.replaceAssignments(roster)
        viewModel.onForegrounded()
        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()

        viewModel.completeCurrentDuty()
        viewModel.completeCurrentDuty()
        runCurrent()
        assertEquals(1, refresher.requests.size)
        assertEquals(2, store.currentDutyIndex)

        refresher.finish(0)
        runCurrent()
        assertEquals(2, refresher.requests.size)
        assertEquals(lookups(roster.subList(2, 4)), refresher.requests[1].targets)

        viewModel.completeCurrentDuty()
        viewModel.completeCurrentDuty()
        runCurrent()
        assertEquals(2, refresher.requests.size)
        refresher.finish(1)
        runCurrent()
        assertEquals(3, refresher.requests.size)
        assertEquals(lookups(roster.subList(4, 6)), refresher.requests[2].targets)
        assertEquals(4, store.currentDutyIndex)
    }

    @Test
    fun allCompletedDutiesStopAutomaticRefreshButManualRefreshStillUpdatesThem() {
        val roster = duties(2)
        store.replaceAssignments(roster)
        viewModel.onForegrounded()
        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()

        viewModel.completeCurrentDuty()
        viewModel.completeCurrentDuty()
        runCurrent()
        assertTrue(viewModel.uiState.value.assignments.dutyWindowIndices(viewModel.uiState.value.manuallyCompletedCount, baseTime).isEmpty())
        refresher.finish(0)
        runCurrent()
        assertFalse(viewModel.uiState.value.autoRefreshEligible)
        assertFalse(configurations.last())

        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()
        assertEquals(1, refresher.requests.size)

        viewModel.refreshCurrentAssignments(automatic = false)
        runCurrent()
        assertEquals(2, refresher.requests.size)
        assertEquals(lookups(roster), refresher.requests[1].targets)
        assertEquals(FlightRefreshScope.ALL_ROSTER, refresher.requests[1].scope)
        val generation = store.rosterGeneration
        refresher.finish(1)
        runCurrent()
        assertEquals(listOf("TEST-STAND", "TEST-STAND"), store.loadSnapshot().assignments.map { it.departureStand })
        assertEquals(2, store.currentDutyIndex)
        assertEquals(generation, store.rosterGeneration)
        assertFalse(configurations.last())
    }

    @Test
    fun manualRefreshCanUpdateDutiesThatAutomaticallyCompletedPastTheGracePeriod() {
        val roster = duties(3).map { it.copy(scheduledDeparture = baseTime.minusHours(4)) }
        store.replaceAssignments(roster)
        viewModel.onForegrounded()

        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()
        assertTrue(refresher.requests.isEmpty())
        assertFalse(viewModel.uiState.value.autoRefreshEligible)

        viewModel.refreshCurrentAssignments(automatic = false)
        runCurrent()
        assertEquals(lookups(roster), refresher.requests.single().targets)
        assertEquals(FlightRefreshScope.ALL_ROSTER, refresher.requests.single().scope)
        val generation = store.rosterGeneration
        refresher.finish(0)
        runCurrent()
        assertEquals(List(3) { "TEST-STAND" }, store.loadSnapshot().assignments.map { it.departureStand })
        assertEquals(0, store.currentDutyIndex)
        assertEquals(generation, store.rosterGeneration)
    }

    @Test
    fun anEmptyRosterDoesNotRequestFlightsForAutomaticOrManualRefresh() {
        viewModel.refreshCurrentAssignments(automatic = true)
        viewModel.refreshCurrentAssignments(automatic = false)
        runCurrent()
        assertTrue(refresher.requests.isEmpty())
        assertTrue(store.loadSnapshot().assignments.isEmpty())
        assertEquals("请先导入排班图片或 Excel 文件", viewModel.uiState.value.statusMessage)
    }

    @Test
    fun importingANewRosterRefreshesOnlyItsFirstTwoDutiesWithoutAnImmediateDuplicate() {
        val roster = duties(4)
        val reads = mutableListOf<String>()
        viewModel.importExcel(RosterSource { name ->
            reads += name
            RosterParseResult(roster, baseTime.toLocalDate(), emptyList())
        })
        runCurrent()

        assertEquals(listOf(TEST_USER_NAME), reads)
        assertEquals(lookups(roster.take(2)), refresher.requests.single().targets)
        assertEquals(FlightRefreshScope.DUTY_WINDOW, refresher.requests.single().scope)
        assertEquals(store.rosterGeneration, refresher.requests.single().generation)
        assertTrue(viewModel.uiState.value.isWorking)
        refresher.finish(0)
        runCurrent()

        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(1, refresher.requests.size)
        assertEquals(4, store.loadSnapshot().assignments.size)
        assertEquals(roster.drop(2), store.loadSnapshot().assignments.drop(2))
        assertEquals(0, store.currentDutyIndex)
        // 实时数据只会给前两项写入机位字段，其余字段与导入结果一致。
        assertEquals(
            roster,
            viewModel.uiState.value.assignments.map {
                it.copy(departureStand = null, arrivalStand = null, outboundArrivalStand = null)
            },
        )
        assertTrue(configurations.last())
    }

    @Test
    fun importingWithoutAnApiKeyKeepsScheduleTimesAndSkipsTheLiveRefresh() {
        store.variFlightApiKey = null
        val roster = duties(2)
        viewModel.importExcel(RosterSource { RosterParseResult(roster, baseTime.toLocalDate(), listOf("警告")) })
        runCurrent()

        assertTrue(refresher.requests.isEmpty())
        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(roster, store.loadSnapshot().assignments)
        assertEquals(listOf("警告"), viewModel.uiState.value.warnings)
        assertTrue(viewModel.uiState.value.statusMessage.orEmpty().contains("尚未配置飞常准 API Key"))
        assertFalse(configurations.last())
    }

    @Test
    fun aRefreshResultForAReplacedRosterIsIgnored() {
        val roster = duties(2)
        store.replaceAssignments(roster)
        viewModel.onForegrounded()
        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()
        assertEquals(1, refresher.requests.size)

        // 另一写入方（如后台 Worker 之后的新导入）替换了排班。
        val replacement = duties(2).map { it.copy(aircraftRegistration = "NEW-${it.aircraftRegistration}") }
        store.replaceAssignments(replacement)
        refresher.finish(0)
        runCurrent()

        assertEquals(replacement, store.loadSnapshot().assignments)
        assertEquals("排班已变化，已忽略旧排班的刷新结果", viewModel.uiState.value.statusMessage)
        assertEquals(replacement, viewModel.uiState.value.assignments)
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun aClearedViewModelCannotCommitAnInFlightImport() {
        val roster = duties(1)
        val gate = CompletableDeferred<RosterParseResult>()
        val originalGeneration = store.rosterGeneration
        var finished = 0
        viewModel.importExcel(
            RosterSource { gate.await() },
            ImportAttempt(isCurrent = { true }, onFinished = { finished++ }),
        )
        runCurrent()
        assertTrue(viewModel.uiState.value.isWorking)

        viewModelStore.clear()
        gate.complete(RosterParseResult(roster, baseTime.toLocalDate(), emptyList()))
        runCurrent()

        assertEquals(originalGeneration, store.rosterGeneration)
        assertTrue(store.loadSnapshot().assignments.isEmpty())
        assertEquals(0, finished)
        assertTrue(refresher.requests.isEmpty())
    }

    @Test
    fun aStaleImportAttemptIsDroppedBeforeItTouchesTheRoster() {
        val roster = duties(1)
        val gate = CompletableDeferred<RosterParseResult>()
        var attemptCurrent = true
        var finished = 0
        viewModel.importExcel(
            RosterSource { gate.await() },
            ImportAttempt(isCurrent = { attemptCurrent }, onFinished = { finished++ }),
        )
        runCurrent()

        attemptCurrent = false
        gate.complete(RosterParseResult(roster, baseTime.toLocalDate(), emptyList()))
        runCurrent()

        assertTrue(store.loadSnapshot().assignments.isEmpty())
        assertEquals(0, finished)
        assertFalse(viewModel.uiState.value.isWorking)
    }

    @Test
    fun savingSettingsUpdatesTheNameAndKeyAndReconfiguresBackgroundRefresh() {
        store.replaceAssignments(duties(2))
        viewModel.onForegrounded()
        store.variFlightApiKey = null
        viewModel.saveSettings(name = " 新名字 ", apiKey = "fresh-key")
        runCurrent()

        assertEquals("新名字", viewModel.uiState.value.userName)
        assertEquals("fresh-key", store.variFlightApiKey)
        assertTrue(viewModel.uiState.value.hasVariFlightApiKey)
        assertTrue(configurations.last())

        viewModel.clearApiKey()
        runCurrent()
        assertNull(store.variFlightApiKey)
        assertFalse(viewModel.uiState.value.hasVariFlightApiKey)
        assertFalse(configurations.last())
    }

    @Test
    fun importingTomorrowsRosterSavesItWithoutRefreshingAndSaysWhenTrackingStarts() {
        // 头天晚上导入明天的排班：不上班的时候不联网，明天首个任务（13:00）前 3 小时才开始自动跟踪。
        val roster = duties(2).map { it.copy(scheduledDeparture = checkNotNull(it.scheduledDeparture).plusDays(1)) }
        val rosterDate = baseTime.toLocalDate().plusDays(1)
        viewModel.importExcel(RosterSource { RosterParseResult(roster, rosterDate, emptyList()) })
        runCurrent()

        assertTrue(refresher.requests.isEmpty())
        assertFalse(viewModel.uiState.value.isWorking)
        assertEquals(roster, store.loadSnapshot().assignments)
        assertEquals("排班已保存，9/5 10:00 起自动跟踪航班动态", viewModel.uiState.value.statusMessage)
        // 后台周期任务照常登记（首轮延迟到跟踪起点），到点后由 Worker 自己重算窗口。
        assertTrue(configurations.last())
    }

    @Test
    fun automaticRefreshBeforeTheTrackingWindowMakesNoRequestButAManualPullStillCan() {
        val roster = duties(2).map { it.copy(scheduledDeparture = checkNotNull(it.scheduledDeparture).plusDays(1)) }
        store.replaceAssignments(roster)
        viewModel.onForegrounded()

        viewModel.refreshCurrentAssignments(automatic = true)
        runCurrent()
        assertTrue(refresher.requests.isEmpty())
        assertFalse(viewModel.uiState.value.isWorking)

        viewModel.refreshCurrentAssignments(automatic = false)
        runCurrent()
        assertEquals(FlightRefreshScope.ALL_ROSTER, refresher.requests.single().scope)
        assertEquals(lookups(roster), refresher.requests.single().targets)
    }

    private fun runCurrent() = dispatcher.scheduler.runCurrent()

    private fun ports() = DutyPorts(
        store = store,
        specialServices = FakeSpecialServices(),
        reminders = FakeReminders(),
        flightRefresher = refresher,
        airportLocator = AirportLocatorPort { _, _ -> error("The fake response contains no airport candidates") },
        apiKeyTester = ApiKeyTester { _, _, _, _ -> error("Connection tests are not part of these scenarios") },
        configureBackgroundRefresh = { configurations += it },
        notifyWidget = {},
        isNotificationAccessGranted = { false },
        hasPermission = { true },
        refreshClock = { fakeClockMillis },
        clock = clock,
    )

    private fun duties(count: Int): List<RosterAssignment> = (1..count).map { index ->
        RosterAssignment(
            aircraftRegistration = "B000$index",
            aircraftType = "320",
            inboundFlight = null,
            origin = null,
            scheduledArrival = null,
            outboundFlight = "ZZ200$index",
            destination = "测试到达",
            scheduledDeparture = baseTime.plusHours(index.toLong()),
            assignees = TEST_USER_NAME,
        )
    }

    private fun lookups(roster: List<RosterAssignment>): Set<FlightLookup> = roster.flatMap { duty ->
        listOfNotNull(
            duty.inboundFlight?.let { FlightLookup.of(it, duty.inboundLookupDate(baseTime.toLocalDate())) },
            duty.outboundFlight?.let { FlightLookup.of(it, checkNotNull(duty.scheduledDeparture).toLocalDate()) },
        )
    }.toSet()

    private companion object {
        const val TEST_USER_NAME = "测试甲"
        const val TEST_API_KEY = "synthetic-jvm-test-key-never-sent"
    }
}

/** 内存版 [RosterRepository]，语义与 RosterStore 一致（generation、人工前缀、两项窗口合并）。 */
private class FakeRosterRepository(private val clock: Clock) : RosterRepository {
    private var assignments: List<RosterAssignment> = emptyList()
    private var generation = 0L
    private var dutyIndex = 0

    override var userName: String? = null
    override var variFlightApiKey: String? = null
    override val hasVariFlightApiKey: Boolean get() = variFlightApiKey != null
    override fun clearVariFlightApiKey() {
        variFlightApiKey = null
    }

    override var shiftReportMarginMinutes: Int = 15
    override var manualShiftTeam: ShiftTeam? = null
    override var manualShiftGroup: ManualShiftGroup? = null
    override var shiftCalibration: ShiftCalibration? = null

    override val currentDutyIndex: Int get() = dutyIndex
    override val rosterGeneration: Long get() = generation

    override fun loadSnapshot(): RosterSnapshot = RosterSnapshot(assignments, generation, dutyIndex)

    override fun replaceAssignments(assignments: List<RosterAssignment>): Long {
        this.assignments = assignments
        dutyIndex = 0
        generation += 1
        return generation
    }

    override fun completeCurrentDuty(expectedGeneration: Long, expectedDutyIndex: Int, now: LocalDateTime): DutyCompletion? {
        if (generation != expectedGeneration) return null
        val before = loadSnapshot()
        val currentIndex = before.assignments.dutyWindowIndices(before.manuallyCompletedCount, now).firstOrNull()
        if (currentIndex != expectedDutyIndex) return null
        val oldWindow = before.assignments.dutyWindowLookups(before.manuallyCompletedCount, now)
        dutyIndex = (currentIndex + 1).coerceIn(0, assignments.size)
        val after = loadSnapshot()
        val newWindow = after.assignments.dutyWindowLookups(after.manuallyCompletedCount, now)
        return DutyCompletion(after, newWindow - oldWindow)
    }

    override fun mergeLiveInfoIfGeneration(
        live: Map<FlightLookup, List<FlightInfo>>,
        expectedGeneration: Long,
        fallbackDate: LocalDate,
        refreshedAtEpochMillis: Long?,
        scope: FlightRefreshScope,
    ): RosterSnapshot? {
        val current = loadSnapshot()
        if (current.generation != expectedGeneration) return null
        val now = LocalDateTime.now(clock)
        val targetIndices = current.assignments.refreshIndices(current.manuallyCompletedCount, scope, now).toSet()
        val allowed = current.assignments.refreshLookups(current.manuallyCompletedCount, scope, now)
        val relevant = live.filterKeys { it in allowed }
        if (relevant.isEmpty()) return current
        val liveFallbackDate = current.assignments.lookupFallbackDate(fallbackDate)
        assignments = current.assignments.mapIndexed { index, assignment ->
            if (index in targetIndices) assignment.withLiveInfo(relevant, liveFallbackDate) else assignment
        }
        return loadSnapshot()
    }

    override fun runIfGenerationCurrent(expectedGeneration: Long, action: () -> Unit): Boolean {
        if (generation != expectedGeneration) return false
        action()
        return true
    }
}

private class FakeLiveFlightRefresher : LiveFlightRefresher {
    class Recorded(
        val generation: Long,
        val apiKey: String,
        val targets: Set<FlightLookup>,
        val scope: FlightRefreshScope,
        val continuation: Continuation<LiveRefreshResult>,
    )

    val requests = mutableListOf<Recorded>()

    override suspend fun refresh(
        generation: Long,
        apiKey: String,
        lookups: Set<FlightLookup>,
        scope: FlightRefreshScope,
    ): LiveRefreshResult = suspendCancellableCoroutine { continuation ->
        requests += Recorded(generation, apiKey, lookups, scope, continuation)
    }

    fun finish(index: Int) {
        val request = requests[index]
        val live = request.targets.associateWith { lookup -> listOf(flight(lookup.flightNumber)) }
        request.continuation.resume(
            LiveRefreshResult(
                live = live,
                airports = emptyList<AirportPoint>(),
                errors = emptyList(),
                attemptedCount = request.targets.size,
                refreshedCount = live.size,
                fallbackDate = LocalDate.of(2026, 9, 4),
            ),
        )
    }

    private fun flight(flightNumber: String) = FlightInfo(
        flightNumber = flightNumber,
        origin = null,
        destination = null,
        plannedDeparture = null,
        estimatedDeparture = null,
        actualDeparture = null,
        plannedArrival = null,
        estimatedArrival = null,
        actualArrival = null,
        actualOffBlock = null,
        gateClosedObservedAt = null,
        boardingGate = null,
        departureStand = "TEST-STAND",
        arrivalStand = "TEST-STAND",
        arrivalBridge = null,
    )
}

private class FakeSpecialServices : SpecialServicePort {
    private val mutableState = MutableStateFlow(SpecialServiceState())
    override val state: StateFlow<SpecialServiceState> = mutableState
    val rosters = mutableListOf<List<RosterAssignment>>()
    override fun onRosterChanged(assignments: List<RosterAssignment>) {
        rosters += assignments
    }
}

private class FakeReminders : ReminderPort {
    override fun canScheduleExactAlarms(): Boolean = true
    override fun scheduleAll(assignments: List<RosterAssignment>): ScheduleSummary =
        ScheduleSummary(scheduledCount = assignments.size, skippedPastCount = 0, exactAlarmsAllowed = true)
    override fun cancelAll(assignments: List<RosterAssignment>) = Unit
}

@Suppress("unused")
private val unusedImports = listOf(Instant::class, AirportMatch::class)
