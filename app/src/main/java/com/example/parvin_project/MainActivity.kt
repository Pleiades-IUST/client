// MainActivity.kt
package com.example.parvin_project // <--- ENSURE THIS MATCHES YOUR PROJECT'S PACKAGE NAME

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
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
import android.widget.Button // Import Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// MainActivity now implements LocationListener to receive location updates
class MainActivity : AppCompatActivity(), LocationListener {

    private var isDriving: Boolean = false // State variable for recording
    private lateinit var telephonyManager: TelephonyManager
    private lateinit var locationManager: LocationManager // Manages location services
    private lateinit var infoTextView: TextView // Displays cell and location info
    private lateinit var toggleButton: Button // Declare the toggle button

    // Handler to schedule periodic updates
    private val handler = Handler(Looper.getMainLooper())
    private val UPDATE_INTERVAL_MS = 3000L // 3 seconds in milliseconds for both cell and location

    // Stores the last known location
    private var lastKnownLocation: Location? = null

    // List to store recorded data
    private val recordedDataList = mutableListOf<Map<String, Any?>>()

    // Runnable to perform the updates
    private val updateRunnable = object : Runnable {
        override fun run() {
            // Check permissions before attempting to get info, in case they were revoked
            if (checkPermissionsWithoutRequest()) {
                val (uiString, structuredData) = getCellAndLocationData() // Get both UI string and structured data

                // Always update the UI with the latest info
                infoTextView.text = uiString

                // If isDriving is true, add structured data to the list
                if (isDriving) {
                    recordedDataList.add(structuredData)
                    Log.d("CellInfoExtractor", "Recording data: ${structuredData["timestamp"]}")
                }
            } else {
                // If permissions are somehow lost, clear info and log
                infoTextView.text = "Permissions required to access cell and location information.\nPlease grant them in app settings."
                Log.w("CellInfoExtractor", "Permissions missing during scheduled update.")
            }
            // Schedule the next update
            handler.postDelayed(this, UPDATE_INTERVAL_MS)
        }
    }

    // Request code for runtime permissions
    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to the XML layout file
        setContentView(R.layout.activity_main)

        // Initialize UI elements by finding them by their IDs in the layout
        infoTextView = findViewById(R.id.infoTextView)
        toggleButton = findViewById(R.id.toggleButton) // Initialize the toggle button

        // Set initial button state (no driving, blue)
        toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
        toggleButton.text = "start" // Initial text

        // Set a click listener for the toggle button
        toggleButton.setOnClickListener {
            // Toggle the isDriving state
            isDriving = !isDriving

            if (isDriving) {
                // Start driving/recording
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.red_500))
                toggleButton.text = "stop (Recording)"
                recordedDataList.clear() // Clear previous data when starting a new session
                Log.d("ToggleButton", "Started recording. List cleared.")
            } else {
                // Stop driving/recording
                toggleButton.setBackgroundColor(ContextCompat.getColor(this, R.color.blue_500))
                toggleButton.text = "start"
                Log.d("ToggleButton", "Stopped recording. Printing data.")
                displayRecordedData() // Display all recorded data
            }
        }

        // Get instances of system services
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    override fun onResume() {
        super.onResume()
        // When the activity comes to the foreground, check permissions and start updates
        if (checkAndRequestPermissions()) {
            startUpdatingInfo()
        }
    }

    override fun onPause() {
        super.onPause()
        // When the activity goes to the background, stop updates to save battery
        stopUpdatingInfo()
    }

    /**
     * Starts the periodic updates by posting the runnable.
     */
    private fun startUpdatingInfo() {
        Log.d("CellInfoExtractor", "Starting periodic updates.")
        // Post immediately, then every UPDATE_INTERVAL_MS
        handler.post(updateRunnable)
    }

    /**
     * Stops the periodic updates by removing callbacks from the handler.
     */
    private fun stopUpdatingInfo() {
        Log.d("CellInfoExtractor", "Stopping periodic updates.")
        handler.removeCallbacks(updateRunnable)
        locationManager.removeUpdates(this) // Also remove any pending location updates
    }

    /**
     * Checks if the required permissions (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_PHONE_STATE)
     * are granted. If not, it requests them from the user.
     * This version requests permissions if not granted.
     * @return true if all required permissions are already granted, false otherwise.
     */
    private fun checkAndRequestPermissions(): Boolean {
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        val readPhoneStatePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_PHONE_STATE
        )

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

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(), // Convert list to array
                PERMISSION_REQUEST_CODE // Use a unique request code
            )
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
        return (fineLocationGranted || coarseLocationGranted) && readPhoneStateGranted
    }

    /**
     * Callback method that is invoked when the user responds to the permission request dialog.
     * This method checks if all requested permissions were granted.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>, // The requested permissions
        grantResults: IntArray // The grant results for the corresponding permissions
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Check if the result is for our specific permission request
        if (requestCode == PERMISSION_REQUEST_CODE) {
            var allPermissionsGranted = true
            // Iterate through the grant results to see if any permission was denied
            for (result in grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false // At least one permission was denied
                    break
                }
            }
            if (allPermissionsGranted) {
                // If all permissions are granted, start the periodic updates
                startUpdatingInfo()
            } else {
                // If permissions are denied, update the TextView to inform the user
                infoTextView.text = "Permissions required to access cell and location information.\nPlease grant them in app settings."
                Log.w("CellInfoExtractor", "Required permissions not granted.")
            }
        }
    }

    /**
     * Requests location updates from the LocationManager.
     * Requires ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION.
     */
    private fun requestLocationUpdates() {
        // Check for location permissions before requesting updates
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            try {
                // Determine the best available provider
                val provider = when {
                    locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    else -> null
                }

                if (provider != null) {
                    // Request updates every 3 seconds (UPDATE_INTERVAL_MS) and 10 meters
                    locationManager.requestLocationUpdates(
                        provider,
                        UPDATE_INTERVAL_MS, // Minimum time interval between updates in milliseconds (3 seconds)
                        10f,  // Minimum distance between updates in meters (10 meters)
                        this // The LocationListener instance (this Activity)
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
        // The UI update for location will happen when getCellInfo() is called by the runnable
        // as it will now use lastKnownLocation.
    }

    // --- LocationListener Interface Methods (Required, can be empty if no specific logic needed) ---
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Called when the provider status changes (e.g., GPS signal lost/gained)
        Log.d("CellInfoExtractor", "Location provider status changed: $provider, status: $status")
    }

    override fun onProviderEnabled(provider: String) {
        // Called when the provider is enabled by the user (e.g., GPS turned on)
        Log.d("CellInfoExtractor", "Location provider enabled: $provider")
        // No need to re-request here, the runnable will handle it.
    }

    override fun onProviderDisabled(provider: String) {
        // Called when the provider is disabled by the user (e.g., GPS turned off)
        Log.d("CellInfoExtractor", "Location provider disabled: $provider")
        // The UI will be updated by the runnable if permissions are missing or providers disabled.
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
     * Extracts and returns serving cell information and location data as a Pair:
     * first element is the formatted string for UI, second is Map<String, Any?> for structured data.
     */
    private fun getCellAndLocationData(): Pair<String, Map<String, Any?>> {
        val uiStringBuilder = StringBuilder()
        val structuredDataMap = mutableMapOf<String, Any?>()

        // Get current time and format it
        val currentTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        uiStringBuilder.append("--- Last Updated: $currentTime ---\n\n")
        structuredDataMap["timestamp"] = currentTime

        // Append Location Info
        uiStringBuilder.append("--- Current Location ---\n")
        val locationData = mutableMapOf<String, Any?>()
        if (lastKnownLocation != null) {
            val lat = String.format("%.6f", lastKnownLocation!!.latitude).toDouble()
            val long = String.format("%.6f", lastKnownLocation!!.longitude).toDouble()
            uiStringBuilder.append("  Latitude: $lat\n")
            uiStringBuilder.append("  Longitude: $long\n")
            locationData["latitude"] = lat
            locationData["longitude"] = long
            locationData["status"] = "Fixed"
        } else {
            uiStringBuilder.append("  Location: Waiting for GPS/Network fix or providers disabled.\n")
            val status = if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                "GPS/Network providers not enabled on device settings. Please enable Location Services."
            } else {
                "Waiting for GPS/Network fix."
            }
            uiStringBuilder.append("  $status\n")
            locationData["latitude"] = null
            locationData["longitude"] = null
            locationData["status"] = status
        }
        structuredDataMap["location"] = locationData
        uiStringBuilder.append("\n") // Separator

        // Append Cell Info
        uiStringBuilder.append("--- Serving Cell Power & IDs ---\n\n")
        val cellData = mutableMapOf<String, Any?>()

        try {
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

            if (cellInfoList.isNullOrEmpty()) {
                uiStringBuilder.append("No cell information available or permissions not granted.\n")
                cellData["status"] = "No cell information available or permissions not granted."
            } else {
                var servingCellFound = false
                for (cellInfo in cellInfoList) {
                    if (cellInfo.isRegistered) { // This is the serving cell
                        servingCellFound = true
                        uiStringBuilder.append("Serving Cell Details:\n")

                        // Extract technology-specific signal strength and IDs
                        when (cellInfo) {
                            is CellInfoGsm -> {
                                val ssGsm: CellSignalStrengthGsm = cellInfo.cellSignalStrength
                                val cellIdentityGsm = cellInfo.cellIdentity
                                uiStringBuilder.append("  Technology: GSM\n")
                                uiStringBuilder.append("  Signal Strength (dBm): ${ssGsm.dbm}\n")
                                uiStringBuilder.append("  PLMN-ID (MCC-MNC): ${getPlmnId(cellIdentityGsm.mcc, cellIdentityGsm.mnc)}\n")
                                uiStringBuilder.append("  LAC: ${cellIdentityGsm.lac}\n")
                                uiStringBuilder.append("  Cell ID (CID): ${cellIdentityGsm.cid}\n")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24
                                    uiStringBuilder.append("  ARFCN: ${cellIdentityGsm.arfcn}\n")
                                }

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
                                uiStringBuilder.append("  Technology: CDMA\n")
                                uiStringBuilder.append("  Signal Strength (dBm): ${ssCdma.dbm}\n")
                                uiStringBuilder.append("  Network ID: ${cellIdentityCdma.networkId}\n")
                                uiStringBuilder.append("  System ID: ${cellIdentityCdma.systemId}\n")
                                uiStringBuilder.append("  Base Station ID (Cell ID): ${cellIdentityCdma.basestationId}\n")

                                cellData["technology"] = "CDMA"
                                cellData["signalStrength_dBm"] = ssCdma.dbm
                                cellData["networkId"] = cellIdentityCdma.networkId
                                cellData["systemId"] = cellIdentityCdma.systemId
                                cellData["cellId"] = cellIdentityCdma.basestationId
                            }
                            is CellInfoLte -> {
                                val ssLte: CellSignalStrengthLte = cellInfo.cellSignalStrength
                                val cellIdentityLte = cellInfo.cellIdentity
                                uiStringBuilder.append("  Technology: LTE\n")
                                uiStringBuilder.append("  Signal Strength (dBm): ${ssLte.dbm}\n")
                                uiStringBuilder.append("  RSRP (dBm): ${ssLte.rsrp}\n")
                                uiStringBuilder.append("  RSRQ (dB): ${ssLte.rsrq}\n")
                                uiStringBuilder.append("  PLMN-ID (MCC-MNC): ${getPlmnId(cellIdentityLte.mcc, cellIdentityLte.mnc)}\n")
                                uiStringBuilder.append("  TAC: ${cellIdentityLte.tac}\n")
                                uiStringBuilder.append("  Cell ID (CI): ${cellIdentityLte.ci}\n")
                                uiStringBuilder.append("  PCI: ${cellIdentityLte.pci}\n")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24
                                    uiStringBuilder.append("  EARFCN: ${cellIdentityLte.earfcn}\n")
                                }
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // API 28
                                    uiStringBuilder.append("  Bandwidth: ${cellIdentityLte.bandwidth} kHz\n")
                                }

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
                                uiStringBuilder.append("  Technology: WCDMA\n")
                                uiStringBuilder.append("  Signal Strength (dBm): ${ssWcdma.dbm}\n")
                                uiStringBuilder.append("  PLMN-ID (MCC-MNC): ${getPlmnId(cellIdentityWcdma.mcc, cellIdentityWcdma.mnc)}\n")
                                uiStringBuilder.append("  LAC: ${cellIdentityWcdma.lac}\n")
                                uiStringBuilder.append("  Cell ID (CID): ${cellIdentityWcdma.cid}\n")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24
                                    uiStringBuilder.append("  UARFCN: ${cellIdentityWcdma.uarfcn}\n")
                                }

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
                                uiStringBuilder.append("  Technology: TDSCDMA\n")
                                uiStringBuilder.append("  Signal Strength (dBm): ${ssTdscdma.dbm}\n")
//                                uiStringBuilder.append("  PLMN-ID (MCC-MNC): ${getPlmnId(cellIdentityTdscdma.mcc, cellIdentityTdscdma.mnc)}\n")
                                uiStringBuilder.append("  LAC: ${cellIdentityTdscdma.lac}\n")
                                uiStringBuilder.append("  Cell ID (CID): ${cellIdentityTdscdma.cid}\n")
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // API 24
                                    uiStringBuilder.append("  UARFCN: ${cellIdentityTdscdma.uarfcn}\n")
                                }

                                cellData["technology"] = "TDSCDMA"
                                cellData["signalStrength_dBm"] = ssTdscdma.dbm
//                                cellData["plmnId"] = getPlmnId(cellIdentityTdscdma.mcc, cellIdentityTdscdma.mnc)
                                cellData["lac"] = cellIdentityTdscdma.lac
                                cellData["cellId"] = cellIdentityTdscdma.cid
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                    cellData["uarfcn"] = cellIdentityTdscdma.uarfcn
                                }
                            }
                            is CellInfoNr -> { // 5G NR
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29
                                    val ssNr = cellInfo.cellSignalStrength as? CellSignalStrengthNr
                                    val cellIdentityNr = cellInfo.cellIdentity as? android.telephony.CellIdentityNr // Safe cast for CellIdentityNr

                                    if (ssNr != null) {
                                        uiStringBuilder.append("  Technology: 5G NR\n")
                                        uiStringBuilder.append("  Signal Strength (dBm): ${ssNr.dbm}\n")
                                        cellData["technology"] = "5G NR"
                                        cellData["signalStrength_dBm"] = ssNr.dbm

                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) { // API 30
                                            uiStringBuilder.append("  SS-RSRP (dBm): ${ssNr.ssRsrp}\n")
                                            uiStringBuilder.append("  SS-RSRQ (dB): ${ssNr.ssRsrq}\n")
                                            uiStringBuilder.append("  CSI-RSRP (dBm): ${ssNr.csiRsrp}\n")
                                            uiStringBuilder.append("  CSI-RSRQ (dB): ${ssNr.csiRsrq}\n")

                                            cellData["ssRsrp_dBm"] = ssNr.ssRsrp
                                            cellData["ssRsrq_dB"] = ssNr.ssRsrq
                                            cellData["csiRsrp_dBm"] = ssNr.csiRsrp
                                            cellData["csiRsrq_dB"] = ssNr.csiRsrq

                                            if (cellIdentityNr != null) {
                                                uiStringBuilder.append("  PLMN-ID (MCC-MNC): ${cellIdentityNr.mccString ?: "N/A"}-${cellIdentityNr.mncString ?: "N/A"}\n")
                                                uiStringBuilder.append("  NCI (Cell ID): ${cellIdentityNr.nci}\n") // NR Cell Identity
                                                uiStringBuilder.append("  TAC: ${cellIdentityNr.tac}\n") // Tracking Area Code
                                                uiStringBuilder.append("  NR-ARFCN: ${cellIdentityNr.nrarfcn}\n")
                                                val bands = cellIdentityNr.bands
                                                if (bands != null && bands.isNotEmpty()) {
                                                    uiStringBuilder.append("  Bands: ${bands.joinToString(", ")}\n")
                                                } else {
                                                    uiStringBuilder.append("  Bands: N/A\n")
                                                }

                                                cellData["plmnId"] = "${cellIdentityNr.mccString ?: "N/A"}-${cellIdentityNr.mncString ?: "N/A"}"
                                                cellData["cellId"] = cellIdentityNr.nci
                                                cellData["tac"] = cellIdentityNr.tac
                                                cellData["nrarfcn"] = cellIdentityNr.nrarfcn
                                                cellData["bands"] = bands?.toList() // Convert to List for JSON
                                            } else {
                                                uiStringBuilder.append("  NR Cell Identity details (PLMN-ID, NCI, TAC, NR-ARFCN, Bands) not available or not CellIdentityNr type.\n")
                                                cellData["identityDetails"] = "Not available or not CellIdentityNr type."
                                            }
                                        } else {
                                            uiStringBuilder.append("  NR specific details (PLMN-ID, NCI, TAC, NR-ARFCN, Bands, SS/CSI RSRP/RSRQ/SINR) require Android R (API 30+)\n")
                                            cellData["details_api_level"] = "Requires Android R (API 30+)"
                                        }
                                        // PCI is available from Q (API 29) on CellIdentityNr
                                        if (cellIdentityNr != null) {
                                            uiStringBuilder.append("  PCI: ${cellIdentityNr.pci}\n") // Physical Cell ID
                                            cellData["pci"] = cellIdentityNr.pci
                                        } else {
                                            uiStringBuilder.append("  PCI not available or not CellIdentityNr type.\n")
                                        }
                                    } else {
                                        uiStringBuilder.append("  Technology: 5G NR (Signal strength details not available for this type)\n")
                                        Log.w("CellInfoExtractor", "CellInfoNr.cellSignalStrength was not CellSignalStrengthNr for a CellInfoNr instance.")
                                        cellData["status"] = "Signal strength details not available for this type."
                                    }
                                } else {
                                    uiStringBuilder.append("  Technology: 5G NR (requires Android Q+)\n")
                                    cellData["status"] = "Requires Android Q+ (API 29+)"
                                }
                            }
                            else -> {
                                uiStringBuilder.append("  Technology: Unknown or unsupported type\n")
                                uiStringBuilder.append("  CellInfo type: ${cellInfo.javaClass.simpleName}\n")
                                cellData["technology"] = "Unknown"
                                cellData["cellInfoType"] = cellInfo.javaClass.simpleName
                                try {
                                    val signalStrengthMethod = CellInfo::class.java.getMethod("getCellSignalStrength")
                                    val genericSignalStrength = signalStrengthMethod.invoke(cellInfo) as CellSignalStrength
                                    uiStringBuilder.append("  Generic Signal Strength (dBm): ${genericSignalStrength.dbm}\n")
                                    cellData["signalStrength_dBm"] = genericSignalStrength.dbm
                                } catch (e: Exception) {
                                    Log.e("CellInfoExtractor", "Could not get generic signal strength: ${e.message}")
                                    cellData["signalStrength_error"] = e.message
                                }
                            }
                        }
                        break // Found the serving cell, no need to check others for this specific request
                    }
                }
                if (!servingCellFound) {
                    uiStringBuilder.append("No serving cell found.\n")
                    cellData["status"] = "No serving cell found."
                }
            }
        } catch (e: SecurityException) {
            uiStringBuilder.append("Permission denied: ${e.message}\n")
            Log.e("CellInfoExtractor", "SecurityException: ${e.message}")
            cellData["error"] = "Permission denied: ${e.message}"
        } catch (e: Exception) {
            uiStringBuilder.append("Error getting cell info: ${e.message}\n")
            Log.e("CellInfoExtractor", "Error: ${e.message}", e)
            cellData["error"] = "Error getting cell info: ${e.message}"
        }
        structuredDataMap["servingCell"] = cellData
        return Pair(uiStringBuilder.toString(), structuredDataMap)
    }

    /**
     * Displays all recorded data in the infoTextView.
     */
    private fun displayRecordedData() {
        val displayBuilder = StringBuilder()
        if (recordedDataList.isEmpty()) {
            displayBuilder.append("No data was recorded during the session.\n")
        } else {
            displayBuilder.append("--- Recorded Data Session ---\n\n")
            recordedDataList.forEachIndexed { index, dataMap ->
                displayBuilder.append("--- Record ${index + 1} (${dataMap["timestamp"]}) ---\n")

                // Location data
                val location = dataMap["location"] as? Map<*, *>
                if (location != null) {
                    displayBuilder.append("  Location:\n")
                    displayBuilder.append("    Latitude: ${location["latitude"] ?: "N/A"}\n")
                    displayBuilder.append("    Longitude: ${location["longitude"] ?: "N/A"}\n")
                    displayBuilder.append("    Status: ${location["status"] ?: "N/A"}\n")
                }

                // Cell data
                val cell = dataMap["servingCell"] as? Map<*, *>
                if (cell != null) {
                    displayBuilder.append("  Serving Cell:\n")
                    displayBuilder.append("    Technology: ${cell["technology"] ?: "N/A"}\n")
                    displayBuilder.append("    Signal Strength (dBm): ${cell["signalStrength_dBm"] ?: "N/A"}\n")
                    displayBuilder.append("    PLMN-ID: ${cell["plmnId"] ?: "N/A"}\n")
                    cell["lac"]?.let { displayBuilder.append("    LAC: $it\n") }
                    cell["tac"]?.let { displayBuilder.append("    TAC: $it\n") }
                    cell["cellId"]?.let { displayBuilder.append("    Cell ID: $it\n") }
                    cell["pci"]?.let { displayBuilder.append("    PCI: $it\n") }
                    cell["arfcn"]?.let { displayBuilder.append("    ARFCN: $it\n") }
                    cell["earfcn"]?.let { displayBuilder.append("    EARFCN: $it\n") }
                    cell["uarfcn"]?.let { displayBuilder.append("    UARFCN: $it\n") }
                    cell["nrarfcn"]?.let { displayBuilder.append("    NR-ARFCN: $it\n") }
                    cell["bandwidth_kHz"]?.let { displayBuilder.append("    Bandwidth (kHz): $it\n") }
                    (cell["bands"] as? List<*>)?.let { bands ->
                        if (bands.isNotEmpty()) {
                            displayBuilder.append("    Bands: ${bands.joinToString(", ")}\n")
                        }
                    }
                }
                displayBuilder.append("\n")
            }
        }
        infoTextView.text = displayBuilder.toString()
    }
}
