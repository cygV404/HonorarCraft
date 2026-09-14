package de.v404.honorarcraft

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import de.v404.honorarcraft.resources.Res
import de.v404.honorarcraft.resources.iconWindows
import de.v404.honorarcraft.ui.HonorarCraftApp
import de.v404.honorarcraft.ui.platform.rememberMainViewModel
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")

    Window(
        icon = painterResource(Res.drawable.iconWindows),
        onCloseRequest = ::exitApplication,
        title = "HonorarCraft",
        // Startgröße oberhalb der 900-dp-Grenze, sonst öffnet der Desktop in der
        // Handy-Ansicht mit Leiste unten statt mit der Seitenleiste.
        state = rememberWindowState(size = DpSize(1200.dp, 820.dp)),
    ) {
        // Deutlich kleiner als die früheren 1440x900: darunter passte die App auf kein
        // kleineres Notebook. Die Seitenleiste erscheint ab 900 dp Breite, darunter greift
        // dieselbe Pager-Ansicht wie auf dem Handy.
        window.minimumSize = Dimension(800, 600)

        // Kein Theme hier: es sitzt in HonorarCraftApp, weil die gewaehlte Darstellung aus
        // dem ViewModel kommt.
        Box(modifier = Modifier.fillMaxSize()) {
            HonorarCraftApp(viewModel = rememberMainViewModel())
        }
    }
}
