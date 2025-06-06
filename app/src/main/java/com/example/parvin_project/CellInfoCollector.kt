// CellInfoCollector.kt
package com.example.parvin_project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.*
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

class CellInfoCollector(private val context: Context) {

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    fun getCellInfoData(): CellInfoData {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("CellInfoCollector", "Permissions not granted for cell info access.")
            // Return CellInfoData with all nulls for data fields, and a status message
            return CellInfoData(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "Permissions Denied" // 15 parameters correctly handled
            )
        }

        try {
            val allCellInfo = telephonyManager.allCellInfo
            if (allCellInfo.isNullOrEmpty()) {
                Log.d("CellInfoCollector", "No cell info available.")
                return CellInfoData(
                    null, null, null, null, null, null, null, null, null,
                    null, null, null, null, null, "No Cell Info Available"
                )
            }

            // Iterate through available cell info and extract data from the first serving cell found
            for (cellInfo in allCellInfo) {
                when (cellInfo) {
                    is CellInfoLte -> return extractLteCellInfo(cellInfo)
                    is CellInfoWcdma -> return extractWcdmaCellInfo(cellInfo)
                    is CellInfoGsm -> return extractGsmCellInfo(cellInfo)
                    is CellInfoCdma -> return extractCdmaCellInfo(cellInfo)
                    is CellInfoNr -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            return extractNrCellInfo(cellInfo)
                        }
                    }
                }
            }
            Log.d("CellInfoCollector", "No supported serving cell info found.")
            return CellInfoData(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "No Supported Serving Cell"
            )

        } catch (e: Exception) {
            Log.e("CellInfoCollector", "Error collecting cell info: ${e.message}", e)
            return CellInfoData(
                null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, "Error: ${e.message}"
            )
        }
    }

    private fun extractLteCellInfo(cellInfo: CellInfoLte): CellInfoData {
        val cellIdentity = cellInfo.cellIdentity
        val cellSignalStrength = cellInfo.cellSignalStrength
        return CellInfoData(
            technology = "LTE",
            signalStrength_dBm = cellSignalStrength.dbm,
            plmnId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cellIdentity.mccString + cellIdentity.mncString else null,
            lac = cellIdentity.tac,
            cellId = cellIdentity.ci.toLong(),
            pci = cellIdentity.pci,
            tac = cellIdentity.tac,
            nci = null,
            nrarfcn = null,
            bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cellIdentity.bands.toList() else null,
            csiRsrp_dBm = null,
            csiRsrq_dB = null,
            rsrp_dBm = cellSignalStrength.rsrp,
            rsrq_dB = cellSignalStrength.rsrq,
            status = "OK"
        )
    }

    private fun extractWcdmaCellInfo(cellInfo: CellInfoWcdma): CellInfoData {
        val cellIdentity = cellInfo.cellIdentity
        val cellSignalStrength = cellInfo.cellSignalStrength
        return CellInfoData(
            technology = "WCDMA",
            signalStrength_dBm = cellSignalStrength.dbm,
            plmnId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cellIdentity.mccString + cellIdentity.mncString else null,
            lac = cellIdentity.lac,
            cellId = cellIdentity.cid.toLong(),
            pci = null,
            tac = null,
            nci = null,
            nrarfcn = null,
            bands = null,
            csiRsrp_dBm = null,
            csiRsrq_dB = null,
            rsrp_dBm = null, // WCDMA typically uses RSCP, not RSRP
            rsrq_dB = null,
            status = "OK"
        )
    }

    private fun extractGsmCellInfo(cellInfo: CellInfoGsm): CellInfoData {
        val cellIdentity = cellInfo.cellIdentity
        val cellSignalStrength = cellInfo.cellSignalStrength
        return CellInfoData(
            technology = "GSM",
            signalStrength_dBm = cellSignalStrength.dbm,
            plmnId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cellIdentity.mccString + cellIdentity.mncString else null,
            lac = cellIdentity.lac,
            cellId = cellIdentity.cid.toLong(),
            pci = null,
            tac = null,
            nci = null,
            nrarfcn = null,
            bands = null,
            csiRsrp_dBm = null,
            csiRsrq_dB = null,
            rsrp_dBm = null,
            rsrq_dB = null,
            status = "OK"
        )
    }

    private fun extractCdmaCellInfo(cellInfo: CellInfoCdma): CellInfoData {
        val cellIdentity = cellInfo.cellIdentity
        val cellSignalStrength = cellInfo.cellSignalStrength
        return CellInfoData(
            technology = "CDMA",
            signalStrength_dBm = cellSignalStrength.dbm,
            plmnId = null, // CDMA typically doesn't have a direct PLMN-ID in the same way GSM/WCDMA/LTE do
            lac = cellIdentity.networkId, // In CDMA, NetworkId acts somewhat like LAC
            cellId = cellIdentity.basestationId.toLong(), // Base station ID
            pci = null,
            tac = null,
            nci = null,
            nrarfcn = null,
            bands = null,
            csiRsrp_dBm = null,
            csiRsrq_dB = null,
            rsrp_dBm = null,
            rsrq_dB = null,
            status = "OK"
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun extractNrCellInfo(cellInfo: CellInfoNr): CellInfoData {
        val cellIdentity = cellInfo.cellIdentity as CellIdentityNr
        val cellSignalStrength = cellInfo.cellSignalStrength as CellSignalStrengthNr
        return CellInfoData(
            technology = "NR",
            signalStrength_dBm = cellSignalStrength.dbm,
            plmnId = cellIdentity.mccString + cellIdentity.mncString,
            lac = null, // Not directly applicable for 5G NR
            cellId = cellIdentity.nci, // NR Cell Identity
            pci = cellIdentity.pci,
            tac = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cellIdentity.tac else null,
            nci = cellIdentity.nci,
            nrarfcn = cellIdentity.nrarfcn,
            bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) cellIdentity.bands.toList() else null,
            csiRsrp_dBm = cellSignalStrength.csiRsrp,
            csiRsrq_dB = cellSignalStrength.csiRsrq,
            rsrp_dBm = cellSignalStrength.ssRsrp, // SS-RSRP for 5G NR
            rsrq_dB = cellSignalStrength.ssRsrq, // SS-RSRQ for 5G NR
            status = "OK"
        )
    }
}
