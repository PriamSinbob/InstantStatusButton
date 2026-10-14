package com.instantstatusbutton.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.instantstatusbutton.app.share.Place
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-shot location at tap time. Uses plain [LocationManager] (no Google Play services dependency).
 * Only foreground location is needed because the widget tap runs inside a visible (transparent) activity.
 */
object LocationHelper {
    const val TIMEOUT_MS = 12_000L
    private const val MAX_AGE_MS = 30 * 60_000L

    val permissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasPermission(context: Context) = permissions.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Current fix, or a recent last-known fix if the device cannot produce one in time. */
    suspend fun currentPlace(context: Context): Place? {
        if (!hasPermission(context)) return null
        val loc = currentLocation(context) ?: return null
        return Place.of(loc, reverseGeocode(context, loc))
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) || it == LocationManager.PASSIVE_PROVIDER }
        val live = providers.filter { it != LocationManager.PASSIVE_PROVIDER }

        // Fastest useful answer: network gives a fix quickly, GPS is more precise. Race them, prefer the first.
        val fresh = withTimeoutOrNull(TIMEOUT_MS) { raceForFix(lm, context, live) }
        if (fresh != null) return fresh

        val now = System.currentTimeMillis()
        return providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { now - it.time < MAX_AGE_MS }
            .maxByOrNull { it.time }
    }

    @SuppressLint("MissingPermission")
    private suspend fun raceForFix(lm: LocationManager, context: Context, providers: List<String>): Location? {
        if (providers.isEmpty()) return null
        return suspendCancellableCoroutine { cont ->
            val signals = mutableListOf<CancellationSignal>()
            val listeners = mutableListOf<LocationListener>()
            var pending = providers.size

            fun finish(l: Location?) {
                if (l != null || --pending == 0) {
                    signals.forEach { it.cancel() }
                    listeners.forEach { runCatching { lm.removeUpdates(it) } }
                    if (cont.isActive) cont.resume(l)
                }
            }

            cont.invokeOnCancellation {
                signals.forEach { it.cancel() }
                listeners.forEach { runCatching { lm.removeUpdates(it) } }
            }

            val executor = ContextCompat.getMainExecutor(context)
            for (p in providers) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val sig = CancellationSignal().also { signals += it }
                    lm.getCurrentLocation(p, sig, executor) { l -> finish(l) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) = finish(location)
                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = finish(null)
                    }
                    listeners += listener
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(p, listener, Looper.getMainLooper())
                }
            }
        }
    }

    /** Short place name such as "Kraków, Poland". Returns null when no geocoder backend is available. */
    private suspend fun reverseGeocode(context: Context, loc: Location): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val addresses: List<Address>? = withTimeoutOrNull(6_000L) {
            runCatching {
                @Suppress("DEPRECATION")
                Geocoder(context, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)
            }.getOrNull()
        }
        val a = addresses?.firstOrNull() ?: return@withContext null
        val local = a.locality ?: a.subAdminArea ?: a.subLocality ?: a.adminArea ?: a.featureName
        val parts = listOfNotNull(local, a.countryName).distinct()
        parts.joinToString(", ").ifBlank { null }
    }
}
