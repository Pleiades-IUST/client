// CellInfoCollector.kt
package com.example.parvin_project

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.core.content.ContextCompat
import java.util.Locale

/**
 * Collects cellular network information using TelephonyManager.
 * @param context The application context.
 */
class CellInfoCollector(private val context: Context) {

    private val telephonyManager: TelephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    /**
     * Gathers current cellular network information and returns it as a CellInfoData object.
     * Requires READ_PHONE_STATE permission.
     * @return A CellInfoData object containing detailed cell information, or a status message if unavailable.
     */
    fun getCellInfoData(): CellInfoData {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return CellInfoData(status = "Permission to read phone state not granted.")
        }

        try {
            val cellInfoList: List<CellInfo>? = telephonyManager.allCellInfo

            if (cellInfoList.isNullOrEmpty()) {
                return CellInfoData(status = "No cell information available.")
            } else {
                for (cellInfo in cellInfoList) {
                    if (cellInfo.isRegistered) { // This is the serving cell
                        return parseServingCellInfo(cellInfo)
                    }
                }
                return CellInfoData(status = "No serving cell found (isRegistered = true).")
            }
        } catch (e: SecurityException) {
            Log.e("CellInfoCollector", "Security Exception getting cell info: ${e.message}")
            return CellInfoData(status = "Security Exception: Permission to read cell info not granted.")
        } catch (e: Exception) {
            Log.e("CellInfoCollector", "Error getting cell info: ${e.message}", e)
            return CellInfoData(status = "Error getting cell info: ${e.message}")
        }
    }

    /**
     * Parses a CellInfo object to extract relevant details into a CellInfoData object.
     * @param cellInfo The CellInfo object representing a serving cell.
     * @return A populated CellInfoData object.
     */
    private fun parseServingCellInfo(cellInfo: CellInfo): CellInfoData {
        val cellData = mutableMapOf<String, Any?>()

        when (cellInfo) {
            is CellInfoGsm -> {
                val ssGsm: CellSignalStrengthGsm = cellInfo.cellSignalStrength
                val cellIdentityGsm = cellInfo.cellIdentity
                cellData["technology"] = "GSM"
                cellData["signalStrength_dBm"] = ssGsm.dbm
                cellData["plmnId"] = getPlmnId(cellIdentityGsm.mcc, cellIdentityGsm.mnc)
                cellData["lac"] = cellIdentityGsm.lac
                cellData["cellId"] = cellIdentityGsm.cid.toLong() // Ensure Long for consistency
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
                cellData["cellId"] = cellIdentityCdma.basestationId.toLong() // Ensure Long for consistency
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
                cellData["cellId"] = cellIdentityLte.ci.toLong() // Ensure Long for consistency
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
                cellData["cellId"] = cellIdentityWcdma.cid.toLong() // Ensure Long for consistency
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
                cellData["cellId"] = cellIdentityTdscdma.cid.toLong() // Ensure Long for consistency
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    cellData["uarfcn"] = cellIdentityTdscdma.uarfcn
                }
            }
            is CellInfoNr -> { // 5G NR (Requires Android Q+)
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
        return CellInfoData(
            technology = cellData["technology"] as? String,
            signalStrength_dBm = cellData["signalStrength_dBm"] as? Int ?: cellData["signalStrength_dBm_generic"] as? Int,
            plmnId = cellData["plmnId"] as? String,
            lac = cellData["lac"] as? Int,
            tac = cellData["tac"] as? Int,
            cellId = cellData["cellId"] as? Long,
            pci = cellData["pci"] as? Int,
            rsrp_dBm = cellData["rsrp_dBm"] as? Int,
            rsrq_dB = cellData["rsrq_dB"] as? Int,
            arfcn = cellData["arfcn"] as? Int,
            earfcn = cellData["earfcn"] as? Int,
            uarfcn = cellData["uarfcn"] as? Int,
            nrarfcn = cellData["nrarfcn"] as? Int,
            bands = cellData["bands"] as? List<Int>,
            ssRsrp_dBm = cellData["ssRsrp_dBm"] as? Int,
            ssRsrq_dB = cellData["ssRsrq_dB"] as? Int,
            csiRsrp_dBm = cellData["csiRsrp_dBm"] as? Int,
            csiRsrq_dB = cellData["csiRsrq_dB"] as? Int,
            bandwidth_kHz = cellData["bandwidth_kHz"] as? Int,
            networkId = cellData["networkId"] as? Int,
            systemId = cellData["systemId"] as? Int,
            status = cellData["status"] as? String
        )
    }

    /**
     * Helper function to get PLMN-ID string, handling unavailable integer values.
     */
    private fun getPlmnId(mcc: Int, mnc: Int): String {
        val mccStr = if (mcc != Int.MAX_VALUE) mcc.toString() else "N/A"
        val mncStr = if (mnc != Int.MAX_VALUE) mnc.toString() else "N/A"
        return "$mccStr-$mncStr"
    }

    // Helper extension function to capitalize words for better display of map keys
    fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
        word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
