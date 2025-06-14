// ForegroundRecordingService.kt
package com.example.parvin_project

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.ContextCompat
import com.example.parvin_project.RetrofitClient // Add this
import com.example.parvin_project.PleiadesApiService // Add this
import retrofit2.HttpException // Add this for handling HTTP errors
import java.io.IOException // Add this for network errors

// IMPORTANT: Ensure these data classes are defined in a file like `data_classes.kt`
/*
data class LocationData(
    val latitude: Double?,
    val longitude: Double?,
    val status: String // e.g., "OK", "Waiting for fix", "Providers disabled"
)

data class CellInfoData(
    val technology: String?, // e.g., "LTE", "NR", "GSM"
    val signalStrength_dBm: Int?, // General signal strength in dBm
    val plmnId: String?, // Public Land Mobile Network ID (MCC+MNC)
    val lac: Int?, // Location Area Code (for 2G/3G)
    val cellId: Long?, // Cell Identity (for 2G/3G/4G/5G NR - using Long for NCI)
    val pci: Int?, // Physical Cell Identity (for 4G/5G)
    val tac: Int?, // Tracking Area Code (for 4G)
    val nci: Long?, // NR Cell Identity (for 5G NR)
    val nrarfcn: Int?, // NR Absolute Radio Frequency Channel Number (for 5G NR)
    val bands: List<Int>?, // List of frequency bands (for 5G NR, LTE)
    val csiRsrp_dBm: Int?, // CSI Reference Signal Received Power (for 5G NR)
    val csiRsrq_dB: Int?, // CSI Reference Signal Received Quality (for 5G NR)
    val rsrp_dBm: Int?, // Reference Signal Received Power (for LTE)
    val rsrq_dB: Int?, // Reference Signal Received Quality (for LTE)
    val status: String // e.g., "OK", "Permissions Denied", "No Cell Info"
)
*/

class ForegroundRecordingService : LifecycleService() {

    private lateinit var handler: Handler
    private val UPDATE_INTERVAL_MS = 10000L // 5 seconds

    // Helper classes (initialized with service context)
    private lateinit var locationTracker: LocationTracker
    private lateinit var cellInfoCollector: CellInfoCollector
    private lateinit var networkTester: NetworkTester
    private lateinit var smsTester: SmsTester
    private lateinit var dnsTester: DnsTester
    private lateinit var uploadTester: UploadTester

    // Data lists and flags
    private val recordedDataList = mutableListOf<MutableMap<String, Any?>>()
    private var entryCount: Int = 0

    // Checkbox states (should be passed from MainActivity)
    private var shouldCaptureDownloadRate: Boolean = true
    private var shouldCapturePingTest: Boolean = true
    private var shouldCaptureSmsTest: Boolean = true
    private var shouldCaptureDnsTest: Boolean = true
    private var shouldCaptureUploadRate: Boolean = true

    private lateinit var apiService: PleiadesApiService

    private lateinit var localBroadcastManager: LocalBroadcastManager

    // Runnable for periodic data collection
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Launch a coroutine in the service's lifecycle scope for background (IO) operations
            lifecycleScope.launch(Dispatchers.IO) {
                val currentDataEntry = mutableMapOf<String, Any?>()
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                currentDataEntry["timestamp"] = currentTime

                currentDataEntry["location"] = locationTracker.getLocationDataForLog()
                currentDataEntry["cellInfo"] = cellInfoCollector.getCellInfoData()

                // Conditionally perform network tests based on flags
                var downloadRateKbps: Double? = null
                if (shouldCaptureDownloadRate) {
                    downloadRateKbps = networkTester.performHttpDownloadTest()
                }
                currentDataEntry["downloadRateKbps"] = downloadRateKbps
                currentDataEntry["downloadCaptureEnabled"] = shouldCaptureDownloadRate

                var pingResultMs: Double? = null
                if (shouldCapturePingTest) {
                    pingResultMs = networkTester.performPingTest()
                }
                currentDataEntry["pingResultMs"] = pingResultMs
                currentDataEntry["pingCaptureEnabled"] = shouldCapturePingTest

                var smsDeliveryTime: String? = null
                currentDataEntry["smsCaptureEnabled"] = shouldCaptureSmsTest
                if (shouldCaptureSmsTest) {
                    // Note: SEND_SMS permission should be handled by MainActivity before starting service
                    if (ContextCompat.checkSelfPermission(this@ForegroundRecordingService, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        entryCount++
                        // Send SMS on the 1st entry, and then every 10 entries (11th, 21st, etc.)
                        if (entryCount == 1 || (entryCount - 1) % 10 == 0) {
                            Log.d("ForegroundService", "Triggering SMS test (Entry $entryCount)")
                            smsTester.sendSmsAndTrackDelivery("Test SMS from service. Entry: $entryCount, Time: $currentTime")
                            smsDeliveryTime = "Pending..."
                        } else {
                            smsDeliveryTime = "Skipped"
                        }
                    } else {
                        Log.e("ForegroundService", "SMS permission not granted in service context. Cannot send SMS.")
                        smsDeliveryTime = "Permission Denied (Service)"
                    }
                } else {
                    smsDeliveryTime = "Not Captured"
                }
                currentDataEntry["smsDeliveryTimeMs"] = smsDeliveryTime

                var dnsLookupTimeMs: Double? = null
                if (shouldCaptureDnsTest) {
                    dnsLookupTimeMs = dnsTester.performDnsTest()
                }
                currentDataEntry["dnsLookupTimeMs"] = dnsLookupTimeMs
                currentDataEntry["dnsCaptureEnabled"] = shouldCaptureDnsTest

                var uploadRateKbps: Double? = null
                if (shouldCaptureUploadRate) {
                    uploadRateKbps = uploadTester.performUploadTest()
                }
                currentDataEntry["uploadRateKbps"] = uploadRateKbps
                currentDataEntry["uploadCaptureEnabled"] = shouldCaptureUploadRate

                recordedDataList.add(currentDataEntry) // Add data to the service's list

                // Send live data update to MainActivity via LocalBroadcastManager
                withContext(Dispatchers.Main) {
                    val intent = Intent(ServiceConstants.ACTION_UPDATE_UI)
                    intent.putExtra(ServiceConstants.EXTRA_LIVE_DATA, formatDataForDisplay(currentDataEntry))
                    localBroadcastManager.sendBroadcast(intent)
                    Log.d("ForegroundService", "Sent live data update.")
                }
            }
            // Reschedule the next update
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    /**
     * Helper to format a single data entry for broadcast to the UI.
     * @param dataMap The map containing data for a single record.
     * @return A formatted String representing the live data.
     */
    private fun formatDataForDisplay(dataMap: MutableMap<String, Any?>): String {
        val displayString = StringBuilder()
        displayString.append("--- Live Data: ${dataMap["timestamp"] as? String ?: "N/A"} ---\n")

        val downloadCaptureEnabled = dataMap["downloadCaptureEnabled"] as? Boolean ?: false
        displayString.append("  Download: ")
        if (downloadCaptureEnabled) {
            (dataMap["downloadRateKbps"] as? Double)?.let { rate ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", rate)} KB/s\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        val pingCaptureEnabled = dataMap["pingCaptureEnabled"] as? Boolean ?: false
        displayString.append("  Ping: ")
        if (pingCaptureEnabled) {
            (dataMap["pingResultMs"] as? Double)?.let { ping ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", ping)} ms\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        val smsCaptureEnabled = dataMap["smsCaptureEnabled"] as? Boolean ?: false
        displayString.append("  SMS: ")
        if (smsCaptureEnabled) {
            displayString.append("${dataMap["smsDeliveryTimeMs"] ?: "N/A"}\n")
        } else {
            displayString.append("Not captured\n")
        }

        val dnsCaptureEnabled = dataMap["dnsCaptureEnabled"] as? Boolean ?: false
        displayString.append("  DNS Lookup: ")
        if (dnsCaptureEnabled) {
            (dataMap["dnsLookupTimeMs"] as? Double)?.let { dns ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", dns)} ms\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        val uploadCaptureEnabled = dataMap["uploadCaptureEnabled"] as? Boolean ?: false
        displayString.append("  Upload: ")
        if (uploadCaptureEnabled) {
            (dataMap["uploadRateKbps"] as? Double)?.let { upload ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", upload)} KB/s\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        val locationData = dataMap["location"] as? LocationData
        if (locationData != null && locationData.latitude != null) {
            displayString.append("  Location: Fixed (Lat: ${String.format("%.6f", locationData.latitude)}, Long: ${String.format("%.6f", locationData.longitude)})\n")
        } else {
            displayString.append("  Location: ${locationData?.status ?: "N/A"}\n")
        }

        val cellInfoData = dataMap["cellInfo"] as? CellInfoData
        if (cellInfoData != null && cellInfoData.technology != null) {
            displayString.append("  Cell Tech: ${cellInfoData.technology ?: "N/A"}, Signal: ${cellInfoData.signalStrength_dBm ?: "N/A"} dBm\n")
        } else {
            displayString.append("  Cell Status: ${cellInfoData?.status ?: "N/A"}\n")
        }

        return displayString.toString()
    }


    override fun onCreate() {
        super.onCreate()
        handler = Handler(Looper.getMainLooper())
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        locationTracker = LocationTracker(this, UPDATE_INTERVAL_MS) { /* Location updates handled internally by service */ }
        cellInfoCollector = CellInfoCollector(this)
        networkTester = NetworkTester(this)
        smsTester = SmsTester(this) { deliveryTime ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (recordedDataList.isNotEmpty()) {
                    val lastEntry = recordedDataList.last() as MutableMap<String, Any?>
                    lastEntry["smsDeliveryTimeMs"] = deliveryTime?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "Failed"
                    val intent = Intent(ServiceConstants.ACTION_UPDATE_UI)
                    intent.putExtra(ServiceConstants.EXTRA_LIVE_DATA, formatDataForDisplay(lastEntry))
                    localBroadcastManager.sendBroadcast(intent)
                }
            }
        }
        dnsTester = DnsTester(this)
        uploadTester = UploadTester(this)

        apiService = RetrofitClient.apiService // Initialize apiService here

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ServiceConstants.ACTION_START_RECORDING -> {
                Log.d("ForegroundService", "Received START command.")
                shouldCaptureDownloadRate = intent.getBooleanExtra("shouldCaptureDownloadRate", true)
                shouldCapturePingTest = intent.getBooleanExtra("shouldCapturePingTest", true)
                shouldCaptureSmsTest = intent.getBooleanExtra("shouldCaptureSmsTest", true)
                shouldCaptureDnsTest = intent.getBooleanExtra("shouldCaptureDnsTest", true)
                shouldCaptureUploadRate = intent.getBooleanExtra("shouldCaptureUploadRate", true)

                // Start the service in the foreground with the updated notification
                startForeground(ServiceConstants.NOTIFICATION_ID, createNotification("Network Monitor: Mobile network data collection is active in the background.").build())
                startRecording()
            }
            ServiceConstants.ACTION_STOP_RECORDING -> {
                Log.d("ForegroundService", "Received STOP command.")
                stopRecording()
            }
        }

        return START_STICKY
    }

    private fun startRecording() {
        handler.removeCallbacks(updateRunnable)
        recordedDataList.clear()
        entryCount = 0
        locationTracker.startLocationUpdates()
        smsTester.registerReceivers()
        handler.post(updateRunnable)
        Log.d("ForegroundService", "Recording started.")
        // Update notification to indicate active background recording
        updateNotification("⚠ Big Brother Broadcast: Connection traced. Coordinates acquired. You are exposed.");
    }

    private fun stopRecording() {
        handler.removeCallbacks(updateRunnable)
        locationTracker.stopLocationUpdates()
        smsTester.unregisterReceivers()
        Log.d("ForegroundService", "Recording stopped. Preparing to send data to API.")
        // Update notification to indicate recording has stopped
        updateNotification("Network Monitor: Recording stopped. Tap to re-open the app.")
        // Now call the new API upload function
        sendDataToApiAndStopSelf(andThenStopSelf = true) // <-- Changed here
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                ServiceConstants.NOTIFICATION_CHANNEL_ID,
                ServiceConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Creates a NotificationCompat.Builder instance for the foreground service notification.
     * This version uses BigTextStyle for longer content and removes contentIntent to make it non-clickable.
     * @param contentText The text to display in the notification (can be long).
     * @return A NotificationCompat.Builder instance.
     */
    private fun createNotification(contentText: String): NotificationCompat.Builder {
        // No PendingIntent is needed for setContentIntent if we want it non-clickable
        // The notification will just display information.

        return NotificationCompat.Builder(this, ServiceConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Network Monitor Service Running") // More explicit title
            .setContentText(contentText)
            .setSmallIcon(R.drawable.logo) // Ensure you have a 'logo' drawable
            .setOngoing(true) // Makes the notification non-dismissible
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText)) // ADDED: For longer text
        // REMOVED: .setContentIntent(pendingIntent) to make it non-clickable
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText).build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ServiceConstants.NOTIFICATION_ID, notification)
    }

    /**
     * Formats all recorded data and sends it to the `MainActivity`.
     * @param andThenStopSelf If true, calls stopSelf() after the broadcast is sent.
     */
    /**
     * Converts recordedDataList to DriveData format and sends it to the API.
     * @param andThenStopSelf If true, calls stopSelf() after the broadcast is sent.
     */
    private fun sendDataToApiAndStopSelf(andThenStopSelf: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            if (recordedDataList.isEmpty()) {
                Log.w("ForegroundService", "recordedDataList is empty. No data to send to API.")
                withContext(Dispatchers.Main) {
                    val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS) // Re-use this action to signal completion
                    intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, "No data was recorded during the session to upload.")
                    localBroadcastManager.sendBroadcast(intent)
                    if (andThenStopSelf) stopSelf()
                }
                return@launch
            }

            val driveName = "MobileDrive_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}"
            val signals = recordedDataList.mapNotNull { dataMap ->
                try {
                    val locationData = dataMap["location"] as? LocationData
                    val cellInfoData = dataMap["cellInfo"] as? CellInfoData

                    // Convert CellInfoData fields to String as required by API Signal schema
                    val cellIdString = cellInfoData?.cellId?.toString()
                    val pciString = cellInfoData?.pci?.toString()
                    val tacString = cellInfoData?.tac?.toString()

                    // Ensure record_time is in ISO 8601 format
                    val recordTime = dataMap["timestamp"] as? String // Assuming timestamp is already in YYYY-MM-DD HH:mm:ss
                    val isoRecordTime = recordTime?.let {
                        try {
                            val originalFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            val date = originalFormat.parse(it)
                            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                            isoFormat.timeZone = java.util.TimeZone.getTimeZone("UTC") // API expects Z for UTC
                            isoFormat.format(date)
                        } catch (e: Exception) {
                            Log.e("ForegroundService", "Error converting timestamp to ISO 8601: $it", e)
                            null
                        }
                    }

                    // Convert smsDeliveryTimeMs to Double, handle "Failed" or "Skipped" strings
                    val smsDeliveryTime = when (val smsResult = dataMap["smsDeliveryTimeMs"]) {
                        is Double -> smsResult
                        is String -> smsResult.toDoubleOrNull()
                        else -> null
                    }


                    Signal(
                        record_time = recordTime,
                        plmn_id = cellInfoData?.plmnId,
                        cell_id = cellIdString, // Converted to String
                        technology = cellInfoData?.technology,
                        signal_strength = cellInfoData?.signalStrength_dBm,
                        download_rate = dataMap["downloadRateKbps"] as? Double,
                        upload_rate = dataMap["uploadRateKbps"] as? Double,
                        dns_lookup_rate = dataMap["dnsLookupTimeMs"] as? Double,
                        ping = dataMap["pingResultMs"] as? Double,
                        sms_delivery_time = smsDeliveryTime, // Converted to Double?
                        rsrp = cellInfoData?.rsrp_dBm,
                        rsrq = cellInfoData?.rsrq_dB,
                        longitude = locationData?.longitude,
                        latitude = locationData?.latitude,
                        pci = pciString, // Converted to String
                        tac = tacString // Converted to String
                    )
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Error converting data entry to Signal object: ${e.message}", e)
                    null // Skip this entry if conversion fails
                }
            }

            val driveData = DriveData(
                drive = DriveBase(name = driveName),
                signals = signals
            )

            // Send to API
            withContext(Dispatchers.Main) { // Temporarily switch to Main for Toast/UI update
                Toast.makeText(this@ForegroundRecordingService, "Uploading data to server...", Toast.LENGTH_LONG).show()
                Log.d("ForegroundService", "Attempting to upload ${signals.size} signals to /drive API.")
            }


            try {
                val response = apiService.createDriveEntry(driveData)
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Log.d("ForegroundService", "Data successfully uploaded to API. Response code: ${response.code()}")
                        val message = "Data uploaded successfully! ${signals.size} entries."
                        Toast.makeText(this@ForegroundRecordingService, message, Toast.LENGTH_LONG).show()
                        val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS) // Re-use for success message
                        intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, "Upload successful. Total entries: ${signals.size}\n\n$message")
                        localBroadcastManager.sendBroadcast(intent)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("ForegroundService", "API upload failed. Code: ${response.code()}, Error: $errorBody")
                        val message = "Upload failed: ${response.code()} ${response.message()}\nError: ${errorBody ?: "No error body"}"
                        Toast.makeText(this@ForegroundRecordingService, message, Toast.LENGTH_LONG).show()
                        val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS) // Re-use for error message
                        intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, "Upload failed.\n\n$message")
                        localBroadcastManager.sendBroadcast(intent)
                    }
                }
            } catch (e: HttpException) {
                withContext(Dispatchers.Main) {
                    Log.e("ForegroundService", "HTTP Exception during API upload: ${e.message()}", e)
                    val message = "Network Error: HTTP Exception (${e.code()})\n${e.message()}"
                    Toast.makeText(this@ForegroundRecordingService, message, Toast.LENGTH_LONG).show()
                    val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS)
                    intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, "Upload failed.\n\n$message")
                    localBroadcastManager.sendBroadcast(intent)
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e("ForegroundService", "IO Exception during API upload: ${e.message}", e)
                    val message = "Network Error: Could not connect to server.\nCheck URL, internet connection, or server status. Message: ${e.message}"
                    Toast.makeText(this@ForegroundRecordingService, message, Toast.LENGTH_LONG).show()
                    val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS)
                    intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, "Upload failed.\n\n$message")
                    localBroadcastManager.sendBroadcast(intent)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("ForegroundService", "Unknown Error during API upload: ${e.message}", e)
                    val message = "An unknown error occurred during upload: ${e.message}"
                    Toast.makeText(this@ForegroundRecordingService, message, Toast.LENGTH_LONG).show()
                    val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS)
                    intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, "Upload failed.\n\n$message")
                    localBroadcastManager.sendBroadcast(intent)
                }
            } finally {
                // Clear the recorded data list after attempting to send, regardless of success or failure.
                // This prevents sending duplicate data if the service is stopped and started again without a full app restart.
                recordedDataList.clear()
                entryCount = 0 // Reset entry count
                if (andThenStopSelf) {
                    stopSelf()
                    Log.d("ForegroundService", "Service stopping self after API upload attempt.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
        locationTracker.stopLocationUpdates()
        smsTester.unregisterReceivers()
        Log.d("ForegroundService", "Service destroyed.")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
