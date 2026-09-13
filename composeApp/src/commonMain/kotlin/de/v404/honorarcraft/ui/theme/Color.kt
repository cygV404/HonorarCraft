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

// --- Flächen -----------------------------------------------------------------------
// Material 3 kennt eine ganze Reihe abgestufter Flächenfarben (surfaceContainer*), die
// Karten und erhöhte Elemente benutzen. Werden sie nicht gesetzt, füllt Material sie aus
// seiner **lila Grundpalette** — daher der rosa Schimmer auf den Karten. Die Töne hier sind
// neutrale Grautöne mit einem Hauch Türkis, passend zu den Markenfarben.

val LightSurfaceDim = Color(0xFFD5DBDB)
val LightSurfaceBright = Color(0xFFF5FAFA)
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFEFF5F5)
val LightSurfaceContainer = Color(0xFFE9EFEF)
val LightSurfaceContainerHigh = Color(0xFFE4EAEA)
val LightSurfaceContainerHighest = Color(0xFFDEE4E4)
val LightOutlineVariant = Color(0xFFBEC8C8)
val LightInverseSurface = Color(0xFF2D3131)
val LightInverseOnSurface = Color(0xFFEFF1F1)
val LightInversePrimary = Color(0xFF4DDBDC)

val DarkSurfaceDim = Color(0xFF101414)
val DarkSurfaceBright = Color(0xFF363A3A)
val DarkSurfaceContainerLowest = Color(0xFF0B0F0F)
val DarkSurfaceContainerLow = Color(0xFF191C1C)
val DarkSurfaceContainer = Color(0xFF1D2020)
val DarkSurfaceContainerHigh = Color(0xFF272B2B)
val DarkSurfaceContainerHighest = Color(0xFF323535)
val DarkOutlineVariant = Color(0xFF3F4949)
val DarkInverseSurface = Color(0xFFE0E3E3)
val DarkInverseOnSurface = Color(0xFF2D3131)
val DarkInversePrimary = Color(0xFF00696B)

val Scrim = Color(0xFF000000)

// --- Fehlerfarben ------------------------------------------------------------------

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)
