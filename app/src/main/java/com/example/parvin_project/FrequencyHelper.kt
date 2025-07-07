package com.example.parvin_project

// --- Frequency and Band Calculation Helpers ---

/**
 * Determines the GSM band based on the Absolute Radio Frequency Channel Number (ARFCN).
 * This function covers common GSM bands as defined by 3GPP TS 45.005.
 *
 * @param arfcn The Absolute Radio Frequency Channel Number.
 * @return A String representing the GSM band (e.g., "GSM-900", "DCS-1800"), or "Unknown GSM" if the ARFCN does not match a known band.
 */
fun getGsmBand(arfcn: Int): String {
    return when (arfcn) {
        in 259..293 -> "GSM-450"
        in 306..340 -> "GSM-480"
        in 438..511 -> "GSM-750" // Also referred to as T-GSM 710/750
        in 128..251 -> "GSM-850"
        in 1..124 -> "P-GSM-900" // Primary GSM 900
        in 0..124, in 975..1023 -> "E-GSM-900" // Extended GSM 900 (includes P-GSM range and extended range)
        in 0..124, in 955..1023 -> "R-GSM-900" // Railways GSM 900 (overlaps with E-GSM)
        in 0..124, in 940..1023 -> "ER-GSM-900" // Extended Railways GSM 900 (overlaps with E-GSM/R-GSM)
        in 512..885 -> "DCS-1800"
        in 512..810 -> "PCS-1900" // Note: PCS-1900 ARFCNs overlap with DCS-1800. Context (e.g., MCC/MNC) might be needed for precise distinction.
        else -> "Unknown GSM"
    }
}

/**
 * Calculates the GSM downlink frequency in Hz based on the ARFCN and the determined band.
 * Formulas are derived from 3GPP TS 45.005.
 *
 * @param arfcn The Absolute Radio Frequency Channel Number.
 * @param band The GSM band string obtained from `getGsmBand`.
 * @return The downlink frequency in Hz, or null if the band is unknown or the ARFCN is out of range for the given band.
 */
fun getGsmDownlinkFrequency(arfcn: Int, band: String): Long? {
    val freqMHz: Double? = when (band) {
        "GSM-450" -> 460.6 + 0.2 * (arfcn - 259)
        "GSM-480" -> 488.8 + 0.2 * (arfcn - 306)
        "GSM-750" -> 777.2 + 0.2 * (arfcn - 438)
        "GSM-850" -> 869.2 + 0.2 * (arfcn - 128)
        "P-GSM-900" -> 935.2 + 0.2 * arfcn
        "E-GSM-900" -> when (arfcn) {
            in 0..124 -> 925.2 + 0.2 * arfcn
            in 975..1023 -> 935.2 + 0.2 * (arfcn - 1024)
            else -> null
        }
        "R-GSM-900" -> when (arfcn) {
            in 0..124 -> 921.2 + 0.2 * arfcn
            in 955..1023 -> 935.2 + 0.2 * (arfcn - 1024)
            else -> null
        }
        "ER-GSM-900" -> when (arfcn) {
            in 0..124 -> 918.2 + 0.2 * arfcn
            in 940..1023 -> 935.2 + 0.2 * (arfcn - 1024)
            else -> null
        }
        "DCS-1800" -> 1805.2 + 0.2 * (arfcn - 512)
        "PCS-1900" -> 1930.2 + 0.2 * (arfcn - 512)
        else -> null
    }
    return freqMHz?.let { (it * 1_000_000).toLong() }
}

/**
 * Determines the UMTS (UTRA FDD) band based on the UARFCN (UTRA Absolute Radio Frequency Channel Number).
 * This function covers common UMTS FDD bands as defined by 3GPP TS 25.101.
 *
 * @param uarfcn The UTRA Absolute Radio Frequency Channel Number.
 * @return A String representing the UMTS band (e.g., "Band 1", "Band 5"), or "Unknown UMTS" if the UARFCN does not match a known band.
 */
fun getUtranBand(uarfcn: Int): String {
    return when (uarfcn) {
        in 10562..10838 -> "Band 1" // IMT 2100
        in 9662..9938 -> "Band 2" // PCS 1900
        in 1162..1513 -> "Band 3" // DCS 1800
        in 1537..1738 -> "Band 4" // AWS-1 1700/2100
        in 4357..4458 -> "Band 5" // CLR 850
        in 4387..4413 -> "Band 6" // UMTS 800 (Japan)
        in 2237..2563 -> "Band 7" // IMT-E 2600
        in 2937..3088 -> "Band 8" // E-GSM 900
        in 9237..9387 -> "Band 9" // UMTS 1700 (Japan)
        in 3112..3388 -> "Band 10" // Extended AWS 1700/2100
        in 3712..3787 -> "Band 11" // Lower PDC 1500
        in 3842..3903 -> "Band 12" // Lower SMH 700
        in 4017..4043 -> "Band 13" // Upper SMH 700
        in 4117..4143 -> "Band 14" // Upper SMH 700
        in 712..763 -> "Band 19" // Upper 800 (Japan)
        in 4512..4638 -> "Band 20" // Digital Dividend (EU) 800
        in 862..912 -> "Band 21" // Upper PDC 1500
        in 4662..5038 -> "Band 22" // C-Band 3500
        in 5112..5413 -> "Band 25" // Extended PCS 1900
        in 5762..5913 -> "Band 26" // Extended CLR 850
        in 6617..7012 -> "Band 32" // L-band EU 1500 (SDL)
        else -> "Unknown UMTS"
    }
}

/**
 * Calculates the UMTS (UTRA FDD) downlink frequency in Hz based on the UARFCN.
 * Formulas are derived from 3GPP TS 25.101.
 *
 * @param uarfcn The UTRA Absolute Radio Frequency Channel Number.
 * @return The downlink frequency in Hz, or null if the UARFCN does not correspond to a known band or formula.
 */
fun getUtranDownlinkFrequency(uarfcn: Int): Long? {
    val freqMHz: Double? = when (uarfcn) {
        in 10562..10838 -> 2110.0 + 0.2 * (uarfcn - 10562) // Band 1
        in 9662..9938 -> 1930.0 + 0.2 * (uarfcn - 9662) // Band 2
        in 1162..1513 -> 1805.0 + 0.2 * (uarfcn - 1162) // Band 3
        in 1537..1738 -> 2110.0 + 0.2 * (uarfcn - 1537) // Band 4
        in 4357..4458 -> 869.0 + 0.2 * (uarfcn - 4357) // Band 5
        in 4387..4413 -> 875.0 + 0.2 * (uarfcn - 4387) // Band 6
        in 2237..2563 -> 2620.0 + 0.2 * (uarfcn - 2237) // Band 7
        in 2937..3088 -> 925.0 + 0.2 * (uarfcn - 2937) // Band 8
        in 9237..9387 -> 1844.9 + 0.2 * (uarfcn - 9237) // Band 9
        in 3112..3388 -> 2110.0 + 0.2 * (uarfcn - 3112) // Band 10
        in 3712..3787 -> 1475.9 + 0.2 * (uarfcn - 3712) // Band 11
        in 3842..3903 -> 729.0 + 0.2 * (uarfcn - 3842) // Band 12
        in 4017..4043 -> 746.0 + 0.2 * (uarfcn - 4017) // Band 13
        in 4117..4143 -> 758.0 + 0.2 * (uarfcn - 4117) // Band 14
        in 712..763 -> 875.0 + 0.2 * (uarfcn - 712) // Band 19
        in 4512..4638 -> 791.0 + 0.2 * (uarfcn - 4512) // Band 20
        in 862..912 -> 1495.9 + 0.2 * (uarfcn - 862) // Band 21
        in 4662..5038 -> 3510.0 + 0.2 * (uarfcn - 4662) // Band 22
        in 5112..5413 -> 1930.0 + 0.2 * (uarfcn - 5112) // Band 25
        in 5762..5913 -> 859.0 + 0.2 * (uarfcn - 5762) // Band 26
        in 6617..7012 -> 1452.0 + 0.2 * (uarfcn - 6617) // Band 32 (SDL)
        else -> null
    }
    return freqMHz?.let { (it * 1_000_000).toLong() }
}

/**
 * Determines the LTE band based on the EARFCN (E-UTRA Absolute Radio Frequency Channel Number)
 * or a provided array of bands (preferred if available from API).
 * This function covers common LTE FDD and TDD bands as defined by 3GPP TS 36.101.
 *
 * @param earfcn The E-UTRA Absolute Radio Frequency Channel Number. Can be null if `bands` is provided.
 * @param bands An array of integer band numbers, typically from Android's `CellInfoLte.getBands()`.
 * @return A String representing the LTE band (e.g., "LTE Band 1", "LTE Band 38"), or "Unknown LTE" if no band can be determined.
 */
fun getLteBand(earfcn: Int?, bands: IntArray): String {
    // Prioritize the bands array if available and not empty
    if (bands.isNotEmpty()) {
        return "LTE Band ${bands[0]}" // Return the first band if multiple are reported
    }

    // Fallback for older APIs or if bands array is empty: Infer from EARFCN
    earfcn?.let {
        return when (it) {
            // FDD Bands
            in 0..599 -> "LTE Band 1"
            in 600..1199 -> "LTE Band 2"
            in 1200..1949 -> "LTE Band 3"
            in 1950..2399 -> "LTE Band 4"
            in 2400..2649 -> "LTE Band 5"
            in 2650..2749 -> "LTE Band 6"
            in 2750..3449 -> "LTE Band 7"
            in 3450..3799 -> "LTE Band 8"
            in 3800..4149 -> "LTE Band 9"
            in 4150..4749 -> "LTE Band 10"
            in 4750..4949 -> "LTE Band 11"
            in 5010..5179 -> "LTE Band 12"
            in 5180..5279 -> "LTE Band 13"
            in 5280..5379 -> "LTE Band 14"
            in 5730..5849 -> "LTE Band 17"
            in 5850..5999 -> "LTE Band 18"
            in 6000..6149 -> "LTE Band 19"
            in 6150..6449 -> "LTE Band 20"
            in 6450..6599 -> "LTE Band 21"
            in 6600..7399 -> "LTE Band 22"
            in 7500..7699 -> "LTE Band 23"
            in 7700..8039 -> "LTE Band 24"
            in 8040..8689 -> "LTE Band 25"
            in 8690..9039 -> "LTE Band 26"
            in 9040..9209 -> "LTE Band 27"
            in 9210..9659 -> "LTE Band 28"
            in 9660..9769 -> "LTE Band 29" // SDL
            in 9770..9869 -> "LTE Band 30"
            in 9870..9919 -> "LTE Band 31"
            in 9920..10359 -> "LTE Band 32" // SDL
            in 10360..11259 -> "LTE Band 65"
            in 11260..11759 -> "LTE Band 66"
            in 11760..11959 -> "LTE Band 67" // SDL
            in 11960..12259 -> "LTE Band 68"
            in 12260..12759 -> "LTE Band 69" // SDL
            in 12760..12999 -> "LTE Band 70"
            in 13000..13349 -> "LTE Band 71"
            in 13350..13399 -> "LTE Band 72"
            in 13400..13449 -> "LTE Band 73"
            in 13450..13879 -> "LTE Band 74"
            in 13880..14729 -> "LTE Band 75" // SDL
            in 14730..14779 -> "LTE Band 76" // SDL
            in 14780..14959 -> "LTE Band 85"
            in 14960..15009 -> "LTE Band 87"
            in 15010..15059 -> "LTE Band 88"
            in 15060..15069 -> "LTE Band 103"
            in 15070..15119 -> "LTE Band 106"

            // TDD Bands
            in 36000..36199 -> "LTE Band 33"
            in 36200..36349 -> "LTE Band 34"
            in 36350..36949 -> "LTE Band 35"
            in 36950..37549 -> "LTE Band 36"
            in 37550..37749 -> "LTE Band 37"
            in 37750..38249 -> "LTE Band 38"
            in 38250..38649 -> "LTE Band 39"
            in 38650..39649 -> "LTE Band 40"
            in 39650..41589 -> "LTE Band 41"
            in 41590..43589 -> "LTE Band 42"
            in 43590..45589 -> "LTE Band 43"
            in 45590..46589 -> "LTE Band 44"
            in 46590..46789 -> "LTE Band 45"
            in 46790..54539 -> "LTE Band 46"
            in 54540..55239 -> "LTE Band 47"
            in 55240..56739 -> "LTE Band 48"
            in 56740..57589 -> "LTE Band 50"
            in 57590..57639 -> "LTE Band 51"
            in 57640..58639 -> "LTE Band 52"
            in 58640..58754 -> "LTE Band 53"
            in 58755..58804 -> "LTE Band 54"
            else -> "Unknown LTE"
        }
    }
    return "Unknown LTE"
}

/**
 * Calculates the LTE downlink frequency in Hz based on the EARFCN.
 * Formulas and offsets are derived from 3GPP TS 36.101.
 *
 * @param earfcn The E-UTRA Absolute Radio Frequency Channel Number.
 * @return The downlink frequency in Hz, or null if the EARFCN does not correspond to a known band or formula.
 */
fun getLteDownlinkFrequency(earfcn: Int): Long? {
    // Define a data class to hold band specific parameters
    data class LteBandInfo(val fdlLow: Double, val nOffsDl: Int)

    // Map of EARFCN ranges to their corresponding band info (FDL_low and NOffs-DL)
    val lteBandParameters = mapOf(
        // FDD Bands
        0..599 to LteBandInfo(2110.0, 0), // Band 1
        600..1199 to LteBandInfo(1930.0, 600), // Band 2
        1200..1949 to LteBandInfo(1805.0, 1200), // Band 3
        1950..2399 to LteBandInfo(2110.0, 1950), // Band 4
        2400..2649 to LteBandInfo(869.0, 2400), // Band 5
        2650..2749 to LteBandInfo(875.0, 2650), // Band 6
        2750..3449 to LteBandInfo(2620.0, 2750), // Band 7
        3450..3799 to LteBandInfo(925.0, 3450), // Band 8
        3800..4149 to LteBandInfo(1844.9, 3800), // Band 9
        4150..4749 to LteBandInfo(2110.0, 4150), // Band 10
        4750..4949 to LteBandInfo(1475.9, 4750), // Band 11
        5010..5179 to LteBandInfo(729.0, 5010), // Band 12
        5180..5279 to LteBandInfo(746.0, 5180), // Band 13
        5280..5379 to LteBandInfo(758.0, 5280), // Band 14
        5730..5849 to LteBandInfo(734.0, 5730), // Band 17
        5850..5999 to LteBandInfo(860.0, 5850), // Band 18
        6000..6149 to LteBandInfo(875.0, 6000), // Band 19
        6150..6449 to LteBandInfo(791.0, 6150), // Band 20
        6450..6599 to LteBandInfo(1495.9, 6450), // Band 21
        6600..7399 to LteBandInfo(3510.0, 6600), // Band 22
        7500..7699 to LteBandInfo(2180.0, 7500), // Band 23
        7700..8039 to LteBandInfo(1525.0, 7700), // Band 24
        8040..8689 to LteBandInfo(1930.0, 8040), // Band 25
        8690..9039 to LteBandInfo(859.0, 8690), // Band 26
        9040..9209 to LteBandInfo(852.0, 9040), // Band 27
        9210..9659 to LteBandInfo(758.0, 9210), // Band 28
        9660..9769 to LteBandInfo(717.0, 9660), // Band 29 (SDL)
        9770..9869 to LteBandInfo(2350.0, 9770), // Band 30
        9870..9919 to LteBandInfo(462.5, 9870), // Band 31
        9920..10359 to LteBandInfo(1452.0, 9920), // Band 32 (SDL)
        10360..11259 to LteBandInfo(2110.0, 10360), // Band 65
        11260..11759 to LteBandInfo(2110.0, 11260), // Band 66
        11760..11959 to LteBandInfo(738.0, 11760), // Band 67 (SDL)
        11960..12259 to LteBandInfo(753.0, 11960), // Band 68
        12260..12759 to LteBandInfo(2570.0, 12260), // Band 69 (SDL)
        12760..12999 to LteBandInfo(1995.0, 12760), // Band 70
        13000..13349 to LteBandInfo(617.0, 13000), // Band 71
        13350..13399 to LteBandInfo(461.0, 13350), // Band 72
        13400..13449 to LteBandInfo(460.0, 13400), // Band 73
        13450..13879 to LteBandInfo(1475.0, 13450), // Band 74
        13880..14729 to LteBandInfo(1432.0, 13880), // Band 75 (SDL)
        14730..14779 to LteBandInfo(1427.0, 14730), // Band 76 (SDL)
        14780..14959 to LteBandInfo(728.0, 14780), // Band 85
        14960..15009 to LteBandInfo(420.0, 14960), // Band 87
        15010..15059 to LteBandInfo(422.0, 15010), // Band 88
        15060..15069 to LteBandInfo(757.0, 15060), // Band 103
        15070..15119 to LteBandInfo(935.0, 15070), // Band 106

        // TDD Bands (FDL_low and NOffs-DL are the same as FUL_low and NOffs-UL for TDD)
        36000..36199 to LteBandInfo(1900.0, 36000), // Band 33
        36200..36349 to LteBandInfo(2010.0, 36200), // Band 34
        36350..36949 to LteBandInfo(1850.0, 36350), // Band 35
        36950..37549 to LteBandInfo(1930.0, 36950), // Band 36
        37550..37749 to LteBandInfo(1910.0, 37550), // Band 37
        37750..38249 to LteBandInfo(2570.0, 37750), // Band 38
        38250..38649 to LteBandInfo(1880.0, 38250), // Band 39
        38650..39649 to LteBandInfo(2300.0, 38650), // Band 40
        39650..41589 to LteBandInfo(2496.0, 39650), // Band 41
        41590..43589 to LteBandInfo(3400.0, 41590), // Band 42
        43590..45589 to LteBandInfo(3600.0, 43590), // Band 43
        45590..46589 to LteBandInfo(703.0, 45590), // Band 44
        46590..46789 to LteBandInfo(1447.0, 46590), // Band 45
        46790..54539 to LteBandInfo(5150.0, 46790), // Band 46
        54540..55239 to LteBandInfo(5855.0, 54540), // Band 47
        55240..56739 to LteBandInfo(3550.0, 55240), // Band 48
        56740..57589 to LteBandInfo(1432.0, 56740), // Band 50
        57590..57639 to LteBandInfo(1427.0, 57590), // Band 51
        57640..58639 to LteBandInfo(3300.0, 57640), // Band 52
        58640..58754 to LteBandInfo(2483.5, 58640), // Band 53
        58755..58804 to LteBandInfo(1670.0, 58755)  // Band 54
    )

    // Find the matching band info for the given EARFCN
    val bandInfo = lteBandParameters.entries.find { earfcn in it.key }?.value

    return bandInfo?.let {
        val freqMHz = it.fdlLow + 0.1 * (earfcn - it.nOffsDl)
        (freqMHz * 1_000_000).toLong()
    }
}
