// DataModels.kt
package com.example.parvin_project

// Data class to hold Location information
data class LocationData(
    val latitude: Double?,
    val longitude: Double?,
    val status: String // e.g., "OK", "Waiting for fix", "Providers disabled"
)

// Data class to hold detailed Cell Information
data class CellInfoData(
    val technology: String?, // e.g., "LTE", "NR", "GSM"
    val signalStrength_dBm: Int?, // General signal strength in dBm (e.g., RSSI, RSRP, etc.)
    val plmnId: String?, // Public Land Mobile Network ID (MCC+MNC)
    val lac: Int?, // Location Area Code (for 2G/3G)
    val cellId: Long?, // Cell Identity (for 2G/3G/4G/5G NR - using Long for NCI)
    val pci: Int?, // Physical Cell Identity (for 4G/5G)
    val tac: Int?, // Tracking Area Code (for 4G)
    val nci: Long?, // NR Cell Identity (for 5G NR, often same as cellId if 5G primary)
    val nrarfcn: Int?, // NR Absolute Radio Frequency Channel Number (for 5G NR)
    val bands: List<Int>?, // List of frequency bands (for 5G NR, LTE)
    val csiRsrp_dBm: Int?, // CSI Reference Signal Received Power (for 5G NR)
    val csiRsrq_dB: Int?, // CSI Reference Signal Received Quality (for 5G NR)
    val rsrp_dBm: Int?, // Reference Signal Received Power (for LTE)
    val rsrq_dB: Int?, // Reference Signal Received Quality (for LTE)
    val status: String // e.g., "OK", "Permissions Denied", "No Cell Info"
)
