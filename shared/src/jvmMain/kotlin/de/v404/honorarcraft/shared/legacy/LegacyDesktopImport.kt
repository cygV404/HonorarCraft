package de.v404.honorarcraft.shared.legacy

import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.Constants
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.InvoiceEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Übernimmt die Daten der alten, JSON-gestützten Desktop-Fassung in die Room-Datenbank.
 *
 * Läuft einmalig beim ersten Start der umgestellten App. Danach wird der Altbestand in einen
 * Unterordner verschoben statt gelöscht — beim nächsten Start findet der Importer nichts mehr
 * vor und tut nichts.
 */
object LegacyDesktopImport {

    private val json = Json { ignoreUnknownKeys = true }

    /** Die alte Desktop-Position: Stunden als `Double`, kein Satz je Position. */
    @Serializable
    private data class LegacyEntry(
        val date: String = "",
        val hours: Double = 0.0,
        val teachingSubject: String = "",
        val id: Long = 0,
    )

    /** Die alte `company.json`. Identitätsfelder liegen darin verschlüsselt. */
    @Serializable
    private data class LegacyCompany(
        val eduCenter: String = "",
        val locationNr: String = "",
        val schoolType: String = "",
        val customerSecondNameOrOrga: String = "",
        val customerFirstName: String = "",
        val customerPlz: String = "",
        val customerCityName: String = "",
        val customerMailBox: String = "",
        val customerStreet: String = "",
        val customerStreetNumber: String = "",
        val billerSecondName: String = "",
        val billerFirstName: String = "",
        val billerStreetName: String = "",
        val billerStreetNumber: String = "",
        val billerPlzNumber: String = "",
        val billerCityName: String = "",
        val taxNumber: String = "",
        val billerIban: String = "",
        val billerBIC: String = "",
        val hourRate: String = "",
        val signaturePath: String = "",
        val pdfPath: String = "",
    )

    data class Report(
        val invoices: Int,
        val entries: Int,
        val companyImported: Boolean,
        val lastInvoiceNumber: String?,
        /** Jahr → manuell eingetragener Startwert, der **nicht** übernommen werden konnte. */
        val droppedYearStartValues: Map<String, String>,
        val archivedTo: File,
    )

    /** Liegt im Ordner noch etwas, das übernommen werden müsste? */
    fun hasLegacyData(folder: File): Boolean =
        File(folder, "company.json").isFile ||
            File(folder, "invoices").isDirectory ||
            File(folder, "totals").isDirectory ||
            File(folder, "last_invoice_number.txt").isFile

    /**
     * Übernimmt alles Vorgefundene und verschiebt den Altbestand anschließend nach
     * `vor-room-<datum>/`.
     *
     * @return `null`, wenn es nichts zu übernehmen gab.
     */
    suspend fun run(
        folder: File,
        database: AppDatabase,
        crypto: LegacyVaultCrypto = LegacyVaultCrypto.fromPreferences(),
        today: LocalDate = LocalDate.now(),
    ): Report? {
        if (!hasLegacyData(folder)) return null

        val invoiceDao = database.invoiceDao()
        val companyDao = database.companyDao()

        // --- Firmendaten ---------------------------------------------------------
        val companyFile = File(folder, "company.json")
        val legacyCompany = companyFile.takeIf { it.isFile && it.length() > 0 }
            ?.let { runCatching { json.decodeFromString<LegacyCompany>(it.readText()) }.getOrNull() }

        val rate = legacyCompany?.hourRate
            ?.replace(",", ".")
            ?.toBigDecimalOrNull()
            ?.setScale(2, java.math.RoundingMode.HALF_UP)
            ?: Constants.DEFAULT_RATE

        if (legacyCompany != null) {
            companyDao.insertCompanyData(
                CompanyData(
                    id = 1,
                    eduCenter = legacyCompany.eduCenter,
                    locationNr = legacyCompany.locationNr,
                    schoolType = legacyCompany.schoolType,
                    customerSecondNameOrOrga = legacyCompany.customerSecondNameOrOrga,
                    customerFirstName = legacyCompany.customerFirstName,
                    customerPlz = legacyCompany.customerPlz,
                    customerCityName = legacyCompany.customerCityName,
                    customerMailBox = legacyCompany.customerMailBox,
                    customerStreet = legacyCompany.customerStreet,
                    customerStreetNumber = legacyCompany.customerStreetNumber,
                    // Diese Felder lagen verschlüsselt in der Datei.
                    billerSecondName = crypto.decrypt(legacyCompany.billerSecondName),
                    billerFirstName = crypto.decrypt(legacyCompany.billerFirstName),
                    billerStreetName = crypto.decrypt(legacyCompany.billerStreetName),
                    billerStreetNumber = crypto.decrypt(legacyCompany.billerStreetNumber),
                    billerPlzNumber = crypto.decrypt(legacyCompany.billerPlzNumber),
                    billerCityName = crypto.decrypt(legacyCompany.billerCityName),
                    taxNumber = crypto.decrypt(legacyCompany.taxNumber),
                    billerIban = crypto.decrypt(legacyCompany.billerIban),
                    billerBIC = crypto.decrypt(legacyCompany.billerBIC),
                    rate = rate,
                    signaturePath = legacyCompany.signaturePath,
                    pdfPath = legacyCompany.pdfPath,
                )
            )
        }

        // --- Rechnungen und Positionen -------------------------------------------
        var invoiceCount = 0
        var entryCount = 0
        val invoicesDir = File(folder, "invoices")
        val invoiceFiles = invoicesDir.listFiles { f: File ->
            f.isFile && f.name.startsWith("invoice_") && f.name.endsWith(".json")
        }.orEmpty().sortedBy { it.name }

        for (file in invoiceFiles) {
            val number = file.name.removePrefix("invoice_").removeSuffix(".json").trim()
            if (number.isEmpty()) continue

            val entries = runCatching { json.decodeFromString<List<LegacyEntry>>(file.readText()) }
                .getOrDefault(emptyList())

            invoiceDao.insertInvoice(InvoiceData(invoiceNumber = number))
            invoiceCount++

            for (entry in entries) {
                invoiceDao.insertEntry(
                    InvoiceEntry(
                        invoiceNumber = number,
                        // Getrimmt wie in Migration 10 → 11: ein Leerzeichen im Datum
                        // würde die Jahreszuordnung über date.endsWith(jahr) kippen.
                        date = entry.date.trim(),
                        // Über den String, nicht über den Double-Wert: BigDecimal(1.5)
                        // liefert sonst die binäre Näherung statt genau 1,5.
                        lessonUnits = BigDecimal(entry.hours.toString()),
                        teachingSubject = entry.teachingSubject.trim(),
                        // Die alte Fassung kannte nur einen Satz für alles.
                        rate = rate,
                    )
                )
                entryCount++
            }
        }

        // --- Jahres-Startwerte ----------------------------------------------------
        // Die Rechnungssummen in totals/ werden nicht übernommen: sie lassen sich aus den
        // Positionen wieder ausrechnen. Der reservierte Schlüssel "S" ist etwas anderes -
        // ein von Hand eingetragener Startwert, für den es im Room-Modell keine Entsprechung
        // gibt. Er wird gemeldet, damit der Verlust nicht unbemerkt bleibt.
        val dropped = linkedMapOf<String, String>()
        File(folder, "totals").listFiles { f: File ->
            f.isFile && f.name.startsWith("year_") && f.name.endsWith(".json")
        }.orEmpty().sortedBy { it.name }.forEach { file ->
            val year = file.name.removePrefix("year_").removeSuffix(".json")
            val map = runCatching { json.decodeFromString<Map<String, String>>(file.readText()) }
                .getOrDefault(emptyMap())
            map["S"]?.takeIf { it.toBigDecimalOrNull()?.signum() != 0 }?.let { dropped[year] = it }
        }

        val lastNumber = File(folder, "last_invoice_number.txt")
            .takeIf { it.isFile }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        // --- Altbestand beiseiteräumen -------------------------------------------
        val archive = File(folder, "vor-room-" + today.format(DateTimeFormatter.ISO_LOCAL_DATE))
        archive.mkdirs()
        listOf("company.json", "invoices", "totals", "last_invoice_number.txt").forEach { name ->
            val source = File(folder, name)
            if (source.exists()) source.renameTo(File(archive, name))
        }

        return Report(
            invoices = invoiceCount,
            entries = entryCount,
            companyImported = legacyCompany != null,
            lastInvoiceNumber = lastNumber,
            droppedYearStartValues = dropped,
            archivedTo = archive,
        )
    }
}
