package com.example.parvin_project

// --- Frequency and Band Calculation Helpers ---

fun getGsmBand(arfcn: Int): String? {
    return when (arfcn) {
        in 0..124 -> "GSM-900"
        in 128..251 -> "GSM-850"
        in 259..293 -> "GSM-450"
        in 306..340 -> "GSM-480"
        in 512..885 -> "DCS-1800"
        in 955..1023 -> "E-GSM-900"
        // This is a simplified list. A full implementation would be more complex.
        else -> "Unknown GSM"
    }
}

fun getGsmDownlinkFrequency(arfcn: Int, band: String?): Long? {
    val freq: Double = when (band) {
        "GSM-900", "E-GSM-900" -> 935.2 + 0.2 * (arfcn - 975)
        "DCS-1800" -> 1805.2 + 0.2 * (arfcn - 512)
        "GSM-850" -> 869.2 + 0.2 * (arfcn - 128)
        else -> return null
    }
    return (freq * 1_000_000).toLong()
}

fun getUtranBand(uarfcn: Int): String {
    return when (uarfcn) {
        in 10562..10838 -> "Band 1"
        in 9662..9938 -> "Band 2"
        in 1162..1513 -> "Band 3"
        in 1537..1738 -> "Band 4"
        in 4357..4458 -> "Band 5"
        // This is a highly simplified list for demonstration.
        else -> "Unknown UMTS"
    }
}

fun getUtranDownlinkFrequency(uarfcn: Int): Long? {
    // This is a very complex calculation with many bands.
    // Simplified example for Band 1. A full implementation needs a large lookup table.
    if (uarfcn in 10562..10838) { // Band 1
        return (uarfcn * 0.2 * 1_000_000).toLong()
    }
    return null // Placeholder for other bands
}

fun getLteBand(earfcn: Int?, bands: IntArray): String? {
    if (bands.isNotEmpty()) {
        return "LTE Band ${bands[0]}"
    }
    // Fallback for older APIs: Infer from EARFCN (less reliable)
    earfcn?.let {
        return when (it) {
            in 0..599 -> "LTE Band 1"
            in 1200..1949 -> "LTE Band 3"
            in 2750..3449 -> "LTE Band 7"
            in 6150..6449 -> "LTE Band 20"
            // This is a highly simplified list for demonstration.
            else -> "Unknown LTE"
        }
    }
    return null
}

fun getLteDownlinkFrequency(earfcn: Int): Long? {
    // This is a very complex calculation with many bands.
    // Simplified example for Band 3. A full implementation needs a large lookup table.
    if (earfcn in 1200..1949) { // Band 3
        val fdl = 1805.0 + 0.1 * (earfcn - 1200)
        return (fdl * 1_000_000).toLong()
    }
    return null // Placeholder for other bands
}