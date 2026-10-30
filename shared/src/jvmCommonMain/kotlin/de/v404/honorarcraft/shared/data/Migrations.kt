package de.v404.honorarcraft.shared.data

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Explizite Migrationspfade für die Room-Datenbank.
 *
 * Bis Version 9 lief die App mit `fallbackToDestructiveMigration`, d. h. jede Erhöhung
 * von [AppDatabase.version] hat die komplette Datenbank des Nutzers verworfen. Das ist
 * für eine Abrechnungs-App nicht tragbar; ab hier wird jeder Schritt migriert.
 *
 * Die Pfade sind aus den exportierten Schemas unter `shared/schemas/` abgeleitet.
 */

/**
 * Schritt ohne Strukturänderung.
 *
 * Die Schemas 3/4 sowie 6/7/8/9 tragen jeweils denselben `identityHash`
 * (`e6f1985c…` bzw. `65b085cd…`), die Versionsnummer wurde also ohne echte
 * Schemaänderung erhöht. Room verlangt trotzdem einen registrierten Pfad.
 */
private fun noOp(from: Int, to: Int) = object : Migration(from, to) {
    override fun migrate(connection: SQLiteConnection) = Unit
}

val MIGRATION_3_4 = noOp(3, 4)
val MIGRATION_6_7 = noOp(6, 7)
val MIGRATION_7_8 = noOp(7, 8)
val MIGRATION_8_9 = noOp(8, 9)

/**
 * 4 → 5: Der Honorarsatz wandert von der Rechnung auf die einzelne Position,
 * damit eine Rechnung gemischte Sätze enthalten kann. Bestandspositionen erben
 * den Satz ihrer Rechnung.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `invoice_entries` ADD COLUMN `rate` TEXT NOT NULL DEFAULT '23.00'"
        )
        connection.execSQL(
            """
            UPDATE `invoice_entries`
               SET `rate` = (
                   SELECT i.`rate` FROM `invoices` i
                    WHERE i.`invoiceNumber` = `invoice_entries`.`invoiceNumber`
               )
             WHERE EXISTS (
                   SELECT 1 FROM `invoices` i
                    WHERE i.`invoiceNumber` = `invoice_entries`.`invoiceNumber`
               )
            """.trimIndent()
        )
    }
}

/**
 * 5 → 6: `invoices.rate` entfällt, weil der Satz jetzt an der Position hängt.
 *
 * SQLite vor 3.35 (Android < 12) kennt kein `DROP COLUMN`, die Elterntabelle muss
 * also neu gebaut werden.
 *
 * Achtung: `DROP TABLE invoices` löst das `ON DELETE CASCADE` von
 * `invoice_entries` aus und würde alle Positionen mitlöschen. `defer_foreign_keys`
 * verschiebt nur die *Prüfung* der Constraints, nicht die Cascade-Aktion selbst –
 * es reicht hier nicht. Ob Room die Migration mit aktivierten Foreign Keys
 * ausführt, ist ein Implementierungsdetail, auf das man sich nicht verlassen
 * sollte; deshalb werden die Positionen vor dem Umbau in eine Hilfstabelle ohne
 * FK-Beziehung kopiert und danach zurückgespielt. Gegen SQLite verifiziert für
 * `foreign_keys = ON` und `= OFF`.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA defer_foreign_keys = TRUE")

        // Positionen sichern (CREATE TABLE AS SELECT übernimmt keine Constraints)
        connection.execSQL("CREATE TABLE `_entries_backup` AS SELECT * FROM `invoice_entries`")

        // Elterntabelle ohne die Spalte `rate` neu aufbauen
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_invoices` " +
                "(`invoiceNumber` TEXT NOT NULL, PRIMARY KEY(`invoiceNumber`))"
        )
        connection.execSQL(
            "INSERT INTO `_new_invoices` (`invoiceNumber`) " +
                "SELECT `invoiceNumber` FROM `invoices`"
        )
        connection.execSQL("DROP TABLE `invoices`")
        connection.execSQL("ALTER TABLE `_new_invoices` RENAME TO `invoices`")

        // Positionen zurückspielen (Indizes von `invoice_entries` bleiben erhalten,
        // die Tabelle selbst wurde nie gelöscht)
        connection.execSQL("DELETE FROM `invoice_entries`")
        connection.execSQL(
            "INSERT INTO `invoice_entries` " +
                "(`id`, `invoiceNumber`, `date`, `lessonUnits`, `teachingSubject`, `rate`) " +
                "SELECT `id`, `invoiceNumber`, `date`, `lessonUnits`, `teachingSubject`, `rate` " +
                "FROM `_entries_backup`"
        )
        connection.execSQL("DROP TABLE `_entries_backup`")
    }
}

/**
 * 9 → 10: Ausgeblendete Fachvorschläge bekommen eine eigene Tabelle.
 *
 * Vorher löschte das "X" am Vorschlag alle Positionen mit diesem Fach aus allen
 * Rechnungen. Jetzt wird nur noch der Vorschlag ausgeblendet; die Belege bleiben
 * unangetastet. Die Tabelle startet leer – es gibt nichts zu übernehmen.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `hidden_subjects` " +
                "(`name` TEXT NOT NULL, PRIMARY KEY(`name`))"
        )
    }
}

/**
 * 10 → 11: Bestandsdaten von führenden/nachfolgenden Leerzeichen befreien.
 *
 * Eingaben wurden bis hierher ungetrimmt gespeichert. Folgen: `"Mathe"` und
 * `"Mathe "` erschienen als zwei identisch aussehende Vorschläge, ein Leerzeichen
 * im Datum hätte über `date.endsWith(jahr)` den Jahresumsatz verfälscht, und ein
 * ausgeblendetes Fach liess sich nicht mehr zurückholen, weil der Abgleich exakt
 * ist. Ab jetzt wird beim Speichern getrimmt.
 *
 * Keine Schemaänderung, nur Daten – die Tabellenstruktur bleibt identisch.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "UPDATE `invoice_entries` SET `teachingSubject` = TRIM(`teachingSubject`) " +
                "WHERE `teachingSubject` <> TRIM(`teachingSubject`)"
        )
        connection.execSQL(
            "UPDATE `invoice_entries` SET `date` = TRIM(`date`) " +
                "WHERE `date` <> TRIM(`date`)"
        )
        // `name` ist Primärschlüssel: erst die getrimmte Fassung anlegen (vorhandene
        // Treffer ignorieren), dann die ungetrimmte entfernen. Ein direktes UPDATE
        // würde bei bereits existierendem Gegenstück den PK verletzen.
        connection.execSQL(
            "INSERT OR IGNORE INTO `hidden_subjects` (`name`) " +
                "SELECT TRIM(`name`) FROM `hidden_subjects` WHERE `name` <> TRIM(`name`)"
        )
        connection.execSQL("DELETE FROM `hidden_subjects` WHERE `name` <> TRIM(`name`)")
        // Leere Namen sind nach dem Trimmen wertlos
        connection.execSQL("DELETE FROM `hidden_subjects` WHERE `name` = ''")
    }
}

/**
 * 11 → 12: Mehrere Kunden.
 *
 * - Neue Tabelle `customers`. Der bisherige Empfänger aus `company_data` wird Kunde 1.
 * - `invoices` bekommt `customerId` (Fremdschlüssel, `RESTRICT`) und die drei Angaben, die je
 *   Rechnung wechseln (`eduCenter`, `locationNr`, `schoolType`). Alle Bestandsrechnungen hängen
 *   an Kunde 1 und erben die bisherigen Werte aus `company_data`.
 * - `company_data` verliert die zehn verschobenen Spalten.
 *
 * Kunde 1 entsteht nur, wenn es etwas zu übernehmen gibt: Rechnungen oder ausgefüllte
 * Empfängerfelder. Ohne beides (frische Installation) startet die Tabelle leer.
 *
 * `invoices` wird neu gebaut, weil SQLite keine Fremdschlüssel nachträglich anlegen kann. Dabei
 * gilt dasselbe wie in [MIGRATION_5_6]: `DROP TABLE invoices` löst das `ON DELETE CASCADE` von
 * `invoice_entries` aus, deshalb werden die Positionen vorher in eine Hilfstabelle ohne
 * Fremdschlüssel gesichert und danach zurückgespielt.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA defer_foreign_keys = TRUE")

        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `customers` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `nameOrOrga` TEXT NOT NULL, " +
                "`firstName` TEXT NOT NULL, `street` TEXT NOT NULL, `streetNumber` TEXT NOT NULL, " +
                "`plz` TEXT NOT NULL, `mailBox` TEXT NOT NULL, `cityName` TEXT NOT NULL, " +
                "`rate` TEXT, `archived` INTEGER NOT NULL)"
        )

        // Der bisherige Empfänger wird Kunde 1. LEFT JOIN auf eine Ein-Zeilen-Abfrage, damit
        // auch ohne Zeile in company_data (aber mit Rechnungen) ein Kunde entsteht.
        connection.execSQL(
            """
            INSERT INTO `customers`
                (`id`, `nameOrOrga`, `firstName`, `street`, `streetNumber`, `plz`, `mailBox`,
                 `cityName`, `rate`, `archived`)
            SELECT 1,
                   CASE WHEN TRIM(COALESCE(c.`customerSecondNameOrOrga`, '')) = ''
                         AND TRIM(COALESCE(c.`customerFirstName`, '')) = ''
                        THEN 'Kunde 1'
                        ELSE COALESCE(c.`customerSecondNameOrOrga`, '') END,
                   COALESCE(c.`customerFirstName`, ''),
                   COALESCE(c.`customerStreet`, ''),
                   COALESCE(c.`customerStreetNumber`, ''),
                   COALESCE(c.`customerPlz`, ''),
                   COALESCE(c.`customerMailBox`, ''),
                   COALESCE(c.`customerCityName`, ''),
                   NULL,
                   0
              FROM (SELECT 1) AS eins
              LEFT JOIN `company_data` c ON c.`id` = 1
             WHERE EXISTS (SELECT 1 FROM `invoices`)
                OR TRIM(COALESCE(c.`customerSecondNameOrOrga`, '') || COALESCE(c.`customerFirstName`, '')
                        || COALESCE(c.`customerStreet`, '') || COALESCE(c.`customerStreetNumber`, '')
                        || COALESCE(c.`customerPlz`, '') || COALESCE(c.`customerMailBox`, '')
                        || COALESCE(c.`customerCityName`, '')) <> ''
            """.trimIndent()
        )

        // --- invoices neu bauen, Positionen dabei schützen -----------------------------
        connection.execSQL("CREATE TABLE `_entries_backup` AS SELECT * FROM `invoice_entries`")
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_invoices` (`invoiceNumber` TEXT NOT NULL, " +
                "`customerId` INTEGER NOT NULL, `eduCenter` TEXT NOT NULL, " +
                "`locationNr` TEXT NOT NULL, `schoolType` TEXT NOT NULL, " +
                "PRIMARY KEY(`invoiceNumber`), FOREIGN KEY(`customerId`) REFERENCES " +
                "`customers`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )"
        )
        connection.execSQL(
            """
            INSERT INTO `_new_invoices`
                (`invoiceNumber`, `customerId`, `eduCenter`, `locationNr`, `schoolType`)
            SELECT i.`invoiceNumber`, 1,
                   COALESCE(c.`eduCenter`, ''),
                   COALESCE(c.`locationNr`, ''),
                   COALESCE(c.`schoolType`, '')
              FROM `invoices` i
              LEFT JOIN `company_data` c ON c.`id` = 1
            """.trimIndent()
        )
        connection.execSQL("DROP TABLE `invoices`")
        connection.execSQL("ALTER TABLE `_new_invoices` RENAME TO `invoices`")
        connection.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_invoices_customerId` ON `invoices` (`customerId`)"
        )

        connection.execSQL("DELETE FROM `invoice_entries`")
        connection.execSQL(
            "INSERT INTO `invoice_entries` " +
                "(`id`, `invoiceNumber`, `date`, `lessonUnits`, `teachingSubject`, `rate`) " +
                "SELECT `id`, `invoiceNumber`, `date`, `lessonUnits`, `teachingSubject`, `rate` " +
                "FROM `_entries_backup`"
        )
        connection.execSQL("DROP TABLE `_entries_backup`")

        // --- company_data ohne die verschobenen Spalten --------------------------------
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `_new_company_data` (`id` INTEGER NOT NULL, " +
                "`billerSecondName` TEXT NOT NULL, `billerFirstName` TEXT NOT NULL, " +
                "`billerStreetName` TEXT NOT NULL, `billerStreetNumber` TEXT NOT NULL, " +
                "`billerPlzNumber` TEXT NOT NULL, `billerCityName` TEXT NOT NULL, " +
                "`taxNumber` TEXT NOT NULL, `billerIban` TEXT NOT NULL, `billerBIC` TEXT NOT NULL, " +
                "`rate` TEXT NOT NULL, `signaturePath` TEXT NOT NULL, `pdfPath` TEXT NOT NULL, " +
                "PRIMARY KEY(`id`))"
        )
        connection.execSQL(
            "INSERT INTO `_new_company_data` (`id`, `billerSecondName`, `billerFirstName`, " +
                "`billerStreetName`, `billerStreetNumber`, `billerPlzNumber`, `billerCityName`, " +
                "`taxNumber`, `billerIban`, `billerBIC`, `rate`, `signaturePath`, `pdfPath`) " +
                "SELECT `id`, `billerSecondName`, `billerFirstName`, `billerStreetName`, " +
                "`billerStreetNumber`, `billerPlzNumber`, `billerCityName`, `taxNumber`, " +
                "`billerIban`, `billerBIC`, `rate`, `signaturePath`, `pdfPath` FROM `company_data`"
        )
        connection.execSQL("DROP TABLE `company_data`")
        connection.execSQL("ALTER TABLE `_new_company_data` RENAME TO `company_data`")
    }
}

/** Alle Migrationen in einer Liste – auch für den Migrationstest verwendbar. */
val ALL_MIGRATIONS = arrayOf(
    MIGRATION_3_4,
    MIGRATION_4_5,
    MIGRATION_5_6,
    MIGRATION_6_7,
    MIGRATION_7_8,
    MIGRATION_8_9,
    MIGRATION_9_10,
    MIGRATION_10_11,
    MIGRATION_11_12,
)
