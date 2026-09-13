package de.v404.honorarcraft.shared.backup

import androidx.room.RoomDatabase
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.DATABASE_VERSION
import de.v404.honorarcraft.shared.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private const val TAG = "Backup"

/**
 * Der Import ist gescheitert, nachdem die Datenbankverbindung bereits geschlossen war.
 * Die Daten sind zurueckgerollt, die App muss aber trotzdem neu starten - sonst laeuft sie
 * auf einer geschlossenen Verbindung weiter.
 */
class NeustartNoetigException(message: String, cause: Throwable?) : Exception(message, cause)

/**
 * Export und Import der kompletten Datenbank in eine vom Nutzer gewählte Datei.
 *
 * Warum das nötig ist: die Android-App hat `allowBackup="false"`, damit Steuernummer, IBAN
 * und Umsätze nicht im Google-Backup landen. Ohne einen eigenen Weg gäbe es damit gar keine
 * Möglichkeit mehr, Daten auf ein neues Gerät zu bekommen oder sich gegen einen Verlust
 * abzusichern. Auf dem Desktop ist es der Weg, eine Sicherung vom Handy einzulesen.
 *
 * Format ist die Room-Datenbankdatei selbst. Das klingt roh, hat aber einen handfesten
 * Vorteil: eine ältere Sicherung wird beim Öffnen automatisch über die Migrationskette in
 * `Migrations` auf den aktuellen Stand gehoben.
 *
 * Die Datei-Auswahl bleibt draußen — auf Android ein `Uri` aus dem Storage Access Framework,
 * auf dem Desktop ein `JFileChooser`. Hier kommen nur die fertigen Ströme an.
 */
object Backup {

    const val MIME_TYPE = "application/octet-stream"
    const val FILE_EXTENSION = "hcbackup"

    /** Dateiname mit Datum, damit mehrere Sicherungen nebeneinander liegen können. */
    fun suggestedFileName(today: LocalDate = LocalDate.now()): String =
        "HonorarCraft_Sicherung_${today.format(DateTimeFormatter.ISO_LOCAL_DATE)}.$FILE_EXTENSION"

    /**
     * Schreibt die Datenbank nach [target]. Der Strom wird geschlossen.
     *
     * Der entscheidende Schritt ist der WAL-Checkpoint: Room läuft im
     * Write-Ahead-Logging-Modus, frische Änderungen stehen also noch in der `-wal`-Datei und
     * nicht in der Hauptdatei. Ohne `TRUNCATE`-Checkpoint würde die Sicherung genau die
     * zuletzt erfassten Positionen nicht enthalten.
     */
    suspend fun export(
        database: AppDatabase,
        databaseFile: File,
        target: OutputStream,
    ): Result<Long> = runCatching {
        checkpoint(database)

        withContext(Dispatchers.IO) {
            check(databaseFile.exists()) { "Datenbankdatei nicht gefunden" }
            target.use { out -> databaseFile.inputStream().use { input -> input.copyTo(out) } }
            databaseFile.length()
        }
    }.onFailure { logError(TAG, "Export fehlgeschlagen", it) }

    /**
     * Ersetzt die Datenbank durch den Inhalt von [source]. Der Strom wird geschlossen.
     *
     * Bewusst vorsichtig: die Datei wird erst in einen Arbeitsordner kopiert und geprüft, und
     * die bestehende Datenbank wird gesichert, bevor irgendetwas überschrieben wird. Schlägt
     * der Austausch mittendrin fehl, wird der alte Stand zurückgerollt — ein misslungener
     * Import darf nicht die Daten kosten, die vorher da waren.
     *
     * Der Aufrufer muss die App danach neu starten: die bestehenden Room-Flows hängen an der
     * alten, inzwischen geschlossenen Verbindung. [closeDatabase] muss die Verbindung
     * tatsächlich schließen, sonst steht die Datei noch offen, während sie getauscht wird.
     */
    suspend fun import(
        databaseFile: File,
        workDir: File,
        source: InputStream,
        closeDatabase: () -> Unit,
    ): Result<Int> = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val kandidat = File(workDir, "import_candidate.db")
        val sicherung = File(workDir, "pre_import_backup.db")
        runCatching {
            // 1. Datei in den Arbeitsordner holen
            source.use { input -> kandidat.outputStream().use { out -> input.copyTo(out) } }

            // 2. Prüfen, bevor irgendetwas angefasst wird
            val version = pruefe(kandidat)

            // 3. Aktuellen Stand sichern und Datenbank schliessen
            closeDatabase()
            if (databaseFile.exists()) databaseFile.copyTo(sicherung, overwrite = true)

            try {
                // 4. Austauschen. -wal und -shm muessen weg, sonst mischt SQLite
                //    das alte Write-Ahead-Log in die neue Datei.
                File(databaseFile.parentFile, databaseFile.name + "-wal").delete()
                File(databaseFile.parentFile, databaseFile.name + "-shm").delete()
                kandidat.copyTo(databaseFile, overwrite = true)
            } catch (e: Throwable) {
                logError(TAG, "Austausch fehlgeschlagen, rolle zurück", e)
                if (sicherung.exists()) sicherung.copyTo(databaseFile, overwrite = true)
                // Die Daten sind gerettet, aber die Verbindung ist zu diesem Zeitpunkt schon
                // geschlossen - ohne Neustart wuerde die App nur noch leere Listen zeigen.
                // Deshalb als eigener Fehlertyp, damit der Aufrufer trotzdem neu startet.
                throw NeustartNoetigException(
                    "Einlesen fehlgeschlagen. Der vorherige Stand wurde wiederhergestellt, " +
                        "die App startet neu.",
                    e
                )
            }
            version
        }.onFailure {
            logError(TAG, "Import fehlgeschlagen", it)
        }.also {
            kandidat.delete()
            sicherung.delete()
        }
    }

    private suspend fun checkpoint(database: RoomDatabase) {
        // PRAGMA wal_checkpoint liefert eine Zeile zurück, deshalb usePrepared statt execSQL.
        database.useWriterConnection { connection ->
            connection.usePrepared("PRAGMA wal_checkpoint(TRUNCATE)") { it.step() }
        }
    }

    /**
     * Stellt sicher, dass [datei] wirklich eine HonorarCraft-Datenbank ist, und liefert ihre
     * Schemaversion.
     *
     * Ohne diese Prüfung würde eine beliebige ausgewählte Datei die Buchhaltung überschreiben
     * und die App beim nächsten Start nur noch abstürzen.
     *
     * Geöffnet wird mit dem gebündelten SQLite — dasselbe auf beiden Plattformen, damit eine
     * Sicherung vom Handy auf dem Desktop nach denselben Regeln geprüft wird.
     */
    private fun pruefe(datei: File): Int {
        val connection = try {
            BundledSQLiteDriver().open(datei.absolutePath)
        } catch (e: Exception) {
            throw IllegalArgumentException("Das ist keine gültige Sicherungsdatei.", e)
        }

        connection.use { db ->
            val tabellen = mutableSetOf<String>()
            try {
                db.prepare("SELECT name FROM sqlite_master WHERE type='table'").use { stmt ->
                    while (stmt.step()) tabellen.add(stmt.getText(0))
                }
            } catch (e: Exception) {
                throw IllegalArgumentException("Das ist keine gültige Sicherungsdatei.", e)
            }

            val fehlend = listOf("invoices", "invoice_entries", "company_data") - tabellen
            require(fehlend.isEmpty()) {
                "Die Datei stammt nicht von HonorarCraft (fehlende Tabellen: ${fehlend.joinToString()})."
            }

            val version = db.prepare("PRAGMA user_version").use { stmt ->
                if (stmt.step()) stmt.getInt(0) else 0
            }
            require(version in 3..DATABASE_VERSION) {
                if (version > DATABASE_VERSION) {
                    "Die Sicherung stammt aus einer neueren App-Version. Bitte zuerst die App aktualisieren."
                } else {
                    "Die Sicherung ist zu alt und kann nicht mehr gelesen werden (Version $version)."
                }
            }
            return version
        }
    }
}

/** Vorgabename der Datenbankdatei, für die Plattformschichten. */
val backupDatabaseFileName: String get() = DATABASE_FILE_NAME
