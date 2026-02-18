package app.accounting.accountingapp

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DataWindowContent(onClose: () -> Unit) {

    val loaded = loadCompanyData()
    val scope = rememberCoroutineScope()
    var showLoader by remember { mutableStateOf(false) }

    // States für die linke Spalte
    var eduCenter by remember { mutableStateOf(loaded?.eduCenter ?: "") }
    var locationNr by remember { mutableStateOf(loaded?.locationNr ?: "") }
    var schoolType by remember { mutableStateOf(loaded?.schoolType ?: "") }
    var customerSecondNameOrOrga by remember { mutableStateOf(loaded?.customerSecondNameOrOrga ?: "") }
    var customerFirstName by remember { mutableStateOf(loaded?.customerFirstName ?: "") }
    var customerStreet by remember { mutableStateOf(loaded?.customerStreet ?: "") }
    var hourRate by remember { mutableStateOf(loaded?.hourRate ?: "") }
    var customerPlz by remember { mutableStateOf(loaded?.customerPlz ?: "") }
    var customerCityName by remember { mutableStateOf(loaded?.customerCityName ?: "") }
    var customerMailBox by remember { mutableStateOf(loaded?.customerMailBox ?: "") }
    var pdfPath by remember { mutableStateOf(loaded?.pdfPath ?: "") }

    // States für die rechte Spalte
    var billerSecondName by remember { mutableStateOf(loaded?.billerSecondName ?: "") }
    var billerFirstName by remember { mutableStateOf(loaded?.billerFirstName ?: "") }
    var billerStreetName by remember { mutableStateOf(loaded?.billerStreetName ?: "") }
    var billerPlzNumber by remember { mutableStateOf(loaded?.billerPlzNumber ?: "") }
    var billerCityName by remember { mutableStateOf(loaded?.billerCityName ?: "") }
    var billerIban by remember { mutableStateOf(loaded?.billerIban ?: "") }
    var billerBIC by remember { mutableStateOf(loaded?.billerBIC ?: "") }
    var signaturePath by remember { mutableStateOf(loaded?.signaturePath ?: "") }
    var taxNumber by remember { mutableStateOf(loaded?.taxNumber ?: "") }


    // Die Box erlaubt das Übereinanderlegen von Elementen (FAB über die Row)
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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = locationNr,
                    onValueChange = { locationNr = it },
                    label = { Text("Standortnummer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = schoolType,
                    onValueChange = { schoolType = it },
                    label = { Text("Schulart/Maßnahme") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customerSecondNameOrOrga,
                    onValueChange = { customerSecondNameOrOrga = it },
                    label = { Text("Empfänger: Nachname oder Orga") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customerFirstName,
                    onValueChange = { customerFirstName = it },
                    label = { Text("Empfänger: Vorname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customerStreet,
                    onValueChange = { customerStreet = it },
                    label = { Text("Empfänger: Straße, Hausnr.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customerPlz,
                    onValueChange = { customerPlz = it },
                    label = { Text("Empfänger: PLZ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customerMailBox,
                    onValueChange = { customerMailBox = it },
                    label = { Text("Empfänger: Postfach") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = customerCityName,
                    onValueChange = { customerCityName = it },
                    label = { Text("Empfänger: Ort") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

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
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billerFirstName,
                    onValueChange = { billerFirstName = it },
                    label = { Text("Vorname") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billerStreetName,
                    onValueChange = { billerStreetName = it },
                    label = { Text("Straße, Hausnr.") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billerPlzNumber,
                    onValueChange = { billerPlzNumber = it },
                    label = { Text("PLZ") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billerCityName,
                    onValueChange = { billerCityName = it },
                    label = { Text("Ort") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billerIban,
                    onValueChange = { billerIban = it },
                    label = { Text("IBAN") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = billerBIC,
                    onValueChange = { billerBIC = it },
                    label = { Text("BIC") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = taxNumber,
                    onValueChange = { taxNumber = it },
                    label = { Text("Steuernummer") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = hourRate,
                    onValueChange = { hourRate = it },
                    label = { Text("Honorarbasis (€)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

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


        FloatingActionButton(
            onClick = {
                // Nur ausführen, wenn nicht bereits ein Speichervorgang läuft
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
                                        hourRate = hourRate,
                                        billerSecondName = billerSecondName,
                                        billerFirstName = billerFirstName,
                                        billerStreetName = billerStreetName,
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
                            kotlinx.coroutines.delay(500) // UX Pause

                        } catch (e: Exception) {
                            println("Fehler beim Speichern: ${e.message}")
                        } finally {
                            showLoader = false
                        }
                    }
                }
            },
            shape = CircleShape,
            // Wenn showLoader true ist, wird die Farbe auf Grau gesetzt
            containerColor = if (showLoader) Color.Gray else MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .size(56.dp)
        ) {

            Icon(
                imageVector = Icons.Default.Save,
                contentDescription = "Speichern",
                tint = if (showLoader) Color.LightGray else MaterialTheme.colorScheme.onPrimary
            )
        }






        FloatingActionButton(
            onClick = {
                // Nur ausführen, wenn nicht bereits ein Speichervorgang läuft
                onClose()
            },
            shape = CircleShape,
            // Wenn showLoader true ist, wird die Farbe auf Grau gesetzt
            containerColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(24.dp)
                .size(56.dp)
        ) {

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Go Back",
                tint = MaterialTheme.colorScheme.onPrimary
            )
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
}

fun getDefaultPdfPath(): String {
    val userHome = File(System.getProperty("user.home"))
    val potentialDirs = listOf("Dokumente", "Documents", "documents")
    val documentsDir = potentialDirs
        .map { File(userHome, it) }
        .firstOrNull { it.exists() && it.isDirectory } ?: userHome

    return File(documentsDir, "Honorarabrechnungen").absolutePath
}



