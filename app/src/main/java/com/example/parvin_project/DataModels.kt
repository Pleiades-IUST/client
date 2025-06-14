// DataModels.kt
package com.example.parvin_project

/**
 * Data class to hold collected Location information.
 * @param latitude The latitude.
 * @param longitude The longitude.
 * @param status A string indicating the status of location fix (e.g., "Fixed", "Waiting for fix", "Providers disabled").
 */
data class LocationData(
    val latitude: Double?,
    val longitude: Double?,
    val status: String
)

/**
 * Data class to hold collected Cellular Network information.
 * This will store details specific to the serving cell.
 * Properties are nullable to handle cases where information might not be available.
 * @param technology The network technology (e.g., "LTE", "GSM", "5G NR").
 * @param signalStrength_dBm The signal strength in dBm.
 * @param plmnId Public Land Mobile Network ID (MCC-MNC).
 * @param lac Location Area Code (GSM, WCDMA, TDSCDMA).
 * @param tac Tracking Area Code (LTE, 5G NR).
 * @param cellId Cell Identity.
 * @param pci Physical Cell Identity (LTE, 5G NR).
 * @param rsrp_dBm Reference Signal Received Power in dBm (LTE).
 * @param rsrq_dB Reference Signal Received Quality in dB (LTE).
 * @param arfcn Absolute Radio Frequency Channel Number (GSM).
 * @param earfcn E-UTRA Absolute Radio Frequency Channel Number (LTE).
 * @param uarfcn UTRA Absolute Radio Frequency Channel Number (WCDMA, TDSCDMA).
 * @param nrarfcn NR Absolute Radio Frequency Channel Number (5G NR).
 * @param bands List of NR bands (5G NR).
 * @param ssRsrp_dBm SS-RSRP in dBm (5G NR, API 30+).
 * @param ssRsrq_dB SS-RSRQ in dB (5G NR, API 30+).
 * @param csiRsrp_dBm CSI-RSRP in dBm (5G NR, API 30+).
 * @param csiRsrq_dB CSI-RSRQ in dB (5G NR, API 30+).
 * @param bandwidth_kHz Bandwidth in kHz (LTE, API 28+).
 * @param networkId Network ID (CDMA).
 * @param systemId System ID (CDMA).
 * @param status General status message if specific cell info is not available.
 */
data class CellInfoData(
    val technology: String? = null,
    val signalStrength_dBm: Int? = null,
    val plmnId: String? = null,
    val lac: Int? = null,
    val tac: Int? = null,
    val cellId: Long? = null, // Can be int or long depending on tech
    val pci: Int? = null,
    val rsrp_dBm: Int? = null,
    val rsrq_dB: Int? = null,
    val arfcn: Int? = null,
    val earfcn: Int? = null,
    val uarfcn: Int? = null,
    val nrarfcn: Int? = null,
    val bands: List<Int>? = null,
    val ssRsrp_dBm: Int? = null,
    val ssRsrq_dB: Int? = null,
    val csiRsrp_dBm: Int? = null,
    val csiRsrq_dB: Int? = null,
    val bandwidth_kHz: Int? = null,
    val networkId: Int? = null, // CDMA specific
    val systemId: Int? = null,   // CDMA specific
    val status: String? = null // General status/error message
)

// NEW: Data models for the /drive API
data class DriveBase(
    val name: String
)

data class Signal(
    val record_time: String?, // ISO 8601 format (e.g., "2024-06-14T12:00:00Z")
    val plmn_id: String?,
    val cell_id: String?,
    val technology: String?,
    val signal_strength: Int?, // dBm
    val download_rate: Double?, // KB/s
    val upload_rate: Double?, // KB/s
    val dns_lookup_rate: Double?, // ms
    val ping: Double?, // ms
    val sms_delivery_time: Double?, // ms (was Int in OpenAPI, but your code generates Double for durationMs)
    val rsrp: Int?, // dBm
    val rsrq: Int?, // dB
    val longitude: Double?,
    val latitude: Double?,
    val pci: String?,
    val tac: String?
)

data class DriveData(
    val drive: DriveBase,
    val signals: List<Signal>
)

// NEW: Data models for the /auth/login API (if response is simple string)
// Based on OpenAPI, it just returns "OK" or "Unauthorized".
// If it truly returns only a status code, we might not need a data class for the response body.
// However, if it returns a string like "OK", we can capture that.
// For now, we'll assume it's just a status code and maybe a simple message.
// If your server returns a structured JSON for success/failure, update this.
// For instance, if it returns: {"message": "OK"}
data class LoginResponse(
    val token: String
)

// NEW: Data model for UserLogin request
data class UserLogin(
    val username: String,
    val password: String
)