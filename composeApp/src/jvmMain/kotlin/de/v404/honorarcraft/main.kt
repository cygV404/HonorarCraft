package de.v404.honorarcraft

import de.v404.honorarcraft.resources.Res
import de.v404.honorarcraft.resources.iconWindows
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import de.v404.honorarcraft.ui.theme.HonorarCraftTheme
import org.jetbrains.compose.resources.painterResource
import java.awt.Dimension

fun main() = application {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog")

    var currentScreen by remember { mutableStateOf("second") }
    var invoiceNumberForThirdWindow by remember { mutableStateOf("") }

    Window(
        icon = painterResource(Res.drawable.iconWindows),
        onCloseRequest = ::exitApplication,
        title = "HonorarCraft"
    ) {
        window.minimumSize = Dimension(1440, 900)

        // Vorerst fest hell. Die Screens dieses Moduls tragen noch rund drei Dutzend fest
        // verdrahtete Farben; mit isSystemInDarkTheme() wäre die Oberfläche auf einem dunkel
        // eingestellten System halb unlesbar. Sobald die Screens in Phase 5 durch die
        // gemeinsame Oberfläche ersetzt sind, kann hier der Vorgabewert stehenbleiben.
        HonorarCraftTheme(darkTheme = false) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.92f, animationSpec = tween(400)))
                            .togetherWith(fadeOut(animationSpec = tween(300)))
                    },
                    label = "ScreenTransition"
                ) { targetScreen ->
                    when (targetScreen) {
                        "second" -> Dashboard(
                            onWeiterClick = { number ->
                                invoiceNumberForThirdWindow = number
                                currentScreen = "third"
                            },
                            onOpenData = { currentScreen = "data" },
                            onClose = { exitApplication() }
                        )

                        "data" -> DataWindowContent(onClose = { currentScreen = "second" })
                        "third" -> InvoiceGenerator(
                            invoiceNumber = invoiceNumberForThirdWindow,
                            onCloseApp = { exitApplication() },
                            onBack = { currentScreen = "second" }
                        )
                    }
                }
            }
        }
    }
}