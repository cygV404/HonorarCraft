package de.v404.honorarcraft.shared.db

import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

/**
 * Desktop-Variante: Room bekommt einen Dateipfad und den gebündelten SQLite-Treiber,
 * damit keine System-SQLite-Bibliothek nötig ist.
 */
fun spikeDatabase(path: String): SpikeDatabase =
    Room.databaseBuilder<SpikeDatabase>(name = path)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

fun spikeDatabaseBuilder(path: String): RoomDatabase.Builder<SpikeDatabase> =
    Room.databaseBuilder<SpikeDatabase>(name = path)
