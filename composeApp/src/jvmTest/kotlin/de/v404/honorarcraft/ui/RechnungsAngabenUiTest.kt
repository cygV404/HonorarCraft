package de.v404.honorarcraft.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
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
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Die Felder der Rechnungsangaben halten ihren Text lokal. Sie dürfen dabei nie die Angaben
 * einer anderen Rechnung zeigen — wer dann tippt, schriebe sie in die falsche Rechnung.
 */
@OptIn(ExperimentalTestApi::class)
class RechnungsAngabenUiTest {

    private lateinit var db: AppDatabase
    private lateinit var viewModel: MainViewModel

    @BeforeTest
    fun setUp() {
        val dir = Files.createTempDirectory("honorarcraft-angaben")
        db = createDesktopDatabase(dir.resolve(DATABASE_FILE_NAME).toAbsolutePath().toString())
        runBlocking {
            val id = db.customerDao().upsertCustomer(Customer(nameOrOrga = "Bildungswerk"))
            db.invoiceDao().insertInvoice(InvoiceData("01", id, eduCenter = "Ulm", locationNr = "11"))
            db.invoiceDao().insertInvoice(InvoiceData("02", id, eduCenter = "Ehingen", locationNr = "22"))
        }
        viewModel = MainViewModel(db, InMemorySettings())
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private fun ComposeUiTest.feld(label: String) =
        onNode(hasSetTextAction() and hasText(label))

    private fun ComposeUiTest.zeigeRechnung(nummer: String, bildungszentrum: String) {
        runOnIdle { viewModel.setSelectedInvoiceNumber(nummer) }
        waitUntil(timeoutMillis = 5_000) { viewModel.invoiceDetails.value.eduCenter == bildungszentrum }
        waitForIdle()
    }

    private fun gespeichert(nummer: String) = runBlocking { db.invoiceDao().getInvoice(nummer)!! }

    @Test
    fun `Rechnungswechsel bei fokussiertem Feld zeigt die neue Rechnung`() =
        runDesktopComposeUiTest(width = 1200, height = 820) {
            setContent { HonorarCraftApp(viewModel) }
            onNodeWithText("Erstellen").performClick()
            zeigeRechnung("01", "Ulm")
            onNodeWithText("Angaben zur Rechnung").performClick()
            feld("Bildungszentrum").performClick()
            feld("Bildungszentrum").assertIsFocused()

            // Wie ein Wechsel über das Rechnungsmenü oben, das den Fokus nicht nimmt.
            zeigeRechnung("02", "Ehingen")
            feld("Bildungszentrum").assert(hasText("Ehingen"))
            feld("Standort Nr").assert(hasText("22"))

            feld("Standort Nr").performTextInput("3")
            waitUntil(timeoutMillis = 5_000) { gespeichert("02").locationNr.length == 3 }
            assertEquals("Ehingen", gespeichert("02").eduCenter)
            assertEquals("Ulm", gespeichert("01").eduCenter)
        }

    @Test
    fun `Zuklappen mit Fokus und Rechnungswechsel zeigt danach die neue Rechnung`() =
        runDesktopComposeUiTest(width = 1200, height = 820) {
            setContent { HonorarCraftApp(viewModel) }
            onNodeWithText("Erstellen").performClick()
            zeigeRechnung("01", "Ulm")
            onNodeWithText("Angaben zur Rechnung").performClick()
            feld("Bildungszentrum").performClick()
            feld("Bildungszentrum").assertIsFocused()

            onNodeWithText("Angaben zur Rechnung").performClick()
            zeigeRechnung("02", "Ehingen")
            onNodeWithText("Angaben zur Rechnung").performClick()
            feld("Bildungszentrum").assert(hasText("Ehingen"))
        }
}
