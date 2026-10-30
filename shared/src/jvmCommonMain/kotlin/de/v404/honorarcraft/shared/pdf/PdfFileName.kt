package de.v404.honorarcraft.shared.pdf

import de.v404.honorarcraft.shared.data.Customer

/** Unter Windows im Dateinamen verboten; dazu Steuerzeichen. */
private val VERBOTEN = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

/**
 * Dateiname des Rechnungs-PDFs, auf beiden Plattformen derselbe: `Rechnung_<nummer>_<kunde>.pdf`.
 *
 * Der Kundenname hält den Ablageordner bei mehreren Kunden lesbar. Verbotene Zeichen werden
 * durch `-` ersetzt, Leerzeichen durch `_`; Umlaute bleiben.
 */
fun pdfDateiname(formattedInvoiceNumber: String, customer: Customer?): String {
    fun sauber(text: String) = text.trim()
        .replace(VERBOTEN, "-")
        .replace(Regex("""\s+"""), "_")
        .trim('.', '_', '-')
    val nummer = sauber(formattedInvoiceNumber)
    val kunde = customer?.let { sauber(it.displayName) }.orEmpty()
    return if (kunde.isEmpty()) "Rechnung_$nummer.pdf" else "Rechnung_${nummer}_$kunde.pdf"
}
