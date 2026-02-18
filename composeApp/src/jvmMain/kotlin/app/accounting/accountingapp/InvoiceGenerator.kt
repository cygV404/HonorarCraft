package app.accounting.accountingapp

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun InvoiceGenerator(invoiceNumber: String, onCloseApp: () -> Unit, onBack: () -> Unit) {


    val dateFormatter = remember { java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy") }


    val hourFocusRequester = remember { FocusRequester() }


// State mit dem heutigen Datum initialisieren
    var dateField by remember {
        mutableStateOf(java.time.LocalDate.now().format(dateFormatter))
    }
    var hourField by remember { mutableStateOf("") }
    var teachingSubject by remember { mutableStateOf("") }
    val currentYear = java.time.LocalDate.now().year.toString()

    // Alle Daten-States mit Default-Werten initialisieren
    var allSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    // Diese Variable berechnet sich automatisch neu beim tippen
    val filteredSuggestions = remember(teachingSubject, allSuggestions) {
        allSuggestions.filter {
            it.contains(teachingSubject, ignoreCase = true) && it != teachingSubject
        }
    }
    var showUELabel by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<InvoiceEntry>>(emptyList()) }
    var companyData by remember { mutableStateOf<CompanyData?>(null) }

    var checkedEntries by remember { mutableStateOf(mutableMapOf<InvoiceEntry, Boolean>()) }
    var expanded by remember { mutableStateOf(false) }

    // Zentraler Lade-Block für den Fensterstart
    LaunchedEffect(invoiceNumber) {
        try {
            // alle Ladevorgänge im IO-Thread
            withContext(Dispatchers.IO) {
                val sug = SuggestionsManager.loadSuggestions()
                val ue = SuggestionsManager.loadUEState()
                val ent = SuggestionsManager.loadInvoiceEntries(invoiceNumber)
                val comp = loadCompanyData()
                // Sortierung hier vorbereiten
                val sorted = ent.sortedBy { java.time.LocalDate.parse(it.date, dateFormatter) }
                // Zurück auf dem Main-Thread die UI aktualisieren
                withContext(Dispatchers.Main) {
                    allSuggestions = sug
                    showUELabel = ue
                    entries = sorted
                    companyData = comp
                }
            }
        } catch (e: Exception) {
            println("Fehler beim Laden der Fenster-Daten: ${e.message}")
        }
    }


    val rate = remember(companyData) {
        java.math.BigDecimal(companyData?.hourRate ?: "23.0")
    }

    val invoice = remember(entries, rate) {
        InvoiceData(invoiceNumber, entries, rate)
    }

    var showLoader by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE0A4FE).copy(0.8f))
                .padding(16.dp)
        ) {

            // Left Column
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f),

                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {


                ComposeDatePicker(
                    selectedDate = dateField,
                    onDateSelected = { newDate ->
                        dateField = newDate

                        //Coroutine für den Fokus-Sprung
                        scope.launch {
                            // 50ms warten, bis das Popup-Fenster sicher zu ist
                            kotlinx.coroutines.delay(50)
                            hourFocusRequester.requestFocus()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = hourField,
                    onValueChange = { hourField = it },
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .focusRequester(hourFocusRequester), // Fokus-Wegweiser
                    label = { Text(if (showUELabel) "Unterrichtseinheiten" else "Stunden") },
                    textStyle = TextStyle(
                        fontSize = 16.sp,
                    ),
                    singleLine = true,
                    //colors = OutlinedTextFieldDefaults.colors(
                    //  focusedContainerColor = Color.White,   // Farbe wenn man reinklickt
                    //    unfocusedContainerColor = Color.White, // Farbe wenn nicht ausgewählt
                    // Falls deine Cards eine spezielle Farbe haben (z.B. MaterialTheme.colorScheme.surface),
                    // nimm stattdessen diese Variable.
                    // ),
                    trailingIcon = {
                        Text(
                            text = "⏱",
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable {
                                    val newState = !showUELabel
                                    showUELabel = newState
                                    SuggestionsManager.saveUEState(newState)
                                }
                        )
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth(0.5f)) {
                    OutlinedTextField(
                        value = teachingSubject,
                        onValueChange = {
                            if (it.length <= 39) {
                                teachingSubject = it
                                expanded = it.isNotEmpty()
                            }
                        },
                        label = { Text("Klasse/Unterrichtsfach") },
                        textStyle = TextStyle(
                            fontSize = 16.sp, // Macht das Eingetippte größer
                            // fontWeight = FontWeight.Bold // Optional: fett
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    DropdownMenu(
                        expanded = expanded && filteredSuggestions.isNotEmpty(),
                        onDismissRequest = { expanded = false },
                        properties = androidx.compose.ui.window.PopupProperties(focusable = false)
                    ) {
                        filteredSuggestions.forEach { suggestion ->
                            DropdownMenuItem(
                                text = { Text(suggestion) },
                                onClick = {
                                    teachingSubject = suggestion
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val hours = hourField.replace(",", ".").toDoubleOrNull()

                        if (dateField.isNotBlank() && hours != null) {
                            scope.launch {
                                try {
                                    val newEntry = InvoiceEntry(dateField, hours, teachingSubject)
                                    val updatedEntries = (entries + newEntry).sortedBy {
                                        java.time.LocalDate.parse(it.date, dateFormatter)
                                    }


                                    withContext(Dispatchers.IO) {
                                        SuggestionsManager.addSuggestion(teachingSubject)
                                        SuggestionsManager.saveInvoiceEntries(invoiceNumber, updatedEntries)

                                        val tempInvoice = InvoiceData(invoiceNumber, updatedEntries, rate)
                                        val currentCost =
                                            if (showUELabel) tempInvoice.totalCostsLessonUnits else tempInvoice.totalCostsHours
                                        val currentUE =
                                            if (showUELabel) tempInvoice.totalHours else tempInvoice.totalLessonUnit
                                        val year = if (dateField.length >= 4) dateField.takeLast(4) else currentYear

                                        SuggestionsManager.saveTotalsOnly(
                                            invoiceNumber = invoiceNumber,
                                            totalCost = currentCost.toString(),
                                            totalUE = currentUE.toString(),
                                            year = year
                                        )

                                        // Suggestions im Hintergrund laden
                                        val updatedSugs = SuggestionsManager.loadSuggestions()

                                        // Zurück zum Main-Thread für ALLE UI-Updates auf einmal
                                        withContext(Dispatchers.Main) {
                                            allSuggestions = updatedSugs
                                            entries = updatedEntries
                                            checkedEntries[newEntry] = false
                                            //dateField = ""
                                            hourField = ""
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Fehler beim Speichern: ${e.message}")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.25f)
                ) {
                    Text("Eintrag Speichern")
                }
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val toDelete = entries.filter { checkedEntries[it] == true }
                        if (toDelete.isNotEmpty()) {
                            scope.launch {
                                try {
                                    val updatedEntries = entries - toDelete.toSet()

                                    // Alle Schreibvorgänge im Hintergrund erledigen
                                    withContext(Dispatchers.IO) {
                                        SuggestionsManager.saveInvoiceEntries(invoiceNumber, updatedEntries)

                                        val tempInvoice = InvoiceData(invoiceNumber, updatedEntries, rate)
                                        val currentCost =
                                            if (showUELabel) tempInvoice.totalCostsLessonUnits else tempInvoice.totalCostsHours
                                        val currentUE =
                                            if (showUELabel) tempInvoice.totalHours else tempInvoice.totalLessonUnit
                                        val year = updatedEntries.firstOrNull()?.date?.takeLast(4) ?: currentYear

                                        SuggestionsManager.saveTotalsOnly(
                                            invoiceNumber = invoiceNumber,
                                            totalCost = currentCost.toString(),
                                            totalUE = currentUE.toString(),
                                            year = year
                                        )

                                        // UI-Updates auf dem Main Thread bündeln
                                        withContext(Dispatchers.Main) {
                                            entries = updatedEntries
                                            // Checkboxen sicher aufräumen
                                            toDelete.forEach { checkedEntries.remove(it) }
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("Fehler beim Löschen der Einträge: ${e.message}")
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.25f)
                ) {
                    Text("Eintrag Löschen")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    // Verhindert Doppelklicks und erneutes Starten, während das PDF generiert wird
                    enabled = !showLoader,
                    onClick = {
                        // Vorab-Check
                        val company = loadCompanyData() ?: return@Button
                        val currentNumber = invoiceNumber.toIntOrNull() ?: return@Button

                        scope.launch {
                            try {
                                showLoader = true

                                // PDF Erstellung & Speichern auf dem IO-Thread
                                withContext(Dispatchers.IO) {
                                    createInvoicePdf(
                                        companyData = company,
                                        invoiceData = invoice,
                                        showUELabel = showUELabel
                                    )
                                    saveLastInvoiceNumber(currentNumber + 1)
                                }

                                // Verzögerung Animation
                                kotlinx.coroutines.delay(500)

                            } catch (e: Exception) {
                                // Falls z.B. die Festplatte voll ist oder das PDF offen und blockiert ist
                                println("Fehler bei der PDF-Erstellung: ${e.message}")
                            } finally {
                                // loader immer stoppen
                                showLoader = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.25f)
                ) {
                    if (showLoader) {
                        Text("Erstelle PDF...")
                    } else {
                        Text("PDF erstellen")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))


            }

            // Right Column
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .padding(8.dp)
                    // .border(width = 1.dp, color = Color.Black.copy(alpha = 0.5f), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(
                        color = Color(0xFFEDCDFD).copy(alpha = 0.0f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                    )
                    .padding(top = 24.dp, end = 16.dp, start = 16.dp)
            ) {
                Text(
                    text = "Rechnung: $invoiceNumber",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Header Carc
                Card(
                    modifier = Modifier
                        .fillMaxWidth().padding(bottom = 8.dp)
                        .border(
                            width = 0.5.dp,
                            color = Color.Black.copy(alpha = 0.1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.width(32.dp))
                        Text(
                            "Datum",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "UE a 45 Min",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Kosten",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Klasse/Fach",
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                // Cards mit Einträgen
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    itemsIndexed(entries, key = { _, entry -> entry.id }) { index, entry ->
                        val isChecked = checkedEntries[entry] ?: false


                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem(
                                    // Wie schnell/stark soll die Card einblenden?
                                    fadeInSpec = tween(durationMillis = 500),
                                    // Wie soll die Card nach oben/unten gleiten?
                                    placementSpec = tween(durationMillis = 500),
                                    // Wie soll sie verschwinden?
                                    fadeOutSpec = tween(durationMillis = 400)

                                )
                                .border(
                                    width = 0.5.dp,
                                    color = Color.Black.copy(alpha = 0.1f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                else Color.White.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                                    Checkbox(
                                        modifier = Modifier.scale(0.7f),
                                        checked = isChecked,
                                        onCheckedChange = { wasChecked ->
                                            val newMap = checkedEntries.toMutableMap()
                                            newMap[entry] = wasChecked
                                            checkedEntries = newMap
                                        }
                                    )
                                }

                                Text(
                                    entry.date,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge
                                )


                                if (showUELabel) {
                                    val correctedHours = invoice.calculateCorrectedLessonUnits(entry.hours)
                                    val cost = correctedHours.multiply(invoice.hourRate)
                                        .setScale(2, java.math.RoundingMode.HALF_UP)
                                    Text(
                                        "$correctedHours",
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "$cost €",
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                } else {
                                    val correctedHours = invoice.calculateCorrectedHours(entry.hours)
                                    val cost = correctedHours.multiply(invoice.hourRate)
                                        .setScale(2, java.math.RoundingMode.HALF_UP)
                                    Text(
                                        "$correctedHours",
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        "$cost €",
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }

                                Text(
                                    entry.teachingSubject,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }

                // Summen Card mit dynamischer Slide-Richtung
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 0.5.dp,
                            color = Color.Black.copy(alpha = 0.1f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.4f))
                ) {
                    AnimatedContent(
                        targetState = entries to showUELabel,
                        transitionSpec = {

                            val isIncreasing = targetState.first.size >= initialState.first.size

                            if (isIncreasing) {
                                // Nach oben raus
                                slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                            } else {
                                // Nach unten raus
                                slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
                            }
                        },
                        label = "SumAnimation"
                    ) { (currentEntries, currentShowUE) ->
                        //UI-Werte lokal im AnimatedContent für flüssige Übergänge
                        val tempInvoice = InvoiceData(invoiceNumber, currentEntries, rate)

                        Column(modifier = Modifier.padding(8.dp)) {
                            if (currentShowUE) {
                                Text(
                                    text = "Gesamtkosten: ${tempInvoice.totalCostsLessonUnits} €",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "UE Gesamt: ${tempInvoice.totalHours}",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Text(
                                    text = "Gesamtkosten: ${tempInvoice.totalCostsHours} €",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "UE Gesamt: ${tempInvoice.totalLessonUnit}",
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    }
                }





                Spacer(modifier = Modifier.height(8.dp))
            }


        }

        if (showLoader) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                WavyCircularProgressIndicator(
                    modifier = Modifier.size(120.dp), // Hier gibst du die Größe an
                    color = Color(0xFFEDCDFD)
                )
            }
        }

        FloatingActionButton(
            onClick = onBack,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .size(56.dp) // Standard-Größe für FAB
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go Back",
                tint = Color.White
            )

        }

        // BEENDEN BUTTON
        FloatingActionButton(
            onClick = onCloseApp,
            shape = CircleShape,
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(BiasAlignment(horizontalBias = -0.0f, verticalBias = 1.0f))
                .padding(bottom = 24.dp)
                .size(56.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Beenden",
                tint = Color.White,
            )
        }


    }
}