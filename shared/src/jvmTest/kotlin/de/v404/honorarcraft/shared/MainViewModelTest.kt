package de.v404.honorarcraft.shared

import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.InvoiceFormat
import de.v404.honorarcraft.shared.data.createDesktopDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.math.BigDecimal
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Das ViewModel aus der Android-App läuft jetzt ohne Emulator: Datenbank und
 * Einstellungsspeicher werden hereingereicht, statt aus einem Application-Objekt zu kommen.
 */
class MainViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: AppDatabase
    private lateinit var settings: InMemorySettings

    @BeforeTest
    fun setUp() {
        // Ein einziger Scheduler für viewModelScope und für die Room-Abfragen, sonst wartet
        // advanceUntilIdle auf Arbeit, die längst auf einem anderen Thread läuft.
        Dispatchers.setMain(dispatcher)
        val dir = Files.createTempDirectory("honorarcraft-vm")
        db = createDesktopDatabase(
            path = dir.resolve(DATABASE_FILE_NAME).toAbsolutePath().toString(),
            queryContext = dispatcher,
        )
        settings = InMemorySettings()
    }

    /** Alle Tests laufen auf demselben Scheduler wie das ViewModel. */
    private fun test(block: suspend TestScope.() -> Unit) = runTest(dispatcher, testBody = block)

    @AfterTest
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun viewModel() = MainViewModel(db, settings)

    @Test
    fun `laufende Nummer wird auf zwei Stellen aufgefuellt und gemerkt`() = test {
        val vm = viewModel()
        vm.setSelectedInvoiceNumber("7")

        assertEquals("07", vm.selectedInvoiceNumber.value)
        assertEquals("07", settings.getString(SettingsKeys.SELECTED_INVOICE_NUMBER, ""))
    }

    @Test
    fun `zusammengesetzte Nummer setzt Jahr und Monat mit`() = test {
        val vm = viewModel()
        vm.setSelectedInvoiceNumber("2025-03-04")

        assertEquals(2025, vm.invoiceYear.value)
        assertEquals(3, vm.invoiceMonth.value)
        assertEquals("04", vm.selectedInvoiceNumber.value)
    }

    @Test
    fun `Format YEAR_NUMBER zerlegt Jahr und Nummer`() = test {
        val vm = viewModel()
        vm.setInvoiceFormat(InvoiceFormat.YEAR_NUMBER)
        vm.setSelectedInvoiceNumber("2024-09")

        assertEquals(2024, vm.invoiceYear.value)
        assertEquals("09", vm.selectedInvoiceNumber.value)
    }

    @Test
    fun `Hochzaehlen nach dem PDF erhoeht die Nummer`() = test {
        val vm = viewModel()
        vm.setSelectedInvoiceNumber("9")
        vm.incrementInvoiceNumber()

        assertEquals("10", vm.selectedInvoiceNumber.value)
    }

    @Test
    fun `ungueltige Stundenzahl meldet sich statt zu speichern`() = test {
        val vm = viewModel()
        // messages hat keinen Replay - der Sammler muss vor dem Auslösen stehen.
        val meldung = async { vm.messages.first() }
        testScheduler.advanceUntilIdle()

        vm.updateInvoiceForm(date = "02.09.2026", hours = "keine Zahl", subject = "Mathe")
        vm.addEntryFromForm()
        testScheduler.advanceUntilIdle()

        assertEquals("Ungültige Stundenzahl", meldung.await())
        assertNull(vm.lastAddedEntry.value)
    }

    @Test
    fun `Eintrag uebernimmt den Honorarsatz aus den Firmendaten und trimmt die Eingaben`() =
        test {
            val vm = viewModel()
                db.companyDao().insertCompanyData(CompanyData(rate = BigDecimal("31.50")))

            vm.updateInvoiceForm(date = " 02.09.2026 ", hours = " 3 ", subject = " Mathe ")
            vm.addEntryFromForm()
            testScheduler.advanceUntilIdle()

            val entry = vm.lastAddedEntry.value
            assertTrue(entry != null, "Eintrag muss angelegt worden sein")
            assertEquals("02.09.2026", entry.date)
            assertEquals("Mathe", entry.teachingSubject)
            assertEquals(BigDecimal("31.50"), entry.rate)
            assertEquals(BigDecimal("3"), entry.lessonUnits)
        }

    @Test
    fun `Zuruecksetzen leert Daten und Einstellungen`() = test {
        val vm = viewModel()
        vm.setSelectedInvoiceNumber("42")
        vm.updateInvoiceForm(date = "02.09.2026", hours = "3", subject = "Mathe")
        vm.addEntryFromForm()
        testScheduler.advanceUntilIdle()

        vm.resetAllData()
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), db.invoiceDao().getAllInvoiceNumbers().first())
        assertEquals("1", vm.selectedInvoiceNumber.value)
        assertEquals(InvoiceFormat.NUMBER, vm.invoiceFormat.value)
        assertEquals("", settings.getString(SettingsKeys.SELECTED_INVOICE_NUMBER, ""))
    }
}
