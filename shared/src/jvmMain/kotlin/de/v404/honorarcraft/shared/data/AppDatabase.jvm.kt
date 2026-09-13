package de.v404.honorarcraft.shared.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Desktop-Seite der Datenbank. Anders als auf Android wird hier das gebündelte SQLite
 * mitgeliefert — auf dem Desktop gibt es kein System-SQLite, auf das man sich verlassen
 * könnte.
 *
 * @param path vollständiger Pfad der Datenbankdatei, üblicherweise im App-Datenordner.
 */
fun createDesktopDatabase(path: String): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        // Kein fallbackToDestructiveMigration: ein fehlender Migrationspfad muss
        // auffallen, statt still die Rechnungen zu löschen.
        .addMigrations(*ALL_MIGRATIONS)
        .build()
