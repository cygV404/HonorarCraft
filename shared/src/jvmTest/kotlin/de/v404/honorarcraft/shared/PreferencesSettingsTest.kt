package de.v404.honorarcraft.shared

import java.util.prefs.Preferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Der Einstellungsspeicher des Desktops muss **sofort** auf die Platte schreiben.
 *
 * `java.util.prefs` puffert sonst im Speicher und gibt erst beim periodischen Abgleich oder
 * beim sauberen Beenden ab. Wird die App abgewürgt, stünde nach einem erzeugten PDF wieder
 * die alte Rechnungsnummer da — und zwei Rechnungen bekämen dieselbe Nummer.
 */
class PreferencesSettingsTest {

    private val knoten = "de/v404/honorarcraft/test-${System.nanoTime()}"

    @AfterTest
    fun tearDown() {
        runCatching { Preferences.userRoot().node(knoten).removeNode() }
    }

    @Test
    fun `geschriebene Werte stehen sofort im Knoten`() {
        val settings = PreferencesSettings(knoten)
        settings.putString(SettingsKeys.SELECTED_INVOICE_NUMBER, "22")
        settings.putInt(SettingsKeys.INVOICE_YEAR, 2027)

        // Bewusst am Settings-Objekt vorbei gelesen: nur so zeigt sich, ob der Wert
        // tatsächlich im Speicher der Preferences gelandet ist und nicht nur im Puffer.
        val direkt = Preferences.userRoot().node(knoten)
        assertEquals("22", direkt.get(SettingsKeys.SELECTED_INVOICE_NUMBER, ""))
        assertEquals(2027, direkt.getInt(SettingsKeys.INVOICE_YEAR, 0))
    }

    @Test
    fun `ein zweites Settings-Objekt sieht dieselben Werte`() {
        PreferencesSettings(knoten).putString(SettingsKeys.INVOICE_FORMAT, "YEAR_NUMBER")
        assertEquals("YEAR_NUMBER", PreferencesSettings(knoten).getString(SettingsKeys.INVOICE_FORMAT, ""))
    }

    @Test
    fun `Zuruecksetzen leert den Knoten`() {
        val settings = PreferencesSettings(knoten)
        settings.putString(SettingsKeys.SELECTED_INVOICE_NUMBER, "22")
        settings.clear()
        assertEquals("1", settings.getString(SettingsKeys.SELECTED_INVOICE_NUMBER, "1"))
    }
}
