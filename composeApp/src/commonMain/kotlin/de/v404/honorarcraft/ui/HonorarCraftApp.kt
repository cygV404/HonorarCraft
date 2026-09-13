package de.v404.honorarcraft.ui

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import de.v404.honorarcraft.shared.MainViewModel
import de.v404.honorarcraft.ui.platform.LocalPlatformServices
import de.v404.honorarcraft.ui.platform.rememberPlatformServices
import kotlinx.coroutines.CancellationException

/** Die vier Bereiche der App, auf beiden Plattformen dieselben. */
private data class Bereich(val titel: String, val symbol: ImageVector)

private val bereiche = listOf(
    Bereich("Übersicht", Icons.Default.Home),
    Bereich("Erstellen", Icons.Default.AddCard),
    Bereich("PDF", Icons.Default.PictureAsPdf),
    Bereich("Daten", Icons.Default.Person),
)

/**
 * Ab dieser Fensterbreite steht die Navigation als Seitenleiste statt als Leiste unten.
 *
 * 900 dp liegt knapp über Materials „expanded"-Grenze von 840 dp: ein Tablet im Querformat
 * und jedes Desktop-Fenster bekommen die Seitenleiste, ein Handy nie.
 */
private val BREITE_FUER_SEITENLEISTE = 900.dp

private enum class PagerSteuerung { RUHE, NUTZER, PROGRAMM }

/**
 * Der gemeinsame Rahmen. Enthält die Navigation, den Dialog für ungespeicherte Änderungen und
 * die Rückmeldungen aus dem ViewModel.
 *
 * Die vier Bereiche sind auf beiden Plattformen dieselben; nur die Navigation unterscheidet
 * sich — schmal ein Pager mit Leiste unten, breit eine feste Seitenleiste ohne Wischen.
 */
@Composable
fun HonorarCraftApp(viewModel: MainViewModel) {
    val platform = rememberPlatformServices()
    val ausgewaehlt by viewModel.selectedTabIndex.collectAsState()
    val offeneAenderung by viewModel.pendingTabIndex.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }

    if (offeneAenderung != null) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelTabChange() },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Ungespeicherte Änderungen") },
            text = {
                Text(
                    "Sie haben ungespeicherte Änderungen im Daten-Bereich. Möchten Sie diese " +
                        "verwerfen und fortfahren?"
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmTabChange() }) { Text("Verwerfen") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelTabChange() }) { Text("Abbrechen") }
            }
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalPlatformServices provides platform) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= BREITE_FUER_SEITENLEISTE) {
                BreiteAnsicht(viewModel, ausgewaehlt, snackbar)
            } else {
                SchmaleAnsicht(viewModel, ausgewaehlt, snackbar)
            }
        }
    }
}

/** Desktop und Tablet: feste Seitenleiste, kein Wischen. */
@Composable
private fun BreiteAnsicht(
    viewModel: MainViewModel,
    ausgewaehlt: Int,
    snackbar: SnackbarHostState,
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
    ) { innen ->
        Row(modifier = Modifier.fillMaxSize().padding(innen).consumeWindowInsets(innen)) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                bereiche.forEachIndexed { index, bereich ->
                    NavigationRailItem(
                        selected = ausgewaehlt == index,
                        onClick = { viewModel.requestTabChange(index) },
                        icon = { Icon(bereich.symbol, contentDescription = bereich.titel) },
                        label = { Text(bereich.titel) },
                    )
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Bereichsinhalt(viewModel, ausgewaehlt)
            }
        }
    }
}

/** Handy: Pager mit vier Tabs unten, wie bisher auf Android. */
@Composable
private fun SchmaleAnsicht(
    viewModel: MainViewModel,
    ausgewaehlt: Int,
    snackbar: SnackbarHostState,
) {
    val pagerState = rememberPagerState(initialPage = ausgewaehlt, pageCount = { bereiche.size })
    val wirdGezogen by pagerState.interactionSource.collectIsDraggedAsState()
    var steuerung by remember { mutableStateOf(PagerSteuerung.RUHE) }
    val ungespeichert by viewModel.hasUnsavedChanges.collectAsState()

    // Wischen hat immer Vorrang und übernimmt die Führung.
    LaunchedEffect(wirdGezogen) {
        if (wirdGezogen) steuerung = PagerSteuerung.NUTZER
    }

    // Ändert sich der Index ohne Wischen, war es ein Tippen auf die Leiste.
    LaunchedEffect(ausgewaehlt) {
        if (!wirdGezogen && steuerung != PagerSteuerung.NUTZER) {
            steuerung = PagerSteuerung.PROGRAMM
        }
    }

    // Führt Pager und ViewModel wieder zusammen, sobald nichts mehr in Bewegung ist.
    LaunchedEffect(ausgewaehlt, pagerState, wirdGezogen) {
        snapshotFlow { pagerState.isScrollInProgress }.collect { inBewegung ->
            if (inBewegung || wirdGezogen) return@collect
            when (steuerung) {
                PagerSteuerung.NUTZER -> {
                    viewModel.updateTabIndexFromPager(pagerState.settledPage)
                    steuerung = PagerSteuerung.RUHE
                }

                PagerSteuerung.PROGRAMM, PagerSteuerung.RUHE -> {
                    val amZiel = pagerState.settledPage == ausgewaehlt &&
                        pagerState.currentPageOffsetFraction == 0f
                    if (!amZiel) {
                        try {
                            pagerState.animateScrollToPage(ausgewaehlt)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            // Abbruch ist in Ordnung, beim nächsten Stillstand wird erneut geprüft.
                        }
                    } else {
                        steuerung = PagerSteuerung.RUHE
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                bereiche.forEachIndexed { index, bereich ->
                    NavigationBarItem(
                        selected = ausgewaehlt == index,
                        onClick = { viewModel.requestTabChange(index) },
                        icon = { Icon(bereich.symbol, contentDescription = bereich.titel) },
                        label = { Text(bereich.titel) },
                    )
                }
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
            .exclude(WindowInsets.statusBars)
            .exclude(WindowInsets.ime),
    ) { innen ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize().padding(innen).consumeWindowInsets(innen),
            // Bei ungespeicherten Änderungen soll der Dialog entscheiden, nicht ein Wisch.
            userScrollEnabled = !ungespeichert,
        ) { seite ->
            Bereichsinhalt(viewModel, seite)
        }
    }
}

@Composable
private fun Bereichsinhalt(viewModel: MainViewModel, index: Int) {
    val ausgewaehlt by viewModel.selectedTabIndex.collectAsState()
    when (index) {
        0 -> DashboardScreen(mainViewModel = viewModel)
        1 -> CreateInvoiceScreen(
            mainViewModel = viewModel,
            selectedTabIndex = ausgewaehlt,
            onTabSelected = { viewModel.requestTabChange(it) },
        )

        2 -> EntryWindowScreen(
            mainViewModel = viewModel,
            selectedTabIndex = ausgewaehlt,
            onTabSelected = { viewModel.requestTabChange(it) },
        )

        3 -> DataWindowScreen(
            mainViewModel = viewModel,
            selectedTabIndex = ausgewaehlt,
            onTabSelected = { viewModel.requestTabChange(it) },
        )
    }
}
