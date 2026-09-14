package de.v404.honorarcraft.shared.data

import java.math.BigDecimal

object Constants {
    val DEFAULT_RATE: BigDecimal = BigDecimal("23.00")
    const val DATE_PATTERN = "dd.MM.yyyy"

    /**
     * Angezeigte Programmversion.
     *
     * Kommt aus `gradle/libs.versions.toml` (`appVersion`) und wird von Gradle als
     * `GENERATED_APP_VERSION` erzeugt — dieselbe Quelle wie `packageVersion` auf dem Desktop
     * und `versionName` auf Android. Zum Anheben nur den Katalogeintrag ändern.
     */
    val APP_VERSION: String get() = GENERATED_APP_VERSION
}
