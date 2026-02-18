package app.accounting.accountingapp

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.prefs.Preferences

object SuggestionsManager {


    private val jsonConfig = Json {
        ignoreUnknownKeys = true // Wichtig für Updates!
        prettyPrint = true      // Macht die Dateien lesbar
        encodeDefaults = true
    }

    private val prefs = Preferences.userRoot().node("app/honorarcraft/teaching_suggestions")
    private const val KEY = "subjects_v2"
    private const val EXPIRATION_MILLIS = 40L * 24 * 60 * 60 * 1000 // 40 Tage


    private fun getBaseFolder(): File = getAppDataFolder()

    // Unterordner relativ zum zentralen App-Ordner
    private fun getInvoicesFolder() = File(getBaseFolder(), "invoices")
    private fun getTotalsFolder() = File(getBaseFolder(), "totals")


    // ZENTRALE LADESTATION: Liest die Jahresdatei ein.

    fun loadYearlyData(year: String): Map<String, String> {
        val yearFile = File(getTotalsFolder(), "year_$year.json")
        if (!yearFile.exists()) return emptyMap()
        return try {
            jsonConfig.decodeFromString<Map<String, String>>(yearFile.readText())
        } catch (e: Exception) {
            emptyMap()
        }
    }


    // Aktualisiert einen einzelnen Wert in der Jahresdatei.

    fun saveTotalsOnly(invoiceNumber: String, totalCost: String, year: String) {
        val folder = getTotalsFolder()
        if (!folder.exists()) folder.mkdirs()

        val yearFile = File(folder, "year_$year.json")
        val yearlyData = loadYearlyData(year).toMutableMap()
        yearlyData[invoiceNumber] = totalCost
        yearFile.writeText(jsonConfig.encodeToString(yearlyData))
    }


    // Speichert einen Startwert für das Jahr unter dem speziellen Key "S".

    fun saveYearlyStartValue(year: String, startValue: String) {
        // Wir delegieren einfach an saveTotalsOnly mit dem reservierten Key "S"
        saveTotalsOnly(invoiceNumber = "S", totalCost = startValue, year = year)
    }


    //Berechnet die Summe aller Einträge in der Jahresdatei.

    fun calculateYearlyTotal(year: String): String {
        val data = loadYearlyData(year)
        if (data.isEmpty()) return "0.00"

        return try {
            val sum = data.values.fold(java.math.BigDecimal.ZERO) { acc, cost ->
                val amount = if (cost.isBlank()) 0.0 else cost.replace(",", ".").toDoubleOrNull() ?: 0.0
                acc.add(java.math.BigDecimal(amount.toString()))
            }
            sum.setScale(2, java.math.RoundingMode.HALF_UP).toString()
        } catch (e: Exception) {
            "0.00"
        }
    }

    // Vorschläge in die Java Preferences

    fun loadSuggestions(): List<String> {
        val savedString = prefs.get(KEY, "")
        if (savedString.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        val allEntries = savedString.split(";;;").mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
        }
        val validEntries = allEntries.filter { (now - it.second) < EXPIRATION_MILLIS }
        if (validEntries.size < allEntries.size) saveInternal(validEntries)
        return validEntries.map { it.first }
    }

    fun addSuggestion(subject: String) {
        if (subject.isBlank()) return
        val now = System.currentTimeMillis()
        val currentEntries = prefs.get(KEY, "").split(";;;")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size == 2) parts[0] to (parts[1].toLongOrNull() ?: 0L) else null
            }
            .filter { (now - it.second) < EXPIRATION_MILLIS }
            .toMutableList()
        currentEntries.removeAll { it.first == subject }
        currentEntries.add(subject to now)
        saveInternal(currentEntries.takeLast(10))
    }


    // Setzt die gesamte App auf Werkseinstellungen zurück.
    // Löscht alle Dateien, Rechnungen, Umsätze und Einstellungen.

    fun resetToFactorySettings() {
        try {
            // 1. Alle Dateien löschen
            val appFolder = getBaseFolder()
            if (appFolder.exists()) {
                // Löscht rekursiv alle Unterordner (invoices, totals) und Dateien (company.json, etc.)
                appFolder.deleteRecursively()
            }

            // 2. Ordnerstruktur sofort wieder sauber neu anlegen
            appFolder.mkdirs()
            getInvoicesFolder().mkdirs()
            getTotalsFolder().mkdirs()

            // 3. Preferences (Vorschläge & UE-Status) löschen
            prefs.clear()
            prefs.flush()

            println("Werkseinstellungen wiederhergestellt: Alle Daten gelöscht.")
        } catch (e: Exception) {
            println("Fehler beim Zurücksetzen: ${e.message}")
        }
    }


    // Vorschläge löschen
    fun clearAll() {
        prefs.remove(KEY)
        prefs.flush()
    }


    private fun saveInternal(entries: List<Pair<String, Long>>) {
        prefs.put(KEY, entries.joinToString(";;;") { "${it.first}|${it.second}" })
    }

    // Einträge (JSON) ---

    fun saveInvoiceEntries(invoiceNumber: String, entries: List<InvoiceEntry>) {
        val folder = getInvoicesFolder()
        if (!folder.exists()) folder.mkdirs()
        File(folder, "invoice_$invoiceNumber.json").writeText(jsonConfig.encodeToString(entries))
    }

    fun loadInvoiceEntries(invoiceNumber: String): List<InvoiceEntry> {
        val file = File(getInvoicesFolder(), "invoice_$invoiceNumber.json")
        return if (file.exists()) {
            try {
                jsonConfig.decodeFromString<List<InvoiceEntry>>(file.readText())
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    fun saveUEState(isUE: Boolean) = prefs.putBoolean("show_ue_label", isUE)
    fun loadUEState(): Boolean = prefs.getBoolean("show_ue_label", false)
}