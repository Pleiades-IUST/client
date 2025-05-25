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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

// MainActivity now implements LocationListener to receive location updates
class MainActivity : AppCompatActivity(), LocationListener {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var locationManager: LocationManager // Manages location services
    private lateinit var infoTextView: TextView // Displays cell and location info
    private lateinit var getInfoButton: Button // Button to trigger data retrieval

    // Request code for runtime permissions
    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set the content view to the XML layout file
        setContentView(R.layout.activity_main)

        // Initialize UI elements by finding them by their IDs in the layout
        infoTextView = findViewById(R.id.infoTextView)
        getInfoButton = findViewById(R.id.getInfoButton)

        // Get instances of system services
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Set an OnClickListener for the button
        getInfoButton.setOnClickListener {
            // Check for necessary permissions. If granted, proceed to get data.
            if (checkAndRequestPermissions()) {
                getCellInfo() // Get cellular network information
                requestLocationUpdates() // Request location updates
            }
        }
    }

    /**
     * Checks if the required permissions (ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_PHONE_STATE)
     * are granted. If not, it requests them from the user.
     * @return true if all required permissions are already granted, false otherwise.
     */
    private fun checkAndRequestPermissions(): Boolean {
        // Check the status of each required permission
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

        // Create a mutable list to hold permissions that need to be requested
        val permissionsToRequest = mutableListOf<String>()

        // Add permissions to the list if they are not yet granted
        if (fineLocationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (coarseLocationPermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (readPhoneStatePermission != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.READ_PHONE_STATE)
        }

        // If there are permissions to request, show the permission request dialog to the user
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
                // If all permissions are granted, proceed to get and display cell and location information
                getCellInfo()
                requestLocationUpdates()
            } else {
                // If permissions are denied, update the TextView to inform the user
                infoTextView.text = "Permissions required to access cell and location information."
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
                // Request updates from GPS provider if available, or Network provider
                // We use a short interval (5 seconds, 10 meters) for demonstration.
                // Adjust these values based on your actual needs for accuracy vs. battery.
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000, // Minimum time interval between updates in milliseconds (5 seconds)
                        10f,  // Minimum distance between updates in meters (10 meters)
                        this // The LocationListener instance (this Activity)
                    )
                    Log.d("CellInfoExtractor", "Requesting GPS location updates.")
                    infoTextView.append("\n\nAttempting to get location via GPS...")
                } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000, // Minimum time interval between updates in milliseconds (5 seconds)
                        10f,  // Minimum distance between updates in meters (10 meters)
                        this // The LocationListener instance (this Activity)
                    )
                    Log.d("CellInfoExtractor", "Requesting Network location updates.")
                    infoTextView.append("\n\nAttempting to get location via Network...")
                } else {
                    infoTextView.append("\n\nLocation: GPS/Network providers not enabled on device settings.")
                    Log.w("CellInfoExtractor", "GPS and Network location providers are disabled.")
                }
            } catch (e: SecurityException) {
                infoTextView.append("\n\nLocation permission denied: ${e.message}")
                Log.e("CellInfoExtractor", "SecurityException requesting location updates: ${e.message}")
            } catch (e: Exception) {
                infoTextView.append("\n\nError requesting location updates: ${e.message}")
                Log.e("CellInfoExtractor", "Error requesting location updates: ${e.message}", e)
            }
        } else {
            infoTextView.append("\n\nLocation permission not granted to get updates.")
            Log.w("CellInfoExtractor", "Location permissions not granted for updates.")
        }
    }

    /**
     * Callback for when the location has changed. This is where you get the Latitude and Longitude.
     */
    override fun onLocationChanged(location: Location) {
        val lat = location.latitude
        val long = location.longitude
        // Append location info to the TextView
        infoTextView.append("\n\n--- Current Location ---\n")
        infoTextView.append("  Latitude: ${String.format("%.6f", lat)}\n") // Format for readability
        infoTextView.append("  Longitude: ${String.format("%.6f", long)}\n") // Format for readability
        // It's good practice to remove location updates after getting a satisfactory fix
        // to save battery. If you need continuous updates, remove this line.
        locationManager.removeUpdates(this)
        Log.d("CellInfoExtractor", "Location updated: Lat=$lat, Long=$long. Updates removed.")
    }

    // --- LocationListener Interface Methods (Required, can be empty if no specific logic needed) ---
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
        // Called when the provider status changes (e.g., GPS signal lost/gained)
        Log.d("CellInfoExtractor", "Location provider status changed: $provider, status: $status")
    }

    override fun onProviderEnabled(provider: String) {
        // Called when the provider is enabled by the user (e.g., GPS turned on)
        Log.d("CellInfoExtractor", "Location provider enabled: $provider")
    }

    override fun onProviderDisabled(provider: String) {
        // Called when the provider is disabled by the user (e.g., GPS turned off)
        Log.d("CellInfoExtractor", "Location provider disabled: $provider")
        infoTextView.append("\n\nLocation: $provider provider disabled. Please enable it in settings.")
    }

    /**
     * Extracts and displays serving cell information, specifically its power (signal strength).
     */
    private fun getCellInfo() {
        // Clear previous info (location will be appended later by onLocationChanged)
        infoTextView.text = ""
        val stringBuilder = StringBuilder()

        try {
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

            if (cellInfoList.isNullOrEmpty()) {
                stringBuilder.append("No cell information available or permissions not granted.\n")
                Log.d("CellInfoExtractor", "No cell information available.")
            } else {
                stringBuilder.append("--- Serving Cell Power ---\n\n")

                var servingCellFound = false
                for (cellInfo in cellInfoList) {
                    if (cellInfo.isRegistered) { // This is the serving cell
                        servingCellFound = true
                        stringBuilder.append("Serving Cell Details:\n")

                        // Extract technology-specific signal strength
                        when (cellInfo) {
                            is CellInfoGsm -> {
                                val ssGsm: CellSignalStrengthGsm = cellInfo.cellSignalStrength
                                stringBuilder.append("  Technology: GSM\n")
                                stringBuilder.append("  Signal Strength (dBm): ${ssGsm.dbm}\n")
                            }
                            is CellInfoCdma -> {
                                val ssCdma: CellSignalStrengthCdma = cellInfo.cellSignalStrength
                                stringBuilder.append("  Technology: CDMA\n")
                                stringBuilder.append("  Signal Strength (dBm): ${ssCdma.dbm}\n")
                            }
                            is CellInfoLte -> {
                                val ssLte: CellSignalStrengthLte = cellInfo.cellSignalStrength
                                stringBuilder.append("  Technology: LTE\n")
                                stringBuilder.append("  Signal Strength (dBm): ${ssLte.dbm}\n")
                                stringBuilder.append("  RSRP (dBm): ${ssLte.rsrp}\n") // Reference Signal Received Power
                                stringBuilder.append("  RSRQ (dB): ${ssLte.rsrq}\n") // Reference Signal Received Quality
                            }
                            is CellInfoWcdma -> {
                                val ssWcdma: CellSignalStrengthWcdma = cellInfo.cellSignalStrength
                                stringBuilder.append("  Technology: WCDMA\n")
                                stringBuilder.append("  Signal Strength (dBm): ${ssWcdma.dbm}\n")
                            }
                            is CellInfoTdscdma -> {
                                val ssTdscdma: CellSignalStrengthTdscdma = cellInfo.cellSignalStrength
                                stringBuilder.append("  Technology: TDSCDMA\n")
                                stringBuilder.append("  Signal Strength (dBm): ${ssTdscdma.dbm}\n")
                            }
                            is CellInfoNr -> { // 5G NR
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val ssNr = cellInfo.cellSignalStrength as? CellSignalStrengthNr
                                    val cellIdentityNr = cellInfo.cellIdentity as? android.telephony.CellIdentityNr // Safe cast for CellIdentityNr

                                    if (ssNr != null) {
                                        stringBuilder.append("  Technology: 5G NR\n")
                                        stringBuilder.append("  Signal Strength (dBm): ${ssNr.dbm}\n")
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                            stringBuilder.append("  SS-RSRP (dBm): ${ssNr.ssRsrp}\n")
                                            stringBuilder.append("  SS-RSRQ (dB): ${ssNr.ssRsrq}\n")
                                            stringBuilder.append("  CSI-RSRP (dBm): ${ssNr.csiRsrp}\n")
                                            stringBuilder.append("  CSI-RSRQ (dB): ${ssNr.csiRsrq}\n")

                                            // Access NCI and TAC only if cellIdentityNr is not null and API level is R+
                                            if (cellIdentityNr != null) {
                                                stringBuilder.append("  NCI: ${cellIdentityNr.nci}\n") // NR Cell Identity
                                                stringBuilder.append("  TAC: ${cellIdentityNr.tac}\n") // Tracking Area Code
                                            } else {
                                                stringBuilder.append("  NR Cell Identity details (NCI, TAC) not available or not CellIdentityNr type.\n")
                                            }
                                        } else {
                                            stringBuilder.append("  NR specific details (NCI, TAC, SS/CSI RSRP/RSRQ/SINR) require Android R (API 30+)\n")
                                        }
                                        // PCI is available from Q (API 29) on CellIdentityNr
                                        if (cellIdentityNr != null) {
                                            stringBuilder.append("  PCI: ${cellIdentityNr.pci}\n") // Physical Cell ID
                                        } else {
                                            stringBuilder.append("  PCI not available or not CellIdentityNr type.\n")
                                        }
                                    } else {
                                        // Fallback if the signal strength object isn't the specific NR type
                                        stringBuilder.append("  Technology: 5G NR (Signal strength details not available for this type)\n")
                                        Log.w("CellInfoExtractor", "CellInfoNr.cellSignalStrength was not CellSignalStrengthNr for a CellInfoNr instance.")
                                    }
                                } else {
                                    stringBuilder.append("  Technology: 5G NR (requires Android Q+)\n")
                                }
                            }
                            else -> {
                                stringBuilder.append("  Technology: Unknown or unsupported type\n")
                                stringBuilder.append("  CellInfo type: ${cellInfo.javaClass.simpleName}\n")
                                try {
                                    val signalStrengthMethod = CellInfo::class.java.getMethod("getCellSignalStrength")
                                    val genericSignalStrength = signalStrengthMethod.invoke(cellInfo) as CellSignalStrength
                                    stringBuilder.append("  Generic Signal Strength (dBm): ${genericSignalStrength.dbm}\n")
                                } catch (e: Exception) {
                                    Log.e("CellInfoExtractor", "Could not get generic signal strength: ${e.message}")
                                }
                            }
                        }
                        break // Found the serving cell, no need to check others for this specific request
                    }
                }
                if (!servingCellFound) {
                    stringBuilder.append("No serving cell found.\n")
                }
            }
        } catch (e: SecurityException) {
            stringBuilder.append("Permission denied: ${e.message}\n")
            Log.e("CellInfoExtractor", "SecurityException: ${e.message}")
        } catch (e: Exception) {
            stringBuilder.append("Error getting cell info: ${e.message}\n")
            Log.e("CellInfoExtractor", "Error: ${e.message}", e)
        }
        infoTextView.text = stringBuilder.toString()
    }

    override fun onPause() {
        super.onPause()
        // It's good practice to remove location updates when the activity is paused
        // to save battery, especially if you only need a single location fix.
        locationManager.removeUpdates(this)
        Log.d("CellInfoExtractor", "Location updates removed on pause.")
    }

    // onResume is not strictly needed here as we request on button click,
    // but useful if you want continuous updates when the app is foreground.
    // override fun onResume() {
    //     super.onResume()
    //     // requestLocationUpdates() // Uncomment if you want updates to resume when app comes to foreground
    // }
}
