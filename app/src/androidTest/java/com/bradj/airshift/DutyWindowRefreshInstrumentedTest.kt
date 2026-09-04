package com.bradj.airshift

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.api.FlightInfo
import com.bradj.airshift.api.FlightLookup
import com.bradj.airshift.api.FlightRefreshScope
import com.bradj.airshift.api.LiveFlightRefresher
import com.bradj.airshift.api.LiveRefreshResult
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.duty.AirportLocatorPort
import com.bradj.airshift.duty.ApiKeyTester
import com.bradj.airshift.duty.DutyPorts
import com.bradj.airshift.duty.DutyViewModel
import com.bradj.airshift.duty.ReminderPort
import com.bradj.airshift.duty.SpecialServicePort
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ReminderScheduler
import com.bradj.airshift.reminder.ScheduleSummary
import com.bradj.airshift.specialservice.NotificationAccess
import com.bradj.airshift.specialservice.SpecialServiceRepository
import com.bradj.airshift.specialservice.SpecialServiceState
import com.bradj.airshift.ui.AirShiftApp
import com.bradj.airshift.ui.DutyNavigationViewModel
import com.bradj.airshift.ui.theme.AirShiftTheme
import com.bradj.airshift.widget.DutyWidgetUpdater
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume

@RunWith(AndroidJUnit4::class)
class DutyWindowRefreshInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val preferencePrefix = "duty-window-${UUID.randomUUID()}-"
    private val isolatedPreferenceNames = mutableSetOf<String>()
    private val createdAssignments = mutableListOf<RosterAssignment>()
    private val requests = CopyOnWriteArrayList<RecordedRefresh>()
    private val refreshConfigurations = CopyOnWriteArrayList<Boolean>()
    private val excelReadCount = AtomicInteger()
    private val queue = SharedExcelImportQueueViewModel(SavedStateHandle())
    private val baseTime = LocalDateTime.now().plusHours(8)
    private lateinit var targetContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var store: RosterStore
    private var showApp by mutableStateOf(true)
    private var contentSet = false
    private var viewModelStore = ViewModelStore()
    private var viewModel by mutableStateOf<DutyViewModel?>(null)
    private lateinit var viewModelFactory: ViewModelProvider.Factory
    private lateinit var excelReader: suspend (Uri, String) -> RosterParseResult

    @Before
    fun isolateStorageAndReplaceNetworkAndWorkerBoundaries() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        isolatedContext = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolatedName = preferencePrefix + name
                isolatedPreferenceNames += isolatedName
                return super.getSharedPreferences(isolatedName, mode)
            }

            override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                if (permission in TEST_GRANTED_PERMISSIONS) PackageManager.PERMISSION_GRANTED
                else super.checkPermission(permission, pid, uid)
        }
        store = RosterStore(isolatedContext)
        store.userName = TEST_USER_NAME
        store.variFlightApiKey = TEST_API_KEY
        composeRule.mainClock.autoAdvance = false
    }

    @After
    fun removeOnlyThisTestsStateAndReminders() {
        if (contentSet) {
            composeRule.runOnIdle { showApp = false }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle { viewModelStore.clear() }
        ReminderScheduler.cancelAll(isolatedContext, createdAssignments)
        isolatedPreferenceNames.forEach { name ->
            check(name.startsWith(preferencePrefix))
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            targetContext.deleteSharedPreferences(name)
        }
    }

    @Test
    fun automaticAndManualRefreshOnlyRequestBothLegsOfTheCurrentAndNextDuty() {
        val roster = duties(3).mapIndexed { index, duty ->
            if (index == 0) duty.copy(inboundFlight = "ZZ1001", scheduledArrival = baseTime) else duty
        }
        store.replaceAssignments(roster)
        showAirShiftApp()

        awaitRequests(1)
        val expected = lookups(roster.take(2))
        assertEquals(3, expected.size)
        assertEquals(expected, requests[0].targets)
        finishRequest(0)
        assertEquals(roster[2], store.loadAssignments()[2])

        composeRule.mainClock.advanceTimeBy(AUTO_REFRESH_INTERVAL_MILLIS)
        awaitRequests(2)
        assertEquals(expected, requests[1].targets)
        finishRequest(1)

        selectSection("全部执勤")
        pullToRefresh()
        awaitRequests(3)
        assertEquals(expected, requests[2].targets)
        assertTrue(requests.all { it.generation == store.rosterGeneration })
        assertTrue(requests.all { it.scope == FlightRefreshScope.DUTY_WINDOW })
    }

    @Test
    fun completingADutyOnlyFetchesTheNewEntryAndAnOrdinaryWriteDoesNotFetchAgain() {
        val roster = duties(4)
        store.replaceAssignments(roster)
        showAirShiftApp()
        awaitRequests(1)
        finishRequest(0)

        selectSection("当前执勤")
        completeCurrentDuty()
        awaitRequests(2)
        assertEquals(lookups(listOf(roster[2])), requests[1].targets)
        assertEquals(FlightRefreshScope.DUTY_WINDOW, requests[1].scope)
        assertEquals(1, store.currentDutyIndex)
        finishRequest(1)

        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        assertEquals(2, requests.size)
        assertEquals("TEST-STAND", store.loadAssignments()[2].departureStand)
        assertEquals(roster[3], store.loadAssignments()[3])

        completeCurrentDuty()
        awaitRequests(3)
        assertEquals(lookups(listOf(roster[3])), requests[2].targets)
    }

    @Test
    fun repeatedCompletionDuringAnInFlightRefreshQueuesOnlyTheLatestWindow() {
        val roster = duties(6)
        store.replaceAssignments(roster)
        showAirShiftApp()
        awaitRequests(1)

        selectSection("当前执勤")
        completeCurrentDuty()
        completeCurrentDuty()
        assertEquals(1, requests.size)
        assertEquals(2, store.currentDutyIndex)

        finishRequest(0)
        awaitRequests(2)
        assertEquals(lookups(roster.subList(2, 4)), requests[1].targets)

        completeCurrentDuty()
        completeCurrentDuty()
        assertEquals(2, requests.size)
        finishRequest(1)
        awaitRequests(3)
        assertEquals(lookups(roster.subList(4, 6)), requests[2].targets)
        assertEquals(4, store.currentDutyIndex)
    }

    @Test
    fun anOverduePeriodicRefreshIncludesTheRetainedDutyWhenACompletionWasQueuedWhileBusy() {
        val roster = duties(4)
        store.replaceAssignments(roster)
        showAirShiftApp()
        awaitRequests(1)
        assertEquals(lookups(roster.take(2)), requests[0].targets)

        selectSection("当前执勤")
        completeCurrentDuty()
        composeRule.mainClock.advanceTimeBy(AUTO_REFRESH_INTERVAL_MILLIS + 1_000L)
        composeRule.waitForIdle()
        assertEquals(1, requests.size)

        finishRequest(0)
        awaitRequests(2)
        // B is due for its periodic update; the pending arrival of C must not postpone B.
        assertEquals(lookups(roster.subList(1, 3)), requests[1].targets)
        finishRequest(1)
        composeRule.mainClock.advanceTimeBy(16_000L)
        composeRule.waitForIdle()
        assertEquals(2, requests.size)
    }

    @Test
    fun allCompletedDutiesStopAutomaticRefreshButManualRefreshStillUpdatesThem() {
        val roster = duties(2)
        store.replaceAssignments(roster)
        showAirShiftApp()
        awaitRequests(1)

        selectSection("当前执勤")
        completeCurrentDuty()
        completeCurrentDuty()
        composeRule.onNodeWithText("今日执勤全部完成").assertIsDisplayed()
        finishRequest(0)
        composeRule.mainClock.advanceTimeBy(AUTO_REFRESH_INTERVAL_MILLIS)
        composeRule.waitForIdle()

        assertEquals(1, requests.size)
        assertFalse(refreshConfigurations.last())
        selectSection("全部执勤")
        pullToRefresh()
        awaitRequests(2)
        assertEquals(lookups(roster), requests[1].targets)
        assertEquals(FlightRefreshScope.ALL_ROSTER, requests[1].scope)
        val generation = store.rosterGeneration
        finishRequest(1)
        assertEquals(listOf("TEST-STAND", "TEST-STAND"), store.loadAssignments().map { it.departureStand })
        assertEquals(2, store.currentDutyIndex)
        assertEquals(generation, store.rosterGeneration)
        composeRule.mainClock.advanceTimeBy(AUTO_REFRESH_INTERVAL_MILLIS)
        composeRule.waitForIdle()
        assertEquals(2, requests.size)
        assertFalse(refreshConfigurations.last())
    }

    @Test
    fun manualRefreshCanUpdateDutiesThatAutomaticallyCompletedPastTheGracePeriod() {
        val pastDeparture = LocalDateTime.now().minusHours(4)
        val roster = duties(3).map { it.copy(scheduledDeparture = pastDeparture) }
        store.replaceAssignments(roster)
        showAirShiftApp()
        composeRule.mainClock.advanceTimeBy(AUTO_REFRESH_INTERVAL_MILLIS)
        composeRule.waitForIdle()
        assertTrue(requests.isEmpty())
        assertFalse(refreshConfigurations.last())

        selectSection("全部执勤")
        pullToRefresh()
        awaitRequests(1)
        assertEquals(lookups(roster), requests.single().targets)
        assertEquals(FlightRefreshScope.ALL_ROSTER, requests.single().scope)
        val generation = store.rosterGeneration
        finishRequest(0)
        assertEquals(List(3) { "TEST-STAND" }, store.loadAssignments().map { it.departureStand })
        assertEquals(0, store.currentDutyIndex)
        assertEquals(generation, store.rosterGeneration)
        composeRule.mainClock.advanceTimeBy(AUTO_REFRESH_INTERVAL_MILLIS)
        composeRule.waitForIdle()
        assertEquals(1, requests.size)
        assertFalse(refreshConfigurations.last())
    }

    @Test
    fun anEmptyRosterDoesNotRequestFlightsForAutomaticOrManualRefresh() {
        showAirShiftApp()
        composeRule.mainClock.advanceTimeBy(AUTO_REFRESH_INTERVAL_MILLIS)
        composeRule.waitForIdle()
        selectSection("全部执勤")
        pullToRefresh()
        assertTrue(requests.isEmpty())
        assertTrue(store.loadAssignments().isEmpty())
    }

    @Test
    fun importingANewRosterRefreshesOnlyItsFirstTwoDutiesWithoutAnImmediateDuplicate() {
        val roster = duties(4)
        val sharedUri = Uri.parse("content://com.example.airshift.test/$preferencePrefix/roster.xlsx")
        queue.enqueue(Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, sharedUri)
        })
        showAirShiftApp(RosterParseResult(roster, baseTime.toLocalDate(), emptyList()))

        awaitRequests(1)
        assertEquals(lookups(roster.take(2)), requests.single().targets)
        assertEquals(FlightRefreshScope.DUTY_WINDOW, requests.single().scope)
        assertEquals(store.rosterGeneration, requests.single().generation)
        finishRequest(0)
        // The foreground loop can have been waiting for import to release its busy state.
        composeRule.mainClock.advanceTimeBy(16_000L)
        composeRule.waitForIdle()

        assertEquals(1, requests.size)
        assertTrue(queue.pending.value.isEmpty())
        assertEquals(4, store.loadAssignments().size)
        assertEquals(roster.drop(2), store.loadAssignments().drop(2))
        assertEquals(0, store.currentDutyIndex)
    }

    @Test
    fun aSavedSharedImportIsNotRepeatedWhenThePageIsRecreatedBeforeItsRefreshReturns() {
        val roster = duties(4)
        queue.enqueue(Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME_TYPE
            putExtra(
                Intent.EXTRA_STREAM,
                Uri.parse("content://com.example.airshift.test/$preferencePrefix/recreate.xlsx"),
            )
        })
        showAirShiftApp(RosterParseResult(roster, baseTime.toLocalDate(), emptyList()))
        awaitRequests(1)
        assertTrue(queue.pending.value.isEmpty())
        assertEquals(1, excelReadCount.get())
        val importGeneration = store.rosterGeneration

        selectSection("当前执勤")
        completeCurrentDuty()
        assertEquals(1, store.currentDutyIndex)
        assertEquals(1, requests.size)
        recreateAppWithANewViewModel()
        awaitRequests(2)

        assertEquals(1, excelReadCount.get())
        assertTrue(queue.pending.value.isEmpty())
        assertEquals(importGeneration, store.rosterGeneration)
        assertEquals(1, store.currentDutyIndex)
        assertEquals(lookups(roster.subList(1, 3)), requests[1].targets)
        val restoredSnapshot = store.loadSnapshot()
        finishRequest(0)
        assertEquals(restoredSnapshot, store.loadSnapshot())
        assertEquals(2, requests.size)

        finishRequest(1)
        assertEquals(importGeneration, store.rosterGeneration)
        assertEquals(1, store.currentDutyIndex)
        assertEquals(1, excelReadCount.get())
    }

    private fun showAirShiftApp(importResult: RosterParseResult? = null) {
        createdAssignments += store.loadAssignments()
        createdAssignments += importResult?.assignments.orEmpty()
        // Do not initialize or reset the process singleton used by other instrumented tests.
        val repository = SpecialServiceRepository::class.java.getDeclaredConstructor(Context::class.java)
            .apply { isAccessible = true }
            .newInstance(isolatedContext)
        val ports = DutyPorts(
            store = store,
            specialServices = object : SpecialServicePort {
                override val state: StateFlow<SpecialServiceState> = repository.state
                override fun onRosterChanged(assignments: List<RosterAssignment>) =
                    repository.onRosterChanged(assignments)
            },
            reminders = object : ReminderPort {
                override fun canScheduleExactAlarms(): Boolean = ReminderScheduler.canScheduleExactAlarms(isolatedContext)
                override fun scheduleAll(assignments: List<RosterAssignment>): ScheduleSummary =
                    ReminderScheduler.scheduleAll(isolatedContext, assignments)
                override fun cancelAll(assignments: List<RosterAssignment>) =
                    ReminderScheduler.cancelAll(isolatedContext, assignments)
            },
            flightRefresher = LiveFlightRefresher { generation, apiKey, targets, scope ->
                assertEquals(TEST_API_KEY, apiKey)
                suspendCancellableCoroutine { continuation ->
                    requests += RecordedRefresh(generation, targets.toSet(), scope, continuation)
                }
            },
            airportLocator = AirportLocatorPort { _, _ -> error("The fake response contains no airport candidates") },
            apiKeyTester = ApiKeyTester { _, _, _, _ -> error("Connection tests were not requested") },
            configureBackgroundRefresh = { refreshConfigurations += it },
            notifyWidget = { DutyWidgetUpdater.notifyRosterChanged(isolatedContext) },
            isNotificationAccessGranted = { NotificationAccess.isGranted(isolatedContext) },
            hasPermission = { permission ->
                ContextCompat.checkSelfPermission(isolatedContext, permission) == PackageManager.PERMISSION_GRANTED
            },
            refreshClock = { composeRule.mainClock.currentTime },
            clock = Clock.systemDefaultZone(),
        )
        viewModelFactory = DutyViewModel.factory(ports)
        excelReader = { _, name ->
            excelReadCount.incrementAndGet()
            assertEquals(TEST_USER_NAME, name)
            checkNotNull(importResult)
        }
        viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[DutyViewModel::class.java]
        contentSet = true
        composeRule.setContent {
            val pending by queue.pending.collectAsStateWithLifecycle()
            val activeViewModel = viewModel
            if (showApp && activeViewModel != null) {
                AirShiftTheme {
                    AirShiftApp(
                        viewModel = activeViewModel,
                        readImageRoster = { _, _ -> error("Image import was not requested") },
                        readExcelRoster = excelReader,
                        openExactAlarmSettings = { error("Exact alarm settings were not requested") },
                        openNotificationAccessSettings = { error("Notification settings were not requested") },
                        pendingSharedExcelImport = pending.firstOrNull(),
                        sharedExcelImportQueue = queue,
                        dutyNavigation = DutyNavigationViewModel(),
                    )
                }
            }
        }
    }

    /** 模拟进程重建：旧 ViewModel 被清理（在途协程取消），新 ViewModel 从存储恢复。 */
    private fun recreateAppWithANewViewModel() {
        composeRule.runOnIdle { showApp = false }
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            viewModelStore.clear()
            viewModelStore = ViewModelStore()
            viewModel = ViewModelProvider(viewModelStore, viewModelFactory)[DutyViewModel::class.java]
            showApp = true
        }
    }

    private fun awaitRequests(count: Int) {
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitUntil(timeoutMillis = 5_000L) { requests.size >= count }
        assertEquals(count, requests.size)
    }

    private fun finishRequest(index: Int) {
        val request = requests[index]
        val live = request.targets.associateWith { lookup -> listOf(flight(lookup.flightNumber)) }
        composeRule.runOnIdle {
            request.continuation.resume(
                LiveRefreshResult(
                    live = live,
                    airports = emptyList(),
                    errors = emptyList(),
                    attemptedCount = request.targets.size,
                    refreshedCount = live.size,
                    fallbackDate = LocalDate.now(),
                ),
            )
        }
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitForIdle()
    }

    /**
     * 板头标题与底栏标签同名（如"当前执勤"既是分区名也是板头标题），所以按 testTag 点底栏；
     * 分区切换是 300 ms 的 fade-through，时钟不自动推进时要先推过它，旧页的列表才会卸载。
     */
    private fun selectSection(label: String) {
        val tag = when (label) {
            "全部执勤" -> "nav_all"
            "排班日历" -> "nav_calendar"
            "当前执勤" -> "nav_current"
            else -> "nav_settings"
        }
        composeRule.onNodeWithTag(tag).performClick()
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.waitForIdle()
    }

    private fun completeCurrentDuty() {
        composeRule.onNodeWithText("执勤完成").performClick()
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.waitForIdle()
    }

    private fun pullToRefresh() {
        composeRule.onNode(hasScrollAction()).performTouchInput { swipeDown() }
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
    }

    private fun duties(count: Int): List<RosterAssignment> = (1..count).map { index ->
        RosterAssignment(
            aircraftRegistration = "$preferencePrefix$index",
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
            duty.inboundFlight?.let { FlightLookup.of(it, checkNotNull(duty.scheduledArrival).toLocalDate()) },
            duty.outboundFlight?.let { FlightLookup.of(it, checkNotNull(duty.scheduledDeparture).toLocalDate()) },
        )
    }.toSet()

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

    private data class RecordedRefresh(
        val generation: Long,
        val targets: Set<FlightLookup>,
        val scope: FlightRefreshScope,
        val continuation: Continuation<LiveRefreshResult>,
    )

    private companion object {
        const val TEST_USER_NAME = "测试甲"
        const val TEST_API_KEY = "synthetic-instrumented-test-key-never-sent"
        const val AUTO_REFRESH_INTERVAL_MILLIS = 5 * 60 * 1_000L
        val TEST_GRANTED_PERMISSIONS = setOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
    }
}
