package app.accounting.accountingapp

import accountingapp.composeapp.generated.resources.Firefly1_desktop
import accountingapp.composeapp.generated.resources.Res
import accountingapp.composeapp.generated.resources.iconWindows
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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

        Box(modifier = Modifier.fillMaxSize()) {

            Image(
                painter = painterResource(Res.drawable.Firefly1_desktop),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = 0.7f
            )


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