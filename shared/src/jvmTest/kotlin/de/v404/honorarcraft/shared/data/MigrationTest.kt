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
