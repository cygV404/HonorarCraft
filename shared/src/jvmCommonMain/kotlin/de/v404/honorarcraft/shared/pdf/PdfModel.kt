package de.v404.honorarcraft.shared.pdf

/** Schriftrolle. Welche Datei dahintersteht, entscheidet der jeweilige Renderer. */
enum class PdfFont { REGULAR, BOLD, TITLE }

enum class PdfAlign { LEFT, RIGHT, CENTER }

/**
 * Ein einzelner Zeichenbefehl auf einer Seite.
 *
 * Die Koordinaten sind PDF-Punkte, gezählt **von oben links** — so wie die Android-Fassung
 * gerechnet hat. Der PDFBox-Renderer muss sie spiegeln, weil PDFBox von unten links zählt.
 */
sealed interface DrawCommand {

    /**
     * @param clipWidth Wenn gesetzt, wird der Text auf diese Breite beschnitten. Gebraucht für
     *   die Spalte „Unterrichtsfach/Klasse", damit ein langes Fach nicht über den Seitenrand
     *   hinausläuft.
     */
    data class Text(
        val text: String,
        val x: Float,
        val y: Float,
        val font: PdfFont = PdfFont.REGULAR,
        val align: PdfAlign = PdfAlign.LEFT,
        val clipWidth: Float? = null,
    ) : DrawCommand

    data class Line(val x1: Float, val y1: Float, val x2: Float, val y2: Float) : DrawCommand

    /** Zebra-Streifen der Tabelle. [gray] 0 = schwarz, 1 = weiß. */
    data class Rect(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val gray: Float,
        val alpha: Float,
    ) : DrawCommand

    /** Die Unterschrift. Geladen wird die Datei vom Renderer, je Plattform verschieden. */
    data class Image(
        val path: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
    ) : DrawCommand
}

data class PdfPage(val commands: List<DrawCommand>)

data class PdfDocumentModel(
    val pages: List<PdfPage>,
    val fileName: String,
)

/**
 * Textbreite in PDF-Punkten. Wird vom Layout für Zeilenumbruch und rechtsbündigen Text
 * gebraucht und ist der einzige Grund, warum das Layout überhaupt etwas von der Plattform
 * wissen muss.
 */
fun interface TextMeasurer {
    fun width(text: String, font: PdfFont): Float
}

/** Maße der Seite und der Schrift. Absichtlich dieselben Werte wie in der Android-Fassung. */
object PdfMetrics {
    const val PAGE_WIDTH = 595f
    const val PAGE_HEIGHT = 842f
    const val MARGIN = 50f
    const val CONTENT_WIDTH = PAGE_WIDTH - 2 * MARGIN

    const val FONT_SIZE_REGULAR = 11f
    const val FONT_SIZE_TITLE = 18f

    const val LINE_HEIGHT = 15f
    const val BLOCK_GAP = 30f
    const val ROW_HEIGHT = 25f
    const val TABLE_TOP = 80f
    const val FOOTER_BASELINE = PAGE_HEIGHT - 40f

    /** Spaltenanfänge der Positionstabelle, relativ zum linken Rand. */
    const val COL_DATE = 0f
    const val COL_UNITS = 80f
    const val COL_COST = 160f
    const val COL_SUBJECT = 240f

    /** Darstellungsgröße der Unterschrift. */
    const val SIGNATURE_WIDTH = 150f
    const val SIGNATURE_HEIGHT = 50f
}
