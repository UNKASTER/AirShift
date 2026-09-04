package com.bradj.airshift

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.api.LiveFlightRefresher
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.duty.AirportLocatorPort
import com.bradj.airshift.duty.ApiKeyTester
import com.bradj.airshift.duty.DutyPorts
import com.bradj.airshift.duty.DutyViewModel
import com.bradj.airshift.duty.ReminderPort
import com.bradj.airshift.duty.SpecialServicePort
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.reminder.ScheduleSummary
import com.bradj.airshift.specialservice.SpecialServiceRepository
import com.bradj.airshift.specialservice.SpecialServiceState
import com.bradj.airshift.ui.AirShiftApp
import com.bradj.airshift.ui.DutyNavigationViewModel
import com.bradj.airshift.ui.theme.AirShiftTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.StateFlow
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

/**
 * 分享导入的“所有者”语义现在由 ViewModel 承担：ViewModel 被清理（进程重建）后，
 * 在途读取的结果不能落库也不能消费队列事件；新 ViewModel 会重新发起读取。
 */
@RunWith(AndroidJUnit4::class)
class SharedExcelImportOwnerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val preferencePrefix = "shared-owner-${UUID.randomUUID()}-"
    private val isolatedPreferenceNames = mutableSetOf<String>()
    private lateinit var targetContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var store: RosterStore
    private var viewModelStore = ViewModelStore()
    private var viewModel by mutableStateOf<DutyViewModel?>(null)
    private var showApp by mutableStateOf(true)
    private var contentSet = false

    @Before
    fun isolatePreferences() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        isolatedContext = object : ContextWrapper(targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
                val isolatedName = preferencePrefix + name
                isolatedPreferenceNames += isolatedName
                return super.getSharedPreferences(isolatedName, mode)
            }
        }
        store = RosterStore(isolatedContext)
        store.userName = TEST_USER_NAME
        assertFalse(store.hasVariFlightApiKey)
    }

    @After
    fun disposeContentAndRemoveOnlyIsolatedPreferences() {
        if (contentSet) {
            composeRule.runOnIdle { showApp = false }
            composeRule.mainClock.advanceTimeByFrame()
            composeRule.waitForIdle()
        }
        composeRule.runOnIdle { viewModelStore.clear() }
        isolatedPreferenceNames.forEach { name ->
            check(name.startsWith(preferencePrefix))
            // Flush this test's pending apply() writes before deleting its UUID-prefixed files.
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            targetContext.deleteSharedPreferences(name)
        }
    }

    @Test
    fun aClearedViewModelCannotCommitAndTheRecreatedOneReadsTheSharedFileAgain() {
        val repository = SpecialServiceRepository.get(isolatedContext)
        assertTrue(
            "The repository must be initialized with this test's isolated context",
            preferencePrefix + "air_shift_special_services" in isolatedPreferenceNames,
        )
        val queue = SharedExcelImportQueueViewModel(SavedStateHandle())
        val sharedUri = Uri.parse("content://com.example.airshift.test/$preferencePrefix/roster.xlsx")
        queue.enqueue(Intent(Intent.ACTION_SEND).apply {
            type = XLSX_MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, sharedUri)
        })
        val originalPending = queue.pending.value
        val originalRoster = store.loadSnapshot()
        val reads = CopyOnWriteArrayList<CompletableDeferred<RosterParseResult>>()
        val parsed = syntheticRoster()
        val factory = DutyViewModel.factory(ports(repository))
        fun newViewModel() = ViewModelProvider(viewModelStore, factory)[DutyViewModel::class.java]

        viewModel = newViewModel()
        contentSet = true
        composeRule.setContent {
            val pending by queue.pending.collectAsStateWithLifecycle()
            val activeViewModel = viewModel
            if (showApp && activeViewModel != null) {
                AirShiftTheme {
                    AirShiftApp(
                        viewModel = activeViewModel,
                        readImageRoster = { _, _ -> error("Image import was not requested") },
                        readExcelRoster = { uri, name ->
                            assertEquals(sharedUri, uri)
                            assertEquals(TEST_USER_NAME, name)
                            CompletableDeferred<RosterParseResult>().also { reads += it }.await()
                        },
                        openExactAlarmSettings = { error("Exact alarm settings were not requested") },
                        openNotificationAccessSettings = { error("Notification settings were not requested") },
                        pendingSharedExcelImport = pending.firstOrNull(),
                        sharedExcelImportQueue = queue,
                        dutyNavigation = DutyNavigationViewModel(),
                    )
                }
            }
        }
        composeRule.mainClock.autoAdvance = false
        composeRule.waitUntil(timeoutMillis = 5_000) { reads.size == 1 }
        val staleRead = reads.single()

        // 进程重建：旧 ViewModel 被清理，在途读取随之取消。
        composeRule.runOnIdle { showApp = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle { viewModelStore.clear() }
        staleRead.complete(parsed)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.runOnIdle {
            assertEquals(originalRoster.generation, store.rosterGeneration)
            assertEquals(originalRoster.assignments, store.loadAssignments())
            assertEquals(originalPending, queue.pending.value)
        }

        // 新 ViewModel 从队列恢复同一事件并重新读取；旧结果无论何时到达都不能落库。
        viewModelStore = ViewModelStore()
        composeRule.runOnIdle {
            viewModel = newViewModel()
            showApp = true
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitUntil(timeoutMillis = 5_000) { reads.size == 2 }
        composeRule.runOnIdle {
            assertEquals(originalRoster.generation, store.rosterGeneration)
            assertEquals(originalRoster.assignments, store.loadAssignments())
            assertEquals(originalPending, queue.pending.value)
            assertEquals(2, reads.size)
        }
        // 不完成 reads[1]：真实导入成功会安排提醒并请求权限。
    }

    private fun ports(repository: SpecialServiceRepository) = DutyPorts(
        store = store,
        specialServices = object : SpecialServicePort {
            override val state: StateFlow<SpecialServiceState> = repository.state
            override fun onRosterChanged(assignments: List<RosterAssignment>) = repository.onRosterChanged(assignments)
        },
        reminders = object : ReminderPort {
            override fun canScheduleExactAlarms(): Boolean = true
            override fun scheduleAll(assignments: List<RosterAssignment>): ScheduleSummary =
                error("A stale import must not schedule reminders")
            override fun cancelAll(assignments: List<RosterAssignment>) = Unit
        },
        flightRefresher = LiveFlightRefresher { _, _, _, _ -> error("No API key is configured") },
        airportLocator = AirportLocatorPort { _, _ -> error("A stale import must not request location") },
        apiKeyTester = ApiKeyTester { _, _, _, _ -> error("Connection tests were not requested") },
        configureBackgroundRefresh = {},
        notifyWidget = {},
        isNotificationAccessGranted = { false },
        hasPermission = { true },
        refreshClock = { composeRule.mainClock.currentTime },
        clock = Clock.systemDefaultZone(),
    )

    private fun syntheticRoster() = RosterParseResult(
        assignments = listOf(
            RosterAssignment(
                aircraftRegistration = "B0001",
                aircraftType = "320",
                inboundFlight = null,
                origin = null,
                scheduledArrival = null,
                outboundFlight = "ZZ9001",
                destination = "测试到达",
                scheduledDeparture = LocalDateTime.of(2000, 1, 1, 12, 0),
                assignees = TEST_USER_NAME,
                actualDeparture = LocalDateTime.of(2000, 1, 1, 12, 1),
            ),
        ),
        rosterDate = LocalDate.of(2000, 1, 1),
        warnings = emptyList(),
    )

    private companion object {
        const val TEST_USER_NAME = "测试甲"
    }
}
