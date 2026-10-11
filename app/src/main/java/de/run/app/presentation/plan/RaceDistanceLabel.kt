package de.run.app.presentation.plan

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

internal fun formatRaceDistance(meters: Int): String =
    DecimalFormat("0.#", DecimalFormatSymbols.getInstance(Locale.GERMANY))
        .format(meters / 1_000.0)

internal fun parseRaceDistanceMeters(value: String): Int? = value.replace(',', '.')
    .toDoubleOrNull()
    ?.takeIf { it in 1.0..100.0 }
    ?.let { (it * 1_000).toInt() }

internal fun raceDistanceName(meters: Int): String {
    val kilometers = meters / 1_000.0
    return when {
        kotlin.math.abs(kilometers - 42.195) < 0.05 -> "Marathon"
        kotlin.math.abs(kilometers - 21.0975) < 0.05 -> "Halbmarathon"
        else -> "${formatRaceDistance(meters)} km Lauf"
    }
}
