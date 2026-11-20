package de.v404.honorarcraft.ui.platform

import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.Customer
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.InvoiceEntry
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import kotlinx.coroutines.runBlocking
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Ein Ablageordner aus einer Android-Sicherung (`content://…`) gilt auf dem Desktop als leer. */
class DesktopPdfOrdnerTest {

    private val altesHome = System.getProperty("user.home")
    private lateinit var home: File

    @BeforeTest
    fun setUp() {
        home = Files.createTempDirectory("honorarcraft-home").toFile()
        System.setProperty("user.home", home.absolutePath)
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", altesHome)
        home.deleteRecursively()
    }

    @Test
    fun `content-Adresse vom Handy fuehrt in den Vorgabeordner`() = runBlocking {
        val dienste = DesktopPlatformServices()
        val vorgabe = File(home, "Dokumente/Honorarabrechnungen")
        val handyOrdner = "content://com.android.externalstorage.documents/tree/primary%3ADocuments"

        assertEquals(vorgabe.absolutePath, dienste.describePdfFolder(handyOrdner))

        val rechnung = InvoiceWithEntries(
            InvoiceData("07", 1),
            listOf(InvoiceEntry(invoiceNumber = "07", date = "02.09.2026", lessonUnits = BigDecimal("1"), teachingSubject = "Mathe", rate = BigDecimal("23.00"))),
            Customer(id = 1, nameOrOrga = "Kolping"),
        )
        val ort = dienste.saveInvoicePdf(rechnung, CompanyData(pdfPath = handyOrdner), "07").getOrThrow()

        assertTrue(File(ort).isFile, "PDF fehlt: $ort")
        assertEquals(vorgabe.absolutePath, File(ort).parentFile.absolutePath)
        assertTrue(!File("content:").exists(), "Kein Ordner „content:“ im Arbeitsverzeichnis")
    }
}
