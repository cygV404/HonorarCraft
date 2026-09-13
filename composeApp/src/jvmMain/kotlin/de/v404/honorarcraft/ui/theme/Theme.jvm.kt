package de.v404.honorarcraft.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Der Desktop hat keine dynamischen Systemfarben. */
@Composable
actual fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme? = null
