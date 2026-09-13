package de.v404.honorarcraft.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.widthIn

/**
 * Breite, ab der Inhalt nicht weiter mitwächst.
 *
 * Auf dem Handy greift das nie — dort ist das Fenster schmaler. Auf einem breiten
 * Desktop-Fenster verhindert es, dass Karten und Eingabefelder über die gesamte Breite
 * gezogen werden und der Blick beim Lesen quer über den Bildschirm wandern muss.
 */
val MAX_INHALTSBREITE: Dp = 720.dp

/** Begrenzt die Breite auf [MAX_INHALTSBREITE]. Zentriert wird vom umgebenden Layout. */
fun Modifier.inhaltsbreite(max: Dp = MAX_INHALTSBREITE): Modifier = widthIn(max = max)
