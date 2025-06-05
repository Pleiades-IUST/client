// DnsTester.kt
package com.example.parvin_project

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Locale

/**
 * Performs a DNS lookup test to measure the time it takes for a hostname to resolve to an IP address.
 * @param context The application context.
 */
class DnsTester(private val context: Context) {

    private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val DNS_TARGET_HOST = "google.com" // Hostname to resolve

    /**
     * Checks if the device has an active network connection that is validated for internet access.
     * Required for DNS lookups.
     * @return true if network is available, false otherwise.
     */
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Performs a DNS lookup for a predefined host and measures the time taken.
     * This function should be called from a background (IO) thread.
     * @return DNS resolution time in milliseconds (Double) or null if an error occurs or no internet.
     */
    fun performDnsTest(): Double? {
        if (!isNetworkAvailable()) {
            Log.e("DnsTester", "No active internet connection detected before DNS test.")
            return null
        }

        try {
            val startTime = System.nanoTime()
            val inetAddress = InetAddress.getByName(DNS_TARGET_HOST) // Performs the DNS lookup
            val endTime = System.nanoTime()

            val durationNs = endTime - startTime
            if (durationNs <= 0) {
                Log.w("DnsTester", "DNS lookup duration was zero or negative for $DNS_TARGET_HOST.")
                return null
            }

            val durationMs = durationNs / 1_000_000.0 // Convert nanoseconds to milliseconds

            Log.d("DnsTester", "DNS lookup for $DNS_TARGET_HOST resolved to ${inetAddress.hostAddress} in ${String.format(Locale.getDefault(), "%.2f", durationMs)} ms.")
            return durationMs
        } catch (e: UnknownHostException) {
            Log.e("DnsTester", "UnknownHostException: Could not resolve host $DNS_TARGET_HOST. Check internet connection or hostname. ${e.message}", e)
            return null
        } catch (e: Exception) {
            Log.e("DnsTester", "Error during DNS test for $DNS_TARGET_HOST: ${e.message}", e)
            return null
        }
    }
}
