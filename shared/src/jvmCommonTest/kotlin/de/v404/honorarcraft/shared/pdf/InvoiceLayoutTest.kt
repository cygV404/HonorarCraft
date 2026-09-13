package de.v404.honorarcraft.shared.pdf

import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.InvoiceEntry
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Das Layout der Rechnung, geprüft ohne ein PDF zu öffnen.
 *
 * Weil beide Plattformen dieselbe Befehlsliste zeichnen, genügt es, sie hier festzunageln —
 * die Renderer setzen nur noch um. Vorlage ist die Android-Fassung (Entscheidung 6 im
 * KMP_PLAN.md).
 */
class InvoiceLayoutTest {

    /** Feste Breite je Zeichen: das Layout darf nicht von echten Schriftmetriken abhängen. */
    private val measurer = TextMeasurer { text, _ -> text.length * 5f }

    private val firma = CompanyData(
        eduCenter = "Biberach - Ehingen",
        locationNr = "40 - 381",
        schoolType = "AsA flex",
        customerSecondNameOrOrga = "Kolping Berufsbildung gGmbH",
        customerPlz = "70010",
        customerCityName = "Stuttgart",
        customerMailBox = "10 11 61",
        billerSecondName = "Mustermann",
        billerFirstName = "Max",
        billerStreetName = "Musterstraße",
        billerStreetNumber = "12",
        billerPlzNumber = "89073",
        billerCityName = "Ulm",
        taxNumber = "12/345/678/912",
        billerIban = "DE12 3456 7810 1112 1314 15",
        billerBIC = "SOLADEST600",
        rate = BigDecimal("23.00"),
        signaturePath = "",
    )

    private fun position(datum: String, stunden: String, fach: String, satz: String = "23.00") =
        InvoiceEntry(
            invoiceNumber = "13",
            date = datum,
            lessonUnits = BigDecimal(stunden),
            teachingSubject = fach,
            rate = BigDecimal(satz),
        )

    private fun baue(vararg positionen: InvoiceEntry) = InvoiceLayout.build(
        invoice = InvoiceWithEntries(InvoiceData("13"), positionen.toList()),
        company = firma,
        formattedInvoiceNumber = "13",
        measurer = measurer,
        today = LocalDate.of(2026, 9, 13),
    )

    private fun PdfDocumentModel.texte(): List<String> =
        pages.flatMap { it.commands }.filterIsInstance<DrawCommand.Text>().map { it.text }

    @Test
    fun `Betraege stehen im deutschen Zahlenformat`() {
        val texte = baue(
            position("09.01.2026", "1.5", "Anlagenmechaniker"),
            position("14.01.2026", "2.7", "Mathematik"),
            position("21.01.2026", "3.0", "Deutsch"),
            position("28.01.2026", "2.0", "Prüfungsvorbereitung"),
        ).texte()

        // Kein einziger englischer Dezimalpunkt in einem Betrag - das war der Fehler der
        // alten Desktop-Fassung.
        assertTrue(texte.any { it == "Summe = 282,13 €" }, "Summe fehlt oder falsch: $texte")
        assertTrue(texte.any { it.contains("23,00 € Honorar/UE") })
        assertTrue(texte.any { it.contains("UE Gesamt a 45 Min = 12,27") })
        assertTrue(texte.none { Regex("""\d+\.\d{2} €""").containsMatchIn(it) }, "englischer Punkt: $texte")
    }

    @Test
    fun `der Betrag je Position rechnet mit dem vollen UE-Wert`() {
        // 2 h -> 2,6667 UE. Gerundet mal Satz waere 61,41; richtig ist 61,33.
        val texte = baue(position("28.01.2026", "2.0", "Prüfungsvorbereitung")).texte()
        assertTrue(texte.any { it == "61,33 €" }, "Betrag falsch gerundet: $texte")
        assertTrue(texte.any { it == "2,67" }, "UE-Spalte zeigt zwei Nachkommastellen: $texte")
    }

    @Test
    fun `verschiedene Saetze werden angesagt statt einen zu behaupten`() {
        val texte = baue(
            position("09.01.2026", "3.0", "Mathematik", satz = "23.00"),
            position("14.01.2026", "3.0", "Deutsch", satz = "31.50"),
        ).texte()
        assertTrue(texte.any { it.contains("verschiedene Sätze gemäß Aufstellung") }, "$texte")
    }

    @Test
    fun `jede Seite traegt ihre Seitenzahl`() {
        val modell = baue(position("09.01.2026", "1.5", "Mathematik"))
        assertEquals(2, modell.pages.size, "Deckblatt plus eine Tabellenseite")

        val fusszeilen = modell.pages.map { seite ->
            seite.commands.filterIsInstance<DrawCommand.Text>()
                .single { it.text.startsWith("Seite ") }
        }
        assertEquals(listOf("Seite 1 von 2", "Seite 2 von 2"), fusszeilen.map { it.text })
        assertTrue(fusszeilen.all { it.align == PdfAlign.CENTER })
    }

    @Test
    fun `der Zeitraum kommt aus dem Monat der ersten Position`() {
        val texte = baue(
            position("21.01.2026", "1.0", "Deutsch"),
            position("09.01.2026", "1.0", "Mathematik"),
        ).texte()
        assertTrue(
            texte.any { it.contains("von 01.01.2026 bis 31.01.2026") },
            "Zeitraum falsch: $texte"
        )
    }

    @Test
    fun `Positionen stehen nach Datum sortiert`() {
        val modell = baue(
            position("28.01.2026", "1.0", "Zuletzt"),
            position("09.01.2026", "1.0", "Zuerst"),
        )
        val faecher = modell.pages.last().commands
            .filterIsInstance<DrawCommand.Text>()
            .filter { it.text == "Zuerst" || it.text == "Zuletzt" }
            .map { it.text }
        assertEquals(listOf("Zuerst", "Zuletzt"), faecher)
    }

    @Test
    fun `ohne Unterschrift steht das Datum oben`() {
        val texte = baue(position("09.01.2026", "1.0", "Mathematik")).texte()
        assertTrue(texte.any { it == "Rechnungsdatum: 13.09.2026" }, "$texte")
    }

    @Test
    fun `mit Unterschrift steht das Datum unten bei der Ortsangabe`() {
        val modell = InvoiceLayout.build(
            invoice = InvoiceWithEntries(
                InvoiceData("13"),
                listOf(position("09.01.2026", "1.0", "Mathematik")),
            ),
            company = firma.copy(signaturePath = "/pfad/unterschrift.png"),
            formattedInvoiceNumber = "13",
            measurer = measurer,
            today = LocalDate.of(2026, 9, 13),
        )
        val texte = modell.texte()
        assertTrue(texte.none { it.startsWith("Rechnungsdatum:") }, "doppeltes Datum: $texte")
        assertTrue(texte.any { it == "Ulm, 13.09.2026" }, "$texte")
        assertTrue(
            modell.pages.first().commands.any { it is DrawCommand.Image },
            "Die Unterschrift muss gezeichnet werden"
        )
    }

    @Test
    fun `das lange Unterrichtsfach wird beschnitten statt ueber den Rand zu laufen`() {
        val modell = baue(position("09.01.2026", "1.0", "Ein sehr langes Unterrichtsfach"))
        val fach = modell.pages.last().commands
            .filterIsInstance<DrawCommand.Text>()
            .single { it.text == "Ein sehr langes Unterrichtsfach" }
        assertTrue(fach.clipWidth != null && fach.clipWidth!! > 0f)
    }
}
