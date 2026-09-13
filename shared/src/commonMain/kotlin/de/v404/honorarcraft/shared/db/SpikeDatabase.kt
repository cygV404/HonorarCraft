package de.v404.honorarcraft.shared.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(entities = [SpikeItem::class], version = 1)
@ConstructedBy(SpikeDatabaseConstructor::class)
abstract class SpikeDatabase : RoomDatabase() {
    abstract fun spikeDao(): SpikeDao
}

/**
 * KMP-Room erzeugt die actual-Implementierung je Target selbst; deshalb bleibt hier
 * ein expect-Objekt ohne eigenes actual.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object SpikeDatabaseConstructor : RoomDatabaseConstructor<SpikeDatabase> {
    override fun initialize(): SpikeDatabase
}
