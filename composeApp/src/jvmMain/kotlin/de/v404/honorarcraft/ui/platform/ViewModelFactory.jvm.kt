package de.v404.honorarcraft.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import de.v404.honorarcraft.shared.MainViewModel
import de.v404.honorarcraft.shared.PreferencesSettings
import de.v404.honorarcraft.shared.data.DesktopDatabase
import de.v404.honorarcraft.shared.desktopAppDataFolder
import de.v404.honorarcraft.shared.legacy.LegacyDesktopImport
import de.v404.honorarcraft.shared.logError

@Composable
actual fun rememberMainViewModel(): MainViewModel {
    val viewModel = remember {
        MainViewModel(database = DesktopDatabase.get(), settings = PreferencesSettings())
    }

    // Einmaliger Import der alten JSON-Ablage. Danach liegt der Altbestand im Archivordner
    // und dieser Aufruf findet nichts mehr vor.
    LaunchedEffect(Unit) {
        runCatching {
            LegacyDesktopImport.run(desktopAppDataFolder(), DesktopDatabase.get())
        }.onSuccess { bericht ->
            if (bericht == null) return@onSuccess
            val verloren = bericht.droppedYearStartValues
            val zusatz = if (verloren.isEmpty()) "" else
                " Nicht übernommen wurden die Jahres-Startwerte (" +
                    verloren.entries.joinToString { "${it.key}: ${it.value} €" } +
                    "); sie liegen weiter in ${bericht.archivedTo.name}."
            viewModel.showMessage(
                "${bericht.invoices} Rechnungen mit ${bericht.entries} Positionen übernommen." +
                    zusatz
            )
            bericht.lastInvoiceNumber?.let { viewModel.setSelectedInvoiceNumber(it) }
        }.onFailure {
            logError("Import", "Übernahme der alten Desktop-Daten fehlgeschlagen", it)
            viewModel.showMessage("Die alten Daten konnten nicht übernommen werden.")
        }
    }

    return viewModel
}
