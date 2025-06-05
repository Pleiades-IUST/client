// UploadTester.kt
package com.example.parvin_project

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStream
import java.io.FileInputStream
import java.io.File

/**
 * Performs an HTTP file upload test and measures the upload rate.
 * Uploads a generated dummy file to a temporary file hosting service.
 * @param context The application context.
 */
class UploadTester(private val context: Context) {

    private val connectivityManager: ConnectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val UPLOAD_URL = "https://tmpfiles.org/api/v1/upload"
    private val DUMMY_FILE_SIZE_BYTES = 1 * 1024 * 1024 // 1 MB dummy file for testing

    /**
     * Checks if the device has an active network connection that is validated for internet access.
     * @return true if network is available, false otherwise.
     */
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Generates a dummy byte array for upload.
     * @param size The size of the dummy file in bytes.
     * @return A ByteArray containing dummy data.
     */
    private fun generateDummyData(size: Int): ByteArray {
        val data = ByteArray(size)
        // Fill with some arbitrary data to ensure it's not all zeros, though content doesn't matter for upload speed
        for (i in 0 until size) {
            data[i] = (i % 256).toByte()
        }
        return data
    }

    /**
     * Performs an HTTP POST request to upload a dummy file and measures the upload rate.
     * This function should be called from a background (IO) thread.
     * @return Upload rate in KB/s (Double) or null if an error occurs or no internet.
     */
    fun performUploadTest(): Double? {
        if (!isNetworkAvailable()) {
            Log.e("UploadTester", "No active internet connection detected before upload test.")
            return null
        }

        val dummyData = generateDummyData(DUMMY_FILE_SIZE_BYTES)
        val boundary = "Boundary-${System.currentTimeMillis()}"
        val CRLF = "\r\n" // Line separator required by multipart/form-data.

        var connection: HttpURLConnection? = null
        var outputStream: OutputStream? = null

        try {
            val url = URL(UPLOAD_URL)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.setRequestProperty("Accept", "application/json") // Expecting JSON response

            val startTime = System.nanoTime()

            outputStream = connection.outputStream
            val writer = OutputStreamWriter(outputStream, "UTF-8")

            // Part 1: File data
            writer.append("--$boundary").append(CRLF)
            writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"dummy_test_file.bin\"").append(CRLF)
            writer.append("Content-Type: application/octet-stream").append(CRLF) // or specific mime type like image/jpeg
            writer.append(CRLF).flush()

            outputStream.write(dummyData)
            outputStream.flush()
            writer.append(CRLF).flush()

            // End of multipart/form-data.
            writer.append("--$boundary--").append(CRLF).flush()

            val responseCode = connection.responseCode
            val endTime = System.nanoTime()

            val durationNs = endTime - startTime
            if (durationNs <= 0) {
                Log.e("UploadTester", "Upload duration was zero or negative.")
                return null
            }

            val durationSeconds = durationNs / 1_000_000_000.0
            val uploadedBytes = dummyData.size.toDouble()
            val uploadRateKbps = (uploadedBytes / 1024) / durationSeconds // KB/s

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = connection.inputStream.bufferedReader()
                val response = reader.use { it.readText() }
                Log.d("UploadTester", "Upload successful to $UPLOAD_URL. Response: $response")
                Log.d("UploadTester", "Uploaded ${String.format("%.2f", uploadedBytes / 1024 / 1024)} MB in ${String.format("%.2f", durationSeconds)} s. Rate: ${String.format("%.2f", uploadRateKbps)} KB/s")
                return uploadRateKbps
            } else {
                val errorStream = connection.errorStream?.bufferedReader()
                val errorResponse = errorStream?.use { it.readText() }
                Log.e("UploadTester", "Upload failed. HTTP Code: $responseCode. Error: $errorResponse")
                return null
            }
        } catch (e: Exception) {
            Log.e("UploadTester", "Error during upload test: ${e.message}", e)
            return null
        } finally {
            outputStream?.close()
            connection?.disconnect()
        }
    }
}
