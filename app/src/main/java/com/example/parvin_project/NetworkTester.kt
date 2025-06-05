// NetworkTester.kt
package com.example.parvin_project

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Performs network connectivity checks and download speed tests.
 * @param context The application context.
 */
class NetworkTester(private val context: Context) {

    private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // URL for the download test (a larger, publicly accessible ZIP file)
    private val DOWNLOAD_TEST_URL = "https://google.com"

    /**
     * Checks if the device has an active network connection that is validated for internet access.
     * Requires ACCESS_NETWORK_STATE permission.
     * @return true if network is available, false otherwise.
     */
    fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Performs an HTTP GET download test to measure download rate.
     * This function should be called from a background (IO) thread.
     * @return Download rate in KB/s (Double) or null if an error occurs or no internet.
     */
    fun performHttpDownloadTest(): Double? {
        var urlConnection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            if (!isNetworkAvailable()) {
                Log.e("NetworkTester", "No active internet connection detected before download test.")
                return null
            }

            val url = URL(DOWNLOAD_TEST_URL)
            urlConnection = url.openConnection() as HttpURLConnection
            urlConnection.requestMethod = "GET"
            urlConnection.connectTimeout = 5000 // 5 seconds for connection
            urlConnection.readTimeout = 15000   // 15 seconds for reading data
            urlConnection.instanceFollowRedirects = true // Follow redirects

            val startTime = System.nanoTime()
            urlConnection.connect() // Explicitly connect

            val responseCode = urlConnection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e("NetworkTester", "HTTP error code: $responseCode for $DOWNLOAD_TEST_URL")
                return null
            }

            inputStream = urlConnection.inputStream
            var bytesDownloaded = 0L
            val buffer = ByteArray(4096) // 4KB buffer

            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                bytesDownloaded += bytesRead
            }
            val endTime = System.nanoTime()

            val durationNs = endTime - startTime
            if (durationNs <= 0) {
                Log.w("NetworkTester", "Download duration was zero or negative. Bytes: $bytesDownloaded")
                return null // Avoid division by zero
            }

            val durationSeconds = durationNs / 1_000_000_000.0 // Convert nanoseconds to seconds
            val downloadRateBytesPerSecond = bytesDownloaded / durationSeconds
            val downloadRateKbps = downloadRateBytesPerSecond / 1024.0 // Convert to KB/s

            Log.d("NetworkTester", "Downloaded $bytesDownloaded bytes in ${String.format(Locale.getDefault(), "%.2f", durationSeconds)}s. Rate: ${String.format(Locale.getDefault(), "%.2f", downloadRateKbps)} KB/s")
            return downloadRateKbps
        } catch (e: Exception) {
            Log.e("NetworkTester", "Error during HTTP download test for $DOWNLOAD_TEST_URL: ${e.message}", e)
            return null
        } finally {
            inputStream?.close() // Close input stream
            urlConnection?.disconnect() // Always disconnect the connection
        }
    }
}
