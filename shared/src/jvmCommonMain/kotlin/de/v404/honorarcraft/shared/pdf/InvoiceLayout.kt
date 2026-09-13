package de.v404.honorarcraft.shared.pdf

import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.Constants
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import de.v404.honorarcraft.shared.pdf.PdfMetrics.BLOCK_GAP
import de.v404.honorarcraft.shared.pdf.PdfMetrics.COL_COST
import de.v404.honorarcraft.shared.pdf.PdfMetrics.COL_SUBJECT
import de.v404.honorarcraft.shared.pdf.PdfMetrics.COL_UNITS
import de.v404.honorarcraft.shared.pdf.PdfMetrics.CONTENT_WIDTH
import de.v404.honorarcraft.shared.pdf.PdfMetrics.FONT_SIZE_REGULAR
import de.v404.honorarcraft.shared.pdf.PdfMetrics.FOOTER_BASELINE
import de.v404.honorarcraft.shared.pdf.PdfMetrics.LINE_HEIGHT
import de.v404.honorarcraft.shared.pdf.PdfMetrics.MARGIN
import de.v404.honorarcraft.shared.pdf.PdfMetrics.PAGE_HEIGHT
import de.v404.honorarcraft.shared.pdf.PdfMetrics.PAGE_WIDTH
import de.v404.honorarcraft.shared.pdf.PdfMetrics.ROW_HEIGHT
import de.v404.honorarcraft.shared.pdf.PdfMetrics.SIGNATURE_HEIGHT
import de.v404.honorarcraft.shared.pdf.PdfMetrics.SIGNATURE_WIDTH
import de.v404.honorarcraft.shared.pdf.PdfMetrics.TABLE_TOP
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Baut die Rechnung als Liste von Zeichenbefehlen auf — einmal für beide Plattformen.
 *
 * Vorlage ist die Android-Fassung (siehe Entscheidung 6 im KMP_PLAN.md); vom Desktop
 * übernommen ist die Seitenzahl in der Fußzeile. Das Layout rechnet in PDF-Punkten von oben
 * links; der PDFBox-Renderer spiegelt die Y-Achse.
 */
object InvoiceLayout {

    private val dateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern(Constants.DATE_PATTERN, Locale.GERMANY)

    /** Deutsches Zahlenformat. Die alte Desktop-Fassung schrieb englische Dezimalpunkte. */
    private fun decimalFormat() = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.GERMANY))

    fun build(
        invoice: InvoiceWithEntries,
        company: CompanyData,
        formattedInvoiceNumber: String,
        measurer: TextMeasurer,
        today: LocalDate = LocalDate.now(),
        signaturePath: String? = company.signaturePath.takeIf { it.isNotBlank() },
    ): PdfDocumentModel {
        val zahl = decimalFormat()
        val heute = today.format(dateFormatter)
        val hatUnterschrift = signaturePath != null

        val deckblatt = mutableListOf<DrawCommand>()
        val rechtsBuendig = PAGE_WIDTH - MARGIN
        var y = 80f

        // --- Absender, rechtsbündig -------------------------------------------------
        fun rechts(text: String, font: PdfFont = PdfFont.REGULAR) {
            deckblatt += DrawCommand.Text(text, rechtsBuendig, y, font, PdfAlign.RIGHT)
            y += LINE_HEIGHT
        }
        rechts("${company.billerSecondName} ${company.billerFirstName}", PdfFont.BOLD)
        rechts("${company.billerStreetName} ${company.billerStreetNumber}")
        rechts("${company.billerPlzNumber} ${company.billerCityName}")
        y += LINE_HEIGHT

        if (company.taxNumber.isNotBlank()) rechts("Steuernummer: ${company.taxNumber}")
        if (company.billerIban.isNotBlank()) rechts("IBAN: ${company.billerIban}")
        if (company.billerBIC.isNotBlank()) rechts("BIC: ${company.billerBIC}")

        // Das Datum steht oben nur, wenn es unten keine Unterschrift mit Datum gibt.
        if (!hatUnterschrift) {
            y += LINE_HEIGHT
            deckblatt += DrawCommand.Text(
                "Rechnungsdatum: $heute", rechtsBuendig, y, PdfFont.REGULAR, PdfAlign.RIGHT
            )
        }

        // --- Titel und Empfänger, linksbündig --------------------------------------
        y = 280f
        fun links(text: String, font: PdfFont = PdfFont.REGULAR, abstand: Float = LINE_HEIGHT) {
            deckblatt += DrawCommand.Text(text, MARGIN, y, font)
            y += abstand
        }
        links("Honorarabrechnung", PdfFont.TITLE, BLOCK_GAP)
        links("Rechnung: $formattedInvoiceNumber", PdfFont.BOLD, BLOCK_GAP)

        if (company.customerSecondNameOrOrga.isNotBlank()) {
            links(company.customerSecondNameOrOrga, PdfFont.BOLD)
        }
        if (company.customerFirstName.isNotBlank()) links(company.customerFirstName)
        if (company.customerStreet.isNotBlank()) {
            links("${company.customerStreet} ${company.customerStreetNumber}")
        }
        if (company.customerMailBox.isNotBlank()) links("Postfach ${company.customerMailBox}")
        if (company.customerPlz.isNotBlank() || company.customerCityName.isNotBlank()) {
            links("${company.customerPlz} ${company.customerCityName}", abstand = BLOCK_GAP)
        }

        if (company.eduCenter.isNotBlank()) links("Bildungszentrum: ${company.eduCenter}")
        if (company.locationNr.isNotBlank()) links("Standortnummer: ${company.locationNr}")
        if (company.schoolType.isNotBlank()) links("Schulart / Maßnahme: ${company.schoolType}")
        y += 25f

        // --- Zeitraum und Summen ----------------------------------------------------
        val positionen = invoice.entries.sortedBy { parseDatum(it.date) ?: LocalDate.MIN }
        val erste = positionen.firstOrNull()
        if (erste != null) {
            val monat = parseDatum(erste.date)?.let { YearMonth.from(it) } ?: YearMonth.from(today)
            val von = monat.atDay(1).format(dateFormatter)
            val bis = monat.atEndOfMonth().format(dateFormatter)

            y = umbrechen(
                deckblatt,
                "Für den in der Zeit von $von bis $bis erteilten Fachunterricht und/oder " +
                    "andere Tätigkeiten gemäß meiner Aufstellung anbei, stelle ich wie folgt " +
                    "in Rechnung:",
                MARGIN, y, CONTENT_WIDTH, measurer,
            )
            y += 20f

            val saetze = invoice.entries.map { it.rate }.distinct()
            // Auch die UE-Zahl im deutschen Format. Die Android-Vorlage hat hier das
            // BigDecimal direkt eingesetzt und damit einen englischen Punkt gedruckt.
            val ueGesamt = zahl.format(invoice.totalLessonUnit)
            val ueZeile = if (saetze.size == 1) {
                "UE Gesamt a 45 Min = $ueGesamt  x   " +
                    "${zahl.format(saetze.first())} € Honorar/UE"
            } else {
                "UE Gesamt a 45 Min = $ueGesamt (verschiedene Sätze gemäß Aufstellung)"
            }
            deckblatt += DrawCommand.Text(ueZeile, MARGIN, y, PdfFont.BOLD)
            y += 20f
            deckblatt += DrawCommand.Text(
                "Summe = ${zahl.format(invoice.totalSum)} €", MARGIN, y, PdfFont.BOLD
            )
            y += 40f
        }

        // --- Unterschrift -----------------------------------------------------------
        if (signaturePath != null) {
            deckblatt += DrawCommand.Image(
                signaturePath, MARGIN, y, SIGNATURE_WIDTH, SIGNATURE_HEIGHT
            )
            y += 60f
            deckblatt += DrawCommand.Line(MARGIN, y, MARGIN + 200f, y)
            y += LINE_HEIGHT
            deckblatt += DrawCommand.Text("${company.billerCityName}, $heute", MARGIN, y)
        }

        // --- Tabellenseiten ---------------------------------------------------------
        val seiten = mutableListOf(PdfPage(deckblatt))
        val tabellenEnde = PAGE_HEIGHT - 60f
        var index = 0

        while (index < positionen.size) {
            val befehle = mutableListOf<DrawCommand>()
            var ty = TABLE_TOP

            befehle += DrawCommand.Text("Datum", MARGIN, ty, PdfFont.BOLD)
            befehle += DrawCommand.Text("UE", MARGIN + COL_UNITS, ty, PdfFont.BOLD)
            befehle += DrawCommand.Text("Kosten", MARGIN + COL_COST, ty, PdfFont.BOLD)
            befehle += DrawCommand.Text(
                "Unterrichtsfach/Klasse", MARGIN + COL_SUBJECT, ty, PdfFont.BOLD
            )
            ty += 10f
            befehle += DrawCommand.Line(MARGIN, ty, PAGE_WIDTH - MARGIN, ty)
            ty += ROW_HEIGHT

            while (index < positionen.size && ty < tabellenEnde - 40f) {
                val position = positionen[index]
                // Unterrichtseinheiten mit voller Genauigkeit, damit der Betrag stimmt; in der
                // Spalte stehen zwei Nachkommastellen (siehe Entscheidung 6 im KMP_PLAN.md).
                val ue = position.lessonUnits.multiply(BigDecimal("60"))
                    .divide(BigDecimal("45"), 10, RoundingMode.HALF_UP)
                val betrag = ue.multiply(position.rate).setScale(2, RoundingMode.HALF_UP)

                if (index % 2 == 1) {
                    befehle += DrawCommand.Rect(
                        x = MARGIN,
                        y = ty - 15f,
                        width = PAGE_WIDTH - 2 * MARGIN,
                        height = 25f,
                        gray = 0.83f,
                        alpha = 0.12f,
                    )
                }

                befehle += DrawCommand.Text(position.date, MARGIN, ty)
                befehle += DrawCommand.Text(zahl.format(ue), MARGIN + COL_UNITS, ty)
                befehle += DrawCommand.Text("${zahl.format(betrag)} €", MARGIN + COL_COST, ty)
                befehle += DrawCommand.Text(
                    text = position.teachingSubject,
                    x = MARGIN + COL_SUBJECT,
                    y = ty,
                    clipWidth = PAGE_WIDTH - MARGIN - (MARGIN + COL_SUBJECT),
                )

                ty += ROW_HEIGHT
                index++
            }

            if (index == positionen.size) {
                ty += 10f
                befehle += DrawCommand.Text(
                    "UE Gesamt a 45 Min = ${zahl.format(invoice.totalLessonUnit)}",
                    MARGIN, ty, PdfFont.BOLD,
                )
                ty += 20f
                befehle += DrawCommand.Text(
                    "Summe = ${zahl.format(invoice.totalSum)} €", MARGIN, ty, PdfFont.BOLD
                )
            }

            seiten += PdfPage(befehle)
        }

        // --- Fußzeile mit Seitenzahl (vom Desktop übernommen) -----------------------
        val gesamt = seiten.size
        val mitFusszeile = seiten.mapIndexed { i, seite ->
            PdfPage(
                seite.commands + DrawCommand.Text(
                    text = "Seite ${i + 1} von $gesamt",
                    x = PAGE_WIDTH / 2f,
                    y = FOOTER_BASELINE,
                    align = PdfAlign.CENTER,
                )
            )
        }

        return PdfDocumentModel(
            pages = mitFusszeile,
            fileName = "Rechnung_$formattedInvoiceNumber.pdf",
        )
    }

    private fun parseDatum(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim(), dateFormatter) }.getOrNull()

    /** Bricht [text] auf [maxWidth] um und liefert das neue Y zurück. */
    private fun umbrechen(
        ziel: MutableList<DrawCommand>,
        text: String,
        x: Float,
        yStart: Float,
        maxWidth: Float,
        measurer: TextMeasurer,
    ): Float {
        var zeile = ""
        var y = yStart
        for (wort in text.split(" ")) {
            val versuch = if (zeile.isEmpty()) wort else "$zeile $wort"
            if (measurer.width(versuch, PdfFont.REGULAR) > maxWidth && zeile.isNotEmpty()) {
                ziel += DrawCommand.Text(zeile, x, y)
                zeile = wort
                y += FONT_SIZE_REGULAR + 4
            } else {
                zeile = versuch
            }
        }
        if (zeile.isNotEmpty()) {
            ziel += DrawCommand.Text(zeile, x, y)
            y += FONT_SIZE_REGULAR + 4
        }
        return y
    }
}
