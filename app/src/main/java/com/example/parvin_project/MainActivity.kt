// MainActivity.kt
package com.example.parvin_project

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.transition.TransitionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.lang.SecurityException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// IMPORTANT: Ensure these data classes are defined in DataModels.kt
// IMPORTANT: Ensure the capitalizeWords() extension function is defined in a file like `Utils.kt`

class MainActivity : AppCompatActivity() {

    private var isRecordingActive: Boolean = false // Track if data collection is active
    private lateinit var infoTextView: TextView
    private lateinit var toggleButton: Button
    private lateinit var copyButton: Button
    private lateinit var rootLayout: ViewGroup
    private lateinit var captureDownloadRateCheckBox: CheckBox
    private lateinit var capturePingTestCheckBox: CheckBox
    private lateinit var captureSmsTestCheckBox: CheckBox
    private lateinit var captureDnsTestCheckBox: CheckBox
    private lateinit var captureUploadRateCheckBox: CheckBox

    private lateinit var permissionHandler: PermissionHandler

    // Data collection components (now initialized in MainActivity)
    private lateinit var locationTracker: LocationTracker
    private lateinit var cellInfoCollector: CellInfoCollector
    private lateinit var networkTester: NetworkTester
    private lateinit var smsTester: SmsTester // This one now takes (String, Double?) -> Unit
    private lateinit var dnsTester: DnsTester
    private lateinit var uploadTester: UploadTester

    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 10000L // Changed to 10 seconds (10000ms)

    private val recordedDataList = mutableListOf<MutableMap<String, Any?>>()
    private var entryCount: Int = 0

    // Coroutine scope for data collection (bound to MainActivity's lifecycle)
    private val mainActivityScope = CoroutineScope(Dispatchers.Main)


    // Runnable for periodic data collection (runs only when MainActivity is active)
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (!isRecordingActive) {
                Log.d("MainActivityData", "updateRunnable: Not active, stopping further posts.")
                return
            }

            mainActivityScope.launch(Dispatchers.IO) { // START of the IO CoroutineScope
                val currentDataEntry = mutableMapOf<String, Any?>() // This variable is now correctly scoped within this launch block
                val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                currentDataEntry["timestamp"] = currentTime

                // --- Location Data Collection ---
                try {
                    currentDataEntry["location"] = locationTracker.getLocationDataForLog()
                } catch (e: SecurityException) {
                    Log.e("MainActivityData", "Location permission denied: ${e.message}", e)
                    currentDataEntry["location"] = LocationData(null, null, "Permission Denied")
                } catch (e: Exception) {
                    Log.e("MainActivityData", "Error getting location: ${e.message}", e)
                    currentDataEntry["location"] = LocationData(null, null, "Error: ${e.message}")
                }

                // --- Cell Info Data Collection ---
                try {
                    currentDataEntry["cellInfo"] = cellInfoCollector.getCellInfoData()
                } catch (e: SecurityException) {
                    Log.e("MainActivityData", "Cell info permission denied: ${e.message}", e)
                    currentDataEntry["cellInfo"] = CellInfoData(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, "Permission Denied"
                    )
                } catch (e: Exception) {
                    Log.e("MainActivityData", "Error getting cell info: ${e.message}", e)
                    currentDataEntry["cellInfo"] = CellInfoData(
                        null, null, null, null, null, null, null, null, null,
                        null, null, null, null, null, "Error: ${e.message}"
                    )
                }

                // --- Download Rate Test ---
                var downloadRateKbps: Double? = null
                if (captureDownloadRateCheckBox.isChecked) {
                    try {
                        downloadRateKbps = networkTester.performHttpDownloadTest()
                    } catch (e: IOException) {
                        Log.e("MainActivityData", "Download test failed: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("MainActivityData", "Unexpected error during download test: ${e.message}", e)
                    }
                }
                currentDataEntry["downloadRateKbps"] = downloadRateKbps
                currentDataEntry["downloadCaptureEnabled"] = captureDownloadRateCheckBox.isChecked

                // --- Ping Test ---
                var pingResultMs: Double? = null
                if (capturePingTestCheckBox.isChecked) {
                    try {
                        pingResultMs = networkTester.performPingTest()
                    } catch (e: IOException) {
                        Log.e("MainActivityData", "Ping test failed: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("MainActivityData", "Unexpected error during ping test: ${e.message}", e)
                    }
                }
                currentDataEntry["pingResultMs"] = pingResultMs
                currentDataEntry["pingCaptureEnabled"] = capturePingTestCheckBox.isChecked

                // --- SMS Test ---
                var smsDeliveryStatusForEntry: String? = null
                currentDataEntry["smsCaptureEnabled"] = captureSmsTestCheckBox.isChecked
                if (captureSmsTestCheckBox.isChecked) {
                    try {
                        if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                            entryCount++
                            if (entryCount == 1 || (entryCount - 1) % 10 == 0) {
                                Log.d("MainActivityData", "Triggering SMS test (Entry $entryCount)")
                                smsTester.sendSmsAndTrackDelivery("Test SMS from app. Entry: $entryCount, Time: $currentTime")
                                smsDeliveryStatusForEntry = "Pending..."
                            } else {
                                smsDeliveryStatusForEntry = "Skipped"
                            }
                        } else {
                            Log.e("MainActivityData", "SMS permission (SEND_SMS) not granted. Cannot send SMS.")
                            smsDeliveryStatusForEntry = "Permission Denied"
                        }
                    } catch (e: SecurityException) {
                        Log.e("MainActivityData", "SMS permission issue: ${e.message}", e)
                        smsDeliveryStatusForEntry = "Permission Error"
                    } catch (e: Exception) {
                        Log.e("MainActivityData", "Error during SMS test: ${e.message}", e)
                        smsDeliveryStatusForEntry = "Test Error"
                    }
                } else {
                    smsDeliveryStatusForEntry = "Not Captured"
                }
                currentDataEntry["smsDeliveryTimeMs"] = smsDeliveryStatusForEntry


                // --- DNS Test ---
                var dnsLookupTimeMs: Double? = null
                if (captureDnsTestCheckBox.isChecked) {
                    try {
                        dnsLookupTimeMs = dnsTester.performDnsTest()
                    } catch (e: IOException) {
                        Log.e("MainActivityData", "DNS test failed: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("MainActivityData", "Unexpected error during DNS test: ${e.message}", e)
                    }
                }
                currentDataEntry["dnsLookupTimeMs"] = dnsLookupTimeMs
                currentDataEntry["dnsCaptureEnabled"] = captureDnsTestCheckBox.isChecked

                // --- Upload Rate Test ---
                var uploadRateKbps: Double? = null
                if (captureUploadRateCheckBox.isChecked) {
                    try {
                        uploadRateKbps = uploadTester.performUploadTest()
                    } catch (e: IOException) {
                        Log.e("MainActivityData", "Upload test failed: ${e.message}", e)
                    } catch (e: Exception) {
                        Log.e("MainActivityData", "Unexpected error during upload test: ${e.message}", e)
                    }
                }
                currentDataEntry["uploadRateKbps"] = uploadRateKbps
                currentDataEntry["uploadCaptureEnabled"] = captureUploadRateCheckBox.isChecked

                recordedDataList.add(currentDataEntry) // This line is now correctly inside the launch block
                withContext(Dispatchers.Main) { // This block is also correctly inside the launch block
                    if (isRecordingActive) { // Only update live data if recording is still active
                        infoTextView.text = formatDataForDisplay(currentDataEntry)
                        Log.d("MainActivityData", "Live data update to UI.")
                    }
                }
            } // END of the mainActivityScope.launch(Dispatchers.IO) block

            // This line is for scheduling the *next* runnable, and is outside the coroutine launch
            if (isRecordingActive) {
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.hide() // Hide the action bar

        // Initialize UI elements
        infoTextView = findViewById(R.id.infoTextView)
        toggleButton = findViewById(R.id.toggleButton)
        copyButton = findViewById(R.id.copyButton)
        rootLayout = findViewById(R.id.rootLayout)
        captureDownloadRateCheckBox = findViewById(R.id.captureDownloadRateCheckBox)
        capturePingTestCheckBox = findViewById(R.id.capturePingTestCheckBox)
        captureSmsTestCheckBox = findViewById(R.id.captureSmsTestCheckBox)
        captureDnsTestCheckBox = findViewById(R.id.captureDnsTestCheckBox)
        captureUploadRateCheckBox = findViewById(R.id.captureUploadRateCheckBox)

        permissionHandler = PermissionHandler(this, 101) // Using 101 as the request code

        // Initialize data collection components (now in MainActivity)
        locationTracker = LocationTracker(this, UPDATE_INTERVAL_MS) { /* handled internally */ }
        cellInfoCollector = CellInfoCollector(this)
        networkTester = NetworkTester(this)
        // SMS tester needs a callback that receives messageId (String) and deliveryTime (Double?)
        smsTester = SmsTester(this) { messageId, deliveryTime ->
            mainActivityScope.launch(Dispatchers.Main) {
                Log.d("MainActivitySMS", "SMS callback received for Message ID: $messageId, Delivery time: $deliveryTime")
                // Find the specific entry in recordedDataList that corresponds to this messageId.
                // We're iterating to find the correct entry for update.
                // It searches for any entry whose 'smsDeliveryTimeMs' is "Pending..." AND if it was the last recorded entry.
                // This is a pragmatic approach since MainActivity doesn't store the SmsTester's internal messageId in the list.
                val smsEntryToUpdate = recordedDataList.lastOrNull {
                    (it["smsCaptureEnabled"] as? Boolean == true) && (it["smsDeliveryTimeMs"] == "Pending...")
                }

                smsEntryToUpdate?.let {
                    it["smsDeliveryTimeMs"] = deliveryTime?.let { time -> String.format(Locale.getDefault(), "%.2f", time) } ?: "Failed"
                    Log.d("MainActivitySMS", "Updated SMS status for latest pending entry to ${it["smsDeliveryTimeMs"]}")

                    // If this update is for the currently displayed live data entry AND we are still recording, refresh UI
                    if (isRecordingActive && recordedDataList.isNotEmpty() && recordedDataList.last() == it) {
                        infoTextView.text = formatDataForDisplay(it)
                        Log.d("MainActivitySMS", "UI updated with new SMS status.")
                    }
                } ?: run {
                    Log.w("MainActivitySMS", "Could not find a pending SMS entry to update. It might have been for an older session or missed.")
                }
            }
        }
        dnsTester = DnsTester(this)
        uploadTester = UploadTester(this)


        // Set initial state of checkboxes and their listeners
        captureDownloadRateCheckBox.isChecked = true
        capturePingTestCheckBox.isChecked = true
        captureSmsTestCheckBox.isChecked = true
        captureDnsTestCheckBox.isChecked = true
        captureUploadRateCheckBox.isChecked = true

        captureDownloadRateCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        capturePingTestCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        captureSmsTestCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        captureDnsTestCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }
        captureUploadRateCheckBox.setOnCheckedChangeListener { _, _ -> updateInfoTextViewHint() }

        // Initial UI State
        TransitionManager.beginDelayedTransition(rootLayout)
        updateInfoTextViewHint()
        updateToggleButtonState(false) // Set initial button state to STOPPED

        // Set a click listener for the toggle button
        toggleButton.setOnClickListener {
            if (isRecordingActive) {
                // STOP logic
                stopRecording()
                updateToggleButtonState(false)
                // Immediately indicate logs are loading
                infoTextView.text = "Formatting historical logs... Please wait."
                // Display full logs after stopping
                displayFullLogs()
            } else {
                // START logic
                // Clear any old logs/messages from previous sessions
                recordedDataList.clear()
                infoTextView.text = "" // Clear UI before starting
                if (permissionHandler.checkAndRequestPermissions()) {
                    // Start the ForegroundRecordingService
                    val serviceIntent = Intent(this, ForegroundRecordingService::class.java).apply {
                        action = "com.example.parvin_project.ACTION_START_RECORDING"
                        // You could add extras here for which tests to run in background
                    }
                    ContextCompat.startForegroundService(this, serviceIntent)

                    startRecording() // Keep UI-related recording state in MainActivity
                    updateToggleButtonState(true)
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Starting data collection... Please wait for first live update."
                } else {
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Requesting permissions... Please grant all required permissions to proceed."
                    Log.w("MainActivity", "Permissions were requested. Waiting for user response.")
                }
            }
        }

        // Set click listener for the copy button
        copyButton.setOnClickListener {
            copyTextToClipboard(infoTextView.text.toString())
        }
    }

    override fun onStart() {
        super.onStart()
        // If recording was active when app paused, restart the runnable (e.g., app came back from background)
        if (isRecordingActive) {
            handler.post(updateRunnable)
        }
        // Always register SMS receivers when the activity starts.
        smsTester.registerReceivers()
    }

    override fun onStop() {
        super.onStop()
        // Stop the handler when activity goes to background to prevent updates
        handler.removeCallbacks(updateRunnable)
        // Unregister SMS receivers when activity goes to background to prevent leaks
        smsTester.unregisterReceivers()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Send stop command to ForegroundRecordingService when MainActivity is destroyed
        val serviceIntent = Intent(this, ForegroundRecordingService::class.java).apply {
            action = "com.example.parvin_project.ACTION_STOP_RECORDING"
        }
        stopService(serviceIntent)

        // Clean up coroutine scope to prevent leaks
        mainActivityScope.cancel()
        // Ensure other components are stopped
        locationTracker.stopLocationUpdates()
        smsTester.unregisterReceivers() // Double check unregistration
        handler.removeCallbacks(updateRunnable) // Double check removal
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101) { // Assuming 101 is your permission request code
            if (permissionHandler.handlePermissionsResult(requestCode, grantResults)) {
                // If permissions are granted, start the service and recording
                val serviceIntent = Intent(this, ForegroundRecordingService::class.java).apply {
                    action = "com.example.parvin_project.ACTION_START_RECORDING"
                }
                ContextCompat.startForegroundService(this, serviceIntent)

                startRecording()
                updateToggleButtonState(true)
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = "Permissions granted. Starting data collection..."
            } else {
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = "Not all required permissions were granted. Cannot start data collection. Please grant them in app settings."
                updateToggleButtonState(false)
            }
        }
    }

    private fun startRecording() {
        isRecordingActive = true
        recordedDataList.clear() // Ensure list is clear for a new session
        entryCount = 0 // Reset entry count for SMS test
        locationTracker.startLocationUpdates() // Start location updates
        smsTester.registerReceivers() // Ensure SMS receivers are registered on start
        handler.post(updateRunnable) // Start the periodic updates
        Log.d("MainActivity", "Foreground data collection started.")
    }

    private fun stopRecording() {
        isRecordingActive = false
        handler.removeCallbacks(updateRunnable) // Stop the periodic updates immediately
        locationTracker.stopLocationUpdates() // Stop location updates
        smsTester.unregisterReceivers() // Unregister SMS receivers immediately
        Log.d("MainActivity", "Foreground data collection stopped.")
    }

    private fun displayFullLogs() {
        mainActivityScope.launch(Dispatchers.Default) { // Use Dispatchers.Default for formatting heavy work
            val logBuilder = StringBuilder()
            if (recordedDataList.isEmpty()) {
                logBuilder.append("No data was recorded during the last session.")
                Log.w("MainActivity", "recordedDataList is empty for full logs.")
            } else {
                logBuilder.append("--- RECORDED SESSION LOGS (${recordedDataList.size} entries) ---\n\n")
                recordedDataList.forEachIndexed { index, dataMap ->
                    logBuilder.append("--- Entry ${index + 1} ---\n")
                    (dataMap["timestamp"] as? String)?.let { logBuilder.append("Timestamp: $it\n") }

                    (dataMap["location"] as? LocationData)?.let { locationData ->
                        logBuilder.append("Location:\n")
                        if (locationData.latitude != null && locationData.longitude != null) {
                            logBuilder.append("  Latitude: ${String.format("%.6f", locationData.latitude)}, Longitude: ${String.format("%.6f", locationData.longitude)}\n")
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
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = logBuilder.toString()
                Toast.makeText(this@MainActivity, "Full logs loaded!", Toast.LENGTH_SHORT).show()
            }
        }
    }


    /**
     * Helper function to copy text to the clipboard.
     */
    private fun copyTextToClipboard(text: String) {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clipData = ClipData.newPlainText("Network Logs", text)
        clipboardManager.setPrimaryClip(clipData)
        Toast.makeText(this, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Updates the initial hint text based on selected checkboxes.
     */
    private fun updateInfoTextViewHint() {
        val downloadPart = if (captureDownloadRateCheckBox.isChecked) "Download" else ""
        val pingPart = if (capturePingTestCheckBox.isChecked) "Ping" else ""
        val smsPart = if (captureSmsTestCheckBox.isChecked) "SMS" else ""
        val dnsPart = if (captureDnsTestCheckBox.isChecked) "DNS" else ""
        val uploadPart = if (captureUploadRateCheckBox.isChecked) "Upload" else ""
        val networkTests = listOf(downloadPart, pingPart, smsPart, dnsPart, uploadPart).filter { it.isNotEmpty() }.joinToString(" & ")

        infoTextView.text = "Press START to begin recording network, cell, and location data.\n" +
                            "Network tests to capture: ${networkTests.ifEmpty { "None" }}."
    }

    private fun updateToggleButtonState(isActive: Boolean) {
        if (isActive) {
            toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500))
            toggleButton.text = "STOP (Recording)"
        } else {
            toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
            toggleButton.text = "START"
        }
        this.isRecordingActive = isActive
    }

    // Helper function to format data for display (can be simplified if needed)
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
}
