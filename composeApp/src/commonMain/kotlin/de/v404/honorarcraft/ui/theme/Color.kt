package de.v404.honorarcraft.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Markenfarben aus `res/values/colors.xml` der Android-App — das „H" und das „C" des
 * Launcher-Icons auf schwarzem Grund. Sie sind der Ausgangspunkt des Farbschemas.
 */
val BrandCyan = Color(0xFF18FFFF)
val BrandGreen = Color(0xFF00E676)

/** Hintergrund des Android-Startbildschirms, hell wie dunkel. */
val SplashBackground = Color(0xFF000000)

// --- Helles Schema -----------------------------------------------------------------
// Die Markenfarben selbst sind zu hell für weißen Grund (Cyan auf Weiß ist praktisch
// unlesbar). Für den Hellmodus werden deshalb dunkle Töne derselben Farbtöne verwendet,
// die hellen Originale tauchen als Container-Farben wieder auf.

val LightPrimary = Color(0xFF00696B)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFF9CF1F2)
val LightOnPrimaryContainer = Color(0xFF002020)

val LightSecondary = Color(0xFF006D3C)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFF62FFA3)
val LightOnSecondaryContainer = Color(0xFF002110)

val LightTertiary = Color(0xFF3C6470)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFC0E9F8)
val LightOnTertiaryContainer = Color(0xFF001F28)

val LightBackground = Color(0xFFF5FAFA)
val LightOnBackground = Color(0xFF191C1C)
val LightSurface = Color(0xFFF5FAFA)
val LightOnSurface = Color(0xFF191C1C)
val LightSurfaceVariant = Color(0xFFDAE4E4)
val LightOnSurfaceVariant = Color(0xFF3F4949)
val LightOutline = Color(0xFF6F7979)

// --- Dunkles Schema ----------------------------------------------------------------
// Hier stehen die Markenfarben unverändert, genau wie das Icon auf dem schwarzen
// Startbildschirm.

val DarkPrimary = BrandCyan
val DarkOnPrimary = Color(0xFF003738)
val DarkPrimaryContainer = Color(0xFF004F50)
val DarkOnPrimaryContainer = Color(0xFF9CF1F2)

val DarkSecondary = BrandGreen
val DarkOnSecondary = Color(0xFF00391D)
val DarkSecondaryContainer = Color(0xFF00522C)
val DarkOnSecondaryContainer = Color(0xFF62FFA3)

val DarkTertiary = Color(0xFFA4CDDC)
val DarkOnTertiary = Color(0xFF053542)
val DarkTertiaryContainer = Color(0xFF234C59)
val DarkOnTertiaryContainer = Color(0xFFC0E9F8)

val DarkBackground = Color(0xFF191C1C)
val DarkOnBackground = Color(0xFFE0E3E3)
val DarkSurface = Color(0xFF191C1C)
val DarkOnSurface = Color(0xFFE0E3E3)
val DarkSurfaceVariant = Color(0xFF3F4949)
val DarkOnSurfaceVariant = Color(0xFFBEC8C8)
val DarkOutline = Color(0xFF899393)

// --- Fehlerfarben ------------------------------------------------------------------

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
