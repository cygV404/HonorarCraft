package de.v404.honorarcraft.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import de.v404.honorarcraft.shared.platformName

/**
 * Geruest fuer Phase 1. Der echte Bildschirm kommt in Phase 5 aus :composeApp/commonMain.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("HonorarCraft läuft auf ${platformName()}")
            }
        }
    }
}
