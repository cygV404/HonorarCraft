package de.v404.honorarcraft.shared

import java.io.File

/**
 * Betriebssystemabhängiger Datenordner des Desktops.
 *
 * Derselbe Ort wie in der früheren Desktop-Fassung — dort liegen die Altdaten, die
 * `LegacyDesktopImport` übernimmt, und dorthin kommt die Room-Datenbank.
 */
fun desktopAppDataFolder(): File {
    val os = System.getProperty("os.name").lowercase()
    val folder = when {
        os.contains("win") -> File(System.getenv("APPDATA"), "HonorarCraft")
        os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/HonorarCraft")
        else -> File(System.getProperty("user.home"), ".honorarcraft")
    }
    if (!folder.exists()) folder.mkdirs()
    return folder
}
