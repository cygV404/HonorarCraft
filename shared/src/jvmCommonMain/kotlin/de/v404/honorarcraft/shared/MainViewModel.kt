package de.v404.honorarcraft.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.v404.honorarcraft.shared.data.AppDatabase
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.Constants
import de.v404.honorarcraft.shared.data.HiddenSubject
import de.v404.honorarcraft.shared.data.InvoiceData
import de.v404.honorarcraft.shared.data.InvoiceEntry
import de.v404.honorarcraft.shared.data.InvoiceFormat
import de.v404.honorarcraft.shared.data.InvoiceWithEntries
import de.v404.honorarcraft.shared.data.formatInvoice
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val TAG = "MainViewModel"

/**
 * Gemeinsamer Zustand für Android und Desktop.
 *
 * Datenbank und Einstellungsspeicher werden hereingereicht, statt sie aus einem
 * Application-Objekt zu ziehen — nur so ist das ViewModel plattformunabhängig und im Test
 * ohne Emulator benutzbar. Alles, was eine echte Plattform braucht (PDF-Erzeugung, Backup
 * über Datei-Dialoge), bleibt bewusst draußen; die Oberfläche ruft danach
 * [incrementInvoiceNumber] bzw. die plattformeigene Backup-Schicht auf.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(
    private val database: AppDatabase,
    private val settings: Settings,
) : ViewModel() {
    private val invoiceDao = database.invoiceDao()
    private val companyDao = database.companyDao()
    private val maintenanceDao = database.maintenanceDao()

    /**
     * Kurze Rückmeldungen an den Nutzer. Ersetzt die Toast-Aufrufe: welches Bauteil daraus
     * wird (Toast, Snackbar), entscheidet die jeweilige Oberfläche.
     */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun notifyUser(text: String) {
        _messages.tryEmit(text)
    }

    // Loading State
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Navigation State
    private val _selectedTabIndex = MutableStateFlow(0)
    val selectedTabIndex: StateFlow<Int> = _selectedTabIndex.asStateFlow()

    private val _hasUnsavedChanges = MutableStateFlow(false)
    val hasUnsavedChanges: StateFlow<Boolean> = _hasUnsavedChanges.asStateFlow()

    private val _pendingTabIndex = MutableStateFlow<Int?>(null)
    val pendingTabIndex: StateFlow<Int?> = _pendingTabIndex.asStateFlow()

    // Trigger to reset UI state in DataWindow (increments to force a discard)
    private val _resetDataWindowTrigger = MutableStateFlow(0)
    val resetDataWindowTrigger: StateFlow<Int> = _resetDataWindowTrigger.asStateFlow()

    // Dashboard Year (for Revenue display)
    private val _dashboardYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val dashboardYear: StateFlow<Int> = _dashboardYear.asStateFlow()

    // Selection for Year and Invoice (for Invoice Number generation)
    private val _invoiceYear = MutableStateFlow(
        settings.getInt(SettingsKeys.INVOICE_YEAR, Calendar.getInstance().get(Calendar.YEAR))
    )
    val invoiceYear: StateFlow<Int> = _invoiceYear.asStateFlow()

    private val _invoiceMonth = MutableStateFlow(
        settings.getInt(SettingsKeys.INVOICE_MONTH, Calendar.getInstance().get(Calendar.MONTH) + 1)
    )
    val invoiceMonth: StateFlow<Int> = _invoiceMonth.asStateFlow()

    private val _selectedInvoiceNumber = MutableStateFlow(
        settings.getString(SettingsKeys.SELECTED_INVOICE_NUMBER, "1")
    )
    val selectedInvoiceNumber: StateFlow<String> = _selectedInvoiceNumber.asStateFlow()

    private val _invoiceFormat = MutableStateFlow(
        runCatching {
            InvoiceFormat.valueOf(
                settings.getString(SettingsKeys.INVOICE_FORMAT, InvoiceFormat.NUMBER.name)
            )
        }.getOrDefault(InvoiceFormat.NUMBER)
    )
    val invoiceFormat: StateFlow<InvoiceFormat> = _invoiceFormat.asStateFlow()

    val formattedInvoiceNumber: StateFlow<String> = combine(
        _selectedInvoiceNumber,
        _invoiceFormat,
        _invoiceYear,
        _invoiceMonth
    ) { number, format, year, month ->
        formatInvoice(number, format, year, month)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        formatInvoice(
            _selectedInvoiceNumber.value,
            _invoiceFormat.value,
            _invoiceYear.value,
            _invoiceMonth.value
        )
    )

    // Form State for CreateInvoice
    private val _currentDate = MutableStateFlow(getCurrentDateFormatted())
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _currentHours = MutableStateFlow("")
    val currentHours: StateFlow<String> = _currentHours.asStateFlow()

    private val _currentSubject = MutableStateFlow("")
    val currentSubject: StateFlow<String> = _currentSubject.asStateFlow()

    private val _lastAddedEntry = MutableStateFlow<InvoiceEntry?>(null)
    val lastAddedEntry: StateFlow<InvoiceEntry?> = _lastAddedEntry.asStateFlow()

    private val _showEntryConfirmation = MutableStateFlow(false)
    val showEntryConfirmation: StateFlow<Boolean> = _showEntryConfirmation.asStateFlow()

    private var confirmationJob: Job? = null

    // Yearly Revenue - linked to _dashboardYear
    val yearlyRevenue: StateFlow<BigDecimal> = _dashboardYear
        .flatMapLatest { year ->
            invoiceDao.getInvoicesWithEntriesByYear(year.toString())
                .map { invoices -> year to invoices }
        }
        .map { (year, invoices) ->
            invoices.fold(BigDecimal.ZERO) { acc, invoiceWithEntries ->
                val yearStr = year.toString()
                val entriesForYear = invoiceWithEntries.entries.filter { it.date.endsWith(yearStr) }
                
                val sumForYear = entriesForYear.fold(BigDecimal.ZERO) { entryAcc, entry ->
                    val ue = entry.lessonUnits.multiply(BigDecimal("60"))
                        .divide(BigDecimal("45"), 10, RoundingMode.HALF_UP)
                    entryAcc.add(ue.multiply(entry.rate))
                }
                acc.add(sumForYear)
            }.setScale(2, RoundingMode.HALF_UP)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BigDecimal.ZERO)

    // Company Data
    val companyData: StateFlow<CompanyData?> = companyDao.getCompanyData()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Current Invoice with Entries - reactive to formattedInvoiceNumber
    val currentInvoiceWithEntries: StateFlow<InvoiceWithEntries?> = formattedInvoiceNumber
        .flatMapLatest { number -> invoiceDao.getInvoiceWithEntries(number) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // All available invoice numbers for dropdowns
    val allInvoiceNumbers: StateFlow<List<String>> = invoiceDao.getAllInvoiceNumbers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unique subjects for suggestions
    val uniqueSubjects: StateFlow<List<String>> = invoiceDao.getUniqueSubjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun getCurrentDateFormatted(): String {
        val sdf = SimpleDateFormat(Constants.DATE_PATTERN, Locale.GERMANY)
        return sdf.format(Date())
    }

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun requestTabChange(index: Int) {
        if (_hasUnsavedChanges.value && _selectedTabIndex.value == 3 && index != 3) {
            _pendingTabIndex.value = index
        } else {
            _selectedTabIndex.value = index
            _pendingTabIndex.value = null
        }
    }

    fun updateTabIndexFromPager(index: Int) {
        if (_selectedTabIndex.value != index) {
            _selectedTabIndex.value = index
        }
    }

    fun confirmTabChange() {
        _pendingTabIndex.value?.let { target ->
            _hasUnsavedChanges.value = false
            _resetDataWindowTrigger.value += 1
            _selectedTabIndex.value = target
            _pendingTabIndex.value = null
        }
    }

    fun cancelTabChange() {
        _pendingTabIndex.value = null
    }

    fun setHasUnsavedChanges(hasChanges: Boolean) {
        _hasUnsavedChanges.value = hasChanges
    }

    fun setDashboardYear(year: Int) {
        _dashboardYear.value = year
    }

    fun setInvoiceYear(year: Int) {
        _invoiceYear.value = year
        settings.putInt(SettingsKeys.INVOICE_YEAR, year)
    }

    fun setInvoiceMonth(month: Int) {
        _invoiceMonth.value = month
        settings.putInt(SettingsKeys.INVOICE_MONTH, month)
    }

    fun setSelectedInvoiceNumber(number: String) {
        // Check if it's a composite number like "2026-01-01" or "2026-01"
        val parts = number.split("-")
        if (parts.size >= 2) {
            val yearPart = parts[0].toIntOrNull()
            val monthOrNumPart = parts[1].toIntOrNull()
            
            if (yearPart != null) _invoiceYear.value = yearPart
            
            if (parts.size == 3) {
                // Format: YEAR-MONTH-NUMBER
                if (monthOrNumPart != null) _invoiceMonth.value = monthOrNumPart
                val numPart = parts[2].toIntOrNull()
                val formattedNum = if (numPart != null) String.format(Locale.GERMANY, "%02d", numPart) else parts[2]
                _selectedInvoiceNumber.value = formattedNum
            } else if (parts.size == 2 && invoiceFormat.value == InvoiceFormat.YEAR_NUMBER) {
                // Format: YEAR-NUMBER
                val numPart = parts[1].toIntOrNull()
                val formattedNum = if (numPart != null) String.format(Locale.GERMANY, "%02d", numPart) else parts[1]
                _selectedInvoiceNumber.value = formattedNum
            } else {
                // Fallback for simple number or unknown format
                val numInt = number.toIntOrNull()
                val formatted = if (numInt != null) String.format(Locale.GERMANY, "%02d", numInt) else number
                _selectedInvoiceNumber.value = formatted
            }
        } else {
            val numInt = number.toIntOrNull()
            val formatted = if (numInt != null) String.format(Locale.GERMANY, "%02d", numInt) else number
            _selectedInvoiceNumber.value = formatted
        }
        
        settings.putString(SettingsKeys.SELECTED_INVOICE_NUMBER, _selectedInvoiceNumber.value)
        
        // Reset confirmation card to avoid "ghost" entries from previous invoice
        _lastAddedEntry.value = null
        _showEntryConfirmation.value = false
    }

    fun setInvoiceFormat(format: InvoiceFormat) {
        _invoiceFormat.value = format
        settings.putString(SettingsKeys.INVOICE_FORMAT, format.name)
    }

    fun incrementInvoiceNumber() {
        val current = _selectedInvoiceNumber.value.toIntOrNull() ?: 0
        setSelectedInvoiceNumber((current + 1).toString())
    }

    fun updateInvoiceForm(date: String? = null, hours: String? = null, subject: String? = null) {
        date?.let { _currentDate.value = it }
        hours?.let { _currentHours.value = it }
        subject?.let { _currentSubject.value = it }
    }

    fun saveCompanyData(data: CompanyData) {
        viewModelScope.launch {
            _isLoading.value = true
            companyDao.insertCompanyData(data)
            _isLoading.value = false
            _hasUnsavedChanges.value = false
        }
    }

    /**
     * Blendet einen Fachvorschlag aus der Vorschlagsliste aus.
     *
     * Löscht bewusst keine Rechnungspositionen: der Vorschlag verschwindet, die
     * gebuchten Belege bleiben. Wird das Fach später wieder gebucht, taucht der
     * Vorschlag automatisch wieder auf (siehe [addEntryFromForm]).
     */
    fun hideSubjectSuggestion(subject: String) {
        viewModelScope.launch {
            invoiceDao.hideSubject(HiddenSubject(subject.trim()))
        }
    }

    fun addEntryFromForm() {
        // Durchgaengig getrimmt: ein unsichtbares Leerzeichen im Stundenfeld liess
        // toBigDecimalOrNull() zuvor scheitern ("Ungültige Stundenzahl", obwohl das
        // Feld gueltig aussah), im Fachfeld erzeugte es doppelte Vorschlaege, und im
        // Datumsfeld haette es date.endsWith(jahr) und damit den Jahresumsatz gekippt.
        val datum = _currentDate.value.trim()
        val stunden = _currentHours.value.trim()
        val klasseFach = _currentSubject.value.trim()

        if (datum.isBlank() || stunden.isBlank()) return

        val number = formattedInvoiceNumber.value
        val hours = stunden.replace(",", ".").toBigDecimalOrNull()

        if (hours == null) {
            notifyUser("Ungültige Stundenzahl")
            return
        }

        viewModelScope.launch {
            val existingInvoice = invoiceDao.getInvoice(number)
            val cd = companyDao.getCompanyData().first()
            val rate = cd?.rate ?: Constants.DEFAULT_RATE

            if (existingInvoice == null) {
                invoiceDao.insertInvoice(InvoiceData(invoiceNumber = number))
            }

            val entry = InvoiceEntry(
                invoiceNumber = number,
                date = datum,
                lessonUnits = hours,
                teachingSubject = klasseFach,
                rate = rate
            )
            invoiceDao.insertEntry(entry)
            // Wer ein Fach wieder bucht, will es auch wieder vorgeschlagen bekommen.
            if (klasseFach.isNotBlank()) invoiceDao.unhideSubject(klasseFach)

            confirmationJob?.cancel()
            _lastAddedEntry.value = entry
            _showEntryConfirmation.value = true

            _currentHours.value = ""
            _currentSubject.value = ""
            _currentDate.value = getCurrentDateFormatted()

            confirmationJob = launch {
                delay(4500)  // Delay für 4.5 Sekunden
                _showEntryConfirmation.value = false
            }
        }
    }

    fun deleteEntries(entries: List<InvoiceEntry>) {
        viewModelScope.launch {
            invoiceDao.deleteEntries(entries)
        }
    }

    fun deleteInvoice(invoiceNumber: String) {
        viewModelScope.launch {
            invoiceDao.deleteInvoice(InvoiceData(invoiceNumber))
            if (_selectedInvoiceNumber.value == invoiceNumber) {
                setSelectedInvoiceNumber("1")
            }
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Kein withContext(Dispatchers.IO): Room führt seine suspend-Abfragen
                // ohnehin auf dem Abfrage-Kontext der Datenbank aus.
                maintenanceDao.clearAllTables()
                settings.clear()

                // Reset StateFlows to default values
                val now = Calendar.getInstance()
                _dashboardYear.value = now.get(Calendar.YEAR)
                _invoiceYear.value = now.get(Calendar.YEAR)
                _invoiceMonth.value = now.get(Calendar.MONTH) + 1
                _selectedInvoiceNumber.value = "1"
                _invoiceFormat.value = InvoiceFormat.NUMBER

                _hasUnsavedChanges.value = false
                _resetDataWindowTrigger.value += 1
            } catch (e: Exception) {
                logError(TAG, "Zurücksetzen fehlgeschlagen", e)
                notifyUser("Fehler beim Zurücksetzen: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun resetCompanyData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                companyDao.insertCompanyData(CompanyData(id = 1, rate = Constants.DEFAULT_RATE))
                _hasUnsavedChanges.value = false
                _resetDataWindowTrigger.value += 1
            } catch (e: Exception) {
                logError(TAG, "Zurücksetzen fehlgeschlagen", e)
                notifyUser("Fehler beim Zurücksetzen: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Rückmeldung aus der plattformeigenen Schicht (PDF-Erzeugung, Backup) in denselben
     * Kanal wie die Meldungen des ViewModels.
     */
    fun showMessage(text: String) = notifyUser(text)
}
