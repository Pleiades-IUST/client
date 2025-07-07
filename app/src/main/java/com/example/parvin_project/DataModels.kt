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
 * This structure is designed to store the specific parameters required for the network analysis.
 * All properties are nullable to gracefully handle cases where a parameter is not available
 * for a specific technology or Android API level.
 *
 * @param technology The radio access technology (e.g., "GSM", "UMTS (WCDMA/HSPA)", "LTE", "5G NR").
 * @param plmnId The Public Land Mobile Network ID (MCC-MNC).
 * @param lac The Location Area Code (for GSM, UMTS).
 * @param tac The Tracking Area Code (for LTE, 5G NR).
 * @param rac The Routing Area Code. Note: Not available through public Android APIs, will be null.
 * @param cellId The Cell Identity (CID, CI, or NCI).
 * @param frequencyBand The descriptive name of the frequency band (e.g., "GSM-900", "LTE Band 3", "n78").
 * @param arfcn The Absolute Radio-Frequency Channel Number for GSM.
 * @param earfcn The E-UTRA Absolute Radio-Frequency Channel Number for LTE.
 * @param uarfcn The UTRA Absolute Radio-Frequency Channel Number for UMTS.
 * @param nrarfcn The NR Absolute Radio-Frequency Channel Number for 5G.
 * @param frequencyHz The calculated downlink frequency in Hertz. Null for 5G due to calculation complexity.
 * @param rsrp The Reference Signal Received Power in dBm (for LTE, 5G NR).
 * @param rsrq The Reference Signal Received Quality in dB (for LTE, 5G NR).
 * @param rscp The Received Signal Code Power in dBm (for UMTS).
 * @param ecNo The Ec/N0, the ratio of received energy per chip to noise spectral density (for UMTS).
 * @param rxLev The Received Signal Level in dBm (for GSM).
 * @param errorMessage An optional message for status or errors (e.g., permissions not granted).
 */
data class CellInfoData(
    // Identity Parameters
    val technology: String? = null,
    val plmnId: String? = null,
    val lac: Int? = null,
    val tac: Int? = null,
    val rac: Int? = null, // Not available via public APIs
    val cellId: Long? = null,

    // Frequency Parameters
    val frequencyBand: String? = null,
    val arfcn: Int? = null,
    val earfcn: Int? = null,
    val uarfcn: Int? = null,
    val nrarfcn: Int? = null,
    val frequencyHz: Long? = null,

    // Signal Quality Parameters
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rscp: Int? = null,
    val ecNo: Int? = null,
    val rxLev: Int? = null,

    // Status Message
    val errorMessage: String? = null
)

// NEW: Data models for the /drive API
data class DriveBase(
    val name: String
)

data class Signal(
    val record_time: String?, // ISO 8601 format (e.g., "2024-06-14T12:00:00Z")

    val technology: String? = null,
    val plmnId: String? = null,
    val lac: Int? = null,
    val tac: Int? = null,
    val rac: Int? = null, // Not available via public APIs
    val cellId: Long? = null,

    // Frequency Parameters
    val frequencyBand: String? = null,
    val arfcn: Int? = null,
    val earfcn: Int? = null,
    val uarfcn: Int? = null,
    val nrarfcn: Int? = null,
    val frequencyHz: Long? = null,

    // Signal Quality Parameters
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val rscp: Int? = null,
    val ecNo: Int? = null,
    val rxLev: Int? = null,

    val download_rate: Double?, // KB/s
    val upload_rate: Double?, // KB/s
    val dns_lookup_rate: Double?, // ms
    val ping: Double?, // ms
    val sms_delivery_time: Double?, // ms (was Int in OpenAPI, but your code generates Double for durationMs)

    val longitude: Double?,
    val latitude: Double?,
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
    val Token: String
)

// NEW: Data model for UserLogin request
data class UserLogin(
    val username: String,
    val password: String
)