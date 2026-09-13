package de.v404.honorarcraft.shared.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Wegwerf-Entity für den Room-Spike aus Phase 2 (siehe KMP_PLAN.md). Sie beantwortet
 * nur die Frage, ob Room auf dem JVM-Desktop läuft, und verschwindet mit dem Umzug
 * der echten Entities aus der Android-App.
 */
@Entity(tableName = "spike_item")
data class SpikeItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subject: String,
    val amountCents: Long,
)
