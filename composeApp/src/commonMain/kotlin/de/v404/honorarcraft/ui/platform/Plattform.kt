package de.v404.honorarcraft.ui.platform

/**
 * Läuft die App auf Linux?
 *
 * Nur für einen Hinweis in der Oberfläche: Skiko liefert dort die Systemeinstellung für
 * hell/dunkel nicht aus, „System" bleibt deshalb immer hell.
 */
expect fun istLinux(): Boolean
