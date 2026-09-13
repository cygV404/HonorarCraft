package de.v404.honorarcraft.shared

/**
 * Kleiner Schlüssel-Wert-Speicher für Einstellungen, die nicht in die Datenbank gehören
 * (gewählte Rechnungsnummer, Jahr, Monat, Nummernformat).
 *
 * Ersetzt den direkten Zugriff auf `SharedPreferences` im ViewModel: Android legt weiterhin
 * in `SharedPreferences` ab, der Desktop in `java.util.prefs`. Für Tests reicht eine
 * Implementierung im Speicher.
 */
interface Settings {
    fun getInt(key: String, defaultValue: Int): Int
    fun putInt(key: String, value: Int)
    fun getString(key: String, defaultValue: String): String
    fun putString(key: String, value: String)

    /** Löscht alle Einstellungen. Teil des Zurücksetzens auf Werkseinstellungen. */
    fun clear()
}

/** Einstellungen im Speicher — für Tests und als Rückfall, wenn kein Speicher verfügbar ist. */
class InMemorySettings(initial: Map<String, Any> = emptyMap()) : Settings {
    private val values = LinkedHashMap<String, Any>(initial)

    override fun getInt(key: String, defaultValue: Int): Int =
        values[key] as? Int ?: defaultValue

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }

    override fun getString(key: String, defaultValue: String): String =
        values[key] as? String ?: defaultValue

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun clear() {
        values.clear()
    }
}

/** Schlüsselnamen. Sie müssen zu den bisher von der Android-App geschriebenen passen. */
object SettingsKeys {
    const val INVOICE_YEAR = "invoice_year"
    const val INVOICE_MONTH = "invoice_month"
    const val SELECTED_INVOICE_NUMBER = "selected_invoice_number"
    const val INVOICE_FORMAT = "invoice_format"
}
