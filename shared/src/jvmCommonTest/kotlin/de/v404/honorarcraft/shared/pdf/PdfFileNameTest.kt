package de.v404.honorarcraft.shared.pdf

import de.v404.honorarcraft.shared.data.Customer
import kotlin.test.Test
import kotlin.test.assertEquals

class PdfFileNameTest {

    @Test
    fun `ohne Kunden bleibt der bisherige Name`() {
        assertEquals("Rechnung_2026-05.pdf", pdfDateiname("2026-05", null))
    }

    @Test
    fun `der Kundenname wird angehaengt, Umlaute bleiben`() {
        val kunde = Customer(nameOrOrga = "Bildungswerk Süd", firstName = "")
        assertEquals("Rechnung_07_Bildungswerk_Süd.pdf", pdfDateiname("07", kunde))
    }

    @Test
    fun `unter Windows verbotene Zeichen werden ersetzt`() {
        val kunde = Customer(nameOrOrga = "A/B: \"Test\" <GmbH>?")
        assertEquals("Rechnung_07_A-B-_-Test-_-GmbH.pdf", pdfDateiname("07", kunde))
    }
}
