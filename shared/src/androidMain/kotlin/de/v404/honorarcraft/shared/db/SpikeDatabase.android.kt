package de.v404.honorarcraft.shared.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun spikeDatabase(context: Context): SpikeDatabase =
    Room.databaseBuilder<SpikeDatabase>(
        context = context.applicationContext,
        name = context.getDatabasePath("spike.db").absolutePath,
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
