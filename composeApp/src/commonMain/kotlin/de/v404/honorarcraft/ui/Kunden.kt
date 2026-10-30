package de.v404.honorarcraft.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.v404.honorarcraft.shared.data.Customer
import de.v404.honorarcraft.shared.data.InvoiceDetails
import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

private val betragsFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.GERMANY))

private fun BigDecimal.alsBetrag(): String = betragsFormat.format(this) + " €"

// ---------------------------------------------------------------------------------------------
// Auswahl
// ---------------------------------------------------------------------------------------------

/** Schreibgeschütztes Feld mit Ausklappliste; ohne Alternativen klappt nichts auf. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AuswahlFeld(
    label: String,
    text: String,
    optionen: List<Pair<Long?, String>>,
    onAuswahl: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    hinweis: String? = null,
    waehlbar: Boolean = optionen.size > 1,
) {
    var offen by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = offen && waehlbar,
        onExpandedChange = { offen = it && waehlbar },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
            trailingIcon = {
                if (waehlbar) ExposedDropdownMenuDefaults.TrailingIcon(expanded = offen)
            },
            supportingText = hinweis?.let { { Text(it) } },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = offen && waehlbar, onDismissRequest = { offen = false }) {
            optionen.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        offen = false
                        onAuswahl(id)
                    },
                )
            }
        }
    }
}

/**
 * Wahl des Kunden für die angezeigte Rechnung. Angeboten werden nur aktive Kunden; gehört die
 * Rechnung einem archivierten, steht er trotzdem im Feld.
 */
@Composable
fun KundenAuswahl(
    kunden: List<Customer>,
    ausgewaehlt: Customer?,
    onAuswahl: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val aktive = kunden.filter { !it.archived }
    AuswahlFeld(
        label = "Kunde",
        text = ausgewaehlt?.displayName
            ?: if (kunden.isEmpty()) "Noch kein Kunde angelegt" else "Kein aktiver Kunde",
        optionen = aktive.map { it.id to it.displayName },
        onAuswahl = { id -> id?.let(onAuswahl) },
        hinweis = when {
            ausgewaehlt != null -> null
            kunden.isEmpty() -> "Kunden legst du unter „Daten“ an."
            else -> "Archivierte Kunden stellst du unter „Daten“ wieder her."
        },
        // Auch mit nur einem aktiven Kunden, wenn die Rechnung einem archivierten gehört.
        waehlbar = aktive.any { it.id != ausgewaehlt?.id },
        modifier = modifier,
    )
}

/**
 * Filter der Übersicht. Archivierte Kunden bleiben wählbar — ihr Umsatz gehört weiter zum Jahr.
 * Bei nur einem Kunden wird der Filter gar nicht erst gezeigt.
 */
@Composable
fun KundenFilter(
    kunden: List<Customer>,
    filter: Long?,
    onFilter: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (kunden.size < 2) return
    val optionen = listOf<Pair<Long?, String>>(null to "Alle Kunden") +
        kunden.map { it.id to it.displayName + if (it.archived) " (archiviert)" else "" }
    AuswahlFeld(
        label = "Kunde",
        text = optionen.firstOrNull { it.first == filter }?.second ?: "Alle Kunden",
        optionen = optionen,
        onAuswahl = onFilter,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------------------------
// Angaben je Rechnung
// ---------------------------------------------------------------------------------------------

/**
 * Bildungszentrum, Standortnummer und Schulart der angezeigten Rechnung, zum Aufklappen.
 *
 * Die Felder halten ihren Text lokal: bei einer bestehenden Rechnung geht jede Änderung in die
 * Datenbank und kommt verzögert als neuer Wert zurück. Übernähme das Feld jeden Rücklauf, könnte
 * ein verspäteter Zwischenstand Buchstaben verschlucken. Deshalb merkt es sich, was es selbst
 * abgeschickt hat: ein solcher Rücklauf wird übergangen, alles andere (Rechnungswechsel,
 * Vorbelegung) sofort übernommen — auch wenn ein Feld gerade den Fokus hat. Sonst zeigte es nach
 * einem Wechsel über das Rechnungsmenü die alte Rechnung, und Tippen schriebe deren Angaben in
 * die neue.
 */
@Composable
fun RechnungsAngaben(
    details: InvoiceDetails,
    onAenderung: (InvoiceDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    var offen by remember { mutableStateOf(false) }
    var lokal by remember { mutableStateOf(details) }
    // Abgeschickte Stände, deren Rücklauf noch aussteht, älteste zuerst.
    val unterwegs = remember { mutableListOf<InvoiceDetails>() }
    LaunchedEffect(details) {
        val eigener = unterwegs.indexOf(details)
        if (eigener >= 0) {
            // Rücklauf einer eigenen Änderung; ältere Zwischenstände sind damit überholt.
            repeat(eigener + 1) { unterwegs.removeAt(0) }
        } else {
            unterwegs.clear()
            lokal = details
        }
    }

    fun aendern(neu: InvoiceDetails) {
        lokal = neu
        unterwegs += neu
        onAenderung(neu)
    }

    val zusammenfassung = listOf(details.eduCenter, details.locationNr, details.schoolType)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .ifEmpty { "keine" }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { offen = !offen }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Angaben zur Rechnung",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!offen) {
                    Text(
                        zusammenfassung,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                if (offen) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (offen) "Zuklappen" else "Aufklappen",
            )
        }
        if (offen) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lokal.eduCenter,
                    onValueChange = { aendern(lokal.copy(eduCenter = it)) },
                    label = { Text("Bildungszentrum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lokal.locationNr,
                    onValueChange = { aendern(lokal.copy(locationNr = it)) },
                    label = { Text("Standort Nr") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = lokal.schoolType,
                    onValueChange = { aendern(lokal.copy(schoolType = it)) },
                    label = { Text("Schulart/Maßnahme") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Kundenliste und Bearbeiten
// ---------------------------------------------------------------------------------------------

/** Abschnitt „Kunden“ auf der Daten-Seite. */
fun LazyListScope.kundenAbschnitt(
    kunden: List<Customer>,
    onBearbeiten: (Customer) -> Unit,
    onNeu: () -> Unit,
) {
    if (kunden.isEmpty()) {
        item {
            Text(
                "Noch kein Kunde angelegt. Ohne Kunden lassen sich keine Stunden erfassen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(kunden, key = { it.id }) { kunde ->
        val farbe = if (kunde.archived) MaterialTheme.colorScheme.onSurfaceVariant
        else MaterialTheme.colorScheme.onSurface
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBearbeiten(kunde) },
            colors = CardDefaults.cardColors(
                containerColor = if (kunde.archived) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surfaceVariant,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    kunde.displayName + if (kunde.archived) " (archiviert)" else "",
                    style = MaterialTheme.typography.titleSmall,
                    color = farbe,
                )
                val zeile = listOfNotNull(
                    "${kunde.plz} ${kunde.cityName}".trim().ifEmpty { null },
                    kunde.rate?.let { "Satz ${it.alsBetrag()}" },
                ).joinToString(" · ")
                if (zeile.isNotEmpty()) {
                    Text(zeile, style = MaterialTheme.typography.bodySmall, color = farbe)
                }
            }
        }
    }
    item {
        OutlinedButton(onClick = onNeu, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Kunde hinzufügen")
        }
    }
}

/**
 * Anlegen oder Bearbeiten eines Kunden. Abbrechen verwirft die Eingaben — der Dialog ist modal,
 * deshalb braucht er die Rückfrage für ungespeicherte Änderungen nicht.
 */
@Composable
fun KundenDialog(
    kunde: Customer,
    onSpeichern: (Customer) -> Unit,
    onArchivieren: (Boolean) -> Unit,
    onLoeschen: () -> Unit,
    onSchliessen: () -> Unit,
) {
    var entwurf by remember(kunde) { mutableStateOf(kunde) }
    var satzText by remember(kunde) {
        mutableStateOf(kunde.rate?.toPlainString()?.replace(".", ",") ?: "")
    }
    var loeschenBestaetigen by remember(kunde) { mutableStateOf(false) }

    val satz = satzText.trim().replace(",", ".")
    val satzGueltig = satz.isEmpty() || satz.toBigDecimalOrNull()?.let { it.signum() > 0 } == true
    val neu = kunde.id == 0L

    AlertDialog(
        onDismissRequest = onSchliessen,
        title = { Text(if (neu) "Neuer Kunde" else "Kunde bearbeiten") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DataField("Name/Orga", entwurf.nameOrOrga) { entwurf = entwurf.copy(nameOrOrga = it) }
                DataField("Vorname", entwurf.firstName) { entwurf = entwurf.copy(firstName = it) }
                DataField("Straße", entwurf.street) { entwurf = entwurf.copy(street = it) }
                DataField("Hausnummer", entwurf.streetNumber) { entwurf = entwurf.copy(streetNumber = it) }
                DataField("PLZ", entwurf.plz) { entwurf = entwurf.copy(plz = it) }
                DataField("Postfach", entwurf.mailBox) { entwurf = entwurf.copy(mailBox = it) }
                DataField("Ort", entwurf.cityName) { entwurf = entwurf.copy(cityName = it) }
                OutlinedTextField(
                    value = satzText,
                    onValueChange = { satzText = it },
                    label = { Text("Eigener Honorarsatz € a 45 Minuten") },
                    supportingText = {
                        Text(
                            if (satzGueltig) "Leer lassen für den allgemeinen Satz aus „Finanzdaten“."
                            else "Bitte einen Betrag wie 25,00 eingeben."
                        )
                    },
                    isError = !satzGueltig,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!neu) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { onArchivieren(!kunde.archived) }) {
                            Text(if (kunde.archived) "Wiederherstellen" else "Archivieren")
                        }
                        TextButton(
                            onClick = {
                                if (loeschenBestaetigen) onLoeschen() else loeschenBestaetigen = true
                            }
                        ) {
                            Text(
                                if (loeschenBestaetigen) "Wirklich löschen?" else "Löschen",
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = satzGueltig,
                onClick = {
                    onSpeichern(
                        entwurf.copy(
                            rate = satz.toBigDecimalOrNull()
                                ?.setScale(2, java.math.RoundingMode.HALF_UP)
                        )
                    )
                },
            ) { Text("Speichern") }
        },
        dismissButton = { TextButton(onClick = onSchliessen) { Text("Abbrechen") } },
    )
}
