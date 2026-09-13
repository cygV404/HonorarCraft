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
    }

    override fun getString(key: String, defaultValue: String): String =
        prefs.get(key, defaultValue)

    override fun putString(key: String, value: String) {
        prefs.put(key, value)
    }

    override fun clear() {
        prefs.clear()
    }
}
