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
import kotlinx.coroutines.flow.Flow

@Dao
interface InvoiceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInvoice(invoice: InvoiceData)

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

    @Delete
    suspend fun deleteInvoice(invoice: InvoiceData)

    @Query("SELECT invoiceNumber FROM invoices")
    fun getAllInvoiceNumbers(): Flow<List<String>>

    @Transaction
    @Query("""
        SELECT DISTINCT i.* FROM invoices i 
        JOIN invoice_entries e ON i.invoiceNumber = e.invoiceNumber 
        WHERE e.date LIKE '%.' || :year
    """)
    fun getInvoicesWithEntriesByYear(year: String): Flow<List<InvoiceWithEntries>>
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
 * Rechnungen und müssen zuerst weg.
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

    @Transaction
    suspend fun clearAllTables() {
        deleteAllEntries()
        deleteAllInvoices()
        deleteAllHiddenSubjects()
        deleteAllCompanyData()
    }
}

/**
 * Aktuelle Schemaversion. Einzige Quelle der Wahrheit – [Backup] prueft eingelesene
 * Sicherungen dagegen, damit eine Datei aus einer neueren App-Version nicht zu
 * einem Absturz beim Start fuehrt.
 */
const val DATABASE_VERSION = 11

@Database(
    entities = [
        InvoiceData::class,
        InvoiceEntry::class,
        CompanyData::class,
        HiddenSubject::class,
    ],
    version = DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun invoiceDao(): InvoiceDao
    abstract fun companyDao(): CompanyDao
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
