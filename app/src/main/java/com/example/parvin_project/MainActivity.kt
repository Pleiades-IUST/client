// MainActivity.kt
package com.example.parvin_project // <--- ENSURE THIS MATCHES YOUR PROJECT'S PACKAGE NAME

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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

class MainActivity : AppCompatActivity() {

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var infoTextView: TextView // This will display the cell info
    private lateinit var getInfoButton: Button // This button will trigger getting info

    // Request code for permissions
    private val PERMISSION_REQUEST_CODE = 101

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main) // Ensure this layout has infoTextView and getInfoButton

        // Initialize UI elements
        infoTextView = findViewById(R.id.infoTextView)
        getInfoButton = findViewById(R.id.getInfoButton)

        // Get an instance of TelephonyManager
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Set click listener for the button
        getInfoButton.setOnClickListener {
            // Check for necessary permissions before trying to get cell info
            if (checkAndRequestPermissions()) {
                getCellInfo()
            }
        }
    }

    /**
     * Checks if the required permissions are granted and requests them if not.
     * @return true if permissions are already granted, false otherwise.
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
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
            return false
        }
        return true
    }

    /**
     * Callback for the permission request result.
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
                // Permissions granted, now get the cell info
                getCellInfo()
            } else {
                // Permissions denied, inform the user
                infoTextView.text = "Permissions required to access cell information."
                Log.w("CellInfoExtractor", "Required permissions not granted.")
            }
        }
    }

    /**
     * Extracts and displays serving cell information, specifically its power (signal strength).
     */
    private fun getCellInfo() {
        infoTextView.text = "" // Clear previous info
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
}
