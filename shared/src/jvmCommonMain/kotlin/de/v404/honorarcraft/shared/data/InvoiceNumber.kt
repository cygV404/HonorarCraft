package de.v404.honorarcraft.shared.data

import java.util.Locale

/** Aufbau der Rechnungsnummer, vom Nutzer in den Einstellungen gewählt. */
enum class InvoiceFormat {
    NUMBER,
    YEAR_NUMBER,
    YEAR_MONTH_NUMBER
}

/**
 * Setzt die Rechnungsnummer aus laufender Nummer, Jahr und Monat zusammen.
 *
 * Die laufende Nummer kommt aus den Einstellungen und ist damit nicht garantiert
 * numerisch; ein unerwarteter Wert bleibt unverändert stehen, statt eine Exception
 * auszulösen.
 */
fun formatInvoice(number: String, format: InvoiceFormat, year: Int, month: Int): String {
    val numInt = number.toIntOrNull()
    val displayNum = if (numInt != null) String.format(Locale.GERMANY, "%02d", numInt) else number

    return when (format) {
        InvoiceFormat.NUMBER -> displayNum
        InvoiceFormat.YEAR_NUMBER -> "$year-$displayNum"
        InvoiceFormat.YEAR_MONTH_NUMBER -> String.format(
            Locale.GERMANY,
            "%d-%02d-%02d",
            year,
            month,
            numInt ?: 0
        )
    }
}
