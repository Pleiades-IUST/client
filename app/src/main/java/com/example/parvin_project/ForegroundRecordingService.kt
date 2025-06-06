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
import java.io.IOException
import java.lang.SecurityException

// IMPORTANT: Ensure these data classes are defined in DataModels.kt
/*
data class LocationData(
    val latitude: Double?,
    val longitude: Double?,
    val status: String // e.g., "OK", "Waiting for fix", "Providers disabled"
)

data class CellInfoData(
    val technology: String?, // 1
    val signalStrength_dBm: Int?, // 2
    val plmnId: String?, // 3
    val lac: Int?, // 4
    val cellId: Long?, // 5
    val pci: Int?, // 6
    val tac: Int?, // 7
    val nci: Long?, // 8
    val nrarfcn: Int?, // 9
    val bands: List<Int>?, // 10
    val csiRsrp_dBm: Int?, // 11
    val csiRsrq_dB: Int?, // 12
    val rsrp_dBm: Int?, // 13
    val rsrq_dB: Int?, // 14
    val status: String // 15
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
            Log.d("ForegroundService", "updateRunnable: Starting data collection cycle.")
            lifecycleScope.launch(Dispatchers.IO) {
                val currentDataEntry = mutableMapOf<String, Any?>()
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                currentDataEntry["timestamp"] = currentTime

                // --- Location Data Collection ---
                try {
                    Log.d("ForegroundService", "Collecting location data...")
                    currentDataEntry["location"] = locationTracker.getLocationDataForLog()
                    Log.d("ForegroundService", "Location data collected: ${currentDataEntry["location"]}")
                } catch (e: SecurityException) {
                    Log.e("ForegroundService", "Location permission denied: ${e.message}", e)
                    currentDataEntry["location"] = LocationData(null, null, "Permission Denied")
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Error getting location: ${e.message}", e)
                    currentDataEntry["location"] = LocationData(null, null, "Error: ${e.message}")
                }

                // --- Cell Info Data Collection ---
                try {
                    Log.d("ForegroundService", "Collecting cell info data...")
                    currentDataEntry["cellInfo"] = cellInfoCollector.getCellInfoData()
                    Log.d("ForegroundService", "Cell info data collected: ${currentDataEntry["cellInfo"]}")
                } catch (e: SecurityException) {
                    Log.e("ForegroundService", "Cell info permission denied: ${e.message}", e)
                    currentDataEntry["cellInfo"] = CellInfoData(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, "Permission Denied"
                    )
                } catch (e: Exception) {
                    Log.e("ForegroundService", "Error getting cell info: ${e.message}", e)
                    currentDataEntry["cellInfo"] = CellInfoData(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, "Error: ${e.message}"
                    )
                }

                // --- Download Rate Test ---
                var downloadRateKbps: Double? = null
                if (shouldCaptureDownloadRate) {
                    try {
                        Log.d("ForegroundService", "Performing download test...")
                        downloadRateKbps = networkTester.performHttpDownloadTest()
                        Log.d("ForegroundService", "Download rate: $downloadRateKbps KB/s")
                    } catch (e: IOException) {
                        Log.e("ForegroundService", "Download test failed (IOException): ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("ForegroundService", "Unexpected error during download test: ${e.message}", e)
                    }
                }
                currentDataEntry["downloadRateKbps"] = downloadRateKbps
                currentDataEntry["downloadCaptureEnabled"] = shouldCaptureDownloadRate

                // --- Ping Test ---
                var pingResultMs: Double? = null
                if (shouldCapturePingTest) {
                    try {
                        Log.d("ForegroundService", "Performing ping test...")
                        pingResultMs = networkTester.performPingTest()
                        Log.d("ForegroundService", "Ping result: $pingResultMs ms")
                    } catch (e: IOException) {
                        Log.e("ForegroundService", "Ping test failed (IOException): ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("ForegroundService", "Unexpected error during ping test: ${e.message}", e)
                    }
                }
                currentDataEntry["pingResultMs"] = pingResultMs
                currentDataEntry["pingCaptureEnabled"] = shouldCapturePingTest

                // --- SMS Test ---
                var smsDeliveryTime: String? = null
                currentDataEntry["smsCaptureEnabled"] = shouldCaptureSmsTest
                if (shouldCaptureSmsTest) {
                    try {
                        Log.d("ForegroundService", "Checking SMS permission and attempting test...")
                        if (ContextCompat.checkSelfPermission(this@ForegroundRecordingService, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                            entryCount++
                            if (entryCount == 1 || (entryCount - 1) % 10 == 0) {
                                Log.d("ForegroundService", "Triggering SMS test (Entry $entryCount)")
                                smsTester.sendSmsAndTrackDelivery("Test SMS from service. Entry: $entryCount, Time: $currentTime")
                                smsDeliveryTime = "Pending..."
                            } else {
                                smsDeliveryTime = "Skipped"
                            }
                        } else {
                            Log.e("ForegroundService", "SMS permission (SEND_SMS) not granted in service context. Cannot send SMS.")
                            smsDeliveryTime = "Permission Denied (Service)"
                        }
                    } catch (e: SecurityException) {
                        Log.e("ForegroundService", "SMS permission issue: ${e.message}", e)
                        smsDeliveryTime = "Permission Error"
                    } catch (e: Exception) {
                        Log.e("ForegroundService", "Error during SMS test: ${e.message}", e)
                        smsDeliveryTime = "Test Error"
                    }
                } else {
                    smsDeliveryTime = "Not Captured"
                }
                currentDataEntry["smsDeliveryTimeMs"] = smsDeliveryTime

                // --- DNS Test ---
                var dnsLookupTimeMs: Double? = null
                if (shouldCaptureDnsTest) {
                    try {
                        Log.d("ForegroundService", "Performing DNS test...")
                        dnsLookupTimeMs = dnsTester.performDnsTest()
                        Log.d("ForegroundService", "DNS result: $dnsLookupTimeMs ms")
                    } catch (e: IOException) {
                        Log.e("ForegroundService", "DNS test failed (IOException): ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("ForegroundService", "Unexpected error during DNS test: ${e.message}", e)
                    }
                }
                currentDataEntry["dnsLookupTimeMs"] = dnsLookupTimeMs
                currentDataEntry["dnsCaptureEnabled"] = shouldCaptureDnsTest

                // --- Upload Rate Test ---
                var uploadRateKbps: Double? = null
                if (shouldCaptureUploadRate) {
                    try {
                        Log.d("ForegroundService", "Performing upload test...")
                        uploadRateKbps = uploadTester.performUploadTest()
                        Log.d("ForegroundService", "Upload rate: $uploadRateKbps KB/s")
                    } catch (e: IOException) {
                        Log.e("ForegroundService", "Upload test failed (IOException): ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("ForegroundService", "Unexpected error during upload test: ${e.message}", e)
                    }
                }
                currentDataEntry["uploadRateKbps"] = uploadRateKbps
                currentDataEntry["uploadCaptureEnabled"] = shouldCaptureUploadRate

                recordedDataList.add(currentDataEntry)

                withContext(Dispatchers.Main) {
                    val intent = Intent(ServiceConstants.ACTION_UPDATE_UI)
                    intent.putExtra(ServiceConstants.EXTRA_LIVE_DATA, formatDataForDisplay(currentDataEntry))
                    localBroadcastManager.sendBroadcast(intent)
                    Log.d("ForegroundService", "Sent live data update for UI.")
                }
                Log.d("ForegroundService", "updateRunnable: Data collection cycle completed.")
            }
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

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
        Log.d("ForegroundService", "Service onCreate started.")
        handler = Handler(Looper.getMainLooper())
        localBroadcastManager = LocalBroadcastManager.getInstance(this)

        // Initialize helper classes with the service context
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

        createNotificationChannel()
        Log.d("ForegroundService", "Service onCreate completed.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d("ForegroundService", "Service onStartCommand: action = ${intent?.action}")

        when (intent?.action) {
            ServiceConstants.ACTION_START_RECORDING -> {
                Log.d("ForegroundService", "Received START command. Setting up foreground service.")
                shouldCaptureDownloadRate = intent.getBooleanExtra("shouldCaptureDownloadRate", true)
                shouldCapturePingTest = intent.getBooleanExtra("shouldCapturePingTest", true)
                shouldCaptureSmsTest = intent.getBooleanExtra("shouldCaptureSmsTest", true)
                shouldCaptureDnsTest = intent.getBooleanExtra("shouldCaptureDnsTest", true)
                shouldCaptureUploadRate = intent.getBooleanExtra("shouldCaptureUploadRate", true)

                // Start the service in the foreground immediately
                startForeground(ServiceConstants.NOTIFICATION_ID, createNotification("Network Monitor: Starting recording in background...").build())
                startRecording() // Begin data collection
                Log.d("ForegroundService", "Foreground service started, recording initiated.")
            }
            ServiceConstants.ACTION_STOP_RECORDING -> {
                Log.d("ForegroundService", "Received STOP command. Stopping service.")
                stopRecording() // This method now handles sending full logs and stopping the service
            }
            ServiceConstants.ACTION_REQUEST_FULL_LOGS -> {
                Log.d("ForegroundService", "Received explicit request for full logs. Sending now.")
                sendFullLogsToActivity(andThenStopSelf = false) // If activity somehow requested logs explicitly
            }
        }

        return START_STICKY // Service will be re-created if killed by system
    }

    private fun startRecording() {
        Log.d("ForegroundService", "startRecording method called.")
        handler.removeCallbacks(updateRunnable) // Remove any existing callbacks to prevent duplicates
        recordedDataList.clear() // Clear previous session's data
        entryCount = 0 // Reset entry counter for SMS test
        locationTracker.startLocationUpdates() // Start listening for location updates
        smsTester.registerReceivers() // Register SMS broadcast receivers
        handler.post(updateRunnable) // Start the periodic data collection
        Log.d("ForegroundService", "Recording updates scheduled.")
        updateNotification("Network Monitor: Recording active in background. Data is being collected regularly.") // Update the persistent notification
    }

    private fun stopRecording() {
        Log.d("ForegroundService", "stopRecording method called.")
        handler.removeCallbacks(updateRunnable) // Stop the periodic updates
        locationTracker.stopLocationUpdates() // Stop listening for location
        smsTester.unregisterReceivers() // Ensure SMS receivers are unregistered
        Log.d("ForegroundService", "Recording stopped. Preparing to send full logs.")
        updateNotification("Network Monitor: Recording stopped. Tap to re-open the app.") // Update notification status
        sendFullLogsToActivity(andThenStopSelf = true) // Send the complete session logs to MainActivity, then stop service
    }

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

    private fun createNotification(contentText: String): NotificationCompat.Builder {
        return NotificationCompat.Builder(this, ServiceConstants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Network Monitor Service Running") // More explicit title
            .setContentText(contentText)
            .setSmallIcon(R.drawable.logo) // Use your app logo or a suitable icon
            .setOngoing(true) // Makes the notification non-dismissible
            .setPriority(NotificationCompat.PRIORITY_LOW) // Consistent with channel importance
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText)) // For longer text
        // No .setContentIntent(pendingIntent) to make it non-clickable
    }

    private fun updateNotification(contentText: String) {
        val notification = createNotification(contentText).build()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(ServiceConstants.NOTIFICATION_ID, notification)
    }

    private fun sendFullLogsToActivity(andThenStopSelf: Boolean) {
        lifecycleScope.launch(Dispatchers.IO) {
            Log.d("ForegroundService", "sendFullLogsToActivity: Formatting logs.")
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
                        cellInfoData.nci?.let { logBuilder.append("  NCI: ${it}\n") }
                        cellInfoData.nrarfcn?.let { logBuilder.append("  NR-ARFCN: $it\n") }
                        cellInfoData.bands?.let { bands -> if (bands.isNotEmpty()) logBuilder.append("  Bands: ${bands.joinToString(", ")}\n") }
                        cellInfoData.csiRsrp_dBm?.let { logBuilder.append("  CSI-RSRP: $it dBm\n") }
                        cellInfoData.csiRsrq_dB?.let { logBuilder.append("  CSI-RSRQ: $it dB\n") }
                        cellInfoData.status?.let { status -> logBuilder.append("  Status: $status\n") }

                    } ?: logBuilder.append("  Cell Info Status: N/A\n")
                    logBuilder.append("\n")
                }
            }

            withContext(Dispatchers.Main) {
                val intent = Intent(ServiceConstants.ACTION_RECEIVE_FULL_LOGS)
                intent.putExtra(ServiceConstants.EXTRA_FULL_LOGS, logBuilder.toString())
                localBroadcastManager.sendBroadcast(intent)
                Log.d("ForegroundService", "ACTION_RECEIVE_FULL_LOGS broadcast sent to UI.")

                if (andThenStopSelf) {
                    stopSelf()
                    Log.d("ForegroundService", "Service stopping self after sending logs.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ForegroundService", "Service onDestroy started.")
        handler.removeCallbacks(updateRunnable)
        locationTracker.stopLocationUpdates()
        smsTester.unregisterReceivers()
        Log.d("ForegroundService", "Service onDestroy completed.")
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }
}
