package app.accounting.accountingapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DataWindowContent(onClose: () -> Unit) {

    val loaded = remember { loadCompanyData() }
    val scope = rememberCoroutineScope()
    var showLoader by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    // States für die linke Spalte
    var eduCenter by remember { mutableStateOf(loaded?.eduCenter ?: "") }
    var locationNr by remember { mutableStateOf(loaded?.locationNr ?: "") }
    var schoolType by remember { mutableStateOf(loaded?.schoolType ?: "") }
    var customerSecondNameOrOrga by remember { mutableStateOf(loaded?.customerSecondNameOrOrga ?: "") }
    var customerFirstName by remember { mutableStateOf(loaded?.customerFirstName ?: "") }
    var customerStreet by remember { mutableStateOf(loaded?.customerStreet ?: "") }
    var customerStreetNumber by remember { mutableStateOf(loaded?.customerStreetNumber ?: "") }
    var hourRate by remember { mutableStateOf(loaded?.hourRate ?: "") }
    var customerPlz by remember { mutableStateOf(loaded?.customerPlz ?: "") }
    var customerCityName by remember { mutableStateOf(loaded?.customerCityName ?: "") }
    var customerMailBox by remember { mutableStateOf(loaded?.customerMailBox ?: "") }
    var pdfPath by remember { mutableStateOf(loaded?.pdfPath ?: "") }

    // States für die rechte Spalte
    var billerSecondName by remember { mutableStateOf(loaded?.billerSecondName ?: "") }
    var billerFirstName by remember { mutableStateOf(loaded?.billerFirstName ?: "") }
    var billerStreetName by remember { mutableStateOf(loaded?.billerStreetName ?: "") }
    var billerStreetNumber by remember { mutableStateOf(loaded?.billerStreetNumber ?: "") }
    var billerPlzNumber by remember { mutableStateOf(loaded?.billerPlzNumber ?: "") }
    var billerCityName by remember { mutableStateOf(loaded?.billerCityName ?: "") }
    var billerIban by remember { mutableStateOf(loaded?.billerIban ?: "") }
    var billerBIC by remember { mutableStateOf(loaded?.billerBIC ?: "") }
    var signaturePath by remember { mutableStateOf(loaded?.signaturePath ?: "") }
    var taxNumber by remember { mutableStateOf(loaded?.taxNumber ?: "") }

    var isEditingIban by remember { mutableStateOf(false) }
    var isEditingBic by remember { mutableStateOf(false) }
    var isEditingTax by remember { mutableStateOf(false) }

    val hasChanges by remember {
        derivedStateOf {
            eduCenter != (loaded?.eduCenter ?: "") ||
                    locationNr != (loaded?.locationNr ?: "") ||
                    schoolType != (loaded?.schoolType ?: "") ||
                    customerSecondNameOrOrga != (loaded?.customerSecondNameOrOrga ?: "") ||
                    customerFirstName != (loaded?.customerFirstName ?: "") ||
                    customerStreet != (loaded?.customerStreet ?: "") ||
                    customerStreetNumber != (loaded?.customerStreetNumber ?: "") ||
                    customerPlz != (loaded?.customerPlz ?: "") ||
                    customerCityName != (loaded?.customerCityName ?: "") ||
                    customerMailBox != (loaded?.customerMailBox ?: "") ||
                    hourRate != (loaded?.hourRate ?: "") ||
                    pdfPath != (loaded?.pdfPath ?: "") ||
                    billerSecondName != (loaded?.billerSecondName ?: "") ||
                    billerFirstName != (loaded?.billerFirstName ?: "") ||
                    billerStreetName != (loaded?.billerStreetName ?: "") ||
                    billerStreetNumber != (loaded?.billerStreetNumber ?: "") ||
                    billerPlzNumber != (loaded?.billerPlzNumber ?: "") ||
                    billerCityName != (loaded?.billerCityName ?: "") ||
                    billerIban != (loaded?.billerIban ?: "") ||
                    billerBIC != (loaded?.billerBIC ?: "") ||
                    taxNumber != (loaded?.taxNumber ?: "") ||
                    signaturePath != (loaded?.signaturePath ?: "")
        }
    }


    // Hilfsfunktion für die Sternchen-Maskierung
    fun getMaskedValue(isEditing: Boolean, actualValue: String): String {
        return if (isEditing) actualValue else if (actualValue.isBlank()) "" else "********************"
    }


    // Box erlaubt das Übereinanderlegen von Elementen (FAB über die Row)
    Box(modifier = Modifier.fillMaxSize()) {

        Row(
            modifier = Modifier
                .fillMaxSize()

                .background(color = Color(0xFFEDCDFD).copy(alpha = 0.85f))
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {

            // Linke Spalte
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = eduCenter,
                    onValueChange = { eduCenter = it },
                    label = { Text("Bildungszentrum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = locationNr,
                    onValueChange = { locationNr = it },
                    label = { Text("Standortnummer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = schoolType,
                    onValueChange = { schoolType = it },
                    label = { Text("Schulart/Maßnahme") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customerSecondNameOrOrga,
                    onValueChange = { customerSecondNameOrOrga = it },
                    label = { Text("Empfänger: Nachname oder Orga") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customerFirstName,
                    onValueChange = { customerFirstName = it },
                    label = { Text("Empfänger: Vorname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customerStreet,
                    onValueChange = { customerStreet = it },
                    label = { Text("Empfänger: Straße") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customerStreetNumber,
                    onValueChange = { customerStreetNumber = it },
                    label = { Text("Empfänger: Hausnr.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))




                OutlinedTextField(
                    value = customerPlz,
                    onValueChange = { customerPlz = it },
                    label = { Text("Empfänger: PLZ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customerMailBox,
                    onValueChange = { customerMailBox = it },
                    label = { Text("Empfänger: Postfach") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = customerCityName,
                    onValueChange = { customerCityName = it },
                    label = { Text("Empfänger: Ort") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                // Unterschrift Pfad-Auswahl
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    OutlinedTextField(
                        value = pdfPath, onValueChange = {
                            pdfPath = if (it.isBlank()) getDefaultPdfPath() else it
                        },
                        readOnly = true, label = { Text("Pfad: PDF Ordner") }, modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier.matchParentSize().padding(end = 48.dp).pointerHoverIcon(PointerIcon.Hand)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                val chooser = javax.swing.JFileChooser()


                                chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
                                chooser.dialogTitle = "Wähle den PDF-Export Ordner"

                                val result = chooser.showOpenDialog(null)
                                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {

                                    pdfPath = chooser.selectedFile.absolutePath
                                }
                            }
                    )
                    if (pdfPath.isNotEmpty()) {
                        IconButton(
                            onClick = { pdfPath = getDefaultPdfPath() },
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Löschen")
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))


            }

            // Rechte Spalte
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = billerSecondName,
                    onValueChange = { billerSecondName = it },
                    label = { Text("Nachname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = billerFirstName,
                    onValueChange = { billerFirstName = it },
                    label = { Text("Vorname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = billerStreetName,
                    onValueChange = { billerStreetName = it },
                    label = { Text("Straße") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = billerStreetNumber,
                    onValueChange = { billerStreetNumber = it },
                    label = { Text("Hausnr.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))




                OutlinedTextField(
                    value = billerPlzNumber,
                    onValueChange = { billerPlzNumber = it },
                    label = { Text("PLZ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = billerCityName,
                    onValueChange = { billerCityName = it },
                    label = { Text("Ort") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = getMaskedValue(isEditingIban, billerIban),
                    onValueChange = {
                        isEditingIban = true
                        billerIban = it
                    },
                    label = { Text("IBAN") },
                    placeholder = { Text("Neue IBAN eingeben zum Ändern") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = getMaskedValue(isEditingBic, billerBIC),
                    onValueChange = {
                        isEditingBic = true
                        billerBIC = it
                    },
                    label = { Text("BIC") },
                    placeholder = { Text("Neue BIC eingeben zum Ändern") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = getMaskedValue(isEditingTax, taxNumber),
                    onValueChange = {
                        isEditingTax = true
                        taxNumber = it
                    },
                    label = { Text("Steuernummer") },
                    placeholder = { Text("Neue Steuernummer eingeben zum Ändern") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = hourRate,
                    onValueChange = { hourRate = it },
                    label = { Text("Honorarbasis (€)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))

                // Dateipfad-Auswahl für Unterschrift
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    OutlinedTextField(
                        value = signaturePath,
                        onValueChange = { signaturePath = it },
                        readOnly = true,
                        label = { Text("Pfad: Unterschrift (PNG/JPG)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier.matchParentSize().padding(end = 48.dp).pointerHoverIcon(PointerIcon.Hand)
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                val chooser = javax.swing.JFileChooser()

                                // Filter: Nur Bilder erlauben
                                val filter = javax.swing.filechooser.FileNameExtensionFilter(
                                    "Bilder (PNG, JPG, JPEG)", "png", "jpg", "jpeg"
                                )
                                chooser.fileFilter = filter
                                chooser.dialogTitle = "Wähle deine Unterschrift"

                                val result = chooser.showOpenDialog(null)
                                if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                                    signaturePath = chooser.selectedFile.absolutePath
                                }
                            }
                    )
                    if (signaturePath.isNotEmpty()) {
                        IconButton(onClick = { signaturePath = "" }, modifier = Modifier.padding(end = 8.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Löschen")
                        }
                    }
                }
            }
        }


        // --- Button Reihe unten mittig ---
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), // Kompakt nebeneinander
            verticalAlignment = Alignment.CenterVertically
        ) {

            // 1. Zurück Button
            SmallFloatingActionButton(
                onClick = {
                    if (hasChanges) {
                        showUnsavedChangesDialog = true
                    } else {
                        onClose()
                    }
                },
                shape = CircleShape,
                containerColor = Color(0xFFEDCDFD),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Go Back",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // 2. Speichern Button
            FloatingActionButton(
                onClick = {
                    if (!showLoader) {
                        scope.launch {
                            try {
                                showLoader = true
                                withContext(Dispatchers.IO) {
                                    saveCompanyData(
                                        CompanyData(
                                            eduCenter = eduCenter,
                                            locationNr = locationNr,
                                            schoolType = schoolType,
                                            customerSecondNameOrOrga = customerSecondNameOrOrga,
                                            customerFirstName = customerFirstName,
                                            customerStreet = customerStreet,
                                            customerStreetNumber = customerStreetNumber,
                                            hourRate = hourRate,
                                            billerSecondName = billerSecondName,
                                            billerFirstName = billerFirstName,
                                            billerStreetName = billerStreetName,
                                            billerStreetNumber = billerStreetNumber,
                                            billerPlzNumber = billerPlzNumber,
                                            billerCityName = billerCityName,
                                            taxNumber = taxNumber,
                                            billerIban = billerIban,
                                            billerBIC = billerBIC,
                                            customerPlz = customerPlz,
                                            customerCityName = customerCityName,
                                            customerMailBox = customerMailBox,
                                            signaturePath = signaturePath,
                                            pdfPath = pdfPath
                                        )
                                    )
                                }
                                isEditingIban = false
                                isEditingBic = false
                                isEditingTax = false
                                kotlinx.coroutines.delay(750)
                            } catch (e: Exception) {
                                println("Fehler: ${e.message}")
                            } finally {
                                showLoader = false
                            }
                        }
                    }
                },
                shape = CircleShape,
                containerColor = if (showLoader) Color.Gray else Color(0xFFEDCDFD),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = "Speichern",
                    tint = if (showLoader) Color.LightGray else MaterialTheme.colorScheme.primary
                )
            }

            SmallFloatingActionButton(
                onClick = { showResetDialog = true }, // Nur den Dialog öffnen
                shape = CircleShape,
                containerColor = Color(0xFFEDCDFD),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Werkseinstellungen",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }


        // 2. Progress Indicator (Ganz oben auf allen anderen Ebenen)
        if (showLoader) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                WavyCircularProgressIndicator(
                    modifier = Modifier.size(120.dp),
                    color = Color(0xFFEDCDFD)
                )
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            modifier = Modifier.border(
                width = 2.dp,
                color = Color.Black,
                shape = RoundedCornerShape(28.dp)
            ),
            containerColor = Color(0xFFEDCDFD),
            title = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Werkseinstellungen", fontWeight = FontWeight.Bold)
                }
            },
            text = { Text("Möchtest du wirklich alle Daten löschen und die App auf die Werkseinstellungen zurücksetzen?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            // 1. Dateien löschen
                            SuggestionsManager.resetToFactorySettings()

                            // 2. UI Felder aktualisieren
                            withContext(Dispatchers.Main) {
                                eduCenter = "Biberach - Ehingen"
                                locationNr = "40 - 381"
                                schoolType = "AsA flex"
                                customerSecondNameOrOrga = "Kolping Berufsbildung gGmbH"
                                customerFirstName = ""
                                customerStreet = ""
                                customerStreetNumber = ""
                                customerPlz = "70010"
                                customerCityName = "Stuttgart"
                                customerMailBox = "10 11 61"
                                pdfPath = getDefaultPdfPath()
                                hourRate = "23.0"
                                billerSecondName = ""
                                billerFirstName = ""
                                billerStreetName = ""
                                billerStreetNumber = ""
                                billerPlzNumber = ""
                                billerCityName = ""
                                billerIban = ""
                                billerBIC = ""
                                taxNumber = ""
                                signaturePath = ""
                                isEditingIban = false
                                isEditingBic = false
                                isEditingTax = false

                                showResetDialog = false // Dialog schließen
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Ja, alles löschen", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }


    if (showUnsavedChangesDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedChangesDialog = false },
            modifier = Modifier.border(
                width = 2.dp,
                color = Color.Black,
                shape = RoundedCornerShape(28.dp)
            ),
            containerColor = Color(0xFFEDCDFD),
            title = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Ungespeicherte Änderungen", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "Du hast Änderungen vorgenommen. Möchtest du wirklich zurückkehren, ohne zu speichern?",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedChangesDialog = false
                        onClose() // Jetzt wirklich schließen
                    }
                ) {
                    Text("Ja, verwerfen", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedChangesDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }


}


fun getDefaultPdfPath(): String {
    val userHome = File(System.getProperty("user.home"))
    val os = System.getProperty("os.name").lowercase()


    val folderName = when {
        os.contains("win") -> "Documents"
        os.contains("mac") -> "Documents"
        else -> "Dokumente" // Standard für dein T470 / Linux
    }

    val documentsDir = File(userHome, folderName)

    return if (documentsDir.exists()) {
        File(documentsDir, "Honorarabrechnungen").absolutePath
    } else {
        // Falls kein Dokumenten-Ordner gefunden wird, direkt ins Home
        File(userHome, "Honorarabrechnungen").absolutePath
    }
}

