package de.v404.honorarcraft.shared.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.math.BigDecimal
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Abnahme für Phase 2: Die aus der Android-App übernommene Datenschicht läuft
 * unverändert auf dem Desktop — Datenbank anlegen, Rechnung mit Positionen schreiben,
 * über die Flows zurücklesen und die Summen rechnen.
 */
class DesktopDatabaseTest {

    private fun newDatabase(): Pair<AppDatabase, java.nio.file.Path> {
        val dir = Files.createTempDirectory("honorarcraft-db")
        val file = dir.resolve(DATABASE_FILE_NAME)
        return createDesktopDatabase(file.toAbsolutePath().toString()) to file
    }

    @Test
    fun legtRechnungMitPositionenAnUndRechnetDieSummen() = runTest {
        val (db, file) = newDatabase()
        try {
            val dao = db.invoiceDao()
            dao.insertInvoice(InvoiceData(invoiceNumber = "2026-001"))
            dao.insertEntry(
                InvoiceEntry(
                    invoiceNumber = "2026-001",
                    date = "15.01.2026",
                    lessonUnits = BigDecimal("55"),
                    teachingSubject = "Mathematik",
                    rate = BigDecimal("23.00"),
                )
            )

            val withEntries = dao.getInvoiceWithEntries("2026-001").first()
            assertNotNull(withEntries)
            assertEquals(1, withEntries.entries.size)

            // 55 Zeitstunden sind 73,33 Unterrichtseinheiten à 23 Euro.
            assertEquals(BigDecimal("73.33"), withEntries.totalLessonUnit)
            assertEquals(BigDecimal("1686.67"), withEntries.totalSum)

            assertTrue(Files.exists(file), "Room muss die Datenbankdatei anlegen")
        } finally {
            db.close()
        }
    }

    @Test
    fun liefertFachvorschlaegeUndBlendetSieAus() = runTest {
        val (db, _) = newDatabase()
        try {
            val dao = db.invoiceDao()
            dao.insertInvoice(InvoiceData(invoiceNumber = "2026-002"))
            listOf("Mathematik", "Mathematik", "Deutsch").forEach { subject ->
                dao.insertEntry(
                    InvoiceEntry(
                        invoiceNumber = "2026-002",
                        date = "15.01.2026",
                        lessonUnits = BigDecimal("2"),
                        teachingSubject = subject,
                    )
                )
            }

            // Nach Häufigkeit sortiert
            assertEquals(listOf("Mathematik", "Deutsch"), dao.getUniqueSubjects().first())

            dao.hideSubject(HiddenSubject("Mathematik"))
            assertEquals(listOf("Deutsch"), dao.getUniqueSubjects().first())

            // Ausblenden darf keine Belege anfassen.
            val entries = dao.getInvoiceWithEntries("2026-002").first()?.entries
            assertEquals(3, entries?.size)

            dao.unhideSubject("Mathematik")
            assertEquals(listOf("Mathematik", "Deutsch"), dao.getUniqueSubjects().first())
        } finally {
            db.close()
        }
    }

    @Test
    fun findetRechnungenUeberDasJahrImDatum() = runTest {
        val (db, _) = newDatabase()
        try {
            val dao = db.invoiceDao()
            dao.insertInvoice(InvoiceData(invoiceNumber = "2025-009"))
            dao.insertInvoice(InvoiceData(invoiceNumber = "2026-003"))
            dao.insertEntry(
                InvoiceEntry(
                    invoiceNumber = "2025-009",
                    date = "20.12.2025",
                    lessonUnits = BigDecimal("1"),
                    teachingSubject = "Physik",
                )
            )
            dao.insertEntry(
                InvoiceEntry(
                    invoiceNumber = "2026-003",
                    date = "05.01.2026",
                    lessonUnits = BigDecimal("1"),
                    teachingSubject = "Physik",
                )
            )

            val year2026 = dao.getInvoicesWithEntriesByYear("2026").first()
            assertEquals(listOf("2026-003"), year2026.map { it.invoice.invoiceNumber })
        } finally {
            db.close()
        }
    }

    @Test
    fun speichertFirmendatenMitBigDecimalSatz() = runTest {
        val (db, _) = newDatabase()
        try {
            val dao = db.companyDao()
            dao.insertCompanyData(CompanyData(billerFirstName = "Julian", rate = BigDecimal("27.50")))

            val loaded = dao.getCompanyData().first()
            assertNotNull(loaded)
            assertEquals("Julian", loaded.billerFirstName)
            // Der TypeConverter muss den Satz verlustfrei durch die Datenbank bringen.
            assertEquals(BigDecimal("27.50"), loaded.rate)
        } finally {
            db.close()
        }
    }
}
