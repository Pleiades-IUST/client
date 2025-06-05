// MainActivity.kt
package com.example.parvin_project // <--- ENSURE THIS MATCHES YOUR PROJECT'S PACKAGE NAME

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.CheckBox // Import CheckBox
import android.widget.CompoundButton // Import CompoundButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.transition.TransitionManager
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Arrays
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), LocationListener {

    private var isDriving: Boolean = false // State variable for recording
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var locationManager: LocationManager // Manages location services
    private lateinit var infoTextView: TextView // Displays cell and location info
    private lateinit var toggleButton: Button // Declare the toggle button
    private lateinit var rootLayout: ViewGroup // Reference to the root layout for transitions
    private lateinit var connectivityManager: ConnectivityManager // For checking network status
    private lateinit var captureDownloadRateCheckBox: CheckBox // Declare the checkbox

    // State for whether to capture download rate
    private var shouldCaptureDownloadRate: Boolean = true // Default to true

    // Handler to schedule periodic updates
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 5000L // 5 seconds in milliseconds for each entry

    // Stores the last known location
    private var lastKnownLocation: Location? = null

    // List to store recorded data
    private val recordedDataList = mutableListOf<Map<String, Any?>>()

    // URL for the download test (a larger, publicly accessible ZIP file)
    private val DOWNLOAD_TEST_URL = "https://google.com"

    // Runnable to perform the updates (only records when isDriving is true)
    private val updateRunnable = object : Runnable {
        override fun run() {
            if (checkPermissionsWithoutRequest()) {
                // First, immediately show that a test is performing
                runOnUiThread {
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Performing network & data collection... Please wait."
                }

                // Launch a coroutine for background network operation and data collection
                lifecycleScope.launch(Dispatchers.IO) {
                    val currentDataEntry = mutableMapOf<String, Any?>()
                    val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                    currentDataEntry["timestamp"] = currentTime

                    var downloadRateKbps: Double? = null
                    if (shouldCaptureDownloadRate) {
                        // Perform HTTP download test only if checkbox is checked
                        downloadRateKbps = performHttpDownloadTest()
                    }
                    currentDataEntry["downloadRateKbps"] = downloadRateKbps
                    currentDataEntry["downloadCaptureEnabled"] = shouldCaptureDownloadRate // Log setting

                    // Collect location data
                    currentDataEntry["location"] = getLocationData()

                    // Collect cell info data
                    currentDataEntry["cellInfo"] = getCellInfoData()

                    // Switch to Main thread to update UI and add to recorded list
                    withContext(Dispatchers.Main) {
                        // Only update UI and record if recording is still active (user hasn't pressed STOP)
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
                            val locationMap = currentDataEntry["location"] as? Map<*, *>
                            if (locationMap != null && locationMap["latitude"] != null) {
                                displayString.append("  Location: Fixed (Lat: ${String.format("%.6f", locationMap["latitude"] as? Double)}, Long: ${String.format("%.6f", locationMap["longitude"] as? Double)})\n")
                            } else {
                                displayString.append("  Location: ${locationMap?.get("status") ?: "N/A"}\n")
                            }

                            // Display simplified cell info
                            val cellInfoMap = currentDataEntry["cellInfo"] as? Map<*, *>
                            if (cellInfoMap != null && cellInfoMap["technology"] != null) {
                                displayString.append("  Cell Tech: ${cellInfoMap["technology"] ?: "N/A"}, Signal: ${cellInfoMap["signalStrength_dBm"] ?: "N/A"} dBm\n")
                            } else {
                                displayString.append("  Cell Status: ${cellInfoMap?.get("status") ?: "N/A"}\n")
                            }

                            infoTextView.text = displayString.toString()
                            recordedDataList.add(currentDataEntry) // Add to list
                            Log.d("CellInfoExtractor", "Recording data: ${currentDataEntry["timestamp"]}")
                        }
                    }
                }
            } else {
                Log.w("CellInfoExtractor", "Permissions missing during scheduled update.")
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
            // This ensures the runnable keeps running only when recording is active.
            if (isDriving) {
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            }
        }
    }

    // Request code for runtime permissions
    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        infoTextView = findViewById(R.id.infoTextView)
        toggleButton = findViewById(R.id.toggleButton)
        rootLayout = findViewById(R.id.rootLayout) // Initialize rootLayout for transitions
        captureDownloadRateCheckBox = findViewById(R.id.captureDownloadRateCheckBox) // Initialize CheckBox

        // Initialize ConnectivityManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Set initial state of checkbox and its listener
        captureDownloadRateCheckBox.isChecked = shouldCaptureDownloadRate
        captureDownloadRateCheckBox.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            shouldCaptureDownloadRate = isChecked
            Log.d("CheckBox", "Capture Download Rate: $shouldCaptureDownloadRate")
            // You might want to update the UI hint if recording is not active
            if (!isDriving) {
                if (shouldCaptureDownloadRate) {
                    infoTextView.text = "Press START to begin recording network, cell, and location data (Download Rate will be captured)."
                } else {
                    infoTextView.text = "Press START to begin recording cell and location data (Download Rate will NOT be captured)."
                }
            }
        }


        // **Initial UI State: No logs displayed, button ready to start**
        TransitionManager.beginDelayedTransition(rootLayout) // Smooth transition for initial text
        infoTextView.text = "Press START to begin recording network, cell, and location data."
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
        toggleButton.text = "START"

        // Initialize managers
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Set a click listener for the toggle button
        toggleButton.setOnClickListener {
            Log.d("ToggleButton", "Button click detected! isDriving state before: $isDriving") // Debug log
            // Toggle the isDriving state
            isDriving = !isDriving

            if (isDriving) {
                // **User pressed START: Begin recording**
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500))
                toggleButton.text = "STOP (Recording)"
                recordedDataList.clear() // Clear previous data when starting a new session

                // Initial message before the first live data comes in
                TransitionManager.beginDelayedTransition(rootLayout)
                infoTextView.text = "Starting data collection... Please wait for first live update."
                Log.d("ToggleButton", "Started recording. List cleared.")

                // Request permissions and start updates
                if (checkAndRequestPermissions()) {
                    startUpdatingInfo() // This now triggers the coroutine-based update
                    requestLocationUpdates() // Keep location updates running in parallel
                } else {
                    // Permissions not granted, reset state and inform user
                    isDriving = false // Revert state as recording can't start
                    toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                    toggleButton.text = "START"
                    TransitionManager.beginDelayedTransition(rootLayout)
                    infoTextView.text = "Permissions required to access network, cell, and location information.\nPlease grant them in app settings and press START again."
                    Log.w("ToggleButton", "Recording attempted but permissions not granted.")
                }

            } else {
                // **User pressed STOP: Stop recording and display collected data**
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                toggleButton.text = "START"
                Log.d("ToggleButton", "Stopped recording. Displaying data.")

                stopUpdatingInfo() // Stops the runnable from rescheduling
                locationManager.removeUpdates(this) // Stop location listener

                displayRecordedData() // Display all recorded data
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Ensure location updates are being received if permissions are already granted
        if (checkPermissionsWithoutRequest()) {
            requestLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        // Always remove location updates when the activity goes to background
        locationManager.removeUpdates(this)
        // Also ensure the updateRunnable is stopped if recording was active when app paused
        stopUpdatingInfo()
    }

    /**
     * Starts the periodic updates by posting the runnable.
     * This is called when recording starts.
     */
    private fun startUpdatingInfo() {
        Log.d("CellInfoExtractor", "Starting periodic updates (for recording).")
        // Post immediately, then every UPDATE_INTERVAL_MS
        handler.post(updateRunnable)
    }

    /**
     * Stops the periodic updates by removing callbacks from the handler.
     * This is called when recording stops or app pauses.
     */
    private fun stopUpdatingInfo() {
        Log.d("CellInfoExtractor", "Stopping periodic updates.")
        handler.removeCallbacks(updateRunnable)
    }

    /**
     * Checks if the required permissions (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_PHONE_STATE, INTERNET)
     * are granted. If not, it requests them from the user.
     * @return true if all required permissions are already granted, false otherwise.
     */
    private fun checkAndRequestPermissions(): Boolean {
        val fineLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        val readPhoneStatePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
        val internetPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET)
        val networkStatePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)


        val permissionsToRequest = mutableListOf<String>()

        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (coarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (readPhoneStatePermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (internetPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.INTERNET)
        }
        if (networkStatePermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_NETWORK_STATE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
            return false // Permissions were requested, not yet granted
        }
        return true // All permissions are already granted
    }

    /**
     * Checks if permissions are granted WITHOUT requesting them.
     * Used internally by the runnable to avoid re-prompting the user.
     */
    private fun checkPermissionsWithoutRequest(): Boolean {
        val fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val readPhoneStateGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        val internetGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        val networkStateGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED

        return (fineLocationGranted || coarseLocationGranted) && readPhoneStateGranted && internetGranted && networkStateGranted
    }

    /**
     * Callback method that is invoked when the user responds to the permission request dialog.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false
                    break
                }
            }
            if (allPermissionsGranted) {
                // If permissions are granted, and we were attempting to start recording
                if (isDriving) {
                    startUpdatingInfo() // Start background updates
                }
                requestLocationUpdates() // Always try to request location updates if permissions are granted
            } else {
                // If permissions are denied, update UI and reset state if it was trying to record
                TransitionManager.beginDelayedTransition(rootLayout) // Smooth transition for permission denial message
                infoTextView.text = "Permissions required to access network, cell, and location information.\nPlease grant them in app settings."
                if (isDriving) { // Reset button state if permissions were denied while trying to start
                    isDriving = false
                    toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                    toggleButton.text = "START"
                }
                Log.w("CellInfoExtractor", "Required permissions not granted.")
            }
        }
    }

    /**
     * Checks if the device has an active network connection.
     * Requires ACCESS_NETWORK_STATE permission.
     */
    private fun isNetworkAvailable(): Boolean {
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    /**
     * Requests location updates from the LocationManager.
     * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
     */
    private fun requestLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    locationManager.requestLocationUpdates(
                        provider,
                        UPDATE_INTERVAL_MS,
                        10f,
                        this
                    )
                    Log.d("CellInfoExtractor", "Requesting location updates from $provider.")
                } else {
                    Log.w("CellInfoExtractor", "GPS and Network location providers are disabled.")
                }
            } catch (e: SecurityException) {
                Log.e("CellInfoExtractor", "SecurityException requesting location updates: ${e.message}")
            } catch (e: Exception) {
                Log.e("CellInfoExtractor", "Error requesting location updates: ${e.message}", e)
            }
        } else {
            Log.w("CellInfoExtractor", "Location permissions not granted for updates.")
        }
    }

    /**
     * Callback for when the location has changed. This is where you get the Latitude and Longitude.
     */
    override fun onLocationChanged(location: Location) {
        lastKnownLocation = location // Store the latest location
        Log.d("CellInfoExtractor", "Location updated: Lat=${location.latitude}, Long=${location.longitude}")
    }

    // --- LocationListener Interface Methods (Required, can be empty if no specific logic needed) ---
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        Log.d("CellInfoExtractor", "Location provider status changed: $provider, status: $status")
    }

    override fun onProviderEnabled(provider: String) {
        Log.d("CellInfoExtractor", "Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        Log.d("CellInfoExtractor", "Location provider disabled: $provider")
    }

    /**
     * Helper function to get PLMN-ID string, handling unavailable integer values.
     */
    private fun getPlmnId(mcc: Int, mnc: Int): String {
        val mccStr = if (mcc != Int.MAX_VALUE) mcc.toString() else "N/A"
        val mncStr = if (mnc != Int.MAX_VALUE) mnc.toString() else "N/A"
        return "$mccStr-$mncStr"
    }

    /**
     * Performs an HTTP GET download test to measure download rate.
     * This function should be called from a background thread.
     * @return Download rate in KB/s (Double) or null if an error occurs.
     */
    private fun performHttpDownloadTest(): Double? {
        var urlConnection: HttpURLConnection? = null
        var inputStream: InputStream? = null
        try {
            // First, check for general network availability before attempting download
            if (!isNetworkAvailable()) {
                Log.e("DownloadTest", "No active internet connection detected.")
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
                Log.e("DownloadTest", "HTTP error code: $responseCode for $DOWNLOAD_TEST_URL")
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
                Log.w("DownloadTest", "Download duration was zero or negative. Bytes: $bytesDownloaded")
                return null // Avoid division by zero
            }

            val durationSeconds = durationNs / 1_000_000_000.0 // Convert nanoseconds to seconds
            val downloadRateBytesPerSecond = bytesDownloaded / durationSeconds
            val downloadRateKbps = downloadRateBytesPerSecond / 1024.0 // Convert to KB/s

            Log.d("DownloadTest", "Downloaded $bytesDownloaded bytes in ${String.format(Locale.getDefault(), "%.2f", durationSeconds)}s. Rate: ${String.format(Locale.getDefault(), "%.2f", downloadRateKbps)} KB/s")
            return downloadRateKbps
        } catch (e: Exception) {
            Log.e("DownloadTest", "Error during HTTP download test for $DOWNLOAD_TEST_URL: ${e.message}", e)
            return null
        } finally {
            inputStream?.close() // Close input stream
            urlConnection?.disconnect() // Always disconnect the connection
        }
    }

    /**
     * Collects and returns location data.
     */
    private fun getLocationData(): Map<String, Any?> {
        val locationData = mutableMapOf<String, Any?>()
        if (lastKnownLocation != null) {
            val lat = String.format("%.6f", lastKnownLocation!!.latitude).toDouble()
            val long = String.format("%.6f", lastKnownLocation!!.longitude).toDouble()
            locationData["latitude"] = lat
            locationData["longitude"] = long
            locationData["status"] = "Fixed"
        } else {
            val status = if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                "GPS/Network providers not enabled on device settings. Please enable Location Services."
            } else {
                "Waiting for GPS/Network fix."
            }
            locationData["latitude"] = null
            locationData["longitude"] = null
            locationData["status"] = status
        }
        return locationData
    }

    /**
     * Collects and returns cell information.
     */
    private fun getCellInfoData(): Map<String, Any?> {
        val cellData = mutableMapOf<String, Any?>()
        try {
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

            if (cellInfoList.isNullOrEmpty()) {
                cellData["status"] = "No cell information available or permissions not granted."
            } else {
                var servingCellFound = false
                for (cellInfo in cellInfoList) {
                    if (cellInfo.isRegistered) { // This is the serving cell
                        servingCellFound = true

                        when (cellInfo) {
                            is CellInfoGsm -> {
                                val ssGsm: CellSignalStrengthGsm = cellInfo.cellSignalStrength
                                val cellIdentityGsm = cellInfo.cellIdentity
                                cellData["technology"] = "GSM"
                                cellData["signalStrength_dBm"] = ssGsm.dbm
                                cellData["plmnId"] = getPlmnId(cellIdentityGsm.mcc, cellIdentityGsm.mnc)
                                cellData["lac"] = cellIdentityGsm.lac
                                cellData["cellId"] = cellIdentityGsm.cid
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    cellData["arfcn"] = cellIdentityGsm.arfcn
                                }
                            }
                            is CellInfoCdma -> {
                                val ssCdma: CellSignalStrengthCdma = cellInfo.cellSignalStrength
                                val cellIdentityCdma = cellInfo.cellIdentity
                                cellData["technology"] = "CDMA"
                                cellData["signalStrength_dBm"] = ssCdma.dbm
                                cellData["networkId"] = cellIdentityCdma.networkId
                                cellData["systemId"] = cellIdentityCdma.systemId
                                cellData["cellId"] = cellIdentityCdma.basestationId
                            }
                            is CellInfoLte -> {
                                val ssLte: CellSignalStrengthLte = cellInfo.cellSignalStrength
                                val cellIdentityLte = cellInfo.cellIdentity
                                cellData["technology"] = "LTE"
                                cellData["signalStrength_dBm"] = ssLte.dbm
                                cellData["rsrp_dBm"] = ssLte.rsrp
                                cellData["rsrq_dB"] = ssLte.rsrq
                                cellData["plmnId"] = getPlmnId(cellIdentityLte.mcc, cellIdentityLte.mnc)
                                cellData["tac"] = cellIdentityLte.tac
                                cellData["cellId"] = cellIdentityLte.ci
                                cellData["pci"] = cellIdentityLte.pci
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    cellData["earfcn"] = cellIdentityLte.earfcn
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                    cellData["bandwidth_kHz"] = cellIdentityLte.bandwidth
                                }
                            }
                            is CellInfoWcdma -> {
                                val ssWcdma: CellSignalStrengthWcdma = cellInfo.cellSignalStrength
                                val cellIdentityWcdma = cellInfo.cellIdentity
                                cellData["technology"] = "WCDMA"
                                cellData["signalStrength_dBm"] = ssWcdma.dbm
                                cellData["plmnId"] = getPlmnId(cellIdentityWcdma.mcc, cellIdentityWcdma.mnc)
                                cellData["lac"] = cellIdentityWcdma.lac
                                cellData["cellId"] = cellIdentityWcdma.cid
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    cellData["uarfcn"] = cellIdentityWcdma.uarfcn
                                }
                            }
                            is CellInfoTdscdma -> {
                                val ssTdscdma: CellSignalStrengthTdscdma = cellInfo.cellSignalStrength
                                val cellIdentityTdscdma = cellInfo.cellIdentity
                                cellData["technology"] = "TDSCDMA"
                                cellData["signalStrength_dBm"] = ssTdscdma.dbm
                                cellData["lac"] = cellIdentityTdscdma.lac
                                cellData["cellId"] = cellIdentityTdscdma.cid
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    cellData["uarfcn"] = cellIdentityTdscdma.uarfcn
                                }
                            }
                            is CellInfoNr -> { // 5G NR
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val ssNr = cellInfo.cellSignalStrength as? CellSignalStrengthNr
                                    val cellIdentityNr = cellInfo.cellIdentity as? android.telephony.CellIdentityNr

                                    if (ssNr != null) {
                                        cellData["technology"] = "5G NR"
                                        cellData["signalStrength_dBm"] = ssNr.dbm

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            cellData["ssRsrp_dBm"] = ssNr.ssRsrp
                                            cellData["ssRsrq_dB"] = ssNr.ssRsrq
                                            cellData["csiRsrp_dBm"] = ssNr.csiRsrp
                                            cellData["csiRsrq_dB"] = ssNr.csiRsrq

                                            if (cellIdentityNr != null) {
                                                cellData["plmnId"] = "${cellIdentityNr.mccString ?: "N/A"}-${cellIdentityNr.mncString ?: "N/A"}"
                                                cellData["cellId"] = cellIdentityNr.nci
                                                cellData["tac"] = cellIdentityNr.tac
                                                cellData["nrarfcn"] = cellIdentityNr.nrarfcn
                                                cellData["bands"] = cellIdentityNr.bands?.toList()
                                            } else {
                                                cellData["identityDetails"] = "NR Cell Identity details not available."
                                            }
                                        } else {
                                            cellData["details_api_level"] = "NR specific details require Android R (API 30+)"
                                        }
                                        if (cellIdentityNr != null) {
                                            cellData["pci"] = cellIdentityNr.pci
                                        }
                                    } else {
                                        cellData["status"] = "5G NR: Signal strength details not available."
                                    }
                                } else {
                                    cellData["status"] = "5G NR: Requires Android Q+ (API 29+)"
                                }
                            }
                            else -> {
                                cellData["technology"] = "Unknown"
                                cellData["cellInfoType"] = cellInfo.javaClass.simpleName
                                val genericSignalStrength: CellSignalStrength? = try { cellInfo.cellSignalStrength } catch (e: Exception) { null }
                                genericSignalStrength?.let {
                                    cellData["signalStrength_dBm_generic"] = it.dbm
                                }
                            }
                        }
                        break // Break after finding the serving cell
                    }
                }
                if (!servingCellFound) {
                    cellData["status"] = "No serving cell found (isRegistered = true)."
                }
            }
        } catch (e: SecurityException) {
            cellData["status"] = "Security Exception: Permission to read cell info not granted."
            Log.e("CellInfoExtractor", "Security Exception: ${e.message}")
        } catch (e: Exception) {
            cellData["status"] = "Error getting cell info: ${e.message}"
            Log.e("CellInfoExtractor", "Error getting cell info: ${e.message}", e)
        }
        return cellData
    }


    /**
     * This function is now responsible for gathering all structured data
     * (though for this debug phase, only timestamp is directly returned from here).
     * The download and upload tests are performed in the updateRunnable's coroutine.
     */
    private fun getCellAndLocationData(): Pair<String, Map<String, Any?>> {
        val uiStringBuilder = StringBuilder() // Placeholder; UI string is built in updateRunnable
        val structuredDataMap = mutableMapOf<String, Any?>()

        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        structuredDataMap["timestamp"] = currentTime

        // This method no longer performs the network tests directly
        // It's just a placeholder for data collection structure.

        return Pair(uiStringBuilder.toString(), structuredDataMap)
    }

    /**
     * Displays all recorded data in the infoTextView.
     * Now primarily displays download and upload rates.
     */
    private fun displayRecordedData() {
        // Start transition before updating text
        TransitionManager.beginDelayedTransition(rootLayout)

        val logBuilder = StringBuilder()
        if (recordedDataList.isEmpty()) {
            logBuilder.append("No data was recorded during the last session.")
        } else {
            logBuilder.append("--- RECORDED SESSION LOGS (${recordedDataList.size} entries) ---\n\n") // Updated title
            recordedDataList.forEachIndexed { index, dataMap ->
                logBuilder.append("--- Entry ${index + 1} ---\n")
                // Format timestamp
                (dataMap["timestamp"] as? String)?.let {
                    logBuilder.append("Timestamp: $it\n")
                }

                // Format location
                (dataMap["location"] as? Map<*, *>)?.let { locationMap ->
                    logBuilder.append("Location:\n")
                    if (locationMap["latitude"] != null && locationMap["longitude"] != null) {
                        logBuilder.append("  Latitude: ${String.format("%.6f", locationMap["latitude"] as? Double)}\n")
                        logBuilder.append("  Longitude: ${String.format("%.6f", locationMap["longitude"] as? Double)}\n")
                    } else {
                        logBuilder.append("  Status: ${locationMap["status"] ?: "N/A"}\n")
                    }
                }

                // Format download rate if capture was enabled for this entry
                val downloadCaptureEnabled = dataMap["downloadCaptureEnabled"] as? Boolean ?: true // Default to true if not logged
                if (downloadCaptureEnabled) {
                    (dataMap["downloadRateKbps"] as? Double)?.let { rate ->
                        logBuilder.append("  Download Rate: ${String.format(Locale.getDefault(), "%.2f", rate)} KB/s\n")
                    } ?: run {
                        logBuilder.append("  Download Rate: N/A (Failed or no internet during recording)\n")
                    }
                } else {
                    logBuilder.append("  Download Rate: Not Captured (checkbox unchecked)\n")
                }

                // Removed upload rate formatting

                // Format cell info
                (dataMap["cellInfo"] as? Map<*, *>)?.let { cellInfoMap ->
                    logBuilder.append("Cell Info:\n")
                    cellInfoMap.forEach { (key, value) ->
                        if (key !in listOf("identityDetails", "details_api_level", "cellInfoType")) {
                            logBuilder.append("  ${(key as String).replace("_", " ").capitalizeWords()}: ${value ?: "N/A"}\n")
                        }
                    }
                }
                logBuilder.append("\n") // Separator between entries
            }
        }
        infoTextView.text = logBuilder.toString()
    }

    // Helper extension function to capitalize words for better display of map keys
    private fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        // Use replaceFirstChar for modern Kotlin string capitalization
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}