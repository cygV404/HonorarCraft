package de.v404.honorarcraft.shared

import java.io.File
import java.io.InputStream

/**
 * Legt eine neu gewählte Unterschrift im Datenordner der App ab.
 *
 * Jede Kopie bekommt einen eigenen Namen (`unterschrift-<Zeitstempel>.<endung>`). Früher wurde
 * immer dieselbe Datei überschrieben: die Vorschau lud dann nicht neu, weil sich der Pfad
 * nicht änderte, und „Verwerfen“ holte die alte Unterschrift nicht zurück — sie war schon weg.
 *
 * Danach werden alle anderen `unterschrift*`-Dateien im [zielordner] gelöscht, **außer** der
 * neuen und der unter [gespeicherterPfad] (der in den Firmendaten gespeicherten). Damit bleibt
 * höchstens eine ungespeicherte Auswahl liegen.
 *
 * @return die neue Datei.
 */
fun legeUnterschriftAb(
    quelle: InputStream,
    endung: String,
    zielordner: File,
    gespeicherterPfad: String,
    jetzt: Long = System.currentTimeMillis(),
): File {
    zielordner.mkdirs()
    val ext = endung.lowercase().ifBlank { "png" }
    var stempel = jetzt
    var ziel: File
    do {
        ziel = File(zielordner, "unterschrift-$stempel.$ext")
        stempel++
    } while (ziel.exists())

    quelle.use { input -> ziel.outputStream().use { input.copyTo(it) } }

    val behalten = setOfNotNull(
        ziel.canonicalFile,
        gespeicherterPfad.takeIf { it.isNotBlank() }?.let { File(it).canonicalFile },
    )
    zielordner.listFiles()
        ?.filter { it.isFile && it.name.startsWith("unterschrift") && it.canonicalFile !in behalten }
        ?.forEach { alt ->
            if (!alt.delete()) logError("Unterschrift", "Alte Unterschrift nicht löschbar: $alt")
        }
    return ziel
}
