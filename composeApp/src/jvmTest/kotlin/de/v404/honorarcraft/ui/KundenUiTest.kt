package de.v404.honorarcraft.ui

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.v404.honorarcraft.shared.InMemorySettings
import de.v404.honorarcraft.shared.MainViewModel
import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.Customer
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.createDesktopDatabase
import kotlinx.coroutines.runBlocking
import java.io.File
import java.math.BigDecimal
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Der Ablauf mit mehreren Kunden durch die echte Oberfläche: anlegen, für einen bestimmten
 * Kunden erfassen, Übersicht filtern.
 *
 * Mit gesetzter Umgebungsvariable `HC_SCREENSHOTS` legt der Test dort Bildschirmfotos ab.
 */
@OptIn(ExperimentalTestApi::class)
class KundenUiTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @BeforeTest
    fun setUp() {
        val dir = Files.createTempDirectory("honorarcraft-ui")
        db = createDesktopDatabase(dir.resolve(DATABASE_FILE_NAME).toAbsolutePath().toString())
        viewModel = MainViewModel(db, InMemorySettings())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun ComposeUiTest.foto(name: String, knoten: SemanticsNodeInteraction = onRoot()) {
        val ordner = System.getenv("HC_SCREENSHOTS") ?: return
        waitForIdle()
        val bild = knoten.captureToImage().toAwtImage()
        ImageIO.write(bild, "png", File(ordner, "$name.png"))
    }

    private fun ComposeUiTest.feld(label: String) =
        onNode(hasSetTextAction() and hasText(label))

    /** Hinter dem Dialog liegt die Daten-Seite mit gleich beschrifteten Feldern. */
    private fun ComposeUiTest.dialogFeld(label: String) =
        onNode(hasSetTextAction() and hasText(label) and hasAnyAncestor(isDialog()))

    private fun ComposeUiTest.kundeAnlegen(name: String, ort: String, satz: String = "") {
        onNodeWithText("Kunde hinzufügen").performClick()
        dialogFeld("Name/Orga").performTextInput(name)
        dialogFeld("Ort").performTextInput(ort)
        if (satz.isNotEmpty()) dialogFeld("Eigener Honorarsatz € a 45 Minuten").performTextInput(satz)
        if (name == "Bildungswerk Süd") foto("03-kunde-dialog", onNode(isDialog()))
        onNodeWithText("Speichern").performClick()
        waitUntil(timeoutMillis = 5_000) { viewModel.customers.value.any { it.nameOrOrga == name } }
    }

    @Test
    fun `zwei Kunden anlegen, fuer einen erfassen und die Uebersicht filtern`() =
        runDesktopComposeUiTest(width = 1200, height = 820) {
            setContent { HonorarCraftApp(viewModel) }

            // Ohne Kunden weist „Erstellen“ auf die Daten-Seite hin.
            onNodeWithText("Erstellen").performClick()
            onNodeWithText("Noch kein Kunde angelegt").assertExists()
            foto("01-erstellen-ohne-kunde")

            onNodeWithText("Daten").performClick()
            foto("02-daten-ohne-kunde")
            kundeAnlegen("Bildungswerk Süd", "Ulm", satz = "30,00")
            kundeAnlegen("Kolping", "Stuttgart")
            foto("04-daten-zwei-kunden")

            // Der zuletzt angelegte Kunde ist gewählt; zurück auf das Bildungswerk.
            onNodeWithText("Erstellen").performClick()
            onNodeWithText("Kolping").performClick()
            onAllNodesWithText("Bildungswerk Süd").onLast().performClick()
            waitUntil(timeoutMillis = 5_000) { viewModel.selectedCustomer.value?.nameOrOrga == "Bildungswerk Süd" }

            onNodeWithText("Angaben zur Rechnung").performClick()
            feld("Bildungszentrum").performTextInput("Ehingen")
            feld("Stunden").performTextInput("3")
            foto("05-erstellen-mit-kunde")
            onNodeWithContentDescription("Hinzufügen").performClick()

            waitUntil(timeoutMillis = 5_000) { runBlocking { db.invoiceDao().getInvoice("01") } != null }
            val rechnung = runBlocking { db.invoiceDao().getInvoice("01") }!!
            val bildungswerk = viewModel.customers.value.single { it.nameOrOrga == "Bildungswerk Süd" }
            assertEquals(bildungswerk.id, rechnung.customerId)
            assertEquals("Ehingen", rechnung.eduCenter)
            assertEquals(BigDecimal("30.00"), viewModel.lastAddedEntry.value?.rate)

            // Der PDF-Bereich nennt den Kunden der Rechnung.
            onNodeWithText("PDF").performClick()
            foto("06-pdf-mit-kunde")

            // Übersicht: Filter erscheint ab zwei Kunden.
            onNodeWithText("Übersicht").performClick()
            onNodeWithText("Alle Kunden").assertExists()
            foto("07-uebersicht-filter")
        }

    @Test
    fun `eine bestehende Rechnung wechselt den Kunden nur nach Rueckfrage`() =
        runDesktopComposeUiTest(width = 1200, height = 820) {
            val (sued, kolping) = runBlocking {
                val sued = db.customerDao().upsertCustomer(Customer(nameOrOrga = "Bildungswerk Süd"))
                val kolping = db.customerDao().upsertCustomer(Customer(nameOrOrga = "Kolping"))
                db.invoiceDao().insertInvoice(InvoiceData("01", customerId = sued))
                sued to kolping
            }
            fun kundeVon01() = runBlocking { db.invoiceDao().getInvoice("01")!!.customerId }

            setContent { HonorarCraftApp(viewModel) }
            onNodeWithText("Erstellen").performClick()
            runOnIdle { viewModel.setSelectedInvoiceNumber("01") }
            waitUntil(timeoutMillis = 5_000) { viewModel.selectedCustomer.value?.id == sued }

            onNodeWithText("Bildungswerk Süd").performClick()
            onNodeWithText("Kolping").performClick()
            onNodeWithText("Rechnung umhängen?").assertExists()
            foto("10-umhaengen-rueckfrage", onNode(isDialog()))
            onNodeWithText("Abbrechen").performClick()
            waitForIdle()
            assertEquals(sued, kundeVon01())
            assertEquals(sued, viewModel.selectedCustomer.value?.id)

            onNodeWithText("Bildungswerk Süd").performClick()
            onNodeWithText("Kolping").performClick()
            onNodeWithText("Umhängen").performClick()
            waitUntil(timeoutMillis = 5_000) { kundeVon01() == kolping }
        }

    @Test
    fun `schmale Ansicht wie auf dem Handy`() = runDesktopComposeUiTest(width = 400, height = 800) {
        runBlocking {
            db.customerDao().upsertCustomer(
                Customer(nameOrOrga = "Bildungswerk Süd")
            )
        }
        setContent { HonorarCraftApp(viewModel) }
        onNodeWithText("Erstellen").performClick()
        waitUntil(timeoutMillis = 5_000) { viewModel.selectedCustomer.value != null }
        onNodeWithText("Angaben zur Rechnung").performClick()
        foto("08-handy-erstellen")
        onNodeWithText("Daten").performClick()
        foto("09-handy-daten")
    }
}
