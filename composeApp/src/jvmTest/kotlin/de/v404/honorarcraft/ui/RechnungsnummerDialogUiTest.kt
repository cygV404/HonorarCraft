package de.v404.honorarcraft.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runDesktopComposeUiTest
import de.v404.honorarcraft.shared.InMemorySettings
import de.v404.honorarcraft.shared.MainViewModel
import de.v404.honorarcraft.shared.SettingsKeys
import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.InvoiceFormat
import de.v404.honorarcraft.shared.data.createDesktopDatabase
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Der Dialog „Rechnungsnummer bearbeiten“ darf Jahr und Monat nicht festschreiben, wenn nur
 * die Nummer geändert wurde — sonst folgen sie nicht mehr dem Kalender.
 */
@OptIn(ExperimentalTestApi::class)
class RechnungsnummerDialogUiTest {

    private lateinit var db: AppDatabase
    private lateinit var settings: InMemorySettings
    private lateinit var viewModel: MainViewModel

    @BeforeTest
    fun setUp() {
        val dir = Files.createTempDirectory("honorarcraft-nummer")
        db = createDesktopDatabase(dir.resolve(DATABASE_FILE_NAME).toAbsolutePath().toString())
        settings = InMemorySettings()
        viewModel = MainViewModel(db, settings)
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    @Test
    fun `nur die Nummer zu aendern speichert weder Jahr noch Monat`() =
        runDesktopComposeUiTest(width = 1200, height = 820) {
            setContent { HonorarCraftApp(viewModel) }
            for (format in InvoiceFormat.entries) {
                runOnIdle { viewModel.setInvoiceFormat(format) }
                onNodeWithText("Rechnungsnummer").performClick()
                onNodeWithTag("rechnungNummer").performTextReplacement("5")
                onNodeWithText("Speichern").performClick()
                waitForIdle()

                assertEquals("05", viewModel.selectedInvoiceNumber.value, "$format")
                assertEquals(-1, settings.getInt(SettingsKeys.INVOICE_YEAR, -1), "$format")
                assertEquals(-1, settings.getInt(SettingsKeys.INVOICE_MONTH, -1), "$format")
            }
        }

    @Test
    fun `ein geaenderter Monat wird gespeichert, ein ungueltiger gesperrt`() =
        runDesktopComposeUiTest(width = 1200, height = 820) {
            runOnIdle { viewModel.setInvoiceFormat(InvoiceFormat.YEAR_MONTH_NUMBER) }
            setContent { HonorarCraftApp(viewModel) }
            val monat = if (viewModel.invoiceMonth.value == 12) 1 else viewModel.invoiceMonth.value + 1

            onNodeWithText("Rechnungsnummer").performClick()
            onNodeWithTag("rechnungMonat").performTextReplacement("13")
            onNodeWithText("Speichern").assertIsNotEnabled()
            onNodeWithTag("rechnungMonat").performTextReplacement("0")
            onNodeWithText("Speichern").assertIsNotEnabled()

            onNodeWithTag("rechnungMonat").performTextReplacement(monat.toString())
            onNodeWithText("Speichern").assertIsEnabled().performClick()
            waitForIdle()

            assertEquals(monat, settings.getInt(SettingsKeys.INVOICE_MONTH, -1))
            assertEquals(-1, settings.getInt(SettingsKeys.INVOICE_YEAR, -1), "Jahr blieb gleich")
        }
}
