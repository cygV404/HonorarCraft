package de.v404.honorarcraft.shared.legacy

import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.createDesktopDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Abnahme für den Importer: Die JSON-Ablage der alten Desktop-Fassung landet vollständig in
 * der Room-Datenbank, und der Altbestand bleibt als Ordner erhalten.
 */
class LegacyDesktopImportTest {

    private val dispatcher = StandardTestDispatcher()
    private val crypto = LegacyVaultCrypto.withRandomKey()
    private lateinit var folder: File
    private lateinit var db: AppDatabase

    @BeforeTest
    fun setUp() {
        folder = Files.createTempDirectory("honorarcraft-legacy").toFile()
        db = createDesktopDatabase(
            path = File(folder, DATABASE_FILE_NAME).absolutePath,
            queryContext = dispatcher,
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher, testBody = block)

    /** Baut einen Altbestand nach, wie ihn die frühere Desktop-Fassung hinterlassen hat. */
    private fun writeLegacyData() {
        File(folder, "company.json").writeText(
            """
            {
              "eduCenter": "Biberach - Ehingen",
              "locationNr": "40 - 381",
              "schoolType": "AsA flex",
              "customerSecondNameOrOrga": "Kolping Berufsbildung gGmbH",
              "customerPlz": "70010",
              "customerCityName": "Stuttgart",
              "customerMailBox": "10 11 61",
              "billerSecondName": "${crypto.encrypt("Dobrodolac")}",
              "billerFirstName": "${crypto.encrypt("Julian")}",
              "billerStreetName": "${crypto.encrypt("Musterweg")}",
              "billerStreetNumber": "${crypto.encrypt("7")}",
              "billerPlzNumber": "${crypto.encrypt("89073")}",
              "billerCityName": "${crypto.encrypt("Ulm")}",
              "taxNumber": "${crypto.encrypt("12/345/678/912")}",
              "billerIban": "${crypto.encrypt("DE12 3456 7810 1112 1314 15")}",
              "billerBIC": "${crypto.encrypt("XXXDEFF")}",
              "hourRate": "23.0",
              "signaturePath": "/pfad/zur/unterschrift.png",
              "pdfPath": "/pfad/zu/rechnungen"
            }
            """.trimIndent()
        )

        File(folder, "invoices").mkdirs()
        File(folder, "invoices/invoice_12.json").writeText("[]")
        File(folder, "invoices/invoice_13.json").writeText(
            """
            [
              { "date": "09.01.2026", "hours": 1.5, "teachingSubject": "Anlagenmechaniker", "id": 1 },
              { "date": " 14.01.2026 ", "hours": 2.7, "teachingSubject": " Mathe ", "id": 2 }
            ]
            """.trimIndent()
        )

        File(folder, "totals").mkdirs()
        File(folder, "totals/year_2025.json").writeText("""{"S": "4353.69"}""")
        File(folder, "totals/year_2026.json").writeText("""{"13": "299.23", "12": "0"}""")

        File(folder, "last_invoice_number.txt").writeText("21")
    }

    @Test
    fun `ohne Altbestand passiert nichts`() = test {
        assertFalse(LegacyDesktopImport.hasLegacyData(folder))
        assertNull(LegacyDesktopImport.run(folder, db, crypto))
    }

    @Test
    fun `uebernimmt Rechnungen, Positionen und Firmendaten`() = test {
        writeLegacyData()

        val report = LegacyDesktopImport.run(folder, db, crypto, LocalDate.of(2026, 9, 13))
        assertNotNull(report)
        assertEquals(2, report.invoices)
        assertEquals(2, report.entries)
        assertTrue(report.companyImported)
        assertEquals("21", report.lastInvoiceNumber)

        assertEquals(listOf("12", "13"), db.invoiceDao().getAllInvoiceNumbers().first().sorted())

        val rechnung = db.invoiceDao().getInvoiceWithEntries("13").first()
        assertNotNull(rechnung)
        assertEquals(2, rechnung.entries.size)

        // 1,5 h muss exakt 1,5 bleiben - über den Double-Wert entstünde 1,5000000000000002.
        assertEquals(BigDecimal("1.5"), rechnung.entries[0].lessonUnits)
        assertEquals(BigDecimal("2.7"), rechnung.entries[1].lessonUnits)
        // Der Satz der alten Fassung galt global und hängt jetzt an jeder Position.
        assertEquals(BigDecimal("23.00"), rechnung.entries[0].rate)

        // Getrimmt, sonst kippt die Jahreszuordnung über date.endsWith(jahr).
        assertEquals("14.01.2026", rechnung.entries[1].date)
        assertEquals("Mathe", rechnung.entries[1].teachingSubject)

        // 1,5 h + 2,7 h = 4,2 h -> 5,60 UE zu 23,00 EUR
        assertEquals(BigDecimal("5.60"), rechnung.totalLessonUnit)
        assertEquals(BigDecimal("128.80"), rechnung.totalSum)
    }

    @Test
    fun `entschluesselt die Identitaetsfelder`() = test {
        writeLegacyData()
        LegacyDesktopImport.run(folder, db, crypto, LocalDate.of(2026, 9, 13))

        val firma = db.companyDao().getCompanyData().first()
        assertNotNull(firma)
        assertEquals("Julian", firma.billerFirstName)
        assertEquals("Dobrodolac", firma.billerSecondName)
        assertEquals("DE12 3456 7810 1112 1314 15", firma.billerIban)
        assertEquals("12/345/678/912", firma.taxNumber)
        // Unverschlüsselte Felder kommen unverändert durch.
        assertEquals("Kolping Berufsbildung gGmbH", firma.customerSecondNameOrOrga)
        assertEquals(BigDecimal("23.00"), firma.rate)
    }

    @Test
    fun `meldet den nicht uebernehmbaren Jahres-Startwert`() = test {
        writeLegacyData()
        val report = LegacyDesktopImport.run(folder, db, crypto, LocalDate.of(2026, 9, 13))!!

        // 2025 hatte einen von Hand eingetragenen Startwert, für den es in Room nichts gibt.
        assertEquals(mapOf("2025" to "4353.69"), report.droppedYearStartValues)
    }

    @Test
    fun `raeumt den Altbestand beiseite statt ihn zu loeschen`() = test {
        writeLegacyData()
        val report = LegacyDesktopImport.run(folder, db, crypto, LocalDate.of(2026, 9, 13))!!

        assertEquals("vor-room-2026-09-13", report.archivedTo.name)
        assertTrue(File(report.archivedTo, "company.json").isFile)
        assertTrue(File(report.archivedTo, "invoices/invoice_13.json").isFile)
        assertTrue(File(report.archivedTo, "last_invoice_number.txt").isFile)

        // Am alten Platz liegt nichts mehr, ein zweiter Lauf tut daher nichts.
        assertFalse(LegacyDesktopImport.hasLegacyData(folder))
        assertNull(LegacyDesktopImport.run(folder, db, crypto))
    }
}
