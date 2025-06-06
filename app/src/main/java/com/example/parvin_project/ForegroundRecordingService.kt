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
    private val UPDATE_INTERVAL_MS = 5000L // 5 seconds

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
                        if (entryCount == 1 || (entryCount - 1) % 10 == 0) { // Corrected modulo logic for every 10 entries
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
                    // Pass the formatted live data string to the Activity
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

        // Append download rate
        val downloadCaptureEnabled = dataMap["downloadCaptureEnabled"] as? Boolean ?: false
        displayString.append("  Download: ")
        if (downloadCaptureEnabled) {
            (dataMap["downloadRateKbps"] as? Double)?.let { rate ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", rate)} KB/s\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        // Append ping result
        val pingCaptureEnabled = dataMap["pingCaptureEnabled"] as? Boolean ?: false
        displayString.append("  Ping: ")
        if (pingCaptureEnabled) {
            (dataMap["pingResultMs"] as? Double)?.let { ping ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", ping)} ms\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        // Append SMS
        val smsCaptureEnabled = dataMap["smsCaptureEnabled"] as? Boolean ?: false
        displayString.append("  SMS: ")
        if (smsCaptureEnabled) {
            displayString.append("${dataMap["smsDeliveryTimeMs"] ?: "N/A"}\n")
        } else {
            displayString.append("Not captured\n")
        }

        // Append DNS
        val dnsCaptureEnabled = dataMap["dnsCaptureEnabled"] as? Boolean ?: false
        displayString.append("  DNS Lookup: ")
        if (dnsCaptureEnabled) {
            (dataMap["dnsLookupTimeMs"] as? Double)?.let { dns ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", dns)} ms\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        // Append Upload
        val uploadCaptureEnabled = dataMap["uploadCaptureEnabled"] as? Boolean ?: false
        displayString.append("  Upload: ")
        if (uploadCaptureEnabled) {
            (dataMap["uploadRateKbps"] as? Double)?.let { upload ->
                displayString.append("${String.format(Locale.getDefault(), "%.2f", upload)} KB/s\n")
            } ?: displayString.append("Failed or N/A\n")
        } else {
            displayString.append("Not captured\n")
        }

        // Append location
        val locationData = dataMap["location"] as? LocationData
        if (locationData != null && locationData.latitude != null) {
            displayString.append("  Location: Fixed (Lat: ${String.format("%.6f", locationData.latitude)}, Long: ${String.format("%.6f", locationData.longitude)})\n")
        } else {
            displayString.append("  Location: ${locationData?.status ?: "N/A"}\n")
        }

        // Append cell info
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
        // Initialize Handler with the main Looper for UI-related tasks (like posting to updateRunnable)
        handler = Handler(Looper.getMainLooper())
        // Initialize LocalBroadcastManager for communication with MainActivity
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        // Initialize helper classes, passing the service's context (this)
        locationTracker = LocationTracker(this, UPDATE_INTERVAL_MS) { /* Location updates handled internally by service */ }
        cellInfoCollector = CellInfoCollector(this)
        networkTester = NetworkTester(this)
        // SMS tester needs a callback for delivery status, which will update the recordedDataList
        // and then broadcast the live update to MainActivity.
        smsTester = SmsTester(this) { deliveryTime ->
            lifecycleScope.launch(Dispatchers.Main) {
                if (recordedDataList.isNotEmpty()) {
                    val lastEntry = recordedDataList.last() as MutableMap<String, Any?>
                    lastEntry["smsDeliveryTimeMs"] = deliveryTime?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "Failed"
                    // Re-send update for UI to reflect the SMS status change
                    val intent = Intent(ServiceConstants.ACTION_UPDATE_UI)
                    intent.putExtra(ServiceConstants.EXTRA_LIVE_DATA, formatDataForDisplay(lastEntry))
                    localBroadcastManager.sendBroadcast(intent)
                }
            }
        }
        dnsTester = DnsTester(this)
        uploadTester = UploadTester(this)

        // Create the notification channel when the service is created
        createNotificationChannel()
    }

    /**
     * Called by the system when the service is first created or when startService() is called.
     * This is where we handle START/STOP commands and get initial configuration.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ServiceConstants.ACTION_START_RECORDING -> {
                Log.d("ForegroundService", "Received START command.")
                // Retrieve checkbox states passed from MainActivity
                shouldCaptureDownloadRate = intent.getBooleanExtra("shouldCaptureDownloadRate", true)
                shouldCapturePingTest = intent.getBooleanExtra("shouldCapturePingTest", true)
                shouldCaptureSmsTest = intent.getBooleanExtra("shouldCaptureSmsTest", true)
                shouldCaptureDnsTest = intent.getBooleanExtra("shouldCaptureDnsTest", true)
                shouldCaptureUploadRate = intent.getBooleanExtra("shouldCaptureUploadRate", true)

                // Start the service in the foreground immediately
                startForeground(ServiceConstants.NOTIFICATION_ID, createNotification("Recording network data...").build())
                startRecording() // Begin data collection
            }
            ServiceConstants.ACTION_STOP_RECORDING -> {
                Log.d("ForegroundService", "Received STOP command.")
                stopRecording() // This method now handles sending full logs and stopping the service
            }
            ServiceConstants.ACTION_REQUEST_FULL_LOGS -> {
                Log.d("ForegroundService", "Received explicit request for full logs. Sending now.")
                sendFullLogsToActivity(andThenStopSelf = false) // No need to stop self for an explicit request
            }
        }

        // START_STICKY means the service will be re-created by the system if it's killed.
        // The last intent received will be null upon recreation.
        return START_STICKY
    }

    /**
     * Starts the periodic data recording process.
     */
    private fun startRecording() {
        handler.removeCallbacks(updateRunnable) // Remove any existing callbacks to prevent duplicates
        recordedDataList.clear() // Clear previous session's data
        entryCount = 0 // Reset entry counter for SMS test
        locationTracker.startLocationUpdates() // Start listening for location updates
        smsTester.registerReceivers() // Register SMS broadcast receivers
        handler.post(updateRunnable) // Start the periodic data collection
        Log.d("ForegroundService", "Recording started.")
        updateNotification("Recording active...") // Update the persistent notification
    }

    /**
     * Stops the data recording process, sends full logs, and stops the service.
     */
    private fun stopRecording() {
        handler.removeCallbacks(updateRunnable) // Stop the periodic updates
        locationTracker.stopLocationUpdates() // Stop listening for location
        smsTester.unregisterReceivers() // Ensure SMS receivers are unregistered
        Log.d("ForegroundService", "Recording stopped. Preparing to send full logs.")
        updateNotification("Recording stopped. Tap to open app.") // Update notification status
        // Pass 'true' to indicate that stopSelf() should be called AFTER broadcast
        sendFullLogsToActivity(andThenStopSelf = true)
    }

    /**
     * Creates a notification channel for Android 8.0 (Oreo) and above.
     * Required for showing notifications.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                ServiceConstants.NOTIFICATION_CHANNEL_ID,
                ServiceConstants.NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Low importance so it's less intrusive
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    /**
     * Creates a NotificationCompat.Builder instance for the foreground service notification.
     * @param contentText The text to display in the notification.
     * @return A NotificationCompat.Builder instance.
     */
    private fun createNotification(contentText: String): NotificationCompat.Builder {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, ServiceConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Network Monitor")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.logo) // Use your app logo or a suitable icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissible
            .setPriority(NotificationCompat.PRIORITY_LOW) // Consistent with channel importance
    }

    /**
     * Updates the existing foreground service notification.
     * @param contentText The new text content for the notification.
     */
    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText).build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ServiceConstants.NOTIFICATION_ID, notification)
    }

    /**
     * Formats all recorded data from `recordedDataList` into a single string
     * and sends it to the `MainActivity` via `LocalBroadcastManager`.
     * @param andThenStopSelf If true, calls stopSelf() after the broadcast is sent.
     */
    private fun sendFullLogsToActivity(andThenStopSelf: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            val logBuilder = StringBuilder()
            if (recordedDataList.isEmpty()) {
                logBuilder.append("No data was recorded during the last session.")
                Log.w("ForegroundService", "recordedDataList is empty when trying to send full logs.")
            } else {
                logBuilder.append("--- RECORDED SESSION LOGS (${recordedDataList.size} entries) ---\n\n")
                recordedDataList.forEachIndexed { index, dataMap ->
                    logBuilder.append("--- Entry ${index + 1} ---\n")
                    (dataMap["timestamp"] as? String)?.let { logBuilder.append("Timestamp: $it\n") }

                    (dataMap["location"] as? LocationData)?.let { locationData ->
                        logBuilder.append("Location:\n")
                        if (locationData.latitude != null && locationData.longitude != null) {
                            logBuilder.append("  Latitude: ${String.format("%.6f", locationData.latitude)}\n")
                            logBuilder.append("  Longitude: ${String.format("%.6f", locationData.longitude)}\n")
                        } else {
                            logBuilder.append("  Status: ${locationData.status}\n")
                        }
                    }

                    val downloadCaptureEnabled = dataMap["downloadCaptureEnabled"] as? Boolean ?: false
                    if (downloadCaptureEnabled) {
                        (dataMap["downloadRateKbps"] as? Double)?.let { rate ->
                            logBuilder.append("  Download Rate: ${String.format(Locale.getDefault(), "%.2f", rate)} KB/s\n")
                        } ?: logBuilder.append("  Download Rate: N/A\n")
                    } else {
                        logBuilder.append("  Download Rate: Not Captured\n")
                    }

                    val pingCaptureEnabled = dataMap["pingCaptureEnabled"] as? Boolean ?: false
                    if (pingCaptureEnabled) {
                        (dataMap["pingResultMs"] as? Double)?.let { ping ->
                            logBuilder.append("  Ping Result: ${String.format(Locale.getDefault(), "%.2f", ping)} ms\n")
                        } ?: logBuilder.append("  Ping Result: N/A\n")
                    } else {
                        logBuilder.append("  Ping Result: Not Captured\n")
                    }

                    val smsCaptureEnabled = dataMap["smsCaptureEnabled"] as? Boolean ?: false
                    if (smsCaptureEnabled) {
                        (dataMap["smsDeliveryTimeMs"] as? String)?.let { smsResult ->
                            logBuilder.append("  SMS Delivery Time: $smsResult\n")
                        } ?: logBuilder.append("  SMS Delivery Time: N/A\n")
                    } else {
                        logBuilder.append("  SMS Delivery Time: Not Captured\n")
                    }

                    val dnsCaptureEnabled = dataMap["dnsCaptureEnabled"] as? Boolean ?: false
                    if (dnsCaptureEnabled) {
                        (dataMap["dnsLookupTimeMs"] as? Double)?.let { dns ->
                            logBuilder.append("  DNS Lookup Time: ${String.format(Locale.getDefault(), "%.2f", dns)} ms\n")
                        } ?: logBuilder.append("  DNS Lookup Time: N/A\n")
                    } else {
                        logBuilder.append("  DNS Lookup Time: Not Captured\n")
                    }

                    val uploadCaptureEnabled = dataMap["uploadCaptureEnabled"] as? Boolean ?: false
                    if (uploadCaptureEnabled) {
                        (dataMap["uploadRateKbps"] as? Double)?.let { upload ->
                            logBuilder.append("  Upload Rate: ${String.format(Locale.getDefault(), "%.2f", upload)} KB/s\n")
                        } ?: logBuilder.append("  Upload Rate: N/A\n")
                    } else {
                        logBuilder.append("  Upload Rate: Not Captured\n")
                    }


                    (dataMap["cellInfo"] as? CellInfoData)?.let { cellInfoData ->
                        logBuilder.append("Cell Info:\n")
                        cellInfoData.technology?.let { logBuilder.append("  Technology: $it\n") }
                        cellInfoData.signalStrength_dBm?.let { logBuilder.append("  Signal: $it dBm\n") }
                        cellInfoData.plmnId?.let { logBuilder.append("  PLMN-ID: $it\n") }
                        cellInfoData.lac?.let { logBuilder.append("  LAC: $it\n") }
                        cellInfoData.cellId?.let { logBuilder.append("  Cell ID: ${it}\n") }
                        cellInfoData.rsrp_dBm?.let { logBuilder.append("  RSRP: $it dBm\n") }
                        cellInfoData.rsrq_dB?.let { logBuilder.append("  RSRQ: $it dB\n") }
                        cellInfoData.pci?.let { logBuilder.append("  PCI: $it\n") }
                        cellInfoData.tac?.let { logBuilder.append("  TAC: $it\n") }
//                        cellInfoData.nci?.let { logBuilder.append("  NCI: ${it}\n") }
                        cellInfoData.nrarfcn?.let { logBuilder.append("  NR-ARFCN: $it\n") }
                        cellInfoData.bands?.let { bands -> if (bands.isNotEmpty()) logBuilder.append("  Bands: ${bands.joinToString(", ")}\n") }
                        cellInfoData.csiRsrp_dBm?.let { logBuilder.append("  CSI-RSRP: $it dBm\n") }
                        cellInfoData.csiRsrq_dB?.let { logBuilder.append("  CSI-RSRQ: $it dB\n") }
                        cellInfoData.status?.let { status -> logBuilder.append("  Status: $status\n") }

                    } ?: logBuilder.append("  Cell Info Status: N/A\n")
                    logBuilder.append("\n") // Separator between entries
                }
            }

            // Send the full formatted log string back to MainActivity
            withContext(Dispatchers.Main) {
                val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS)
                intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, logBuilder.toString())
                localBroadcastManager.sendBroadcast(intent)
                Log.d("ForegroundService", "ACTION_RECEIVE_FULL_LOGS broadcast sent.")

                // Only call stopSelf() if the flag is true
                if (andThenStopSelf) {
                    stopSelf()
                    Log.d("ForegroundService", "Service stopping self after sending logs.")
                }
            }
        }
    }

    /**
     * Called when the service is no longer used and is being destroyed.
     */
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable) // Stop any pending updates
        locationTracker.stopLocationUpdates() // Ensure location updates are stopped
        smsTester.unregisterReceivers() // Ensure SMS receivers are unregistered
        Log.d("ForegroundService", "Service destroyed.")
    }

    /**
     * Provides the binding mechanism for the service.
     * In this setup, we're using startService and LocalBroadcastManager, so no direct binding is needed.
     */
    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent) // Important to call super.onBind for LifecycleService
        return null // No direct binding is provided or necessary for this service's functionality
    }
}
