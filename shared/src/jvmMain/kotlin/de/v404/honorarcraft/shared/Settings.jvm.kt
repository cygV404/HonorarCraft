package de.v404.honorarcraft.shared

import java.util.prefs.Preferences

/**
 * Einstellungen in `java.util.prefs` auf dem Desktop.
 *
 * Bewusst ein eigener Knoten: `app/accounting/honorarcraft` enthält den AES-Schlüssel der
 * alten Desktop-Fassung und darf nicht mit geleert werden.
 */
class PreferencesSettings(
    nodeName: String = "de/v404/honorarcraft/settings",
) : Settings {
    private val prefs: Preferences = Preferences.userRoot().node(nodeName)

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        prefs.putInt(key, value)
        sofortSchreiben()
    }

    override fun getString(key: String, defaultValue: String): String =
        prefs.get(key, defaultValue)

    override fun putString(key: String, value: String) {
        prefs.put(key, value)
        sofortSchreiben()
    }

    override fun clear() {
        prefs.clear()
        sofortSchreiben()
    }

    /**
     * `java.util.prefs` schreibt sonst nur verzögert — der Wert steht im Speicher und landet
     * erst beim periodischen Abgleich oder beim sauberen Beenden auf der Platte. Wird die App
     * vorher abgewürgt, ist er weg.
     *
     * Für die Rechnungsnummer wäre das ernst: nach einem erzeugten PDF stünde beim nächsten
     * Start wieder die alte Nummer, und zwei Rechnungen bekämen dieselbe.
     */
    private fun sofortSchreiben() {
        runCatching { prefs.flush() }
            .onFailure { logError("Settings", "Einstellungen konnten nicht geschrieben werden", it) }
    }
}
