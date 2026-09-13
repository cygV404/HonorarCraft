package de.v404.honorarcraft.ui.platform

import androidx.compose.runtime.Composable
import de.v404.honorarcraft.shared.MainViewModel

/**
 * Baut das gemeinsame ViewModel mit der Datenbank und dem Einstellungsspeicher der jeweiligen
 * Plattform. Auf dem Desktop läuft dabei einmalig der Import der alten JSON-Daten.
 */
@Composable
expect fun rememberMainViewModel(): MainViewModel
