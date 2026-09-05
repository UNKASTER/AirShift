package com.bradj.airshift.api

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.bradj.airshift.data.RosterStore
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 用设备上已配置的飞常准 API Key 做真实查询，把返回的航段打到 logcat（tag `AirShiftLiveProbe`），
 * 用来核对接口“按日期查询”的语义（date 是出发日还是到达日、过去日期是否返回当天的那一班）。
 *
 * 每个探针都是一次付费调用，因此默认跳过，必须显式开启：
 * `-Pandroid.testInstrumentationRunnerArguments.airshift.liveVariFlight=true`；
 * 探针列表可用 `airshift.liveProbes=MU2418@2026-09-04;FM9211@2026-09-04` 覆盖。
 * 明文 Key 只在进程内解密后传给客户端，不写日志、不断言。
 */
@RunWith(AndroidJUnit4::class)
class VariFlightLiveProbeInstrumentedTest {
    @Test
    fun logsWhatTheApiReturnsForEachProbe() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Opt in with airshift.liveVariFlight=true; every probe is a paid VariFlight call",
            arguments.getString("airshift.liveVariFlight") == "true",
        )
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val apiKey = RosterStore(context).variFlightApiKey
        assumeTrue("No VariFlight API key is configured on this device", apiKey != null)
        val client = VariFlightClient(checkNotNull(apiKey))
        val probes = arguments.getString("airshift.liveProbes")?.split(";")?.filter { it.isNotBlank() }
            ?: DEFAULT_PROBES

        probes.forEach { probe ->
            val (flight, date) = probe.trim().split("@")
            val legs = try {
                client.fetchFlightBlocking(flight, LocalDate.parse(date))
            } catch (error: VariFlightClientException) {
                Log.i(TAG, "$flight@$date -> error: ${error.message}")
                return@forEach
            }
            Log.i(TAG, "$flight@$date -> ${legs.size} leg(s)")
            legs.forEach { leg ->
                Log.i(
                    TAG,
                    "  ${leg.flightNumber} ${leg.origin?.code}->${leg.destination?.code}" +
                        " plan ${leg.plannedDeparture}->${leg.plannedArrival}" +
                        " est ${leg.estimatedDeparture}->${leg.estimatedArrival}" +
                        " act ${leg.actualDeparture}->${leg.actualArrival}" +
                        " stands ${leg.departureStand}/${leg.arrivalStand}",
                )
            }
        }
    }

    private companion object {
        const val TAG = "AirShiftLiveProbe"

        /** 2026-09-04 夜班排班里的真实航班：MU2418 次日 01:00 到达，FM9211 当天 17:50 到达。 */
        val DEFAULT_PROBES = listOf("MU2418@2026-09-04", "MU2418@2026-09-05", "FM9211@2026-09-04")
    }
}
