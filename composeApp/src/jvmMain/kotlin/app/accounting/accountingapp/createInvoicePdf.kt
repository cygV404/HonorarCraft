package app.accounting.accountingapp

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import java.awt.Desktop
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun createInvoicePdf(
    companyData: CompanyData,
    invoiceData: InvoiceData,
    showUELabel: Boolean


) {
    val doc = PDDocument()

// 1. Die Pfade relativ zum 'resources'-Ordner
    val regularPath = "/font/Roboto-Regular.ttf"
    val boldPath = "/font/Roboto-Bold.ttf"

    // 2. Sicher laden mit dem ClassLoader
    fun loadFont(path: String, default: org.apache.pdfbox.pdmodel.font.PDFont): org.apache.pdfbox.pdmodel.font.PDFont {
        val stream = object {}.javaClass.getResourceAsStream(path)
        return if (stream != null) {
            PDType0Font.load(doc, stream)
        } else {
            println("Warnung: Schriftart $path nicht gefunden! Nutze Fallback.")
            default
        }
    }

    val font = loadFont(regularPath, PDType1Font.HELVETICA)
    val fontBold = loadFont(boldPath, PDType1Font.HELVETICA_BOLD)

// Ab hier'font' und 'fontBold' für das Layout

    val left = 50f
    val rightMargin = 50f
    val lineHeight = 14f
    val fontSize = 11f
    val pageWidth = PDRectangle.A4.width
    val maxWidth = pageWidth - left - rightMargin
    val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val today = LocalDate.now().format(dateFormatter)

    // Hilfsfunktionen

    fun drawTextWrapped(
        content: PDPageContentStream,
        text: String,
        x: Float,
        yStart: Float,
        font: org.apache.pdfbox.pdmodel.font.PDFont,
        fontSize: Float,
        maxWidth: Float,
        bold: Boolean = false
    ): Float {
        val words = text.split(" ")
        var line = ""
        var y = yStart
        content.setFont(if (bold) fontBold else font, fontSize)

        for (word in words) {
            val testLine = if (line.isEmpty()) word else "$line $word"
            val width = font.getStringWidth(testLine) / 1000f * fontSize
            if (width > maxWidth) {
                content.beginText()
                content.newLineAtOffset(x, y)
                content.showText(line)
                content.endText()
                line = word
                y -= fontSize + 2
            } else {
                line = testLine
            }
        }

        if (line.isNotEmpty()) {
            content.beginText()
            content.newLineAtOffset(x, y)
            content.showText(line)
            content.endText()
            y -= fontSize + 2
        }
        return y
    }

    fun drawRightText(
        content: PDPageContentStream,
        text: String,
        y: Float,
        bold: Boolean = false
    ) {
        val f = if (bold) fontBold else font
        val textWidth = f.getStringWidth(text) / 1000f * fontSize
        val startX = pageWidth - rightMargin - textWidth

        content.beginText()
        content.setFont(f, fontSize)
        content.newLineAtOffset(startX, y)
        content.showText(text)
        content.endText()
    }


    // Seite 1

    val page1 = PDPage(PDRectangle.A4)
    doc.addPage(page1)
    val content = PDPageContentStream(doc, page1)
    val topStart = 790f
    var y = topStart
    var senderY = topStart


// Absender Rechts
    drawRightText(content, "${companyData.billerFirstName} ${companyData.billerSecondName}", senderY, bold = true)
    senderY -= lineHeight

// GEÄNDERT: Straße und Hausnummer zusammengefügt
    drawRightText(content, "${companyData.billerStreetName} ${companyData.billerStreetNumber}", senderY)

    senderY -= lineHeight
    drawRightText(content, "${companyData.billerPlzNumber} ${companyData.billerCityName}", senderY)
    senderY -= lineHeight * 2
    drawRightText(content, "Steuernummer: ${companyData.taxNumber}", senderY)
    senderY -= lineHeight
    drawRightText(content, "IBAN: ${companyData.billerIban}", senderY)
    senderY -= lineHeight
    drawRightText(content, "BIC: ${companyData.billerBIC}", senderY)
    if (companyData.signaturePath.isBlank()) {


        senderY -= lineHeight * 2
        drawRightText(content, "Rechnungsdatum: $today", senderY)
    }
    // Titel
    y -= 250f
    content.beginText()
    content.setFont(fontBold, 18f)
    content.newLineAtOffset(left, y)
    content.showText("Honorarabrechnung")
    content.endText()
    y -= 30f

    fun text(txt: String, bold: Boolean = false) {
        content.beginText()
        content.setFont(if (bold) fontBold else font, fontSize)
        content.newLineAtOffset(left, y)
        content.showText(txt)
        content.endText()
        y -= lineHeight
    }

    text("Rechnungsnummer: ${invoiceData.invoiceNumber}", bold = true)
    y -= 20f

    // Empfänger
    text(companyData.customerSecondNameOrOrga, bold = true)
    if (companyData.customerFirstName.isNotBlank()) text(companyData.customerFirstName)
    // Empfänger (Korrektur im unteren Teil deines Codes)
    if (companyData.customerMailBox.isNotBlank()) {
        text("Postfach ${companyData.customerMailBox}")
    } else {
        // GEÄNDERT: Hier auch Straße + Nummer
        text("${companyData.customerStreet} ${companyData.customerStreetNumber}")
    }
    text("${companyData.customerPlz} ${companyData.customerCityName}")
    y -= 20f

    //Bildungszentrum
    text("Bildungszentrum: ${companyData.eduCenter}")
    text("Standortnummer: ${companyData.locationNr}")
    text("Schulart / Maßnahme: ${companyData.schoolType}")
    y -= 20f

    // Zeitraum + Summen
    val firstEntry = invoiceData.entries.firstOrNull()
    if (firstEntry != null) {
        val firstDate = LocalDate.parse(firstEntry.date, dateFormatter)
        val monthStart = firstDate.withDayOfMonth(1)
        val monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth())

        y = drawTextWrapped(
            content,
            "Für den in der Zeit von ${monthStart.format(dateFormatter)} bis ${monthEnd.format(dateFormatter)} " +
                    "erteilten Fachunterricht und/oder andere Tätigkeiten gemäß meiner Aufstellung anbei," +
                    " stelle ich wie folgt in Rechnung:",
            left,
            y,
            font,
            fontSize,
            maxWidth
        )

        y -= 20f


        if (showUELabel) {
            y = drawTextWrapped(
                content,
                "UE Gesamt a 45 Min = ${invoiceData.totalHours}  x   ${invoiceData.hourRate}€ Honorar/UE",
                left,
                y,
                font,
                fontSize,
                maxWidth,
                bold = true
            )

            y = drawTextWrapped(
                content,
                "Summe = ${invoiceData.totalCostsLessonUnits} €",
                left,
                y,
                font,
                fontSize,
                maxWidth,
                bold = true
            )

        } else {


            y = drawTextWrapped(
                content,
                "UE Gesamt a 45 Min = ${invoiceData.totalLessonUnit}  x   ${invoiceData.hourRate} € Honorar/UE",
                left,
                y,
                font,
                fontSize,
                maxWidth,
                bold = true
            )

            y = drawTextWrapped(
                content,
                "Summe = ${invoiceData.totalCostsHours} €",
                left,
                y,
                font,
                fontSize,
                maxWidth,
                bold = true
            )


        }






        y -= 40f
    }

    // Unterschrift
    if (companyData.signaturePath.isNotBlank()) {
        val img = PDImageXObject.createFromFile(companyData.signaturePath, doc)
        content.drawImage(img, left, y - 60f, 150f, 50f)
        y -= 70f


        content.setLineWidth(0.5f)
        content.moveTo(left, y)
        content.lineTo(left + 200f, y)
        content.stroke()
        y -= lineHeight

        text("${companyData.billerCityName}, $today")
    }
    content.close()


// Seite 2 bis n Tabelle

    var tableY = 780f  // oberer Start der Tabelle
    val tableBottomMargin = 60f
    val rowHeight = 20f

    fun drawTableHeader(content: PDPageContentStream, y: Float): Float {
        content.beginText()
        content.setFont(fontBold, fontSize)
        content.newLineAtOffset(left, y)
        content.showText("Datum")
        content.newLineAtOffset(80f, 0f)
        content.showText("UE")
        content.newLineAtOffset(80f, 0f)
        content.showText("Kosten")
        content.newLineAtOffset(80f, 0f)
        content.showText("Unterrichtsfach/Klasse")
        content.endText()
        return y - rowHeight
    }

    // Neue Seite mit Stream öffnen
    fun newTablePage(): PDPageContentStream {
        val page = PDPage(PDRectangle.A4)
        doc.addPage(page)
        val content = PDPageContentStream(doc, page)
        tableY = drawTableHeader(content, 780f)
        return content
    }

// Initialen Stream auf Seite 2 erstellen
    var tableContent = newTablePage()  // Seite 2

    invoiceData.entries.forEachIndexed { index, e ->

        if (tableY < tableBottomMargin + rowHeight) {
            tableContent.close()  // alte Seite schließen
            tableContent = newTablePage() // neue Seite mit Header
        }


        val correctedHours = invoiceData.calculateCorrectedHours(e.hours)
        val correctedHours2 = invoiceData.calculateCorrectedLessonUnits(e.hours)

        val cost = correctedHours.multiply(invoiceData.hourRate).setScale(2, java.math.RoundingMode.HALF_UP)
        val cost2 = correctedHours2.multiply(invoiceData.hourRate).setScale(2, java.math.RoundingMode.HALF_UP)

        if (index % 2 == 1) {
            tableContent.setNonStrokingColor(0.9f)
            tableContent.addRect(left, tableY - 4, maxWidth, rowHeight)
            tableContent.fill()
            tableContent.setNonStrokingColor(0f)
        }


        if (showUELabel) {

            tableContent.beginText()
            tableContent.setFont(font, fontSize)
            tableContent.newLineAtOffset(left, tableY)
            tableContent.showText(e.date)
            tableContent.newLineAtOffset(80f, 0f)
            tableContent.showText(correctedHours2.toString())
            tableContent.newLineAtOffset(80f, 0f)
            tableContent.showText("$cost2 €")
            tableContent.newLineAtOffset(80f, 0f)
            tableContent.showText(e.teachingSubject)
            tableContent.endText()

            tableY -= rowHeight

        } else {


            tableContent.beginText()
            tableContent.setFont(font, fontSize)
            tableContent.newLineAtOffset(left, tableY)
            tableContent.showText(e.date)
            tableContent.newLineAtOffset(80f, 0f)
            tableContent.showText(correctedHours.toString())
            tableContent.newLineAtOffset(80f, 0f)
            tableContent.showText("$cost €")
            tableContent.newLineAtOffset(80f, 0f)
            tableContent.showText(e.teachingSubject)
            tableContent.endText()

            tableY -= rowHeight


        }


    }

// Summen auf der letzten Seite
    tableY -= rowHeight
    tableContent.beginText()
    tableContent.setFont(fontBold, fontSize)
    tableContent.newLineAtOffset(left, tableY)

    if (showUELabel) {
        tableContent.showText("UE Gesamt a 45 Min = ${invoiceData.totalHours}")

    } else {

        tableContent.showText("UE Gesamt a 45 Min = ${invoiceData.totalLessonUnit}")


    }



    tableContent.endText()

    tableY -= lineHeight
    tableContent.beginText()
    tableContent.setFont(fontBold, fontSize)
    tableContent.newLineAtOffset(left, tableY)



    if (showUELabel) {

        tableContent.showText("Summe = ${invoiceData.totalCostsLessonUnits} €")


    } else {
        tableContent.showText("Summe = ${invoiceData.totalCostsHours} €")

    }



    tableContent.endText()

    tableContent.close()


    val totalPages = doc.numberOfPages

    val bottomMargin = 30f

    for (i in 0 until totalPages) {
        val page = doc.getPage(i)
        val content = PDPageContentStream(
            doc,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true
        )

        val text = "Seite ${i + 1} von $totalPages"
        val textWidth = font.getStringWidth(text) / 1000f * fontSize
        val x = (PDRectangle.A4.width - textWidth) / 2
        val y = bottomMargin

        content.beginText()
        content.setFont(font, fontSize)
        content.newLineAtOffset(x, y)
        content.showText(text)
        content.endText()

        content.close()
    }

    // 1. Plattformunabhängiges Speicher
    val outputDir = if (companyData.pdfPath.isNotBlank()) {
        File(companyData.pdfPath)
    } else {
        val userHome = File(System.getProperty("user.home"))
        val os = System.getProperty("os.name").lowercase()

        val folderName = when {
            os.contains("win") -> "Documents"
            os.contains("mac") -> "Documents"
            else -> "Dokumente"
        }

        val documentsDir = File(userHome, folderName)
        if (documentsDir.exists()) File(documentsDir, "Honorarabrechnungen")
        else File(userHome, "Honorarabrechnungen")
    }

    // 2. Ordner erstellen, falls er fehlt (Wichtig!)
    if (!outputDir.exists()) {
        outputDir.mkdirs()
    }

    // 3. Die Datei-Variable definieren
    val file = File(outputDir, "rechnung_${invoiceData.invoiceNumber}.pdf")

    // 4. Speichern und Schließen
    doc.save(file)
    doc.close()

    // 5. Ordner automatisch im Explorer/Finder öffnen
    if (Desktop.isDesktopSupported()) {
        val desktop = Desktop.getDesktop()
        try {
            if (outputDir.exists()) {
                desktop.open(outputDir)
            }
        } catch (e: Exception) {
            println("Konnte Ordner nicht öffnen: ${e.message}")
        }
    }
}







