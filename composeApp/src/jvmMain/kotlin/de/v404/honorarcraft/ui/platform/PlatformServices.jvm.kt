package de.v404.honorarcraft.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import de.v404.honorarcraft.shared.backup.Backup
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import de.v404.honorarcraft.shared.pdf.createInvoicePdf
import de.v404.honorarcraft.shared.data.DesktopDatabase
import de.v404.honorarcraft.shared.desktopAppDataFolder
import de.v404.honorarcraft.shared.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Desktop-Umsetzung über `JFileChooser`.
 *
 * Die Dialoge laufen auf dem Swing-Thread; `Dispatchers.Swing` wäre hier richtig, aber
 * `JFileChooser.showDialog` blockiert ohnehin, deshalb genügt ein Wechsel weg vom
 * Compose-Thread.
 */
class DesktopPlatformServices : PlatformServices {

    override suspend fun pickSignatureImage(): String? = withContext(Dispatchers.IO) {
        val chooser = JFileChooser().apply {
            dialogTitle = "Unterschrift als Bild auswählen"
            fileFilter = FileNameExtensionFilter("Bilddateien", "png", "jpg", "jpeg")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) return@withContext null
        val quelle = chooser.selectedFile ?: return@withContext null

        runCatching {
            // Kopiert, nicht verlinkt: verschiebt der Nutzer das Original, soll die
            // Unterschrift auf der Rechnung trotzdem noch da sein.
            val ziel = File(desktopAppDataFolder(), "unterschrift." + quelle.extension.ifBlank { "png" })
            quelle.copyTo(ziel, overwrite = true)
            ziel.absolutePath
        }.onFailure {
            logError("PlatformServices", "Unterschrift konnte nicht kopiert werden", it)
        }.getOrNull()
    }

    override suspend fun exportBackup(): Result<Long>? {
        val ziel = withContext(Dispatchers.IO) {
            val chooser = JFileChooser().apply {
                dialogTitle = "Sicherung speichern"
                selectedFile = File(Backup.suggestedFileName())
                fileFilter = FileNameExtensionFilter("HonorarCraft-Sicherung", Backup.FILE_EXTENSION)
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) null
            else chooser.selectedFile
        } ?: return null

        return Backup.export(DesktopDatabase.get(), DesktopDatabase.file, ziel.outputStream())
    }

    override suspend fun importBackup(): Result<Int>? {
        val quelle = withContext(Dispatchers.IO) {
            val chooser = JFileChooser().apply {
                dialogTitle = "Sicherung einlesen"
                fileFilter = FileNameExtensionFilter("HonorarCraft-Sicherung", Backup.FILE_EXTENSION)
            }
            if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) null
            else chooser.selectedFile?.takeIf { it.isFile }
        } ?: return null

        return Backup.import(
            databaseFile = DesktopDatabase.file,
            workDir = File(desktopAppDataFolder(), "import"),
            source = quelle.inputStream(),
            closeDatabase = { DesktopDatabase.close() },
        )
    }

    /**
     * Schreibt die Rechnung in den eingestellten Ordner. Fehlt die Angabe, landet sie im
     * Dokumentenordner des Nutzers.
     */
    override suspend fun saveInvoicePdf(
        invoice: InvoiceWithEntries,
        company: CompanyData,
        formattedInvoiceNumber: String,
    ): Result<String> = runCatching {
        val ordner = company.pdfPath.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home"), "Dokumente/Honorarabrechnungen")
        withContext(Dispatchers.IO) {
            ordner.mkdirs()
            val ziel = File(ordner, "Rechnung_$formattedInvoiceNumber.pdf")
            createInvoicePdf(invoice, company, formattedInvoiceNumber, ziel.outputStream())
            ziel.absolutePath
        }
    }.onFailure { logError("PlatformServices", "PDF konnte nicht geschrieben werden", it) }

    /**
     * Auf dem Desktop gibt es keinen Weg, sich selbst sauber neu zu starten, ohne den
     * Startbefehl zu kennen. Die Anwendung wird deshalb beendet — der Nutzer startet sie
     * wieder, die Oberfläche sagt ihm das vorher.
     */
    override fun restartApp() {
        DesktopDatabase.close()
        kotlin.system.exitProcess(0)
    }
}

@Composable
actual fun rememberPlatformServices(): PlatformServices = remember { DesktopPlatformServices() }
