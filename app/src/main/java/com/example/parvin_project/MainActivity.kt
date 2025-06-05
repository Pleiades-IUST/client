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

class MainActivity : AppCompatActivity() {

    private var isDriving: Boolean = false // State variable for recording
    private lateinit var infoTextView: TextView // Displays cell and location info
    private lateinit var toggleButton: Button // Declare the toggle button
    private lateinit var rootLayout: ViewGroup // Reference to the root layout for transitions
    private lateinit var captureDownloadRateCheckBox: CheckBox // Declare the checkbox

    // State for whether to capture download rate
    private var shouldCaptureDownloadRate: Boolean = true // Default to true

    // Handlers for periodic updates and location
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 5000L // 5 seconds in milliseconds for each entry

    // New instances of our helper classes
    private lateinit var permissionHandler: PermissionHandler
    private lateinit var locationTracker: LocationTracker
    private lateinit var cellInfoCollector: CellInfoCollector
    private lateinit var networkTester: NetworkTester

    // List to store recorded data
    private val recordedDataList = mutableListOf<Map<String, Any?>>()

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

        // Initialize helper classes
        permissionHandler = PermissionHandler(this, PERMISSION_REQUEST_CODE)
        locationTracker = LocationTracker(this, UPDATE_INTERVAL_MS) { location ->
            // This lambda is called by LocationTracker when location changes
            // You can use the updated location here if needed, or simply rely on getLocationDataForLog()
            // which will pick up the last known location from the tracker.
        }
        cellInfoCollector = CellInfoCollector(this)
        networkTester = NetworkTester(this)


        // Set initial state of checkbox and its listener
        captureDownloadRateCheckBox.isChecked = shouldCaptureDownloadRate
        captureDownloadRateCheckBox.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            shouldCaptureDownloadRate = isChecked
            Log.d("MainActivity", "Capture Download Rate: $shouldCaptureDownloadRate")
            // Update the UI hint if recording is not active
            if (!isDriving) {
                if (shouldCaptureDownloadRate) {
                    infoTextView.text = "Press START to begin recording network, cell, and location data (Download Rate will be captured)."
                } else {
                    infoTextView.text = "Press START to begin recording cell and location data (Download Rate will NOT be captured)."
                }
            }
        }

        // Initial UI State
        TransitionManager.beginDelayedTransition(rootLayout)
        infoTextView.text = "Press START to begin recording network, cell, and location data."
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
        toggleButton.text = "START"

        // Set a click listener for the toggle button
        toggleButton.setOnClickListener {
            Log.d("MainActivity", "Button click detected! isDriving state before: $isDriving")
            isDriving = !isDriving // Toggle recording state

            if (isDriving) {
                // START logic
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500))
                toggleButton.text = "STOP (Recording)"
                recordedDataList.clear() // Clear previous data
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = "Starting data collection... Please wait for first live update."
                Log.d("MainActivity", "Started recording. List cleared.")

                if (permissionHandler.checkAndRequestPermissions()) {
                    // Permissions granted, start background updates and location updates
                    startUpdatingInfo()
                    locationTracker.startLocationUpdates()
                } else {
                    // Permissions not granted, reset state and inform user
                    isDriving = false
                    toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                    toggleButton.text = "START"
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Permissions required to access network, cell, and location information.\nPlease grant them in app settings and press START again."
                    Log.w("MainActivity", "Recording attempted but permissions not granted.")
                }
            } else {
                // STOP logic
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                toggleButton.text = "START"
                Log.d("MainActivity", "Stopped recording. Displaying data.")

                stopUpdatingInfo() // Stop runnable
                locationTracker.stopLocationUpdates() // Stop location listener
                displayRecordedData() // Display all recorded data
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If permissions are already granted, ensure location updates are running when activity resumes
        if (permissionHandler.checkPermissionsWithoutRequest() && isDriving) { // Only resume location if recording was active
            locationTracker.startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        // Always stop location updates when the activity goes to background
        locationTracker.stopLocationUpdates()
        // Also ensure the updateRunnable is stopped if recording was active when app paused
        stopUpdatingInfo()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (permissionHandler.handlePermissionsResult(requestCode, grantResults)) {
            // All permissions granted
            if (isDriving) {
                startUpdatingInfo() // Resume/start background updates
            }
            locationTracker.startLocationUpdates() // Start location updates
        } else {
            // Permissions denied
            TransitionManager.beginDelayedTransition(rootLayout)
            infoTextView.text = "Permissions required to access network, cell, and location information.\nPlease grant them in app settings."
            if (isDriving) { // Reset button state if permissions were denied while trying to start
                isDriving = false
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                toggleButton.text = "START"
            }
            Log.w("MainActivity", "Required permissions not granted.")
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
                val downloadCaptureEnabled = dataMap["downloadCaptureEnabled"] as? Boolean ?: true
                if (downloadCaptureEnabled) {
                    (dataMap["downloadRateKbps"] as? Double)?.let { rate ->
                        logBuilder.append("  Download Rate: ${String.format(Locale.getDefault(), "%.2f", rate)} KB/s\n")
                    } ?: run {
                        logBuilder.append("  Download Rate: N/A (Failed or no internet during recording)\n")
                    }
                } else {
                    logBuilder.append("  Download Rate: Not Captured (checkbox unchecked)\n")
                }

                // Format cell info
                (dataMap["cellInfo"] as? CellInfoData)?.let { cellInfoData ->
                    logBuilder.append("Cell Info:\n")
                    // Iterate over properties of CellInfoData to display them
                    // Using reflection for dynamic display, or explicitly list desired fields
                    cellInfoData.javaClass.declaredFields.forEach { field ->
                        field.isAccessible = true // Allow access to private fields
                        val value = field.get(cellInfoData)
                        if (value != null && field.name != "status" && field.name != "serialVersionUID" && field.name != "\$stable") { // Exclude status which is handled
                            logBuilder.append("  ${field.name.replace("_", " ").capitalizeWords()}: $value\n")
                        }
                    }
                    // Add the status last if it exists and is not null
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
