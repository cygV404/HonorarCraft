package app.accounting.accountingapp


import androidx.compose.animation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// Diese Funktion ermittelt dynamisch den richtigen Speicherort
fun getAppDataFolder(): File {
    val os = System.getProperty("os.name").lowercase()
    val folder = when {
        os.contains("win") -> File(System.getenv("APPDATA"), "HonorarCraft")
        os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/HonorarCraft")
        else -> File(System.getProperty("user.home"), ".honorarcraft") // Linux & Rest
    }

    // Ordner erstellen, falls er noch nicht existiert
    if (!folder.exists()) folder.mkdirs()
    return folder
}

// Jetzt nutzt die Datei diesen dynamischen Pfad
private val invoiceNumberFile = File(getAppDataFolder(), "last_invoice_number.txt")
fun loadLastInvoiceNumber(): Int {
    return if (invoiceNumberFile.exists()) {
        invoiceNumberFile.readText().trim().toIntOrNull() ?: 0
    } else {
        0
    }
}

fun saveLastInvoiceNumber(number: Int) {
    // Erstellt den Ordner falls er fehlt
    invoiceNumberFile.parentFile?.mkdirs()
    invoiceNumberFile.writeText(number.toString())
}


@Composable
fun Dashboard(
    onWeiterClick: (String) -> Unit,
    onOpenData: () -> Unit,
    onClose: () -> Unit
) {
    var invoiceNumber by remember { mutableStateOf(loadLastInvoiceNumber().toString()) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf(java.time.Year.now().value) }

    // State für den Umsatz
    var yearlyRevenue by remember { mutableStateOf("0.00") }

    // States für den Startwert-Dialog
    var showStartValueDialog by remember { mutableStateOf(false) }
    var startValueInput by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedYear, invoiceNumber) {
        try {
            val total = withContext(Dispatchers.IO) {
                SuggestionsManager.calculateYearlyTotal(selectedYear.toString())
            }
            yearlyRevenue = total
        } catch (e: Exception) {
            println("Fehler beim Berechnen des Umsatzes: ${e.message}")
        }
    }
    val textFieldWidthFraction = 0.25f


    Box(modifier = Modifier.fillMaxSize()) {

        //
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // --- Card Jahresumsatz ---
            Card(
                modifier = Modifier
                    .fillMaxWidth(textFieldWidthFraction)
                    .padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            scope.launch(Dispatchers.IO) {

                                val data = SuggestionsManager.loadYearlyData(selectedYear.toString())


                                val existingStartValue = data["S"] ?: ""

                                withContext(Dispatchers.Main) {

                                    startValueInput = existingStartValue
                                    showStartValueDialog = true
                                }
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Functions,
                            contentDescription = "Startwert anpassen",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 2. Mitte: Umsatzanzeige
                    AnimatedContent(
                        targetState = selectedYear,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                            } else {
                                (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                        label = "YearAndRevenueAnimation"
                    ) { targetYear ->
                        SelectionContainer {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Jahresumsatz $targetYear",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$yearlyRevenue €",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                        }


                    }

                    // 3. Rechts: Jahr anpassen
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(onClick = { selectedYear++ }, modifier = Modifier.size(24.dp)) {
                            Text("▲", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                        }
                        IconButton(
                            onClick = { if (selectedYear > 2010) selectedYear-- },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text("▼", color = MaterialTheme.colorScheme.primary, fontSize = 18.sp)
                        }
                    }
                }
            }


// Startwert Dialog
            if (showStartValueDialog) {
                AlertDialog(

                    onDismissRequest = { showStartValueDialog = false },
                    modifier = Modifier.border(
                        width = 2.dp,
                        color = Color.Black,
                        shape = RoundedCornerShape(28.dp) // Standard-Radius für M3 Dialoge
                    ),
                    containerColor = Color(0xFFEDCDFD),
                    title = {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Startwert $selectedYear")
                        }
                    },
                    text = {

                        Column(
                            modifier = Modifier
                                .fillMaxWidth(),

                            horizontalAlignment = Alignment.CenterHorizontally

                        )

                        {


                            Text("Umsätze vor Nutzung der App:", fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = startValueInput,

                                onValueChange = { input ->
                                    // Nur Ziffern und Komma/Punkt erlauben
                                    if (input.all { it.isDigit() || it == '.' || it == ',' }) {
                                        startValueInput = input
                                    }
                                },
                                label = { Text("Betrag in €") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(0.5f)

                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            val cleanValue = startValueInput.replace(",", ".")
                            if (cleanValue.toDoubleOrNull() != null) {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        SuggestionsManager.saveYearlyStartValue(selectedYear.toString(), cleanValue)
                                        yearlyRevenue = SuggestionsManager.calculateYearlyTotal(selectedYear.toString())
                                    }
                                    showStartValueDialog = false
                                    startValueInput = ""
                                }
                            }
                        }) {
                            Text("Speichern")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showStartValueDialog = false }) {
                            Text("Abbrechen")
                        }
                    }
                )
            }


            // Card Jahresumsatz

            Card(
                modifier = Modifier.fillMaxWidth(textFieldWidthFraction).padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            )


            {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Die Box nimmt den Platz ein (weight 1), damit die Pfeile rechts bleiben
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rechnungsnummer", style = MaterialTheme.typography.labelMedium)

                            Box(contentAlignment = Alignment.Center) {
                                AnimatedContent(
                                    targetState = invoiceNumber,
                                    transitionSpec = {
                                        val isIncreasing =
                                            (targetState.toIntOrNull() ?: 0) > (initialState.toIntOrNull() ?: 0)
                                        if (isIncreasing) {
                                            slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                                        } else {
                                            slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
                                        }
                                    },
                                    label = "InvoiceNumberAnimation"
                                ) { targetNumber ->
                                    // 0 erlaubt
                                    if (targetNumber.isEmpty()) {
                                        Text(
                                            text = "Eingeben",
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        )
                                    } else {
                                        Text(
                                            text = targetNumber,
                                            style = MaterialTheme.typography.headlineMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                androidx.compose.foundation.text.BasicTextField(
                                    value = invoiceNumber,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() } || input.isEmpty()) {
                                            invoiceNumber = input
                                            input.toIntOrNull()?.let { saveLastInvoiceNumber(it) }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.headlineMedium.copy(
                                        color = Color.Transparent,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                    singleLine = true
                                )
                            }
                        }
                    }


                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val next = (invoiceNumber.toIntOrNull() ?: 0) + 1
                                invoiceNumber = next.toString()
                                saveLastInvoiceNumber(next)
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(
                                "▲",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(
                            onClick = {
                                val cur = invoiceNumber.toIntOrNull() ?: 0
                                if (cur > 0) { // Erlaubt den Klick runter bis zur 0
                                    val prev = cur - 1
                                    invoiceNumber = prev.toString()
                                    saveLastInvoiceNumber(prev)
                                }
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(
                                "▼",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

// Ende Card

            Button(
                onClick = { onWeiterClick(invoiceNumber) },
                modifier = Modifier.fillMaxWidth(textFieldWidthFraction / 2f)
            ) {
                Text("Rechnung erstellen")
            }

            Spacer(modifier = Modifier.height(16.dp))


        } // Ende der Column


        if (showInfoDialog) {
            AlertDialog(
                onDismissRequest = { showInfoDialog = false },
                title = { Text("Über HonorarCraft") },
                text = {

                    val scrollState = rememberScrollState()


                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState) // Macht den Inhalt scrollbar
                            .padding(end = 8.dp) // Kleiner Puffer für den Scrollbalken
                    ) {


                        Spacer(modifier = Modifier.height(8.dp))
                        SelectionContainer {
                            Text(
                                text = buildAnnotatedString {
                                    // Stil für die Haupt-Überschriften
                                    val headerStyle = SpanStyle(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    // Stil für Zwischen-Überschriften (Fett)
                                    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)

                                    withStyle(headerStyle) { append("HINWEISE ZUR NUTZUNG\n\n") }

                                    withStyle(boldStyle) { append("Anleitung:\n\n") }

                                    withStyle(boldStyle) { append("Schritt 1:\n") }
                                    append("Bitte stelle sicher, dass alle Stammdaten (Steuernummer, Anschrift etc.) korrekt in den Einstellungen (Zahnradsymbol ⚙️) hinterlegt sind, bevor du ein PDF generierst. Empfängerdaten, Bildungsstandort, Schulart/Maßnahme sowie ein Honorarsatz von 23 Euro sind als Standard bereits hinterlegt. Solltest du abweichende Daten haben, passe diese bitte entsprechend an.Zum Speichern der Daten unten auf das Disketten-Symbol 💾 klicken.\n\n")

                                    withStyle(boldStyle) { append("Wichtig: ") }
                                    append("Achte darauf, die korrekte Steuernummer einzutragen (bitte nicht mit der Steuer-ID verwechseln). Das Feld für die Unterschrift ist optional – am besten ein PNG mit transparentem Hintergrund hochladen.\n\n")


                                    withStyle(SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                                        append("⚠️ ACHTUNG - Werkseinstellungen:\n")
                                    }
                                    append("Durch Klick auf den Löschen-Button \uD83D\uDDD1\uFE0F in den Einstellungen werden alle Stammdaten zurückgesetzt. ")
                                    withStyle(boldStyle) {
                                        append("Dabei werden auch alle Rechnungsdaten, Jahresumsätze und die aktuelle Rechnungsnummer unwiderruflich gelöscht. ")
                                    }
                                    append("Bereits generierte PDF-Dateien bleiben hiervon unberührt.\n\n")


                                    withStyle(boldStyle) { append("Schritt 2:\n") }
                                    append("Im Feld für den Jahresumsatz ggf. einen Startwert für Altumsätze wählen (Klick auf das Summenzeichen ∑). Gewünschte Rechnungsnummer wählen und auf \"Rechnung erstellen\" klicken. Hier kannst du Einträge hinzufügen oder löschen. Über das Uhrensymbol 🕒 im Stundenfeld kannst du zwischen Stunden und Unterrichtseinheiten (UE) umschalten. Bitte bleibe pro Rechnung bei einem Konzept (entweder Stunden oder UE).\n\n")

                                    append("Deine Einträge werden gespeichert und beim nächsten Start automatisch geladen. Du kannst auch vorherige Rechnungsnummern im Startfenster wählen, um diese zu bearbeiten.\n\n")

                                    withStyle(boldStyle) { append("Schritt 3:\n") }
                                    append("Nachdem alle Einträge für den Monat erfasst wurden, auf \"PDF erstellen\" klicken. Die Rechnungsnummer wird danach automatisch um eine Stelle erhöht und für die nächste Abrechnung gespeichert.\n\n\n")

                                    withStyle(headerStyle) { append("Sicherheitshinweise:\n") }
                                    append("Der PDF-Export erfolgt standardmäßig in einen unverschlüsselten Pfad. Für maximale Datensicherheit empfehle ich, die Rechnungen in einem verschlüsselten Verzeichnis (z. B. BitLocker oder VeraCrypt) zu speichern.\n\n")

                                    withStyle(boldStyle) { append("Speicherort & Datensicherheit:\n") }
                                    append("Deine Daten werden plattformunabhängig im jeweiligen App-Datenverzeichnis deines Betriebssystems (z. B. unter Windows in 'AppData') im Ordner 'HonorarCraft' gespeichert.\n\n")

                                    append("Um deine Privatsphäre zu schützen, werden alle personenbezogenen Daten (Name, Anschrift, Steuernummer, IBAN/BIC) ")
                                    withStyle(boldStyle) { append("lokal mit AES-Verschlüsselung ") }
                                    append("gesichert. Der dafür notwendige Sicherheitsschlüssel wird sicher im System-Tresor deines Betriebssystems verwaltet. So sind deine Daten selbst dann geschützt, wenn die Dateien unbefugt kopiert werden.\n\n")

                                    withStyle(headerStyle) { append("Rechtlicher Hinweis:\n") }
                                    append("Die berechneten Beträge sollten vor dem Versenden der Rechnung stets auf ihre Richtigkeit geprüft werden. Der Entwickler übernimmt keine Haftung für fehlerhafte Berechnungen oder steuerliche Fehlbehandlungen.\n\n")

                                    append("Bei Fragen, Wünschen oder Anregungen gerne eine Mail an:\n")
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        append("Julian Dobrodolac (v404cyg@proton.me)")
                                    }

                                    append("\n\nViel Erfolg bei der Abrechnung!")
                                },
                                fontSize = 16.sp,
                                lineHeight = 22.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showInfoDialog = false }) {
                        Text("Schließen")
                    }
                },
                icon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }







        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            SmallFloatingActionButton(
                onClick = { showInfoDialog = true },
                shape = CircleShape,
                containerColor = Color(0xFFEDCDFD),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = MaterialTheme.colorScheme.primary)
            }


            FloatingActionButton(
                onClick = onClose,
                shape = CircleShape,
                containerColor = Color(0xFFEDCDFD),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Beenden", tint = MaterialTheme.colorScheme.primary)
            }


            SmallFloatingActionButton(
                onClick = onOpenData,
                shape = CircleShape,
                containerColor = Color(0xFFEDCDFD),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Stammdaten",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

    } // Ende der Box
}









