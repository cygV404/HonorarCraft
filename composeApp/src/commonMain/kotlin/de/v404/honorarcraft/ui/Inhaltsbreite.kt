package de.v404.honorarcraft.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Breite, ab der Inhalt nicht weiter mitwächst.
 *
 * Auf dem Handy greift das nie — dort ist das Fenster schmaler. Auf einem breiten
 * Desktop-Fenster verhindert es, dass Karten und Eingabefelder über die gesamte Breite
 * gezogen werden und der Blick beim Lesen quer über den Bildschirm wandern muss.
 */
val MAX_INHALTSBREITE: Dp = 720.dp

/**
 * Begrenzt die Breite auf [MAX_INHALTSBREITE] und füllt sie darunter aus.
 *
 * Das `fillMaxWidth` steckt bewusst **in** dieser Funktion: als getrennter Aufruf davor
 * geschrieben, setzt es Mindest- und Höchstbreite auf die Elternbreite, und das
 * anschließende `widthIn` bleibt wirkungslos. Genau dieser Reihenfolgefehler hat die
 * Begrenzung beim ersten Anlauf ins Leere laufen lassen.
 *
 * Zentriert wird vom umgebenden Layout — `contentAlignment` der `Box` oder `align(...)`.
 */
fun Modifier.inhaltsbreite(max: Dp = MAX_INHALTSBREITE): Modifier =
    widthIn(max = max).fillMaxWidth()
