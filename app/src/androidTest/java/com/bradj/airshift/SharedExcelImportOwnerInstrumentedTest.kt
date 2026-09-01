package com.bradj.airshift

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.data.RosterStore
import com.bradj.airshift.model.RosterAssignment
import com.bradj.airshift.parser.RosterParseResult
import com.bradj.airshift.specialservice.SpecialServiceRepository
import com.bradj.airshift.ui.DutyNavigationViewModel
import com.bradj.airshift.ui.theme.AirShiftTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(AndroidJUnit4::class)
class SharedExcelImportOwnerInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val preferencePrefix = "shared-excel-owner-${UUID.randomUUID()}-"
    private val isolatedPreferenceNames = mutableSetOf<String>()
    private lateinit var targetContext: Context
    private lateinit var isolatedContext: Context
    private lateinit var store: RosterStore
    private var showApp by mutableStateOf(false)
    private var contentSet = false

    @Before
    fun isolatePreferencesWithoutConfiguringAnApiKey() {
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
        isolatedPreferenceNames.forEach { name ->
            check(name.startsWith(preferencePrefix))
            // Flush this test's pending apply() writes before deleting its UUID-prefixed files.
            targetContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            targetContext.deleteSharedPreferences(name)
        }
    }

    @Test
    fun disposedOwnerCannotCommitBeforeOrAfterTheSameEventIsRetried() {
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
        val callbacks = CopyOnWriteArrayList<(Result<RosterParseResult>) -> Unit>()
        val parsed = syntheticRoster()

        showApp = true
        contentSet = true
        composeRule.setContent {
            assertEquals(ComponentActivity::class.java, LocalContext.current.findActivity()?.javaClass)
            val pending by queue.pending.collectAsStateWithLifecycle()
            if (showApp) {
                AirShiftTheme {
                    AirShiftApp(
                        context = isolatedContext,
                        store = store,
                        specialServiceRepository = repository,
                        readImageRoster = { _, _, _ -> error("Image import was not requested") },
                        readExcelRoster = { uri, name, callback ->
                            assertEquals(sharedUri, uri)
                            assertEquals(TEST_USER_NAME, name)
                            callbacks += callback
                        },
                        refreshLive = { _, _, _, _, _ -> error("No API key is configured") },
                        locateAirport = { _, _ -> error("A stale import must not request location") },
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
        composeRule.waitUntil(timeoutMillis = 5_000) { callbacks.size == 1 }
        val oldCallback = callbacks.single()

        composeRule.runOnIdle { showApp = false }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.runOnIdle {
            // No new read/attempt exists yet: only owner disposal can reject this callback.
            assertEquals(1, callbacks.size)
            oldCallback(Result.success(parsed))
            assertEquals(originalRoster.generation, store.rosterGeneration)
            assertEquals(originalRoster.assignments, store.loadAssignments())
            assertEquals(originalPending, queue.pending.value)
        }

        composeRule.runOnIdle { showApp = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitUntil(timeoutMillis = 5_000) { callbacks.size == 2 }
        composeRule.runOnIdle {
            oldCallback(Result.success(parsed))
            assertEquals(originalRoster.generation, store.rosterGeneration)
            assertEquals(originalRoster.assignments, store.loadAssignments())
            assertEquals(originalPending, queue.pending.value)
            assertEquals(2, callbacks.size)
        }
        // Do not complete callbacks[1]: a successful live owner would schedule reminders/permissions.
    }

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

    private tailrec fun Context.findActivity(): Activity? = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    private companion object {
        const val TEST_USER_NAME = "测试甲"
    }
}
