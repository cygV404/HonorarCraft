package de.v404.honorarcraft.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.v404.honorarcraft.ui.theme.HonorarCraftTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import de.v404.honorarcraft.shared.MainViewModel
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.backup.Backup
import de.v404.honorarcraft.shared.backup.NeustartNoetigException
import de.v404.honorarcraft.ui.platform.LocalPlatformServices
import de.v404.honorarcraft.ui.platform.loadImageFromFile
import androidx.compose.runtime.produceState

@Composable
fun DataWindowScreen(
    mainViewModel: MainViewModel,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val platform = LocalPlatformServices.current
    val scope = rememberCoroutineScope()
    val savedData by mainViewModel.companyData.collectAsState()
    val isLoading by mainViewModel.isLoading.collectAsState()
    val resetTrigger by mainViewModel.resetDataWindowTrigger.collectAsState()

    DataWindowContent(
        savedData = savedData,
        isLoading = isLoading,
        resetTrigger = resetTrigger,
        onSave = { mainViewModel.saveCompanyData(it) },
        onResetAll = { mainViewModel.resetAllData() },
        onResetCompanyOnly = { mainViewModel.resetCompanyData() },
        onChanged = { mainViewModel.setHasUnsavedChanges(true) },
        onExport = {
            scope.launch {
                mainViewModel.setLoading(true)
                val ergebnis = platform.exportBackup()
                mainViewModel.setLoading(false)
                when {
                    // null heisst: der Nutzer hat den Dateidialog abgebrochen.
                    ergebnis == null -> Unit
                    ergebnis.isSuccess -> mainViewModel.showMessage("Sicherung gespeichert")
                    else -> mainViewModel.showMessage(
                        "Sicherung fehlgeschlagen: " + ergebnis.exceptionOrNull()?.message
                    )
                }
            }
        },
        onImport = {
            scope.launch {
                mainViewModel.setLoading(true)
                val ergebnis = platform.importBackup()
                mainViewModel.setLoading(false)
                when {
                    ergebnis == null -> Unit
                    ergebnis.isSuccess -> {
                        mainViewModel.showMessage("Sicherung eingelesen – die App startet neu")
                        platform.restartApp()
                    }
                    else -> {
                        val fehler = ergebnis.exceptionOrNull()
                        mainViewModel.showMessage(fehler?.message ?: "Import fehlgeschlagen")
                        // Scheiterte der Tausch erst nach dem Schliessen der Verbindung, sind
                        // die Daten zwar heil, die laufende App aber nicht mehr benutzbar.
                        if (fehler is NeustartNoetigException) platform.restartApp()
                    }
                }
            }
        },
        onPickSignature = { uebernehmen ->
            scope.launch {
                val pfad = platform.pickSignatureImage()
                if (pfad == null) {
                    // Abbruch im Dialog ist kein Fehler; ein Fehler beim Kopieren schon.
                    return@launch
                }
                uebernehmen(pfad)
            }
        },
        selectedTabIndex = selectedTabIndex,
        onTabSelected = onTabSelected
    )
}

/**
 * Laedt die Unterschrift als Vorschau-Bitmap.
 *
 * Bewusst ohne Bibliothek: die App zeigt genau ein lokales Bild an, und Coil zog dafuer
 * frueher OkHttp samt Netzwerk-Stack ins APK - in einer App ohne INTERNET-Berechtigung.
 * Das Herunterskalieren passiert in [loadImageFromFile], je Plattform verschieden.
 */
@Composable
private fun rememberSignaturePreview(pfad: String): ImageBitmap? {
    val bild by produceState<ImageBitmap?>(initialValue = null, key1 = pfad) {
        value = if (pfad.isBlank()) null
        else loadImageFromFile(pfad, SIGNATURE_PREVIEW_MAX_WIDTH_PX)
    }
    return bild
}

/** Reicht fuer eine 120 dp hohe Vorschau auch auf sehr dichten Bildschirmen. */
private const val SIGNATURE_PREVIEW_MAX_WIDTH_PX = 1080

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataWindowContent(
    savedData: CompanyData?,
    isLoading: Boolean,
    resetTrigger: Int,
    onSave: (CompanyData) -> Unit,
    onResetAll: () -> Unit,
    onResetCompanyOnly: () -> Unit,
    onChanged: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    /** Öffnet die Bildauswahl und meldet den Pfad der angelegten Kopie zurück. */
    onPickSignature: ((String) -> Unit) -> Unit,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    // Initialisierung des lokalen Zustands mit den Daten aus der Datenbank.
    // Durch remember(savedData, resetTrigger) greift remember hier jedes Mal neu,
    // wenn sich die Daten ändern oder ein Reset erzwungen wird.
    var companyDataState by remember(savedData, resetTrigger) {
        mutableStateOf(savedData?.copy() ?: CompanyData())
    }

    var rateText by remember(savedData, resetTrigger) {
        mutableStateOf(companyDataState.rate.toString().replace(".", ","))
    }

    var showMenu by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }
    var showResetCompanyDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Vor dem Einlesen wird nachgefragt - der Vorgang ersetzt alles Vorhandene.
    //
    // Die Reihenfolge ist gegenueber der Android-Fassung vertauscht: dort wurde erst die
    // Datei gewaehlt und danach gefragt. Die gemeinsame Plattformschicht erledigt Auswahl
    // und Einlesen in einem Schritt, deshalb steht die Rueckfrage jetzt davor. Abbrechen
    // geht weiterhin auch noch im Dateidialog.
    var importBestaetigen by remember { mutableStateOf(false) }

    if (importBestaetigen) {
        AlertDialog(
            onDismissRequest = { importBestaetigen = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Sicherung einlesen") },
            text = {
                Text(
                    "Alle aktuellen Rechnungen, Positionen und Firmendaten werden durch " +
                        "die Sicherung ersetzt. Das lässt sich nicht rückgängig machen.\n\n" +
                        "Die App startet danach neu."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        importBestaetigen = false
                        onImport()
                    }
                ) {
                    Text("Ersetzen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { importBestaetigen = false }) { Text("Abbrechen") }
            }
        )
    }

    // Dialog für ALLES zurücksetzen
    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            icon = {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Werkseinstellungen") },
            text = {
                Text(
                    "Möchten Sie wirklich alle Daten zurücksetzen? " +
                            "Dabei werden alle Kundendaten, Rechnungen und Rechnungsnummern gelöscht. " +
                            "Bereits generierte PDF-Dateien bleiben davon unberührt."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetAll()
                        showResetAllDialog = false
                    }
                ) {
                    Text("Alles löschen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    // Dialog für nur Datenfelder zurücksetzen
    if (showResetCompanyDialog) {
        AlertDialog(
            onDismissRequest = { showResetCompanyDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Datenfelder leeren") },
            text = {
                Text(
                    "Möchten Sie nur die persönlichen Datenfelder und Firmendaten leeren? " +
                            "Ihre Rechnungen und Einträge bleiben erhalten."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onResetCompanyOnly()
                        showResetCompanyDialog = false
                    }
                ) {
                    Text("Felder leeren", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetCompanyDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Daten", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                titleContentColor = MaterialTheme.colorScheme.primary,
            ),
            actions = {
                IconButton(onClick = { showMenu = !showMenu }) {
                    Icon(Icons.Default.Settings, contentDescription = "Menü anzeigen")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Daten sichern") },
                        leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onExport()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Sicherung einlesen") },
                        leadingIcon = { Icon(Icons.Default.Restore, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            importBestaetigen = true
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Datenfelder leeren") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showResetCompanyDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Werkseinstellungen") },
                        leadingIcon = { Icon(Icons.Default.Warning, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            showResetAllDialog = true
                        }
                    )
                }
            }
        )

        Box(modifier = Modifier
            .weight(1f)
            .imePadding()
            .background(MaterialTheme.colorScheme.background)) {
            LazyColumn(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .inhaltsbreite()
                    .fillMaxHeight(),
                contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { SectionHeader("Kundendaten") }
                item {
                    DataField(
                        label = "Bildungszentrum",
                        value = companyDataState.eduCenter,
                        onValueChange = {
                            companyDataState = companyDataState.copy(eduCenter = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Standort Nr",
                        value = companyDataState.locationNr,
                        onValueChange = {
                            companyDataState = companyDataState.copy(locationNr = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Schulart/Maßnahme",
                        value = companyDataState.schoolType,
                        onValueChange = {
                            companyDataState = companyDataState.copy(schoolType = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Name/Orga",
                        value = companyDataState.customerSecondNameOrOrga,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(customerSecondNameOrOrga = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Vorname",
                        value = companyDataState.customerFirstName,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(customerFirstName = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Straße",
                        value = companyDataState.customerStreet,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(customerStreet = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Hausnummer",
                        value = companyDataState.customerStreetNumber,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(customerStreetNumber = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "PLZ",
                        value = companyDataState.customerPlz,
                        onValueChange = {
                            companyDataState = companyDataState.copy(customerPlz = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Postfach",
                        value = companyDataState.customerMailBox,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(customerMailBox = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Ort",
                        value = companyDataState.customerCityName,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(customerCityName = it); onChanged()
                        })
                }

                item { SectionHeader("Meine Daten") }
                item {
                    DataField(
                        label = "Name",
                        value = companyDataState.billerSecondName,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(billerSecondName = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Vorname",
                        value = companyDataState.billerFirstName,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(billerFirstName = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Straße",
                        value = companyDataState.billerStreetName,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(billerStreetName = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Hausnummer",
                        value = companyDataState.billerStreetNumber,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(billerStreetNumber = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "PLZ",
                        value = companyDataState.billerPlzNumber,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(billerPlzNumber = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Ort",
                        value = companyDataState.billerCityName,
                        onValueChange = {
                            companyDataState =
                                companyDataState.copy(billerCityName = it); onChanged()
                        })
                }

                // Signature Picker
                item {
                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)) {
                        Text(
                            "Unterschrift",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline,
                                    MaterialTheme.shapes.extraSmall
                                )
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable {
                                    onPickSignature { pfad ->
                                        companyDataState =
                                            companyDataState.copy(signaturePath = pfad)
                                        onChanged()
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (companyDataState.signaturePath.isNotEmpty() && File(companyDataState.signaturePath).exists()) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val vorschau = rememberSignaturePreview(companyDataState.signaturePath)
                                    if (vorschau != null) {
                                        Image(
                                            bitmap = vorschau,
                                            contentDescription = "Unterschrift Vorschau",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(8.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                    // Delete/Clear Button
                                    IconButton(
                                        onClick = {
                                            companyDataState =
                                                companyDataState.copy(signaturePath = "")
                                            onChanged()
                                        },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Unterschrift entfernen",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Unterschrift als Bild auswählen",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                item { SectionHeader("Finanzdaten") }
                item {
                    DataField(
                        label = "Steuernummer",
                        value = companyDataState.taxNumber,
                        onValueChange = {
                            companyDataState = companyDataState.copy(taxNumber = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "IBAN",
                        value = companyDataState.billerIban,
                        onValueChange = {
                            companyDataState = companyDataState.copy(billerIban = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "BIC",
                        value = companyDataState.billerBIC,
                        onValueChange = {
                            companyDataState = companyDataState.copy(billerBIC = it); onChanged()
                        })
                }
                item {
                    DataField(
                        label = "Honorarsatz € a 45 Minuten",
                        value = rateText,
                        onValueChange = {
                            rateText = it
                            it.replace(",", ".").toBigDecimalOrNull()?.let { parsed ->
                                companyDataState = companyDataState.copy(rate = parsed)
                            }
                            onChanged()
                        })
                }
            }

            FloatingActionButton(
                onClick = { if (!isLoading) onSave(companyDataState) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = if (isLoading) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                contentColor = if (isLoading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Save, contentDescription = "Speichern")
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .pointerInput(Unit) { },
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(64.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(vertical = 8.dp),
        style = TextStyle(
            fontWeight = FontWeight(600),
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
    )
}

@Composable
fun DataField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview
@Composable
fun DataWindowPreview() {
    HonorarCraftTheme {
        DataWindowContent(
            savedData = CompanyData(),
            isLoading = false,
            resetTrigger = 0,
            onSave = {},
            onResetAll = {},
            onResetCompanyOnly = {},
            onChanged = {},
            onExport = {},
            onImport = {},
            onPickSignature = {},
            selectedTabIndex = 3,
            onTabSelected = {}
        )
    }
}
