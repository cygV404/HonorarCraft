package de.v404.honorarcraft.ui.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Lädt ein Bild von der Platte und verkleinert es dabei auf höchstens [maxEdge] Pixel
 * Kantenlänge.
 *
 * Gebraucht für die Vorschau der Unterschrift. Das Verkleinern passiert bewusst schon beim
 * Laden: eine mit dem Handy fotografierte Unterschrift hat leicht mehrere tausend Pixel, und
 * die vollständig in den Speicher zu legen, nur um sie als Briefmarke anzuzeigen, hat die
 * Android-App früher schon an den Rand gebracht.
 *
 * Liefert `null`, wenn die Datei fehlt oder kein lesbares Bild ist — die Oberfläche zeigt
 * dann den Platzhalter statt abzustürzen.
 */
expect suspend fun loadImageFromFile(path: String, maxEdge: Int = 512): ImageBitmap?
