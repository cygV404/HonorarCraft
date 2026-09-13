package de.v404.honorarcraft.shared.pdf

import de.v404.honorarcraft.shared.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import java.io.File
import java.io.OutputStream

/**
 * Desktop-Renderer auf PDFBox.
 *
 * Zwei Dinge unterscheiden ihn vom Android-Renderer:
 * - PDFBox zählt Y **von unten**, das Layout von oben. Jede Koordinate wird gespiegelt.
 * - Die Schrift kommt als Roboto-TTF aus den Ressourcen. Fehlt sie, greift Helvetica — und
 *   damit brechen die Umlaute. Deshalb wird der Fallback protokolliert.
 */
private object Fonts {
    private const val REGULAR = "/font/Roboto-Regular.ttf"
    private const val BOLD = "/font/Roboto-Bold.ttf"

    fun load(doc: PDDocument): Pair<PDFont, PDFont> {
        val regular = laden(doc, REGULAR) ?: PDType1Font.HELVETICA
        val bold = laden(doc, BOLD) ?: PDType1Font.HELVETICA_BOLD
        return regular to bold
    }

    private fun laden(doc: PDDocument, pfad: String): PDFont? =
        runCatching {
            Fonts::class.java.getResourceAsStream(pfad)?.use { PDType0Font.load(doc, it) }
        }.onFailure {
            logError("InvoicePdf", "Schrift $pfad nicht ladbar, Umlaute brechen", it)
        }.getOrNull()
}

private fun groesse(font: PdfFont) =
    if (font == PdfFont.TITLE) PdfMetrics.FONT_SIZE_TITLE else PdfMetrics.FONT_SIZE_REGULAR

actual fun pdfTextMeasurer(): TextMeasurer {
    // Zum Messen genügt ein Wegwerf-Dokument; die Metriken hängen nur an der Schrift.
    val doc = PDDocument()
    val (regular, bold) = Fonts.load(doc)
    return TextMeasurer { text, font ->
        val f = if (font == PdfFont.REGULAR) regular else bold
        runCatching { f.getStringWidth(text) / 1000f * groesse(font) }.getOrDefault(0f)
    }
}

actual suspend fun writeInvoicePdf(model: PdfDocumentModel, out: OutputStream) =
    withContext(Dispatchers.IO) {
        PDDocument().use { doc ->
            val (regular, bold) = Fonts.load(doc)
            fun schrift(f: PdfFont) = if (f == PdfFont.REGULAR) regular else bold

            for (seite in model.pages) {
                val page = PDPage(PDRectangle(PdfMetrics.PAGE_WIDTH, PdfMetrics.PAGE_HEIGHT))
                doc.addPage(page)
                PDPageContentStream(doc, page).use { c ->
                    // Das Layout rechnet von oben, PDFBox von unten.
                    fun oben(y: Float) = PdfMetrics.PAGE_HEIGHT - y

                    for (befehl in seite.commands) when (befehl) {
                        is DrawCommand.Text -> {
                            val f = schrift(befehl.font)
                            val size = groesse(befehl.font)
                            val breite = runCatching {
                                f.getStringWidth(befehl.text) / 1000f * size
                            }.getOrDefault(0f)
                            val x = when (befehl.align) {
                                PdfAlign.LEFT -> befehl.x
                                PdfAlign.RIGHT -> befehl.x - breite
                                PdfAlign.CENTER -> befehl.x - breite / 2f
                            }
                            // Beschneiden wird durch Kürzen ersetzt: PDFBox kann zwar clippen,
                            // aber ein abgeschnittener Buchstabe liest sich schlechter als
                            // ein sichtbar gekürzter Text.
                            val text = befehl.clipWidth?.let { max ->
                                kuerzen(befehl.text, f, size, max)
                            } ?: befehl.text

                            c.beginText()
                            c.setFont(f, size)
                            c.newLineAtOffset(x, oben(befehl.y))
                            c.showText(text)
                            c.endText()
                        }

                        is DrawCommand.Line -> {
                            c.setLineWidth(0.5f)
                            c.setStrokingColor(0.6f)
                            c.moveTo(befehl.x1, oben(befehl.y1))
                            c.lineTo(befehl.x2, oben(befehl.y2))
                            c.stroke()
                        }

                        is DrawCommand.Rect -> {
                            val zustand = PDExtendedGraphicsState().apply {
                                nonStrokingAlphaConstant = befehl.alpha
                            }
                            c.saveGraphicsState()
                            c.setGraphicsStateParameters(zustand)
                            c.setNonStrokingColor(befehl.gray)
                            c.addRect(
                                befehl.x,
                                oben(befehl.y + befehl.height),
                                befehl.width,
                                befehl.height,
                            )
                            c.fill()
                            c.restoreGraphicsState()
                        }

                        is DrawCommand.Image -> {
                            val datei = File(befehl.path)
                            if (!datei.isFile) continue
                            runCatching {
                                val bild = PDImageXObject.createFromFile(datei.absolutePath, doc)
                                c.drawImage(
                                    bild,
                                    befehl.x,
                                    oben(befehl.y + befehl.height),
                                    befehl.width,
                                    befehl.height,
                                )
                            }.onFailure {
                                logError("InvoicePdf", "Unterschrift nicht zeichenbar", it)
                            }
                        }
                    }
                }
            }
            out.use { doc.save(it) }
        }
    }

/** Kürzt [text] mit Auslassungspunkten, bis er in [maxWidth] passt. */
private fun kuerzen(text: String, font: PDFont, size: Float, maxWidth: Float): String {
    fun breite(s: String) = runCatching { font.getStringWidth(s) / 1000f * size }.getOrDefault(0f)
    if (breite(text) <= maxWidth) return text
    var gekuerzt = text
    while (gekuerzt.isNotEmpty() && breite("$gekuerzt…") > maxWidth) {
        gekuerzt = gekuerzt.dropLast(1)
    }
    return "$gekuerzt…"
}
