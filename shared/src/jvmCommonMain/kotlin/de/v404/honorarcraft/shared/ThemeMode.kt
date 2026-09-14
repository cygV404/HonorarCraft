package de.v404.honorarcraft.shared

/**
 * Gewünschte Darstellung der Oberfläche.
 *
 * Es gibt sie als eigene Einstellung, weil [SYSTEM] nicht überall trägt: Skiko, die Grundlage
 * von Compose Desktop, liefert die Systemeinstellung **unter Linux nicht** aus (`UNKNOWN`), und
 * Compose liest daraus „hell". Auf Android und unter Windows/macOS funktioniert [SYSTEM].
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}
