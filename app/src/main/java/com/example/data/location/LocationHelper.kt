package com.example.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.os.Build
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.coroutines.resume

data class PlaceLocation(
    val latitude: Double,
    val longitude: Double,
    val placeName: String,
    val address: String
)

class LocationHelper(private val context: Context) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val cancellationTokenSource = CancellationTokenSource()
        continuation.invokeOnCancellation {
            cancellationTokenSource.cancel()
        }

        try {
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location ->
                if (continuation.isActive) {
                    continuation.resume(location)
                }
            }.addOnFailureListener {
                if (continuation.isActive) {
                    // Fallback to last known location if getCurrentLocation fails
                    fusedLocationClient.lastLocation.addOnSuccessListener { lastLoc ->
                        if (continuation.isActive) continuation.resume(lastLoc)
                    }.addOnFailureListener {
                        if (continuation.isActive) continuation.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            if (continuation.isActive) {
                continuation.resume(null)
            }
        }
    }

    suspend fun getPlaceFromCoordinates(latitude: Double, longitude: Double): PlaceLocation = withContext(Dispatchers.IO) {
        var placeName = "Selected Location"
        var fullAddress = String.format(Locale.getDefault(), "%.4f, %.4f", latitude, longitude)

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val feature = addr.featureName
                    val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    val thoroughfare = addr.thoroughfare

                    placeName = feature ?: locality ?: thoroughfare ?: "Saved Place"
                    val parts = listOfNotNull(thoroughfare, locality, addr.adminArea, addr.countryName)
                    fullAddress = if (parts.isNotEmpty()) parts.joinToString(", ") else addr.getAddressLine(0) ?: fullAddress
                }
            } else {
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(latitude, longitude, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val feature = addr.featureName
                    val locality = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    val thoroughfare = addr.thoroughfare

                    placeName = feature ?: locality ?: thoroughfare ?: "Saved Place"
                    val parts = listOfNotNull(thoroughfare, locality, addr.adminArea, addr.countryName)
                    fullAddress = if (parts.isNotEmpty()) parts.joinToString(", ") else addr.getAddressLine(0) ?: fullAddress
                }
            }
        } catch (e: Exception) {
            // fallback
        }

        PlaceLocation(
            latitude = latitude,
            longitude = longitude,
            placeName = placeName,
            address = fullAddress
        )
    }

    suspend fun searchLocations(query: String): List<PlaceLocation> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PlaceLocation>()
        if (query.isBlank()) return@withContext results

        try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(query, 5)
            if (!addresses.isNullOrEmpty()) {
                for (addr in addresses) {
                    val name = addr.featureName ?: addr.locality ?: query
                    val addrLine = addr.getAddressLine(0) ?: "${addr.latitude}, ${addr.longitude}"
                    results.add(
                        PlaceLocation(
                            latitude = addr.latitude,
                            longitude = addr.longitude,
                            placeName = name,
                            address = addrLine
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // Geocoder service might not be available or network error
        }
        results
    }
}
