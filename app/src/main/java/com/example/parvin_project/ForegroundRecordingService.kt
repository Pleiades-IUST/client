// ForegroundRecordingService.kt
package com.example.parvin_project

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat // <--- ADDED THIS IMPORT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.Manifest // Also ensure Manifest is imported for permissions

class ForegroundRecordingService : Service() {

    private val CHANNEL_ID = "ForegroundServiceChannel"
    private val NOTIFICATION_ID = 1
    private val UPDATE_INTERVAL_MS = 10000L // 10 seconds (aligned with MainActivity)

    private lateinit var locationTracker: LocationTracker
    private lateinit var cellInfoCollector: CellInfoCollector
    private lateinit var networkTester: NetworkTester
    private lateinit var smsTester: SmsTester
    private lateinit var dnsTester: DnsTester
    private lateinit var uploadTester: UploadTester

    private val serviceScope = CoroutineScope(Dispatchers.IO) // Use IO dispatcher for network/heavy tasks

    // To store data collected in the background
    private val backgroundRecordedDataList = mutableListOf<MutableMap<String, Any?>>()
    private var entryCount: Int = 0

    // Callback to update UI in MainActivity (if it's active)
    var onDataCollected: ((Map<String, Any?>) -> Unit)? = null

    override fun onCreate() {
        super.onCreate()
        Log.d("ForegroundService", "onCreate called.")
        createNotificationChannel()

        // Initialize components for background data collection
        locationTracker = LocationTracker(this, UPDATE_INTERVAL_MS) { /* handled internally */ }
        cellInfoCollector = CellInfoCollector(this)
        networkTester = NetworkTester(this)
        smsTester = SmsTester(this) { messageId, deliveryTime ->
            // This callback is likely received while the service is running.
            // Find the SMS entry in `backgroundRecordedDataList` to update it precisely.
            serviceScope.launch(Dispatchers.Main) { // Switch to Main for UI update potential, or just log/update internal list
                Log.d("ForegroundServiceSMS", "SMS callback received in service for ID: $messageId, time: $deliveryTime")
                val smsEntryToUpdate = backgroundRecordedDataList.lastOrNull {
                    (it["smsInternalMessageId"] == messageId) // Assuming you'll store smsInternalMessageId when triggering
                }
                smsEntryToUpdate?.let {
                    it["smsDeliveryTimeMs"] = deliveryTime?.let { time -> String.format(Locale.getDefault(), "%.2f", time) } ?: "Failed"
                    Log.d("ForegroundServiceSMS", "Service updated SMS status for entry with ID $messageId to ${it["smsDeliveryTimeMs"]}")
                    // If MainActivity is bound, it would have its own mechanism to find this entry
                    // or receive a full list update. For now, this updates the service's internal list.
                } ?: run {
                    Log.w("ForegroundServiceSMS", "Could not find SMS entry with internal ID: $messageId to update in service's list.")
                }
            }
        }
        dnsTester = DnsTester(this)
        uploadTester = UploadTester(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ForegroundService", "Received START command.")

        // Register SMS receivers when the service starts
        smsTester.registerReceivers()

        when (intent?.action) {
            "com.example.parvin_project.ACTION_START_RECORDING" -> {
                Log.d("ForegroundService", "Starting recording.")
                startForegroundService()
                startDataCollection()
            }
            "com.example.parvin_project.ACTION_STOP_RECORDING" -> {
                Log.d("ForegroundService", "Stopping recording.")
                stopDataCollection()
                stopForeground(true)
                stopSelf()
            }
            else -> Log.d("ForegroundService", "Unknown intent action: ${intent?.action}")
        }

        return START_STICKY // Service will be restarted if killed by system
    }

    private fun startForegroundService() {
        val notification = createNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10 (API 29) and above require foregroundServiceType
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14 (API 34)
                // For API 34+, you must specify the type explicitly if you declared it in manifest
                // (e.g., android:foregroundServiceType="location")
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                Log.d("ForegroundService", "Started foreground service with type LOCATION (API 34+).")
            } else {
                // For API 29-33, use startForeground with type
                // Note: If targeting 34, you might still need FOREGROUND_SERVICE_LOCATION permission
                // even if not explicitly using ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION in startForeground.
                // The manifest entry is what truly counts for the type declaration.
                startForeground(NOTIFICATION_ID, notification)
                Log.d("ForegroundService", "Started foreground service (API < 34).")
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
            Log.d("ForegroundService", "Started foreground service (API < 29).")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Data Collection in Progress")
            .setContentText("Collecting network, cell, and location data in the background.")
            .setSmallIcon(R.mipmap.ic_launcher) // Use your app's launcher icon
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Makes the notification non-dismissible
            .build()
    }

    private fun startDataCollection() {
        // Reset state for new collection session
        backgroundRecordedDataList.clear()
        entryCount = 0

        // Start location updates (if permissions are granted)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationTracker.startLocationUpdates()
        } else {
            Log.e("ForegroundService", "Location permissions not granted, location data will not be collected.")
        }

        // Start the periodic data collection runnable
        serviceScope.launch {
            while (true) { // Loop indefinitely while service is active
                val currentDataEntry = mutableMapOf<String, Any?>()
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                currentDataEntry["timestamp"] = currentTime

                // --- Location Data Collection (within service) ---
                try {
                    currentDataEntry["location"] = locationTracker.getLocationDataForLog()
                } catch (e: SecurityException) {
                    Log.e("ForegroundServiceData", "Location permission denied in FGS: ${e.message}", e)
                    currentDataEntry["location"] = LocationData(null, null, "Permission Denied in FGS")
                } catch (e: Exception) {
                    Log.e("ForegroundServiceData", "Error getting location in FGS: ${e.message}", e)
                    currentDataEntry["location"] = LocationData(null, null, "Error in FGS: ${e.message}")
                }

                // --- Cell Info Data Collection (within service) ---
                try {
                    currentDataEntry["cellInfo"] = cellInfoCollector.getCellInfoData()
                } catch (e: SecurityException) {
                    Log.e("ForegroundServiceData", "Cell info permission denied in FGS: ${e.message}", e)
                    currentDataEntry["cellInfo"] = CellInfoData(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, "Permission Denied in FGS"
                    )
                } catch (e: Exception) {
                    Log.e("ForegroundServiceData", "Error getting cell info in FGS: ${e.message}", e)
                    currentDataEntry["cellInfo"] = CellInfoData(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, "Error in FGS: ${e.message}"
                    )
                }

                // --- Download Rate Test (within service) ---
                var downloadRateKbps: Double? = null
                // Checkbox states are from MainActivity, so we assume a default if not passed or hardcode
                // For simplicity here, we'll assume these tests are always enabled when service is active.
                // In a real app, you might pass these preferences as extras in the Intent to the service.
                try {
                    downloadRateKbps = networkTester.performHttpDownloadTest()
                } catch (e: IOException) {
                    Log.e("ForegroundServiceData", "Download test failed in FGS: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e("ForegroundServiceData", "Unexpected error during download test in FGS: ${e.message}", e)
                }
                currentDataEntry["downloadRateKbps"] = downloadRateKbps

                // --- Ping Test (within service) ---
                var pingResultMs: Double? = null
                try {
                    pingResultMs = networkTester.performPingTest()
                } catch (e: IOException) {
                    Log.e("ForegroundServiceData", "Ping test failed in FGS: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e("ForegroundServiceData", "Unexpected error during ping test in FGS: ${e.message}", e)
                }
                currentDataEntry["pingResultMs"] = pingResultMs

                // --- SMS Test (within service) ---
                var smsDeliveryStatusForEntry: String? = null
                // Assuming SMS tests are enabled in the service based on overall app function
                if (ContextCompat.checkSelfPermission(this@ForegroundRecordingService, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                    entryCount++
                    if (entryCount == 1 || (entryCount - 1) % 10 == 0) { // Send SMS every 10 intervals (100 seconds)
                        // It's crucial that this call sends the correct messageId for the callback to match.
                        // The SmsTester needs to generate a unique ID and then use it in its callback.
                        val messageIdForFGS = System.currentTimeMillis().toString() + "_" + (0..999).random()
                        currentDataEntry["smsInternalMessageId"] = messageIdForFGS // Store this for matching
                        Log.d("ForegroundServiceData", "Triggering SMS test from FGS for ID: $messageIdForFGS (Entry $entryCount)")
                        smsTester.sendSmsAndTrackDelivery("FGS Test SMS. Entry: $entryCount, ID: $messageIdForFGS, Time: $currentTime")
                        smsDeliveryStatusForEntry = "Pending..."
                    } else {
                        smsDeliveryStatusForEntry = "Skipped"
                    }
                } else {
                    Log.e("ForegroundServiceData", "SMS permission (SEND_SMS) not granted in FGS. Cannot send SMS.")
                    smsDeliveryStatusForEntry = "Permission Denied in FGS"
                }
                currentDataEntry["smsDeliveryTimeMs"] = smsDeliveryStatusForEntry // Initialize to Pending/Skipped/Denied

                // --- DNS Test (within service) ---
                var dnsLookupTimeMs: Double? = null
                try {
                    dnsLookupTimeMs = dnsTester.performDnsTest()
                } catch (e: IOException) {
                    Log.e("ForegroundServiceData", "DNS test failed in FGS: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e("ForegroundServiceData", "Unexpected error during DNS test in FGS: ${e.message}", e)
                }
                currentDataEntry["dnsLookupTimeMs"] = dnsLookupTimeMs

                // --- Upload Rate Test (within service) ---
                var uploadRateKbps: Double? = null
                try {
                    uploadRateKbps = uploadTester.performUploadTest()
                } catch (e: IOException) {
                    Log.e("ForegroundServiceData", "Upload test failed in FGS: ${e.message}", e)
                } catch (e: Exception) {
                    Log.e("ForegroundServiceData", "Unexpected error during upload test in FGS: ${e.message}", e)
                }
                currentDataEntry["uploadRateKbps"] = uploadRateKbps


                // Add to internal list
                backgroundRecordedDataList.add(currentDataEntry)
                Log.d("ForegroundServiceData", "Collected background data entry: $currentDataEntry")

                // Optionally, if MainActivity is bound, update its UI
                onDataCollected?.invoke(currentDataEntry)

                // Wait for the next interval
                kotlinx.coroutines.delay(UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun stopDataCollection() {
        locationTracker.stopLocationUpdates()
        serviceScope.cancel() // Cancel all coroutines started by this scope
        smsTester.unregisterReceivers() // Unregister SMS receivers
        Log.d("ForegroundService", "Data collection stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        // Not implementing binding for simplicity for now.
        // If MainActivity needs to receive live updates from Service,
        // we'd implement a Binder and expose `onDataCollected` or a data stream.
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDataCollection() // Ensure all collection is stopped
        Log.d("ForegroundService", "onDestroy called, service stopped.")
    }
}
