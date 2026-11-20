package de.v404.honorarcraft.shared

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class UnterschriftTest {

    private val ordner: File = Files.createTempDirectory("honorarcraft-unterschrift").toFile()

    private fun waehle(inhalt: String, gespeichert: String, jetzt: Long) =
        legeUnterschriftAb(inhalt.byteInputStream(), "PNG", ordner, gespeichert, jetzt)

    @Test
    fun `jede Auswahl bekommt einen eigenen Namen und die gespeicherte bleibt`() {
        val gespeichert = File(ordner, "unterschrift.png").apply { writeText("alt") }
        File(ordner, "honorarcraft.db").writeText("db")

        val erste = waehle("neu 1", gespeichert.path, 1000)
        assertNotEquals(gespeichert.path, erste.path, "Vorschau braucht einen neuen Pfad")
        assertEquals("png", erste.extension)
        assertEquals("neu 1", erste.readText())
        assertEquals("alt", gespeichert.readText(), "Verwerfen muss die alte zurückholen können")

        // Zweite Auswahl ohne Speichern: die erste, nie gespeicherte Kopie verschwindet.
        val zweite = waehle("neu 2", gespeichert.path, 1000)
        assertTrue(!erste.exists())
        assertTrue(zweite.exists() && gespeichert.exists())
        assertTrue(File(ordner, "honorarcraft.db").exists(), "Fremde Dateien bleiben")
    }

    @Test
    fun `nach dem Speichern der neuen faellt die alte beim naechsten Mal weg`() {
        val alt = File(ordner, "unterschrift.png").apply { writeText("alt") }
        val neu = waehle("neu", alt.path, 1)
        val danach = waehle("noch neuer", neu.path, 2)
        assertTrue(!alt.exists())
        assertTrue(neu.exists() && danach.exists())
    }
}
