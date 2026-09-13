package de.v404.honorarcraft.ui.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import de.v404.honorarcraft.shared.backup.Backup
import de.v404.honorarcraft.shared.data.AndroidDatabase
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
    private val pickExportTarget: suspend (String) -> Uri?,
    private val pickImportSource: suspend () -> Uri?,
) : PlatformServices {

    private val databaseFile: File get() = context.getDatabasePath(DATABASE_FILE_NAME)

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
