// NetworkTester.kt
package com.example.parvin_project

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
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
    // Host for the ping test
    private val PING_TARGET_HOST = "google.com"


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

    /**
     * Performs a ping test to a target host and returns the average round-trip time.
     * This function should be called from a background (IO) thread.
     *
     * IMPORTANT: Direct execution of 'ping' command via Runtime.exec() might be restricted
     * or disallowed on non-rooted Android devices due to security sandboxing, especially
     * on newer Android versions. It may result in "Permission denied" or "command not found".
     * @return Average ping time in milliseconds (Double) or null if ping fails/restricted.
     */
    fun performPingTest(): Double? {
        if (!isNetworkAvailable()) {
            Log.e("NetworkTester", "No active internet connection detected before ping test.")
            return null
        }

        var process: Process? = null
        var reader: BufferedReader? = null
        try {
            // ping -c 1: send 1 packet
            // ping -W 5: wait 5 seconds for a response
            // ping -t 64: set TTL (Time To Live) to 64 hops
            val command = "ping -c 1 -W 5 -t 64 $PING_TARGET_HOST"
            Log.d("NetworkTester", "Executing ping command: $command")
            process = Runtime.getRuntime().exec(command)
            reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            val output = StringBuilder()
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }

            val exitCode = process.waitFor() // Wait for the ping command to complete
            val fullOutput = output.toString()
            Log.d("NetworkTester", "Ping command output:\n$fullOutput")
            Log.d("NetworkTester", "Ping command exit code: $exitCode")

            if (exitCode == 0) { // Success
                // Parse the average ping time from the output
                // Example output line: rtt min/avg/max/mdev = 10.123/15.456/20.789/2.345 ms
                val pattern = "avg/max/mdev = ([0-9.]+)/".toRegex()
                val matchResult = pattern.find(fullOutput)
                val avgPingMs = matchResult?.groups?.get(1)?.value?.toDoubleOrNull()

                if (avgPingMs != null) {
                    Log.d("NetworkTester", "Ping to $PING_TARGET_HOST successful. Avg: ${avgPingMs} ms")
                    return avgPingMs
                } else {
                    Log.e("NetworkTester", "Failed to parse average ping time from output for $PING_TARGET_HOST.")
                    return null
                }
            } else {
                Log.e("NetworkTester", "Ping command failed for $PING_TARGET_HOST. Exit code: $exitCode. Output:\n$fullOutput")
                // Check if it's a permission denied error
                if (fullOutput.contains("Permission denied", ignoreCase = true) ||
                    fullOutput.contains("Operation not permitted", ignoreCase = true) ||
                    fullOutput.contains("command not found", ignoreCase = true)
                ) {
                    Log.e("NetworkTester", "Ping command likely failed due to OS restrictions or lack of root privileges. This is common on non-rooted Android devices.")
                }
                return null
            }
        } catch (e: Exception) {
            Log.e("NetworkTester", "Exception during ping test to $PING_TARGET_HOST: ${e.message}", e)
            return null
        } finally {
            reader?.close()
            process?.destroy() // Ensure the process is terminated
        }
    }
}
