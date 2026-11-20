package de.v404.honorarcraft.shared.pdf

import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import java.io.File
import java.io.OutputStream
import java.time.LocalDate

/** Misst Text mit den Schriften, die der jeweilige Renderer benutzt. */
expect fun pdfTextMeasurer(): TextMeasurer

/** Zeichnet [model] und schreibt das fertige PDF nach [out]. Der Strom wird geschlossen. */
expect suspend fun writeInvoicePdf(model: PdfDocumentModel, out: OutputStream)

/**
 * Der gemeinsame Weg von der Rechnung zum PDF: einmal Layout, dann der plattformeigene
 * Renderer. Damit zeichnen Android und Desktop dasselbe Dokument.
 */
suspend fun createInvoicePdf(
    invoice: InvoiceWithEntries,
    company: CompanyData,
    formattedInvoiceNumber: String,
    out: OutputStream,
    today: LocalDate = LocalDate.now(),
): PdfDocumentModel {
    val model = InvoiceLayout.build(
        invoice = invoice,
        company = company,
        formattedInvoiceNumber = formattedInvoiceNumber,
        measurer = pdfTextMeasurer(),
        today = today,
        // Nach dem Einlesen einer Sicherung von einem anderen Gerät zeigt der Pfad ins Leere.
        // Dann gilt die Unterschrift als nicht vorhanden: das Datum steht oben, unten keine
        // leere Linie.
        signaturePath = company.signaturePath.takeIf { it.isNotBlank() && File(it).isFile },
    )
    writeInvoicePdf(model, out)
    return model
}
