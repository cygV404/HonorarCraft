package app.accounting.accountingapp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.prefs.Preferences

object SuggestionsManager {
    private val prefs = Preferences.userRoot().node("app/accounting/teaching_suggestions")
    private const val KEY = "subjects_v2"
    private const val EXPIRATION_MILLIS = 40L * 24 * 60 * 60 * 1000//40 Tage in ms
    private val baseFolder = File(System.getProperty("user.home"), ".accountingapp/invoices")

    /**
     * Lädt nur die Vorschläge, die noch nicht abgelaufen sind.
     */
    fun loadSuggestions(): List<String> {
        val savedString = prefs.get(KEY, "")
        if (savedString.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()

        // Format: "Fach|Zeitstempel;;;Fach|Zeitstempel"
        val allEntries = savedString.split(";;;")
            .mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size == 2) {
                    val name = parts[0]
                    val timestamp = parts[1].toLongOrNull() ?: 0L
                    name to timestamp
                } else null
            }

        // Filtern: Nur Einträge behalten, die jünger als 30 Tage sind
        val validEntries = allEntries.filter { (now - it.second) < EXPIRATION_MILLIS }

        // Falls Einträge abgelaufen sind, bereinigen wir den Speicher direkt
        if (validEntries.size < allEntries.size) {
            saveInternal(validEntries)
        }

        return validEntries.map { it.first }
    }

    /**
     * Fügt einen Vorschlag hinzu oder aktualisiert den Zeitstempel eines vorhandenen.
     */
    fun addSuggestion(subject: String) {
        if (subject.isBlank()) return


        val now = System.currentTimeMillis()
        val savedString = prefs.get(KEY, "")

        val currentEntries = savedString.split(";;;")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
            }
            .filter { (now - it.second) < EXPIRATION_MILLIS } // Abgelaufene direkt beim Adden kicken
            .toMutableList()

        // Bestehenden Eintrag entfernen, um ihn mit neuem Zeitstempel "nach vorne" zu schieben
        currentEntries.removeAll { it.first == subject }

        // Neuen/Aktualisierten Eintrag hinzufügen
        currentEntries.add(subject to now)

        // Auf 10 begrenzen und speichern
        saveInternal(currentEntries.takeLast(10))
    }

    /**
     * Löscht alle Vorschläge sofort.
     */
    fun clearAll() {
        prefs.remove(KEY)
    }

    private fun saveInternal(entries: List<Pair<String, Long>>) {
        val toSave = entries.joinToString(";;;") { "${it.first}|${it.second}" }
        prefs.put(KEY, toSave)
    }


//Funktionen zum laden/speichern der entries im thirdWindow

    fun saveInvoiceEntries(invoiceNumber: String, entries: List<InvoiceEntry>) {
        baseFolder.mkdirs()
        val file = File(baseFolder, "invoice_$invoiceNumber.json")
        val json = Json.encodeToString(entries)
        file.writeText(json)
    }

    fun loadInvoiceEntries(invoiceNumber: String): List<InvoiceEntry> {
        val file = File(baseFolder, "invoice_$invoiceNumber.json")
        return if (file.exists()) {
            try {
                Json.decodeFromString<List<InvoiceEntry>>(file.readText())
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }


    fun saveTotalsOnly(invoiceNumber: String, totalCost: String, totalUE: String, year: String) {
        val totalsFolder = File(System.getProperty("user.home"), ".accountingapp/totals")
        totalsFolder.mkdirs()

        val file = File(totalsFolder, "totals_$invoiceNumber.json")


        val content = "$totalCost|$totalUE|$year"
        file.writeText(content)
    }

    fun calculateYearlyTotal(year: String): String {
        val totalsFolder = File(System.getProperty("user.home"), ".accountingapp/totals")
        if (!totalsFolder.exists()) return "0.00"

        var sum = java.math.BigDecimal.ZERO

        totalsFolder.listFiles()?.forEach { file ->
            try {
                val parts = file.readText().split("|")
                if (parts.size == 3) {
                    val cost = parts[0]
                    val fileYear = parts[2]

                    if (fileYear == year) {
                        sum = sum.add(java.math.BigDecimal(cost))
                    }
                }
            } catch (e: Exception) { /* Fehler ignorieren */
            }
        }
        return sum.setScale(2, java.math.RoundingMode.HALF_UP).toString()
    }

    // Im SuggestionsManager Object
    fun saveUEState(isUE: Boolean) {
        prefs.putBoolean("show_ue_label", isUE)
    }

    fun loadUEState(): Boolean {
        return prefs.getBoolean("show_ue_label", false)
    }

}
