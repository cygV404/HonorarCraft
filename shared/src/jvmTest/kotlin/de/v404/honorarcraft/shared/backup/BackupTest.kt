package de.v404.honorarcraft.shared.backup

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.InvoiceEntry
import de.v404.honorarcraft.shared.data.createDesktopDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Abnahme für Phase 6: Sicherung schreiben und wieder einlesen, und zwar so, dass ein
 * misslungener Import nicht die Daten kostet, die vorher da waren.
 */
class BackupTest {

    private lateinit var dir: File
    private lateinit var dbFile: File
    private var db: AppDatabase? = null

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("honorarcraft-backup").toFile()
        dbFile = File(dir, DATABASE_FILE_NAME)
        db = createDesktopDatabase(dbFile.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        db?.close()
    }

    private suspend fun fuelleDatenbank(nummer: String) {
        val dao = db!!.invoiceDao()
        dao.insertInvoice(InvoiceData(nummer))
        dao.insertEntry(
            InvoiceEntry(
                invoiceNumber = nummer,
                date = "02.09.2026",
                lessonUnits = BigDecimal("3"),
                teachingSubject = "Mathe",
            )
        )
    }

    private fun neuOeffnen() {
        db?.close()
        db = createDesktopDatabase(dbFile.absolutePath)
    }

    @Test
    fun `Sicherung schreiben und wieder einlesen`() = runTest {
        fuelleDatenbank("07")

        val sicherung = File(dir, "sicherung.hcbackup")
        val groesse = Backup.export(db!!, dbFile, sicherung.outputStream()).getOrThrow()
        assertTrue(groesse > 0, "Die Sicherung darf nicht leer sein")
        assertTrue(sicherung.length() > 0)

        // Nach dem WAL-Checkpoint muss die Position wirklich in der Datei stehen,
        // nicht nur im Write-Ahead-Log.
        db!!.invoiceDao().deleteInvoice(InvoiceData("07"))
        assertEquals(emptyList(), db!!.invoiceDao().getAllInvoiceNumbers().first())

        val version = Backup.import(
            databaseFile = dbFile,
            workDir = File(dir, "arbeit"),
            source = sicherung.inputStream(),
            closeDatabase = { db!!.close() },
        ).getOrThrow()
        assertEquals(11, version)

        neuOeffnen()
        assertEquals(listOf("07"), db!!.invoiceDao().getAllInvoiceNumbers().first())
        assertEquals(1, db!!.invoiceDao().getInvoiceWithEntries("07").first()?.entries?.size)
    }

    @Test
    fun `eine fremde Datei wird abgewiesen und die Daten bleiben`() = runTest {
        fuelleDatenbank("07")
        val fremd = File(dir, "urlaubsfoto.hcbackup").apply { writeText("kein SQLite") }

        val ergebnis = Backup.import(
            databaseFile = dbFile,
            workDir = File(dir, "arbeit"),
            source = fremd.inputStream(),
            closeDatabase = { db!!.close() },
        )
        assertTrue(ergebnis.isFailure)
        assertTrue(
            ergebnis.exceptionOrNull()?.message?.contains("keine gültige Sicherungsdatei") == true,
            "Die Meldung muss verständlich sein, war: ${ergebnis.exceptionOrNull()?.message}"
        )

        // Entscheidend: die Prüfung schlägt fehl, *bevor* irgendetwas angefasst wird.
        neuOeffnen()
        assertEquals(listOf("07"), db!!.invoiceDao().getAllInvoiceNumbers().first())
    }

    @Test
    fun `eine SQLite-Datei ohne unsere Tabellen wird abgewiesen`() = runTest {
        fuelleDatenbank("07")

        // Eine echte, aber fremde SQLite-Datenbank.
        val fremdeDb = File(dir, "fremd.db")
        val verbindung = BundledSQLiteDriver().open(fremdeDb.absolutePath)
        verbindung.execSQL("CREATE TABLE rezepte (name TEXT)")
        verbindung.close()

        val ergebnis = Backup.import(
            databaseFile = dbFile,
            workDir = File(dir, "arbeit"),
            source = fremdeDb.inputStream(),
            closeDatabase = { db!!.close() },
        )
        assertTrue(ergebnis.isFailure)
        assertTrue(
            ergebnis.exceptionOrNull()?.message?.contains("stammt nicht von HonorarCraft") == true,
            "war: ${ergebnis.exceptionOrNull()?.message}"
        )

        neuOeffnen()
        assertEquals(listOf("07"), db!!.invoiceDao().getAllInvoiceNumbers().first())
    }

    /**
     * Der eigentliche Grund, warum das Sicherungsformat die rohe Datenbankdatei ist: eine
     * Sicherung aus einer älteren App-Fassung wird beim Einlesen über die Migrationskette auf
     * den aktuellen Stand gehoben, statt abgewiesen zu werden.
     */
    @Test
    fun `eine aeltere Sicherung wird ueber die Migrationskette gehoben`() = runTest {
        // Eine Sicherung im Schemastand 4 bauen - damals hing der Honorarsatz noch an der
        // Rechnung statt an der Position.
        val altOrdner = Files.createTempDirectory("hc-alt")
        val alteDatei = altOrdner.resolve("alt.db")
        val helper = androidx.room.testing.MigrationTestHelper(
            schemaDirectoryPath = java.nio.file.Path.of("schemas"),
            databasePath = alteDatei,
            driver = BundledSQLiteDriver(),
            databaseClass = AppDatabase::class,
        )
        helper.createDatabase(4).use { alt ->
            alt.execSQL("INSERT INTO invoices (invoiceNumber, rate) VALUES ('05', '31.50')")
            alt.execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject) " +
                    "VALUES ('05', '02.09.2026', '3.0', 'Englisch')"
            )
        }

        val version = Backup.import(
            databaseFile = dbFile,
            workDir = File(dir, "arbeit"),
            source = alteDatei.toFile().inputStream(),
            closeDatabase = { db!!.close() },
        ).getOrThrow()
        assertEquals(4, version, "Die Sicherung wird mit ihrer eigenen Version gemeldet")

        // Beim Öffnen hebt Room sie auf den aktuellen Stand.
        neuOeffnen()
        val rechnung = db!!.invoiceDao().getInvoiceWithEntries("05").first()
        assertEquals(1, rechnung?.entries?.size)
        assertEquals(
            BigDecimal("31.50"),
            rechnung?.entries?.first()?.rate,
            "Die Position muss den Satz ihrer Rechnung geerbt haben",
        )
        altOrdner.toFile().deleteRecursively()
    }

    @Test
    fun `der Dateiname traegt das Datum`() {
        assertEquals(
            "HonorarCraft_Sicherung_2026-09-13.hcbackup",
            Backup.suggestedFileName(java.time.LocalDate.of(2026, 9, 13))
        )
    }
}
