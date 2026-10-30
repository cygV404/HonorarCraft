package de.v404.honorarcraft.shared.data

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.TypeConverters
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    /**
     * Legt eine Rechnung an; existiert die Nummer schon, passiert nichts.
     *
     * Nie `REPLACE`: SQLite ersetzt durch Löschen und Neuanlegen, und das `ON DELETE CASCADE`
     * von `invoice_entries` nähme dabei alle Positionen der Rechnung mit. Ändern geht über
     * [setInvoiceCustomer] und [setInvoiceDetails].
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInvoice(invoice: InvoiceData)

    /**
     * Hängt eine Rechnung an einen anderen Kunden. Nur diese Spalte: ein `@Update` der ganzen
     * Zeile aus einem älteren Stand würde gleichzeitig geänderte Angaben zurückdrehen.
     */
    @Query("UPDATE invoices SET customerId = :customerId WHERE invoiceNumber = :invoiceNumber")
    suspend fun setInvoiceCustomer(invoiceNumber: String, customerId: Long)

    /** Bildungszentrum, Standortnummer und Schulart einer Rechnung; den Kunden lässt es stehen. */
    @Query(
        """
        UPDATE invoices SET eduCenter = :eduCenter, locationNr = :locationNr, schoolType = :schoolType
         WHERE invoiceNumber = :invoiceNumber
        """
    )
    suspend fun setInvoiceDetails(
        invoiceNumber: String,
        eduCenter: String,
        locationNr: String,
        schoolType: String,
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: InvoiceEntry)

    @Delete
    suspend fun deleteEntries(entries: List<InvoiceEntry>)

    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber")
    suspend fun getInvoice(invoiceNumber: String): InvoiceData?

    @Transaction
    @Query("SELECT * FROM invoices WHERE invoiceNumber = :invoiceNumber")
    fun getInvoiceWithEntries(invoiceNumber: String): Flow<InvoiceWithEntries?>

    @Query(
        """
        SELECT teachingSubject FROM invoice_entries
         WHERE teachingSubject != ''
           AND teachingSubject NOT IN (SELECT name FROM hidden_subjects)
         GROUP BY teachingSubject
         ORDER BY COUNT(*) DESC
        """
    )
    fun getUniqueSubjects(): Flow<List<String>>

    /** Blendet einen Fachvorschlag aus. Rührt die Rechnungspositionen nicht an. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun hideSubject(subject: HiddenSubject)

    /** Holt einen ausgeblendeten Vorschlag zurück, sobald das Fach wieder gebucht wird. */
    @Query("DELETE FROM hidden_subjects WHERE name = :subject")
    suspend fun unhideSubject(subject: String)

    @Query("DELETE FROM invoices WHERE invoiceNumber = :invoiceNumber")
    suspend fun deleteInvoice(invoiceNumber: String)

    /** Die zuletzt angelegte Rechnung eines Kunden — Vorlage für die Rechnungsangaben. */
    @Query("SELECT * FROM invoices WHERE customerId = :customerId ORDER BY rowid DESC LIMIT 1")
    suspend fun getLastInvoiceOfCustomer(customerId: Long): InvoiceData?

    @Query("SELECT invoiceNumber FROM invoices")
    fun getAllInvoiceNumbers(): Flow<List<String>>

    /** Rechnungen mit Positionen im Jahr [year]; [customerId] `null` heißt: alle Kunden. */
    @Transaction
    @Query("""
        SELECT DISTINCT i.* FROM invoices i
        JOIN invoice_entries e ON i.invoiceNumber = e.invoiceNumber
        WHERE e.date LIKE '%.' || :year
          AND (:customerId IS NULL OR i.customerId = :customerId)
    """)
    fun getInvoicesWithEntriesByYear(year: String, customerId: Long?): Flow<List<InvoiceWithEntries>>
}

@Dao
interface CustomerDao {
    /** Alle Kunden, aktive zuerst, dann alphabetisch. */
    @Query(
        "SELECT * FROM customers ORDER BY archived, nameOrOrga COLLATE NOCASE, firstName COLLATE NOCASE"
    )
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun getCustomer(id: Long): Customer?

    /** Legt an (`id = 0`) oder ändert; liefert die id. Kein `REPLACE` — siehe [InvoiceDao.insertInvoice]. */
    @Upsert
    suspend fun upsertCustomer(customer: Customer): Long

    @Query("SELECT COUNT(*) FROM invoices WHERE customerId = :id")
    suspend fun countInvoices(id: Long): Int

    /** Nur für Kunden ohne Rechnungen; sonst verweigert der Fremdschlüssel. */
    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomer(id: Long)
}

@Dao
interface CompanyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompanyData(companyData: CompanyData)

    @Query("SELECT * FROM company_data WHERE id = 1")
    fun getCompanyData(): Flow<CompanyData?>
}

/**
 * Löschen aller Nutzdaten für das Zurücksetzen auf Werkseinstellungen.
 *
 * Room-KMP kennt kein `clearAllTables()`; die Tabellen werden deshalb einzeln geleert.
 * Die Reihenfolge ist nicht beliebig: die Positionen hängen per Fremdschlüssel an den
 * Rechnungen, die Rechnungen an den Kunden.
 */
@Dao
interface MaintenanceDao {
    @Query("DELETE FROM invoice_entries")
    suspend fun deleteAllEntries()

    @Query("DELETE FROM invoices")
    suspend fun deleteAllInvoices()

    @Query("DELETE FROM hidden_subjects")
    suspend fun deleteAllHiddenSubjects()

    @Query("DELETE FROM company_data")
    suspend fun deleteAllCompanyData()

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()

    @Transaction
    suspend fun clearAllTables() {
        deleteAllEntries()
        deleteAllInvoices()
        deleteAllCustomers()
        deleteAllHiddenSubjects()
        deleteAllCompanyData()
    }
}

/**
 * Aktuelle Schemaversion. Einzige Quelle der Wahrheit – [Backup] prueft eingelesene
 * Sicherungen dagegen, damit eine Datei aus einer neueren App-Version nicht zu
 * einem Absturz beim Start fuehrt.
 */
const val DATABASE_VERSION = 12

@Database(
    entities = [
        InvoiceData::class,
        InvoiceEntry::class,
        CompanyData::class,
        HiddenSubject::class,
        Customer::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun invoiceDao(): InvoiceDao
    abstract fun companyDao(): CompanyDao
    abstract fun customerDao(): CustomerDao
    abstract fun maintenanceDao(): MaintenanceDao
}

/**
 * Room erzeugt die `actual`-Implementierung je Target selbst; deshalb steht hier ein
 * `expect object` ohne eigenes `actual`.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}

/** Dateiname der Datenbank. Auf beiden Plattformen derselbe. */
const val DATABASE_FILE_NAME = "honorarcraft_database"
