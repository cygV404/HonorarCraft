package de.v404.honorarcraft.shared.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpikeDao {
    @Insert
    suspend fun insert(item: SpikeItem): Long

    @Query("SELECT * FROM spike_item ORDER BY id")
    fun observeAll(): Flow<List<SpikeItem>>

    @Query("SELECT COALESCE(SUM(amountCents), 0) FROM spike_item")
    suspend fun sumCents(): Long
}
