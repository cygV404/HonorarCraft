package de.v404.honorarcraft.shared.pdf

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import de.v404.honorarcraft.shared.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * Android-Renderer auf `android.graphics.pdf.PdfDocument`.
 *
 * Anders als PDFBox zählt Canvas Y bereits von oben, die Koordinaten des Layouts passen also
 * unverändert.
 */
private fun paint(font: PdfFont): Paint = Paint().apply {
    isAntiAlias = true
    color = android.graphics.Color.BLACK
    when (font) {
        PdfFont.REGULAR -> textSize = PdfMetrics.FONT_SIZE_REGULAR
        PdfFont.BOLD -> {
            textSize = PdfMetrics.FONT_SIZE_REGULAR
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        PdfFont.TITLE -> {
            textSize = PdfMetrics.FONT_SIZE_TITLE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
    }
}

actual fun pdfTextMeasurer(): TextMeasurer {
    val cache = PdfFont.entries.associateWith { paint(it) }
    return TextMeasurer { text, font -> cache.getValue(font).measureText(text) }
}

actual suspend fun writeInvoicePdf(model: PdfDocumentModel, out: OutputStream) =
    withContext(Dispatchers.IO) {
        val dokument = PdfDocument()
        val bilder = mutableListOf<Bitmap>()
        try {
            val stifte = PdfFont.entries.associateWith { paint(it) }
            val linienStift = Paint().apply {
                color = android.graphics.Color.GRAY
                strokeWidth = 0.5f
            }

            model.pages.forEachIndexed { index, seite ->
                val info = PdfDocument.PageInfo.Builder(
                    PdfMetrics.PAGE_WIDTH.toInt(),
                    PdfMetrics.PAGE_HEIGHT.toInt(),
                    index + 1,
                ).create()
                val seiteImDokument = dokument.startPage(info)
                val canvas: Canvas = seiteImDokument.canvas

                for (befehl in seite.commands) when (befehl) {
                    is DrawCommand.Text -> {
                        val stift = stifte.getValue(befehl.font)
                        stift.textAlign = when (befehl.align) {
                            PdfAlign.LEFT -> Paint.Align.LEFT
                            PdfAlign.RIGHT -> Paint.Align.RIGHT
                            PdfAlign.CENTER -> Paint.Align.CENTER
                        }
                        if (befehl.clipWidth != null) {
                            canvas.save()
                            canvas.clipRect(
                                befehl.x,
                                befehl.y - 15f,
                                befehl.x + befehl.clipWidth,
                                befehl.y + 10f,
                            )
                            canvas.drawText(befehl.text, befehl.x, befehl.y, stift)
                            canvas.restore()
                        } else {
                            canvas.drawText(befehl.text, befehl.x, befehl.y, stift)
                        }
                    }

                    is DrawCommand.Line ->
                        canvas.drawLine(befehl.x1, befehl.y1, befehl.x2, befehl.y2, linienStift)

                    is DrawCommand.Rect -> {
                        val grau = (befehl.gray * 255).toInt().coerceIn(0, 255)
                        val flaeche = Paint().apply {
                            color = android.graphics.Color.rgb(grau, grau, grau)
                            alpha = (befehl.alpha * 255).toInt().coerceIn(0, 255)
                        }
                        canvas.drawRect(
                            befehl.x,
                            befehl.y,
                            befehl.x + befehl.width,
                            befehl.y + befehl.height,
                            flaeche,
                        )
                    }

                    is DrawCommand.Image -> {
                        val bild = ladeUnterschrift(befehl.path)
                        if (bild != null) {
                            bilder += bild
                            canvas.drawBitmap(
                                bild,
                                null,
                                RectF(
                                    befehl.x,
                                    befehl.y,
                                    befehl.x + befehl.width,
                                    befehl.y + befehl.height,
                                ),
                                null,
                            )
                        }
                    }
                }

                dokument.finishPage(seiteImDokument)
            }

            out.use { dokument.writeTo(it) }
        } finally {
            // Muss auch laufen, wenn beim Zeichnen etwas schiefgeht: sonst leckt der native
            // Puffer des PdfDocument.
            bilder.forEach { it.recycle() }
            dokument.close()
        }
    }

/** Zielbreite der Unterschrift: 150 pt Darstellung bei 3-facher Auflösung. */
private const val SIGNATURE_MAX_WIDTH_PX = 450

private fun ladeUnterschrift(pfad: String): Bitmap? {
    if (!File(pfad).isFile) return null
    return runCatching {
        val masse = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(pfad, masse)
        var faktor = 1
        while (masse.outWidth > 0 && masse.outWidth / faktor > SIGNATURE_MAX_WIDTH_PX) faktor *= 2
        BitmapFactory.decodeFile(pfad, BitmapFactory.Options().apply { inSampleSize = faktor })
    }.onFailure {
        logError("InvoicePdf", "Unterschrift konnte nicht geladen werden", it)
    }.getOrNull()
}
