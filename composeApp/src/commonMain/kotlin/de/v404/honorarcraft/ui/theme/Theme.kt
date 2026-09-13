package de.v404.honorarcraft.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

/**
 * Dynamische Systemfarben, sofern die Plattform welche hat.
 *
 * Android ab 12 leitet sie aus dem Hintergrundbild ab; auf dem Desktop gibt es nichts
 * Vergleichbares, dort liefert die Umsetzung `null` und es bleibt beim Schema oben.
 */
@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?

@Composable
fun HonorarCraftTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dynamic = if (dynamicColor) dynamicColorSchemeOrNull(darkTheme) else null
    val colorScheme = dynamic ?: if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = honorarCraftTypography(),
        content = content
    )
}
