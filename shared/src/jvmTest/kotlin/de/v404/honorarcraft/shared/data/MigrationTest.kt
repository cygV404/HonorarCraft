package de.v404.honorarcraft.shared.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Prüft die Migrationskette von [AppDatabase].
 *
 * Hintergrund: Bis Version 9 lief die App mit `fallbackToDestructiveMigration`, d. h. jedes
 * Schema-Update hat die Rechnungen des Nutzers gelöscht. Diese Tests sichern ab, dass das
 * nicht wieder passiert.
 *
 * Übernommen aus der Android-App, wo er als Instrumentierungstest ein Gerät brauchte. Room-KMP
 * bringt einen `MigrationTestHelper` für die JVM mit — der Test läuft jetzt mit
 * `./gradlew :shared:jvmTest`, also ohne Emulator.
 */
class MigrationTest {

    private val schemas: Path = Path.of("schemas")
    private val ordner: Path = Files.createTempDirectory("honorarcraft-migration")
    private val datenbank: Path = ordner.resolve("migration-test.db")

    private val helper = MigrationTestHelper(
        schemaDirectoryPath = schemas,
        databasePath = datenbank,
        driver = BundledSQLiteDriver(),
        databaseClass = AppDatabase::class,
    )

    @AfterTest
    fun tearDown() {
        ordner.toFile().deleteRecursively()
    }

    private fun migriere(von: Int, befuellen: SQLiteConnection.() -> Unit = {}): SQLiteConnection {
        helper.createDatabase(von).use { it.befuellen() }
        return helper.runMigrationsAndValidate(DATABASE_VERSION, ALL_MIGRATIONS.toList())
    }

    private fun SQLiteConnection.zahl(sql: String): Int =
        prepare(sql).use { if (it.step()) it.getInt(0) else -1 }

    private fun SQLiteConnection.text(sql: String): String? =
        prepare(sql).use { if (it.step()) it.getText(0) else null }

    /**
     * Der kritische Schritt: 4 → 5 verschiebt den Honorarsatz von der Rechnung auf die
     * Position, 5 → 6 baut dafür die Elterntabelle um. Dabei darf das `ON DELETE CASCADE` von
     * `invoice_entries` nicht auslösen.
     */
    @Test
    fun `von 4 nach 11 bleiben Positionen erhalten und erben den Satz`() {
        val db = migriere(4) {
            execSQL("INSERT INTO invoices (invoiceNumber, rate) VALUES ('01', '23.00')")
            execSQL("INSERT INTO invoices (invoiceNumber, rate) VALUES ('02', '31.50')")
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject) " +
                    "VALUES ('01', '04.03.2026', '2.0', 'Mathe')"
            )
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject) " +
                    "VALUES ('01', '05.03.2026', '1.5', 'Deutsch')"
            )
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject) " +
                    "VALUES ('02', '11.04.2026', '3.0', 'Englisch')"
            )
        }
        db.use {
            assertEquals(3, it.zahl("SELECT COUNT(*) FROM invoice_entries"), "Keine Position darf verloren gehen")
            assertEquals(
                "31.50",
                it.text("SELECT rate FROM invoice_entries WHERE teachingSubject = 'Englisch'"),
                "Position muss den Satz ihrer Rechnung geerbt haben",
            )
            assertEquals(2, it.zahl("SELECT COUNT(*) FROM invoices"))
        }
    }

    /** 6 → 9 sind reine Versionssprünge ohne Schemaänderung (gleicher identityHash). */
    @Test
    fun `von 6 nach 11 laeuft durch`() {
        val db = migriere(6) {
            execSQL("INSERT INTO invoices (invoiceNumber) VALUES ('07')")
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject, rate) " +
                    "VALUES ('07', '01.09.2026', '4.0', 'Physik', '28.00')"
            )
        }
        db.use { assertEquals("Physik", it.text("SELECT teachingSubject FROM invoice_entries")) }
    }

    /** Ab 10 gibt es die Tabelle für ausgeblendete Fachvorschläge. */
    @Test
    fun `von 9 nach 11 entsteht die Tabelle fuer ausgeblendete Faecher`() {
        migriere(9).use {
            it.execSQL("INSERT INTO hidden_subjects (name) VALUES ('Mathe')")
            assertEquals(1, it.zahl("SELECT COUNT(*) FROM hidden_subjects"))
        }
    }

    private fun SQLiteConnection.firmaMitEmpfaenger(name: String = "Kolping Berufsbildung gGmbH") {
        execSQL(
            """
            INSERT INTO company_data (id, eduCenter, locationNr, schoolType,
                customerSecondNameOrOrga, customerFirstName, customerPlz, customerCityName,
                customerMailBox, customerStreet, customerStreetNumber,
                billerSecondName, billerFirstName, billerStreetName, billerStreetNumber,
                billerPlzNumber, billerCityName, taxNumber, billerIban, billerBIC,
                rate, signaturePath, pdfPath)
            VALUES (1, 'Biberach - Ehingen', '40 - 381', 'AsA flex',
                '$name', '', '70010', 'Stuttgart',
                '10 11 61', '', '',
                'Mustermann', 'Max', 'Musterstraße', '12',
                '89073', 'Ulm', '12/345/678/912', 'DE12', 'BIC',
                '27.50', '/pfad/sig.png', '/pfad/pdf')
            """.trimIndent()
        )
    }

    /**
     * 11 → 12 ist der zweite Umbau der Elterntabelle `invoices` (nach 5 → 6). Das
     * `ON DELETE CASCADE` von `invoice_entries` darf dabei keine Position kosten, und alles
     * muss danach am neuen Kunden 1 hängen.
     */
    @Test
    fun `von 11 nach 12 wird der Empfaenger Kunde 1 und keine Position geht verloren`() {
        val db = migriere(11) {
            firmaMitEmpfaenger()
            execSQL("INSERT INTO invoices (invoiceNumber) VALUES ('01')")
            execSQL("INSERT INTO invoices (invoiceNumber) VALUES ('02')")
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject, rate) " +
                    "VALUES ('01', '02.09.2026', '3', 'Mathe', '23.00')"
            )
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject, rate) " +
                    "VALUES ('01', '03.09.2026', '1.5', 'Deutsch', '23.00')"
            )
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject, rate) " +
                    "VALUES ('02', '10.10.2026', '2', 'Physik', '31.50')"
            )
        }
        db.use {
            assertEquals(3, it.zahl("SELECT COUNT(*) FROM invoice_entries"), "Keine Position darf verloren gehen")
            assertEquals(2, it.zahl("SELECT COUNT(*) FROM invoices"))
            assertEquals(1, it.zahl("SELECT COUNT(*) FROM customers"))
            assertEquals("Kolping Berufsbildung gGmbH", it.text("SELECT nameOrOrga FROM customers WHERE id = 1"))
            assertEquals("10 11 61", it.text("SELECT mailBox FROM customers WHERE id = 1"))
            assertEquals("Stuttgart", it.text("SELECT cityName FROM customers WHERE id = 1"))
            assertEquals(1, it.zahl("SELECT rate IS NULL FROM customers WHERE id = 1"), "Kein eigener Satz")
            assertEquals(2, it.zahl("SELECT COUNT(*) FROM invoices WHERE customerId = 1"))
            assertEquals(
                2,
                it.zahl(
                    "SELECT COUNT(*) FROM invoices WHERE eduCenter = 'Biberach - Ehingen' " +
                        "AND locationNr = '40 - 381' AND schoolType = 'AsA flex'"
                ),
                "Jede Bestandsrechnung erbt die drei Angaben",
            )
            // Der Rechnungssteller bleibt unangetastet.
            assertEquals("Max", it.text("SELECT billerFirstName FROM company_data"))
            assertEquals("27.50", it.text("SELECT rate FROM company_data"))
            assertEquals("/pfad/pdf", it.text("SELECT pdfPath FROM company_data"))
            // Der Fremdschlüssel greift: ein Kunde mit Rechnungen lässt sich nicht löschen.
            it.execSQL("PRAGMA foreign_keys = ON")
            val geloescht = runCatching { it.execSQL("DELETE FROM customers WHERE id = 1") }
            assertTrue(geloescht.isFailure, "RESTRICT muss das Löschen verhindern")
        }
    }

    /** Frische Installation: nichts zu übernehmen, also auch kein Kunde. */
    @Test
    fun `von 11 nach 12 auf leerer Datenbank entsteht kein Kunde`() {
        migriere(11).use {
            assertEquals(0, it.zahl("SELECT COUNT(*) FROM customers"))
            assertEquals(0, it.zahl("SELECT COUNT(*) FROM company_data"))
        }
    }

    /** Rechnungen ohne Firmendaten: der Kunde entsteht trotzdem, sonst scheitert der Fremdschlüssel. */
    @Test
    fun `von 11 nach 12 ohne Firmendaten bekommen Rechnungen einen Platzhalter-Kunden`() {
        val db = migriere(11) {
            execSQL("INSERT INTO invoices (invoiceNumber) VALUES ('05')")
        }
        db.use {
            assertEquals("Kunde 1", it.text("SELECT nameOrOrga FROM customers WHERE id = 1"))
            assertEquals(1, it.zahl("SELECT customerId FROM invoices WHERE invoiceNumber = '05'"))
            assertEquals("", it.text("SELECT eduCenter FROM invoices"))
        }
    }

    /** Nur der Empfänger ist ausgefüllt, Rechnungen gibt es noch keine. */
    @Test
    fun `von 11 nach 12 wird ein ausgefuellter Empfaenger auch ohne Rechnungen Kunde`() {
        migriere(11) { firmaMitEmpfaenger() }.use {
            assertEquals(1, it.zahl("SELECT COUNT(*) FROM customers"))
        }
    }

    /** Die ganze Kette von der ältesten unterstützten Version. */
    @Test
    fun `von 3 nach 12 laeuft die ganze Kette`() {
        val db = migriere(3) {
            execSQL("INSERT INTO invoices (invoiceNumber, rate) VALUES ('01', '23.00')")
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject) " +
                    "VALUES ('01', '04.03.2026', '2.0', 'Mathe')"
            )
        }
        db.use {
            assertEquals(1, it.zahl("SELECT COUNT(*) FROM invoice_entries"))
            assertEquals(1, it.zahl("SELECT customerId FROM invoices"))
        }
    }

    /**
     * 10 → 11 trimmt Bestandsdaten. Der harte Fall ist `hidden_subjects`: dort ist `name`
     * Primärschlüssel, ein getrimmtes Duplikat darf die Migration nicht sprengen, sondern muss
     * mit dem vorhandenen Eintrag verschmelzen.
     */
    @Test
    fun `von 10 nach 11 wird getrimmt und verschmolzen`() {
        val db = migriere(10) {
            execSQL("INSERT INTO invoices (invoiceNumber) VALUES ('01')")
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject, rate) " +
                    "VALUES ('01', '02.09.2026', '2', 'Mathe', '23.00')"
            )
            execSQL(
                "INSERT INTO invoice_entries (invoiceNumber, date, lessonUnits, teachingSubject, rate) " +
                    "VALUES ('01', '03.09.2026 ', '2', 'Mathe ', '23.00')"
            )
            // getrimmt und ungetrimmt gleichzeitig -> PK-Kollision beim naiven UPDATE
            execSQL("INSERT INTO hidden_subjects (name) VALUES ('Mathe')")
            execSQL("INSERT INTO hidden_subjects (name) VALUES ('Mathe ')")
            execSQL("INSERT INTO hidden_subjects (name) VALUES ('   ')")
        }
        db.use {
            assertEquals(
                2,
                it.zahl("SELECT COUNT(*) FROM invoice_entries WHERE teachingSubject = 'Mathe'"),
                "Beide Positionen müssen auf 'Mathe' vereinheitlicht sein",
            )
            assertEquals(
                1,
                it.zahl("SELECT COUNT(*) FROM invoice_entries WHERE date = '03.09.2026'"),
                "Datum muss getrimmt sein",
            )
            assertEquals(
                1,
                it.zahl("SELECT COUNT(*) FROM hidden_subjects"),
                "Duplikat und Leereintrag müssen verschwunden sein",
            )
            assertEquals("Mathe", it.text("SELECT name FROM hidden_subjects"))
        }
    }
}
