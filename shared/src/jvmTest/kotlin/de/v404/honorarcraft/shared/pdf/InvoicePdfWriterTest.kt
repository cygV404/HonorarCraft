package de.v404.honorarcraft.shared.pdf

import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.Customer
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.InvoiceEntry
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertTrue

/** Der Desktop-Renderer auf PDFBox, mit echter Schrift. */
class InvoicePdfWriterTest {

    private fun rechnung(fach: String, kundenname: String = "Kolping") = InvoiceWithEntries(
        InvoiceData(invoiceNumber = "13", customerId = 1),
        listOf(
            InvoiceEntry(
                invoiceNumber = "13",
                date = "09.01.2026",
                lessonUnits = BigDecimal("1.5"),
                teachingSubject = fach,
                rate = BigDecimal("23.00"),
            )
        ),
        Customer(id = 1, nameOrOrga = kundenname),
    )

    @Test
    fun `Zeichen ohne Glyphe lassen das PDF nicht scheitern`() = runTest {
        val out = ByteArrayOutputStream()
        createInvoicePdf(
            rechnung("Mathe 🙂 数学", kundenname = "Kolping\u0007 ☃"),
            CompanyData(billerSecondName = "Muster\nmann"),
            "13",
            out,
        )
        assertTrue(out.size() > 0, "PDF ist leer")
    }

    @Test
    fun `eine fehlende Unterschriftsdatei gilt als keine Unterschrift`() = runTest {
        val modell = createInvoicePdf(
            rechnung("Mathe"),
            CompanyData(signaturePath = "/gibt/es/nicht.png"),
            "13",
            ByteArrayOutputStream(),
        )
        val befehle = modell.pages.flatMap { it.commands }
        assertTrue(
            befehle.filterIsInstance<DrawCommand.Text>().any { it.text.startsWith("Rechnungsdatum:") },
            "Ohne Unterschrift gehört das Datum nach oben"
        )
        assertTrue(befehle.none { it is DrawCommand.Image })
    }
}
