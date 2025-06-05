// MainActivity.kt
package com.example.parvin_project

import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.CheckBox
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.transition.TransitionManager
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Important: capitalizeWords() is now a top-level function in Utils.kt,
// so it will be automatically imported by Android Studio if needed.

class MainActivity : AppCompatActivity() {

    private var isDriving: Boolean = false // State variable for recording
    private lateinit var infoTextView: TextView // Displays cell and location info
    private lateinit var toggleButton: Button // Declare the toggle button
    private lateinit var rootLayout: ViewGroup // Reference to the root layout for transitions
    private lateinit var captureDownloadRateCheckBox: CheckBox // Declare the download checkbox
    private lateinit var capturePingTestCheckBox: CheckBox // Declare the ping checkbox
    private lateinit var captureSmsTestCheckBox: CheckBox // Declare the SMS checkbox

    // States for whether to capture download/ping/SMS rates
    private var shouldCaptureDownloadRate: Boolean = true // Default to true
    private var shouldCapturePingTest: Boolean = true    // Default to true for ping
    private var shouldCaptureSmsTest: Boolean = true     // Default to true for SMS

    // Handlers for periodic updates and location
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 10000L // 5 seconds in milliseconds for each entry

    // New instances of our helper classes
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var locationTracker: LocationTracker
    private lateinit var cellInfoCollector: CellInfoCollector
    private lateinit var networkTester: NetworkTester
    private lateinit var smsTester: SmsTester // New SMS tester instance

    // List to store recorded data - explicitly store mutable maps
    private val recordedDataList = mutableListOf<MutableMap<String, Any?>>()
    private var entryCount: Int = 0 // Counter for periodic SMS test

    // Request code for runtime permissions (must be unique for activity)
    private val PERMISSION_REQUEST_CODE = 101

    // Runnable to perform the updates (only records when isDriving is true)
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Check permissions before attempting any data collection or network tests
            if (permissionHandler.checkPermissionsWithoutRequest()) {
                // Immediately show a 'performing' message on UI
                runOnUiThread {
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Performing network & data collection... Please wait."
                }

                // Launch a coroutine for background operations
                lifecycleScope.launch(Dispatchers.IO) {
                    val currentDataEntry = mutableMapOf<String, Any?>()
                    val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    currentDataEntry["timestamp"] = currentTime

                    // Collect location data
                    currentDataEntry["location"] = locationTracker.getLocationDataForLog()

                    // Collect cell info data
                    currentDataEntry["cellInfo"] = cellInfoCollector.getCellInfoData()

                    // Conditionally perform and store download rate
                    var downloadRateKbps: Double? = null
                    if (shouldCaptureDownloadRate) {
                        downloadRateKbps = networkTester.performHttpDownloadTest()
                    }
                    currentDataEntry["downloadRateKbps"] = downloadRateKbps
                    currentDataEntry["downloadCaptureEnabled"] = shouldCaptureDownloadRate // Log setting

                    // Conditionally perform and store ping test result
                    var pingResultMs: Double? = null
                    if (shouldCapturePingTest) {
                        pingResultMs = networkTester.performPingTest()
                    }
                    currentDataEntry["pingResultMs"] = pingResultMs
                    currentDataEntry["pingCaptureEnabled"] = shouldCapturePingTest // Log setting

                    // Conditionally perform SMS test every 10 entries (including entry 1)
                    // Initialize with "N/A" or "Skipped" and let the callback update to "Pending..." or actual time
                    var smsDeliveryTime: String? = null
                    currentDataEntry["smsCaptureEnabled"] = shouldCaptureSmsTest // Always log setting
                    if (shouldCaptureSmsTest) {
                        // Check for SMS permission right before attempting to send SMS
                        if (ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                            entryCount++ // Increment before checking, so entry 1 is the first time
                            // Send SMS on the 1st entry, and then every 10 entries (11th, 21st, etc.)
//                            if (entryCount == 1 || (entryCount > 1 && (entryCount - 1) % 10 == 0)) {
                            if (true) {
                                Log.d("MainActivity", "Triggering SMS test (Entry $entryCount)")
                                smsTester.sendSmsAndTrackDelivery("Test SMS from app. Entry: $entryCount, Time: $currentTime")
                                smsDeliveryTime = "Pending..." // Placeholder until callback updates it
                            } else {
                                smsDeliveryTime = "Skipped" // Not on a scheduled entry
                            }
                        } else {
                            Log.e("MainActivity", "SMS permission (SEND_SMS) not granted. Cannot send SMS.")
                            smsDeliveryTime = "Permission Denied"
                            // Optionally, turn off the checkbox if permission is denied
                            runOnUiThread { captureSmsTestCheckBox.isChecked = false }
                        }
                    } else {
                        smsDeliveryTime = "Not Captured" // Checkbox unchecked
                    }
                    currentDataEntry["smsDeliveryTimeMs"] = smsDeliveryTime


                    // Switch to Main thread to update UI and add to recorded list
                    withContext(Dispatchers.Main) {
                        // Only update UI and record if recording is still active
                        if (isDriving) {
                            TransitionManager.beginDelayedTransition(rootLayout)
                            val displayString = StringBuilder()
                            displayString.append("--- Live Data: $currentTime ---\n")

                            displayString.append("  Download: ")
                            if (shouldCaptureDownloadRate) {
                                if (downloadRateKbps != null) {
                                    displayString.append("${String.format(Locale.getDefault(), "%.2f", downloadRateKbps)} KB/s\n")
                                } else {
                                    displayString.append("Failed or N/A\n")
                                }
                            } else {
                                displayString.append("Not captured (checkbox unchecked)\n")
                            }

                            displayString.append("  Ping: ")
                            if (shouldCapturePingTest) {
                                if (pingResultMs != null) {
                                    displayString.append("${String.format(Locale.getDefault(), "%.2f", pingResultMs)} ms\n")
                                } else {
                                    displayString.append("Failed or N/A (check Logcat for ping command issues)\n")
                                }
                            } else {
                                displayString.append("Not captured (checkbox unchecked)\n")
                            }

                            displayString.append("  SMS: ")
                            if (shouldCaptureSmsTest) {
                                // For live display, use the value from currentDataEntry (could be "Pending..." or "Skipped" or "Permission Denied")
                                displayString.append("${currentDataEntry["smsDeliveryTimeMs"] ?: "N/A"}\n")
                            } else {
                                displayString.append("Not captured (checkbox unchecked)\n")
                            }


                            // Display simplified location status
                            val locationData = currentDataEntry["location"] as? LocationData
                            if (locationData != null && locationData.latitude != null) {
                                displayString.append("  Location: Fixed (Lat: ${String.format("%.6f", locationData.latitude)}, Long: ${String.format("%.6f", locationData.longitude)})\n")
                            } else {
                                displayString.append("  Location: ${locationData?.status ?: "N/A"}\n")
                            }

                            // Display simplified cell info
                            val cellInfoData = currentDataEntry["cellInfo"] as? CellInfoData
                            if (cellInfoData != null && cellInfoData.technology != null) {
                                displayString.append("  Cell Tech: ${cellInfoData.technology ?: "N/A"}, Signal: ${cellInfoData.signalStrength_dBm ?: "N/A"} dBm\n")
                            } else {
                                displayString.append("  Cell Status: ${cellInfoData?.status ?: "N/A"}\n")
                            }

                            infoTextView.text = displayString.toString()
                            recordedDataList.add(currentDataEntry) // Add to list
                            Log.d("MainActivity", "Recording data: ${currentDataEntry["timestamp"]}")
                        }
                    }
                }
            } else {
                Log.w("MainActivity", "Permissions missing during scheduled update. Stopping recording.")
                // If permissions are lost while recording, stop the recording to prevent errors
                if (isDriving) {
                    runOnUiThread { // Ensure UI updates are on the main thread
                        toggleButton.performClick() // Simulate a click to trigger the STOP logic
                        TransitionManager.beginDelayedTransition(rootLayout)
                        infoTextView.text = "Recording stopped: Permissions lost.\nPlease grant permissions and press START again."
                    }
                }
            }
            // Schedule the next update ONLY IF isDriving is true.
            if (isDriving) {
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        infoTextView = findViewById(R.id.infoTextView)
        toggleButton = findViewById(R.id.toggleButton)
        rootLayout = findViewById(R.id.rootLayout)
        captureDownloadRateCheckBox = findViewById(R.id.captureDownloadRateCheckBox)
        capturePingTestCheckBox = findViewById(R.id.capturePingTestCheckBox)
        captureSmsTestCheckBox = findViewById(R.id.captureSmsTestCheckBox) // Initialize new SMS checkbox

        // Initialize helper classes
        permissionHandler = PermissionHandler(this, PERMISSION_REQUEST_CODE)
        locationTracker = LocationTracker(this, UPDATE_INTERVAL_MS) { location ->
            // This lambda is called by LocationTracker when location changes
            // You can use the updated location here if needed, or simply rely on getLocationDataForLog()
            // which will pick up the last known location from the tracker.
        }
        cellInfoCollector = CellInfoCollector(this)
        networkTester = NetworkTester(this)
        smsTester = SmsTester(this) { deliveryTime ->
            // This callback receives the SMS delivery time
            // Find the last recorded entry and update its smsDeliveryTimeMs
            // This needs to be on the Main thread because it might update UI
            runOnUiThread {
                if (recordedDataList.isNotEmpty()) {
                    val lastEntry = recordedDataList.last() as MutableMap<String, Any?> // Explicitly cast to MutableMap
                    // Update the last entry's SMS delivery time
                    lastEntry["smsDeliveryTimeMs"] = deliveryTime?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "Failed"
                    // If we are still actively recording, refresh the live display to show the updated SMS status
                    if (isDriving) {
                        displayLiveCurrentData() // Call a new helper to refresh live data
                    }
                }
            }
        }

        // Set initial state of checkboxes and their listeners
        captureDownloadRateCheckBox.isChecked = shouldCaptureDownloadRate
        captureDownloadRateCheckBox.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            shouldCaptureDownloadRate = isChecked
            Log.d("MainActivity", "Capture Download Rate: $shouldCaptureDownloadRate")
            updateInfoTextViewHint()
        }

        capturePingTestCheckBox.isChecked = shouldCapturePingTest
        capturePingTestCheckBox.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            shouldCapturePingTest = isChecked
            Log.d("MainActivity", "Capture Ping Test: $shouldCapturePingTest")
            updateInfoTextViewHint()
        }

        captureSmsTestCheckBox.isChecked = shouldCaptureSmsTest
        captureSmsTestCheckBox.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            shouldCaptureSmsTest = isChecked
            Log.d("MainActivity", "Capture SMS Test: $shouldCaptureSmsTest")
            updateInfoTextViewHint()
        }

        // Initial UI State
        TransitionManager.beginDelayedTransition(rootLayout)
        updateInfoTextViewHint() // Set initial hint based on checkbox states
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
        toggleButton.text = "START"

        // Set a click listener for the toggle button
        toggleButton.setOnClickListener {
            Log.d("MainActivity", "Button click detected! isDriving state before: $isDriving")
            isDriving = !isDriving // Toggle recording state

            if (isDriving) {
                // Check if SMS permission is granted if SMS test is enableds
                if (shouldCaptureSmsTest && ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    // If SMS test is enabled but permission is NOT granted, alert user and do not start.
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "SMS Test requires SEND_SMS permission. Please grant it in app settings and press START again."
                    Log.w("MainActivity", "Attempted to start with SMS test enabled but SEND_SMS permission is missing.")
                    isDriving = false // Do not proceed with starting
                    toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                    toggleButton.text = "START"
                    return@setOnClickListener // Exit early
                }

                // START logic
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500))
                toggleButton.text = "STOP (Recording)"
                recordedDataList.clear() // Clear previous data
                entryCount = 0 // Reset entry counter
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = "Starting data collection... Please wait for first live update."
                Log.d("MainActivity", "Started recording. List cleared. Entry count reset.")

                if (permissionHandler.checkAndRequestPermissions()) {
                    // Permissions granted (including SMS if needed), start background updates and location updates
                    startUpdatingInfo()
                    locationTracker.startLocationUpdates()
                    // Register SMS receivers ONLY if SMS test is enabled and permission is granted
                    if (shouldCaptureSmsTest && ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                        smsTester.registerReceivers()
                    }
                } else {
                    // Core permissions (Location/Phone State) not granted, reset state and inform user
                    isDriving = false
                    toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                    toggleButton.text = "START"
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Core permissions required (Location, Phone State) to access network, cell, and location information.\nPlease grant them in app settings and press START again."
                    Log.w("MainActivity", "Recording attempted but core permissions not granted.")
                }
            } else {
                // STOP logic
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                toggleButton.text = "START"
                Log.d("MainActivity", "Stopped recording. Displaying data.")

                stopUpdatingInfo() // Stop runnable
                locationTracker.stopLocationUpdates() // Stop location listener
                // Unregister SMS receivers only if they were potentially registered
                if (shouldCaptureSmsTest) { // No need to check permission here, just unregister if was trying to capture
                    smsTester.unregisterReceivers()
                }
                displayRecordedData() // Display all recorded data
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If permissions are already granted, ensure location updates are running when activity resumes
        // and SMS receivers are registered if recording was active AND SMS permission is granted.
        if (permissionHandler.checkPermissionsWithoutRequest() && isDriving) {
            locationTracker.startLocationUpdates()
            if (shouldCaptureSmsTest && ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED) {
                smsTester.registerReceivers()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Always stop location updates when the activity goes to background
        locationTracker.stopLocationUpdates()
        // Also ensure the updateRunnable is stopped if recording was active when app paused
        stopUpdatingInfo()
        // Always unregister SMS receivers when pausing, regardless of current `shouldCaptureSmsTest`
        // as they might have been registered if it was enabled earlier.
        smsTester.unregisterReceivers()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionHandler.handlePermissionsResult(requestCode, grantResults)) {
            // All core permissions granted. Now check SMS if needed.
            if (isDriving) { // Only resume starting if user intends to drive
                if (shouldCaptureSmsTest && ContextCompat.checkSelfPermission(this, android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                    // SMS test enabled but permission NOT granted after request
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "SMS Test requires SEND_SMS permission. Please grant it in app settings and press START again."
                    isDriving = false // Do not proceed
                    toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                    toggleButton.text = "START"
                    Log.w("MainActivity", "SMS test enabled but SEND_SMS permission denied after request.")
                } else {
                    // All necessary permissions for current configuration are granted
                    startUpdatingInfo() // Resume/start background updates
                    locationTracker.startLocationUpdates() // Start location updates
                    if (shouldCaptureSmsTest) { // Only register if checkbox is still checked
                        smsTester.registerReceivers()
                    }
                }
            }
        } else {
            // Core permissions denied
            TransitionManager.beginDelayedTransition(rootLayout)
            infoTextView.text = "Core permissions required (Location, Phone State) to access network, cell, and location information.\nPlease grant them in app settings."
            if (isDriving) { // Reset button state if permissions were denied while trying to start
                isDriving = false
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                toggleButton.text = "START"
            }
            Log.w("MainActivity", "Required core permissions not granted.")
        }
    }

    /**
     * Updates the initial hint text based on selected checkboxes.
     */
    private fun updateInfoTextViewHint() {
        val downloadPart = if (shouldCaptureDownloadRate) "Download Rate" else ""
        val pingPart = if (shouldCapturePingTest) "Ping Test" else ""
        val smsPart = if (shouldCaptureSmsTest) "SMS Test" else ""
        val networkTests = listOf(downloadPart, pingPart, smsPart).filter { it.isNotEmpty() }.joinToString(" & ")

        infoTextView.text = "Press START to begin recording network, cell, and location data.\n" +
                "Network tests to capture: ${networkTests.ifEmpty { "None" }}."
    }

    /**
     * Refreshes the live data display. Called by SMS callback to update pending SMS status.
     */
    private fun displayLiveCurrentData() {
        if (isDriving && recordedDataList.isNotEmpty()) {
            val lastEntry = recordedDataList.last()
            val currentTime = lastEntry["timestamp"] as? String ?: "N/A"
            val downloadRateKbps = lastEntry["downloadRateKbps"] as? Double
            val pingResultMs = lastEntry["pingResultMs"] as? Double
            val smsDeliveryTimeMs = lastEntry["smsDeliveryTimeMs"] as? String

            TransitionManager.beginDelayedTransition(rootLayout)
            val displayString = StringBuilder()
            displayString.append("--- Live Data: $currentTime ---\n")

            displayString.append("  Download: ")
            if (shouldCaptureDownloadRate) {
                if (downloadRateKbps != null) {
                    displayString.append("${String.format(Locale.getDefault(), "%.2f", downloadRateKbps)} KB/s\n")
                } else {
                    displayString.append("Failed or N/A\n")
                }
            } else {
                displayString.append("Not captured (checkbox unchecked)\n")
            }

            displayString.append("  Ping: ")
            if (shouldCapturePingTest) {
                if (pingResultMs != null) {
                    displayString.append("${String.format(Locale.getDefault(), "%.2f", pingResultMs)} ms\n")
                } else {
                    displayString.append("Failed or N/A (check Logcat for ping command issues)\n")
                }
            } else {
                displayString.append("Not captured (checkbox unchecked)\n")
            }

            displayString.append("  SMS: ")
            if (shouldCaptureSmsTest) {
                displayString.append("${smsDeliveryTimeMs ?: "N/A"}\n")
            } else {
                displayString.append("Not captured (checkbox unchecked)\n")
            }

            val locationData = lastEntry["location"] as? LocationData
            if (locationData != null && locationData.latitude != null) {
                displayString.append("  Location: Fixed (Lat: ${String.format("%.6f", locationData.latitude)}, Long: ${String.format("%.6f", locationData.longitude)})\n")
            } else {
                displayString.append("  Location: ${locationData?.status ?: "N/A"}\n")
            }

            val cellInfoData = lastEntry["cellInfo"] as? CellInfoData
            if (cellInfoData != null && cellInfoData.technology != null) {
                displayString.append("  Cell Tech: ${cellInfoData.technology ?: "N/A"}, Signal: ${cellInfoData.signalStrength_dBm ?: "N/A"} dBm\n")
            } else {
                displayString.append("  Cell Status: ${cellInfoData?.status ?: "N/A"}\n")
            }

            infoTextView.text = displayString.toString()
        }
    }


    /**
     * Starts the periodic updates by posting the runnable.
     */
    private fun startUpdatingInfo() {
        Log.d("MainActivity", "Starting periodic updates (for recording).")
        handler.post(updateRunnable)
    }

    /**
     * Stops the periodic updates by removing callbacks from the handler.
     */
    private fun stopUpdatingInfo() {
        Log.d("MainActivity", "Stopping periodic updates.")
        handler.removeCallbacks(updateRunnable)
    }

    /**
     * Displays all recorded data in the infoTextView.
     */
    private fun displayRecordedData() {
        TransitionManager.beginDelayedTransition(rootLayout)

        val logBuilder = StringBuilder()
        if (recordedDataList.isEmpty()) {
            logBuilder.append("No data was recorded during the last session.")
        } else {
            logBuilder.append("--- RECORDED SESSION LOGS (${recordedDataList.size} entries) ---\n\n")
            recordedDataList.forEachIndexed { index, dataMap ->
                logBuilder.append("--- Entry ${index + 1} ---\n")
                (dataMap["timestamp"] as? String)?.let {
                    logBuilder.append("Timestamp: $it\n")
                }

                // Format location
                (dataMap["location"] as? LocationData)?.let { locationData ->
                    logBuilder.append("Location:\n")
                    if (locationData.latitude != null && locationData.longitude != null) {
                        logBuilder.append("  Latitude: ${String.format("%.6f", locationData.latitude)}\n")
                        logBuilder.append("  Longitude: ${String.format("%.6f", locationData.longitude)}\n")
                    } else {
                        logBuilder.append("  Status: ${locationData.status}\n")
                    }
                }

                // Format download rate if capture was enabled for this entry
                val downloadCaptureEnabled = dataMap["downloadCaptureEnabled"] as? Boolean ?: false
                if (downloadCaptureEnabled) {
                    (dataMap["downloadRateKbps"] as? Double)?.let { rate ->
                        logBuilder.append("  Download Rate: ${String.format(Locale.getDefault(), "%.2f", rate)} KB/s\n")
                    } ?: run {
                        logBuilder.append("  Download Rate: N/A (Failed or no internet during recording)\n")
                    }
                } else {
                    logBuilder.append("  Download Rate: Not Captured (checkbox unchecked)\n")
                }

                // Format ping result if capture was enabled for this entry
                val pingCaptureEnabled = dataMap["pingCaptureEnabled"] as? Boolean ?: false
                if (pingCaptureEnabled) {
                    (dataMap["pingResultMs"] as? Double)?.let { ping ->
                        logBuilder.append("  Ping Result: ${String.format(Locale.getDefault(), "%.2f", ping)} ms\n")
                    } ?: run {
                        logBuilder.append("  Ping Result: N/A (Failed or command restricted during recording)\n")
                    }
                } else {
                    logBuilder.append("  Ping Result: Not Captured (checkbox unchecked)\n")
                }

                // Format SMS result if capture was enabled for this entry
                val smsCaptureEnabled = dataMap["smsCaptureEnabled"] as? Boolean ?: false
                if (smsCaptureEnabled) {
                    (dataMap["smsDeliveryTimeMs"] as? String)?.let { smsResult ->
                        logBuilder.append("  SMS Delivery Time: $smsResult\n")
                    } ?: run {
                        logBuilder.append("  SMS Delivery Time: N/A (Failed or not applicable for this entry)\n")
                    }
                } else {
                    logBuilder.append("  SMS Delivery Time: Not Captured (checkbox unchecked)\n")
                }

                // Format cell info
                (dataMap["cellInfo"] as? CellInfoData)?.let { cellInfoData ->
                    logBuilder.append("Cell Info:\n")
                    cellInfoData.javaClass.declaredFields.forEach { field ->
                        field.isAccessible = true
                        val value = field.get(cellInfoData)
                        // Exclude synthetic fields and known problematic fields from display
                        if (value != null && !field.isSynthetic && field.name != "status" && field.name != "serialVersionUID") {
                            logBuilder.append("  ${field.name.replace("_", " ").capitalizeWords()}: $value\n")
                        }
                    }
                    cellInfoData.status?.let { status ->
                        logBuilder.append("  Status: $status\n")
                    }
                }
                logBuilder.append("\n") // Separator between entries
            }
        }
        infoTextView.text = logBuilder.toString()
    }
}
