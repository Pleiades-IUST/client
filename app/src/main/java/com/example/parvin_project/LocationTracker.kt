// LocationTracker.kt
package com.example.parvin_project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * Tracks device location using LocationManager.
 * @param context The application context.
 * @param updateIntervalMs The desired interval for location updates in milliseconds.
 * @param onLocationUpdate A callback function to be invoked when location changes.
 */
class LocationTracker(
    private val context: Context,
    private val updateIntervalMs: Long,
    private val onLocationUpdate: (Location?) -> Unit
) : LocationListener {

    private val locationManager: LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private var lastKnownLocation: Location? = null

    /**
     * Starts requesting location updates.
     * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION permissions.
     */
    fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    // Request location updates with minimum time and distance
                    locationManager.requestLocationUpdates(
                        provider,
                        updateIntervalMs, // minTime
                        10f,             // minDistance (10 meters)
                        this             // LocationListener callback
                    )
                    Log.d("LocationTracker", "Requesting location updates from $provider.")
                } else {
                    Log.w("LocationTracker", "GPS and Network location providers are disabled.")
                    onLocationUpdate(null) // Inform callback that location is not available
                }
            } catch (e: SecurityException) {
                Log.e("LocationTracker", "SecurityException requesting location updates: ${e.message}")
                onLocationUpdate(null) // Inform callback
            } catch (e: Exception) {
                Log.e("LocationTracker", "Error requesting location updates: ${e.message}", e)
                onLocationUpdate(null) // Inform callback
            }
        } else {
            Log.w("LocationTracker", "Location permissions not granted for updates.")
            onLocationUpdate(null) // Inform callback
        }
    }

    /**
     * Stops receiving location updates.
     */
    fun stopLocationUpdates() {
        locationManager.removeUpdates(this)
        Log.d("LocationTracker", "Stopped location updates.")
    }

    /**
     * Gets the last known location.
     * @return The last known Location object, or null if not available.
     */
    fun getLastKnownLocation(): Location? {
        return lastKnownLocation
    }

    // --- LocationListener Interface Methods ---
    override fun onLocationChanged(location: Location) {
        lastKnownLocation = location // Store the latest location
        onLocationUpdate(location)   // Pass the new location to the callback
        Log.d("LocationTracker", "Location updated: Lat=${location.latitude}, Long=${location.longitude}")
    }

    @Deprecated("Deprecated in API 29")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // This method is deprecated in API 29+, but required for older APIs
        Log.d("LocationTracker", "Location provider status changed: $provider, status: $status")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("LocationTracker", "Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d("LocationTracker", "Location provider disabled: $provider")
        onLocationUpdate(null) // Inform callback if provider is disabled
    }

    /**
     * Formats the last known location into a LocationData object.
     * @return A LocationData object containing latitude, longitude, and status.
     */
    fun getLocationDataForLog(): LocationData {
        return if (lastKnownLocation != null) {
            LocationData(
                latitude = String.format("%.6f", lastKnownLocation!!.latitude).toDouble(),
                longitude = String.format("%.6f", lastKnownLocation!!.longitude).toDouble(),
                status = "Fixed"
            )
        } else {
            val status = if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                "GPS/Network providers not enabled on device settings. Please enable Location Services."
            } else {
                "Waiting for GPS/Network fix."
            }
            LocationData(latitude = null, longitude = null, status = status)
        }
    }
}
