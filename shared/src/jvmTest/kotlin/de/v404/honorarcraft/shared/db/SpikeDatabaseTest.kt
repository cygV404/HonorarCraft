package de.v404.honorarcraft.shared.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Abnahme für den Room-Spike aus Phase 2: Läuft Room 2.8 mit dem gebündelten
 * SQLite-Treiber auf dem JVM-Desktop — Datei anlegen, schreiben, per Flow lesen?
 */
class SpikeDatabaseTest {

    @Test
    fun schreibtUndLiestUeberEinenFlow() = runTest {
        val dir = Files.createTempDirectory("honorarcraft-spike")
        val dbFile = dir.resolve("spike.db")
        val db = spikeDatabase(dbFile.toAbsolutePath().toString())
        try {
            val dao = db.spikeDao()
            assertEquals(emptyList(), dao.observeAll().first())

            dao.insert(SpikeItem(subject = "Mathematik", amountCents = 2300))
            dao.insert(SpikeItem(subject = "Deutsch", amountCents = 4550))

            val items = dao.observeAll().first()
            assertEquals(listOf("Mathematik", "Deutsch"), items.map { it.subject })
            assertEquals(6850, dao.sumCents())
            assertTrue(items.all { it.id > 0 }, "autoGenerate muss IDs vergeben")
        } finally {
            db.close()
        }
        assertTrue(Files.exists(dbFile), "Room muss die Datenbankdatei auf der Platte anlegen")
    }
}
