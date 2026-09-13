package de.v404.honorarcraft.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import de.v404.honorarcraft.shared.platformName
import de.v404.honorarcraft.ui.theme.HonorarCraftTheme

/**
 * Geruest fuer Phase 1/4. Der echte Bildschirm kommt in Phase 5 aus :composeApp/commonMain;
 * das gemeinsame Theme haengt hier bereits.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HonorarCraftTheme {
                Text("HonorarCraft läuft auf ${platformName()}")
            }
        }
    }
}
