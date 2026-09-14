package de.v404.honorarcraft.ui.platform

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.v404.honorarcraft.shared.backup.Backup
import de.v404.honorarcraft.shared.data.AndroidDatabase
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import de.v404.honorarcraft.shared.pdf.createInvoicePdf
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.logError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Android-Umsetzung über das Storage Access Framework.
 *
 * Die Dialoge sind Activity-Ergebnisse und müssen in der Komposition angemeldet werden. Damit
 * die gemeinsame Oberfläche sie trotzdem als schlichte `suspend`-Aufrufe benutzen kann, wartet
 * hier jeweils ein [CompletableDeferred] auf das Ergebnis.
 */
private class AndroidPlatformServices(
    private val context: Context,
    private val pickImage: suspend () -> Uri?,
    private val pickFolder: suspend () -> Uri?,
    private val pickExportTarget: suspend (String) -> Uri?,
    private val pickImportSource: suspend () -> Uri?,
) : PlatformServices {

    private val databaseFile: File get() = context.getDatabasePath(DATABASE_FILE_NAME)

    override suspend fun pickPdfFolder(): String? {
        val baum = pickFolder() ?: return null
        // Ohne dauerhaftes Recht waere die Auswahl nach dem naechsten Start wertlos.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                baum,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure {
            logError("PlatformServices", "Dauerhafter Zugriff auf den Ordner scheiterte", it)
            return null
        }
        return baum.toString()
    }

    override fun describePdfFolder(pdfPath: String): String {
        if (pdfPath.isBlank()) return "Dokumente/HonorarCraft"
        val uri = runCatching { Uri.parse(pdfPath) }.getOrNull() ?: return pdfPath
        // Der Baum-Bezeichner sieht aus wie "primary:Documents/Rechnungen".
        val bezeichner = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return bezeichner?.substringAfter(':')?.ifBlank { null } ?: pdfPath
    }

    override suspend fun pickSignatureImage(): String? {
        val uri = pickImage() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                // Kopiert, weil die Auswahl nur ein kurzlebiges Zugriffsrecht auf eine fremde
                // Datei ist - ein gemerkter Pfad dorthin waere beim naechsten Start wertlos.
                val ziel = File(context.filesDir, "unterschrift.png")
                context.contentResolver.openInputStream(uri).use { input ->
                    checkNotNull(input) { "Datei konnte nicht geöffnet werden" }
                    ziel.outputStream().use { out -> input.copyTo(out) }
                }
                ziel.absolutePath
            }.onFailure {
                logError("PlatformServices", "Unterschrift konnte nicht kopiert werden", it)
            }.getOrNull()
        }
    }

    override suspend fun exportBackup(): Result<Long>? {
        val ziel = pickExportTarget(Backup.suggestedFileName()) ?: return null
        val out = withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(ziel) }
            ?: return Result.failure(IllegalStateException("Zieldatei konnte nicht geöffnet werden"))
        return Backup.export(AndroidDatabase.getDatabase(context), databaseFile, out)
    }

    override suspend fun importBackup(): Result<Int>? {
        val quelle = pickImportSource() ?: return null
        val input = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(quelle) }
            ?: return Result.failure(IllegalStateException("Datei konnte nicht geöffnet werden"))
        return Backup.import(
            databaseFile = databaseFile,
            workDir = File(context.cacheDir, "import"),
            source = input,
            closeDatabase = { AndroidDatabase.closeInstance() },
        )
    }

    /**
     * Legt die Rechnung über den MediaStore in `Dokumente/HonorarCraft` ab.
     *
     * Seit minSdk 29 gibt es nur noch diesen Weg: der früher nötige Zweig für Android 7 bis 9
     * schrieb direkt in den öffentlichen Ordner und brauchte dafür WRITE_EXTERNAL_STORAGE.
     */
    override suspend fun saveInvoicePdf(
        invoice: InvoiceWithEntries,
        company: CompanyData,
        formattedInvoiceNumber: String,
    ): Result<String> = runCatching {
        val dateiname = "Rechnung_$formattedInvoiceNumber.pdf"

        // Hat der Nutzer einen Ordner gewaehlt, wird dorthin geschrieben.
        val gewaehlt = company.pdfPath.takeIf { it.startsWith("content://") }
        if (gewaehlt != null) {
            val baum = Uri.parse(gewaehlt)
            val ordnerUri = DocumentsContract.buildDocumentUriUsingTree(
                baum,
                DocumentsContract.getTreeDocumentId(baum),
            )
            val ziel = DocumentsContract.createDocument(
                context.contentResolver, ordnerUri, "application/pdf", dateiname
            )
            checkNotNull(ziel) { "Datei konnte im gewählten Ordner nicht angelegt werden" }
            val strom = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(ziel)
            }
            checkNotNull(strom) { "Zieldatei konnte nicht geöffnet werden" }
            createInvoicePdf(invoice, company, formattedInvoiceNumber, strom)
            return@runCatching describePdfFolder(gewaehlt)
        }

        val ordner = "${Environment.DIRECTORY_DOCUMENTS}/HonorarCraft"
        val werte = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, dateiname)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, ordner)
        }
        val resolver = context.contentResolver
        val ziel = resolver.insert(MediaStore.Files.getContentUri("external"), werte)
        checkNotNull(ziel) { "Datei konnte im MediaStore nicht angelegt werden" }

        val strom = withContext(Dispatchers.IO) { resolver.openOutputStream(ziel) }
        checkNotNull(strom) { "Zieldatei konnte nicht geöffnet werden" }
        createInvoicePdf(invoice, company, formattedInvoiceNumber, strom)
        ordner
    }.onFailure { logError("PlatformServices", "PDF konnte nicht geschrieben werden", it) }

    override fun restartApp() {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        if (intent == null) {
            logError("PlatformServices", "Kein Launch-Intent gefunden")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { logError("PlatformServices", "Neustart-Intent scheiterte", it) }
    }
}

@Composable
actual fun rememberPlatformServices(): PlatformServices {
    val context = LocalContext.current

    val bildErgebnis = remember { ArrayDeque<CompletableDeferred<Uri?>>() }
    val bildWaehler = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        bildErgebnis.removeFirstOrNull()?.complete(uri)
    }

    val exportErgebnis = remember { ArrayDeque<CompletableDeferred<Uri?>>() }
    val exportWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(Backup.MIME_TYPE)
    ) { uri -> exportErgebnis.removeFirstOrNull()?.complete(uri) }

    val ordnerErgebnis = remember { ArrayDeque<CompletableDeferred<Uri?>>() }
    val ordnerWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> ordnerErgebnis.removeFirstOrNull()?.complete(uri) }

    val importErgebnis = remember { ArrayDeque<CompletableDeferred<Uri?>>() }
    val importWaehler = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> importErgebnis.removeFirstOrNull()?.complete(uri) }

    return remember(context) {
        AndroidPlatformServices(
            context = context.applicationContext,
            pickImage = {
                CompletableDeferred<Uri?>().also { bildErgebnis.addLast(it); bildWaehler.launch("image/*") }.await()
            },
            pickFolder = {
                CompletableDeferred<Uri?>().also { ordnerErgebnis.addLast(it); ordnerWaehler.launch(null) }.await()
            },
            pickExportTarget = { name ->
                CompletableDeferred<Uri?>().also { exportErgebnis.addLast(it); exportWaehler.launch(name) }.await()
            },
            pickImportSource = {
                CompletableDeferred<Uri?>().also {
                    importErgebnis.addLast(it)
                    importWaehler.launch(arrayOf(Backup.MIME_TYPE, "*/*"))
                }.await()
            },
        )
    }
}
