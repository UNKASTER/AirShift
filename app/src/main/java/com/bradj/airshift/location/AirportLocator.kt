package com.bradj.airshift.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.bradj.airshift.api.AirportPoint
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

object AirportLocator {
    fun locate(
        context: Context,
        candidates: Collection<AirportPoint>,
        callback: (Result<AirportMatch>) -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            callback(Result.failure(SecurityException("未授予定位权限")))
            return
        }
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(60_000)
            .setDurationMillis(12_000)
            .build()
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(request, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    callback(Result.failure(IllegalStateException("暂时无法获取位置")))
                    return@addOnSuccessListener
                }
                val nearest = candidates
                    .distinctBy { it.code }
                    .map { it to distanceKm(location, it) }
                    .minByOrNull { it.second }
                if (nearest == null || nearest.second > MAX_AIRPORT_DISTANCE_KM) {
                    callback(Result.failure(IllegalStateException("当前位置附近未匹配到排班相关机场")))
                } else {
                    callback(Result.success(AirportMatch(nearest.first, nearest.second)))
                }
            }
            .addOnFailureListener { callback(Result.failure(it)) }
    }

    private fun distanceKm(location: Location, airport: AirportPoint): Double {
        val output = FloatArray(1)
        Location.distanceBetween(
            location.latitude,
            location.longitude,
            airport.latitude,
            airport.longitude,
            output,
        )
        return output[0] / 1000.0
    }

    private const val MAX_AIRPORT_DISTANCE_KM = 15.0
}

data class AirportMatch(val airport: AirportPoint, val distanceKm: Double)
