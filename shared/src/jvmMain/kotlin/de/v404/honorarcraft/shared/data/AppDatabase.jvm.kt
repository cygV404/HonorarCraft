package de.v404.honorarcraft.shared.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Desktop-Seite der Datenbank. Anders als auf Android wird hier das gebündelte SQLite
 * mitgeliefert — auf dem Desktop gibt es kein System-SQLite, auf das man sich verlassen
 * könnte.
 *
 * @param path vollständiger Pfad der Datenbankdatei, üblicherweise im App-Datenordner.
 * @param queryContext Kontext, auf dem Room die Abfragen ausführt. Im Betrieb
 *   [Dispatchers.IO]; Tests reichen hier ihren Test-Dispatcher herein, sonst laufen die
 *   Abfragen an der Testuhr vorbei.
 */
fun createDesktopDatabase(
    path: String,
    queryContext: CoroutineContext = Dispatchers.IO,
): AppDatabase =
    Room.databaseBuilder<AppDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(queryContext)
        // Kein fallbackToDestructiveMigration: ein fehlender Migrationspfad muss
        // auffallen, statt still die Rechnungen zu löschen.
        .addMigrations(*ALL_MIGRATIONS)
        .build()
