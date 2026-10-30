package de.v404.honorarcraft.shared

import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.InvoiceEntry
import de.v404.honorarcraft.shared.data.InvoiceDetails
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.Customer
import de.v404.honorarcraft.shared.data.DATABASE_FILE_NAME
import de.v404.honorarcraft.shared.data.InvoiceFormat
import de.v404.honorarcraft.shared.data.createDesktopDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
@OptIn(ExperimentalCoroutinesApi::class)
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
            db.companyDao().insertCompanyData(CompanyData(rate = BigDecimal("31.50")))
            db.customerDao().upsertCustomer(Customer(nameOrOrga = "Ohne eigenen Satz"))
            val vm = viewModel()
            testScheduler.advanceUntilIdle()

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

    // ---------- Mehrere Kunden ----------

    private suspend fun kunde(name: String, satz: String? = null): Long =
        db.customerDao().upsertCustomer(Customer(nameOrOrga = name, rate = satz?.let(::BigDecimal)))

    private fun TestScope.erfasse(vm: MainViewModel, stunden: String = "3") {
        vm.updateInvoiceForm(date = "02.09.2026", hours = stunden, subject = "Mathe")
        vm.addEntryFromForm()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `ohne Kunden wird nichts erfasst`() = test {
        val vm = viewModel()
        val meldung = async { vm.messages.first() }
        testScheduler.advanceUntilIdle()

        erfasse(vm)

        assertTrue(meldung.await().contains("Kunden anlegen"))
        assertNull(vm.lastAddedEntry.value)
    }

    @Test
    fun `der Satz des Kunden geht dem allgemeinen vor`() = test {
        db.companyDao().insertCompanyData(CompanyData(rate = BigDecimal("23.00")))
        kunde("Teurer Kunde", satz = "40.00")
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        erfasse(vm)

        assertEquals(BigDecimal("40.00"), vm.lastAddedEntry.value?.rate)
    }

    @Test
    fun `neue Rechnung gehoert dem gewaehlten Kunden und uebernimmt die Angaben`() = test {
        val a = kunde("A")
        val b = kunde("B")
        val vm = viewModel()
        testScheduler.advanceUntilIdle()

        // Erste Rechnung für B mit Angaben aus dem Entwurf.
        vm.selectCustomer(b)
        vm.setSelectedInvoiceNumber("1")
        testScheduler.advanceUntilIdle()
        vm.updateInvoiceDetails(InvoiceDetails("Zentrum B", "B-1", "Maßnahme B"))
        erfasse(vm)

        val erste = db.invoiceDao().getInvoice("01")!!
        assertEquals(b, erste.customerId)
        assertEquals("Zentrum B", erste.eduCenter)

        // Nächste Nummer: neue Rechnung, wieder für B, Angaben vorbelegt.
        vm.incrementInvoiceNumber()
        testScheduler.advanceUntilIdle()
        assertEquals(b, vm.selectedCustomer.value?.id)
        assertEquals(InvoiceDetails("Zentrum B", "B-1", "Maßnahme B"), vm.invoiceDetails.value)

        // Für A gibt es noch keine Rechnung, also nichts vorzubelegen.
        vm.selectCustomer(a)
        testScheduler.advanceUntilIdle()
        assertEquals(InvoiceDetails(), vm.invoiceDetails.value)
    }

    @Test
    fun `eine bestehende Rechnung schaltet auf ihren Kunden um`() = test {
        val a = kunde("A")
        val b = kunde("B")
        db.invoiceDao().insertInvoice(InvoiceData("05", customerId = b))
        val vm = viewModel()
        vm.selectCustomer(a)
        testScheduler.advanceUntilIdle()

        vm.setSelectedInvoiceNumber("5")
        testScheduler.advanceUntilIdle()

        assertEquals(b, vm.selectedCustomer.value?.id)
        assertEquals(b.toString(), settings.getString(SettingsKeys.SELECTED_CUSTOMER_ID, ""))
    }

    @Test
    fun `Angaben einer bestehenden Rechnung werden direkt gespeichert`() = test {
        val a = kunde("A")
        db.invoiceDao().insertInvoice(InvoiceData("05", customerId = a, eduCenter = "alt"))
        val vm = viewModel()
        vm.setSelectedInvoiceNumber("5")
        testScheduler.advanceUntilIdle()

        vm.updateInvoiceDetails(InvoiceDetails("neu", "", ""))
        testScheduler.advanceUntilIdle()

        assertEquals("neu", db.invoiceDao().getInvoice("05")!!.eduCenter)
        assertEquals("neu", vm.invoiceDetails.value.eduCenter)
    }

    @Test
    fun `der Jahresumsatz folgt dem Kundenfilter`() = test {
        val a = kunde("A")
        val b = kunde("B")
        val dao = db.invoiceDao()
        dao.insertInvoice(InvoiceData("01", customerId = a))
        dao.insertInvoice(InvoiceData("02", customerId = b))
        // 3 h = 4 UE; 4 * 23 = 92,00 und 4 * 10 = 40,00
        dao.insertEntry(InvoiceEntry(invoiceNumber = "01", date = "02.09.2026", lessonUnits = BigDecimal("3"), teachingSubject = "", rate = BigDecimal("23.00")))
        dao.insertEntry(InvoiceEntry(invoiceNumber = "02", date = "03.09.2026", lessonUnits = BigDecimal("3"), teachingSubject = "", rate = BigDecimal("10.00")))

        val vm = viewModel()
        val umsatz = async { vm.yearlyRevenue.first { it.signum() != 0 } }
        vm.setDashboardYear(2026)
        testScheduler.advanceUntilIdle()
        assertEquals(BigDecimal("132.00"), umsatz.await())

        val nurB = async { vm.yearlyRevenue.first { it == BigDecimal("40.00") } }
        vm.setDashboardCustomerFilter(b)
        testScheduler.advanceUntilIdle()
        assertEquals(BigDecimal("40.00"), nurB.await())
        assertEquals(b.toString(), settings.getString(SettingsKeys.DASHBOARD_CUSTOMER_FILTER, ""))
    }

    @Test
    fun `ein Kunde mit Rechnungen laesst sich nur archivieren`() = test {
        val a = kunde("A")
        db.invoiceDao().insertInvoice(InvoiceData("01", customerId = a))
        val vm = viewModel()
        val meldung = async { vm.messages.first() }
        testScheduler.advanceUntilIdle()

        vm.deleteCustomer(a)
        testScheduler.advanceUntilIdle()
        assertTrue(meldung.await().contains("archiviert"))
        assertTrue(db.customerDao().getCustomer(a) != null)

        vm.setCustomerArchived(a, true)
        testScheduler.advanceUntilIdle()
        assertTrue(db.customerDao().getCustomer(a)!!.archived)
        // Die bestehende Rechnung zeigt ihren Kunden weiter, auch archiviert ...
        assertEquals(a, vm.selectedCustomer.value?.id)
        // ... für eine neue Rechnung steht er aber nicht mehr zur Wahl.
        vm.setSelectedInvoiceNumber("9")
        testScheduler.advanceUntilIdle()
        assertNull(vm.selectedCustomer.value, "Archivierte Kunden stehen nicht zur Wahl")
    }

    @Test
    fun `die gewaehlte Darstellung wird gemerkt`() = test {
        val vm = viewModel()
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value, "Vorgabe folgt dem System")

        vm.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, vm.themeMode.value)
        assertEquals("DARK", settings.getString(SettingsKeys.THEME_MODE, ""))

        // Ein neues ViewModel auf demselben Speicher muss die Wahl vorfinden.
        assertEquals(ThemeMode.DARK, MainViewModel(db, settings).themeMode.value)
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
        assertEquals(ThemeMode.SYSTEM, vm.themeMode.value)
        assertEquals("", settings.getString(SettingsKeys.SELECTED_INVOICE_NUMBER, ""))
    }
}
