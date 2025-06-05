// Utils.kt
package com.example.parvin_project

import java.util.Locale

/**
 * Extension function to capitalize the first letter of each word in a string.
 * Useful for formatting display text from map keys.
 */
fun String.capitalizeWords(): String = split(" ").joinToString(" ") { word ->
    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
