package de.v404.honorarcraft

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.v404.honorarcraft.resources.Res
import de.v404.honorarcraft.resources.iconWindows
import de.v404.honorarcraft.ui.HonorarCraftApp
import de.v404.honorarcraft.ui.platform.rememberMainViewModel
import de.v404.honorarcraft.ui.theme.HonorarCraftTheme
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")

    Window(
        icon = painterResource(Res.drawable.iconWindows),
        onCloseRequest = ::exitApplication,
        title = "HonorarCraft",
    ) {
        // Deutlich kleiner als die früheren 1440x900: darunter passte die App auf kein
        // kleineres Notebook. Die Seitenleiste erscheint ab 900 dp Breite, darunter greift
        // dieselbe Pager-Ansicht wie auf dem Handy.
        window.minimumSize = Dimension(800, 600)

        HonorarCraftTheme {
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                HonorarCraftApp(viewModel = rememberMainViewModel())
            }
        }
    }
}
