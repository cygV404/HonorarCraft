package de.v404.honorarcraft.shared.data

import java.math.BigDecimal

object Constants {
    val DEFAULT_RATE: BigDecimal = BigDecimal("23.00")
    const val DATE_PATTERN = "dd.MM.yyyy"

    /**
     * Angezeigte Programmversion. Muss zu `versionName` in `androidApp/build.gradle.kts` und
     * zu `packageVersion` in `composeApp/build.gradle.kts` passen — bisher war das die
     * Android-`BuildConfig`, die es auf dem Desktop nicht gibt.
     */
    const val APP_VERSION = "1.5"
}
