package de.v404.honorarcraft.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import de.v404.honorarcraft.ui.HonorarCraftApp
import de.v404.honorarcraft.ui.platform.rememberMainViewModel

/**
 * Einstiegspunkt auf Android. Alles Sichtbare kommt aus `:composeApp/commonMain`; hier stehen
 * nur die Dinge, die es nur auf Android gibt: Startbildschirm und randlose Darstellung.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            // Das Theme sitzt in HonorarCraftApp - die gewaehlte Darstellung kommt aus dem
            // ViewModel und ist hier noch nicht erreichbar.
            HonorarCraftApp(viewModel = rememberMainViewModel())
        }
    }
}
