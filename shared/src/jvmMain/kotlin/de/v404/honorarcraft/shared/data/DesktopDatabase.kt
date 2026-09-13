package de.v404.honorarcraft.shared.data

import de.v404.honorarcraft.shared.desktopAppDataFolder
import java.io.File

/**
 * Gegenstück zu `AndroidDatabase`: hält die eine Datenbankinstanz des Desktops.
 *
 * Der Import einer Sicherung tauscht die Datei unter der Verbindung aus und braucht deshalb
 * einen Weg, sie zu schließen und danach neu zu öffnen.
 */
object DesktopDatabase {
    private var instance: AppDatabase? = null

    val file: File get() = File(desktopAppDataFolder(), DATABASE_FILE_NAME)

    @Synchronized
    fun get(): AppDatabase =
        instance ?: createDesktopDatabase(file.absolutePath).also { instance = it }

    @Synchronized
    fun close() {
        instance?.close()
        instance = null
    }
}
