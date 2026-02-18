package app.accounting.accountingapp


import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val invoiceNumberFile = File(System.getProperty("user.home"), ".accountingapp/last_invoice_number.txt")
fun loadLastInvoiceNumber(): Int {
    return if (invoiceNumberFile.exists()) {
        invoiceNumberFile.readText().trim().toIntOrNull() ?: 0
    } else {
        0
    }
}

fun saveLastInvoiceNumber(number: Int) {
    // Erstellt den Ordner .accountingapp, falls er fehlt
    invoiceNumberFile.parentFile?.mkdirs()
    invoiceNumberFile.writeText(number.toString())
}


@Composable
fun Dashboard(
    onWeiterClick: (String) -> Unit,
    onOpenData: () -> Unit,
    onClose: () -> Unit
) {
    val lastSavedNumber = remember { loadLastInvoiceNumber() }
    var invoiceNumber by remember { mutableStateOf(lastSavedNumber.toString()) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var selectedYear by remember { mutableStateOf(java.time.Year.now().value) }
    //  var showContent by remember { mutableStateOf(true) }

    // State für den Umsatz (startet bei 0.00)
    var yearlyRevenue by remember { mutableStateOf("0.00") }

    // Lade den Umsatz asynchron, sobald sich das Jahr oder die Rechnungsnummer ändert
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
            // Card Jahresumsatz
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

                    AnimatedContent(
                        targetState = selectedYear,
                        transitionSpec = {
                            // Wenn das neue Jahr größer ist, nach oben rollen
                            if (targetState > initialState) {
                                (slideInVertically { it } + fadeIn()).togetherWith(slideOutVertically { -it } + fadeOut())
                            } else {
                                // Wenn kleiner, nach unten rollen
                                (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut())
                            }.using(SizeTransform(clip = false))
                        },
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                        label = "YearAndRevenueAnimation"
                    ) { targetYear ->

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

// Card Rechnungsnummer
            Card(
                modifier = Modifier.fillMaxWidth(textFieldWidthFraction).padding(bottom = 32.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Rechnungsnummer", style = MaterialTheme.typography.labelMedium)

                            Box(contentAlignment = Alignment.Center) {

                                AnimatedContent(
                                    targetState = invoiceNumber,
                                    transitionSpec = {
                                        val isAnyNumber =
                                            (targetState.toIntOrNull() ?: 0) > (initialState.toIntOrNull() ?: 0)
                                        if (isAnyNumber) {
                                            slideInVertically { it } + fadeIn() togetherWith slideOutVertically { -it } + fadeOut()
                                        } else {
                                            slideInVertically { -it } + fadeIn() togetherWith slideOutVertically { it } + fadeOut()
                                        }
                                    },
                                    label = "InvoiceNumberAnimation"
                                ) { targetNumber ->
                                    Text(
                                        text = targetNumber.ifEmpty { " " },
                                        style = MaterialTheme.typography.headlineMedium.copy(
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }

                                // Unsichtbares TextField für Input
                                androidx.compose.foundation.text.BasicTextField(
                                    value = invoiceNumber,
                                    onValueChange = {
                                        if (it.all { c -> c.isDigit() } || it.isEmpty()) invoiceNumber = it
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
                            onClick = { invoiceNumber = ((invoiceNumber.toIntOrNull() ?: 0) + 1).toString() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Text(
                                "▲",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        IconButton(onClick = {
                            val cur = invoiceNumber.toIntOrNull() ?: 0
                            if (cur > 0) invoiceNumber = (cur - 1).toString()
                        }, modifier = Modifier.size(24.dp)) {
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

                        Text(
                            """
    Hinweise zur Nutzung
    
    Schnellanleitung:
    
    Schritt 1:
    Bitte stelle sicher, dass alle Stammdaten (Steuernummer, Anschrift) korrekt in den Einstellungen (Zahnradsymbol) hinterlegt sind, bevor du ein PDF generierst.
    Empfängerdaten, Bildungsstandort, Schulart/Maßnahme sowie ein Honorarsatz von 23 Euro sind als Standard bereits hinterlegt. Solltest du abweichende Daten haben, passe diese bitte entsprechend an.
    
    Wichtig: Achte darauf, die korrekte Steuernummer einzutragen (bitte nicht mit der Steuer-ID verwechseln).
    Das Feld für die Unterschrift ist optional – am besten ein PNG mit transparentem Hintergrund hochladen.
    Zum Speichern unten auf das Disketten-Symbol klicken.
    
    Schritt 2: 
    Gewünschte Rechnungsnummer wählen und auf "Rechnung erstellen" klicken.
    Hier kannst du Einträge hinzufügen oder löschen. Über das Uhrensymbol im Stundenfeld kannst du zwischen Stunden und Unterrichtseinheiten (UE) umschalten. 
    Bitte bleibe pro Rechnung bei einem Konzept (entweder Stunden oder UE).
    
    Deine Einträge werden gespeichert und beim nächsten Start automatisch geladen. Du kannst auch vorherige Rechnungsnummern im Startfenster wählen, um diese zu bearbeiten.
    
    Schritt 3:
    Nachdem alle Einträge für den Monat erfasst wurden, auf "PDF erstellen" klicken.
    Die Rechnungsnummer wird danach automatisch um eine Stelle erhöht und für die nächste Abrechnung gespeichert.
    
    Rechtlicher Hinweis:
    Die berechneten Beträge sollten vor dem Versenden der Rechnung stets auf ihre Richtigkeit geprüft werden. Der Entwickler übernimmt keine Haftung für fehlerhafte Berechnungen oder steuerliche Fehlbehandlungen.
    
    Viel Erfolg bei der Abrechnung!
    """.trimIndent()
                        )
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
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Info, contentDescription = "Info", tint = Color.White)
            }


            FloatingActionButton(
                onClick = onClose,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Beenden", tint = Color.White)
            }


            SmallFloatingActionButton(
                onClick = onOpenData,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Stammdaten", tint = Color.White)
            }
        }

    } // Ende der Box
}









