package de.v404.honorarcraft.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import de.v404.honorarcraft.shared.data.CompanyData
import de.v404.honorarcraft.shared.data.InvoiceWithEntries

/**
 * Alles, was die Oberfläche vom Betriebssystem braucht und was sich zwischen Android und
 * Desktop unterscheidet: Datei-Dialoge und der Neustart nach einem Import.
 *
 * Bewusst eine Schnittstelle und kein `expect`-Objekt. Auf Android müssen die Dialoge über
 * `rememberLauncherForActivityResult` in der Komposition angemeldet werden, auf dem Desktop
 * ist es ein einfacher `JFileChooser` — das lässt sich nur auf der Aufrufseite sauber
 * zusammenführen. Außerdem ist eine Schnittstelle im Test austauschbar.
 *
 * Alle `pick`-Aufrufe liefern `null`, wenn der Nutzer abbricht.
 */
interface PlatformServices {

    /**
     * Lässt ein Bild auswählen und kopiert es in den Datenbereich der App.
     *
     * @return Pfad der Kopie. Kopiert wird, weil die Auswahl auf Android nur ein
     *   kurzlebiges Zugriffsrecht auf eine fremde Datei ist — ein gemerkter Pfad dorthin
     *   wäre beim nächsten Start wertlos.
     */
    suspend fun pickSignatureImage(): String?

    /** Lässt einen Speicherort wählen und schreibt die Sicherung dorthin. */
    suspend fun exportBackup(): Result<Long>?

    /** Lässt eine Sicherung auswählen und liest sie ein. */
    suspend fun importBackup(): Result<Int>?

    /**
     * Lässt den Ordner für die erzeugten PDFs wählen.
     *
     * @return Was in `CompanyData.pdfPath` gespeichert wird. Auf dem Desktop ein Dateipfad,
     *   auf Android die `content://`-Adresse des gewählten Ordnerbaums samt dauerhaftem
     *   Zugriffsrecht. Beides landet im selben Feld — die Plattform weiß, wie sie es liest.
     */
    suspend fun pickPdfFolder(): String?

    /** Beschreibt [pdfPath] so, dass es der Nutzer lesen kann. */
    fun describePdfFolder(pdfPath: String): String

    /**
     * Erzeugt die Rechnung als PDF und legt sie am plattformüblichen Ort ab.
     *
     * Gezeichnet wird auf beiden Seiten aus demselben Layout; hier unterscheidet sich nur,
     * wohin die Datei kommt — auf dem Desktop in den eingestellten Ordner, auf Android über
     * den MediaStore nach `Dokumente/HonorarCraft`.
     *
     * @return Beschreibung des Ablageorts für die Rückmeldung an den Nutzer.
     */
    suspend fun saveInvoicePdf(
        invoice: InvoiceWithEntries,
        company: CompanyData,
        formattedInvoiceNumber: String,
    ): Result<String>

    /**
     * Startet die Anwendung neu.
     *
     * Nach einem Import nicht optional: die Room-Flows hängen an der geschlossenen
     * Verbindung und würden sonst nur noch leere Listen zeigen.
     */
    fun restartApp()
}

val LocalPlatformServices = staticCompositionLocalOf<PlatformServices> {
    error("PlatformServices wurde nicht bereitgestellt")
}

/** Baut die plattformeigene Umsetzung auf. Auf Android meldet sie dabei ihre Dialoge an. */
@Composable
expect fun rememberPlatformServices(): PlatformServices
