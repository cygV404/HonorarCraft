# Fehlerbehebung nach der Durchsicht vom 13.11.2026 — Arbeitsplan

Stand: 13.11.2026. Noch nichts umgesetzt. Grundlage ist eine vollständige Durchsicht des Codes
auf Stand `384a9c7` (Version 2.1). Die CI war auf diesem Stand grün; die Fehler unten fallen
in keinem bestehenden Test auf.

Zeilenangaben beziehen sich auf `384a9c7` und verschieben sich mit jeder Änderung — vor dem
Ändern die Stelle neu suchen.

## Ziel

Neun Fehler und neun kleinere Punkte beheben, ohne das Datenbankschema anzufassen. Ergebnis
ist Version **2.1.1**, deren Sicherungen mit 2.1 austauschbar bleiben.

## Rahmen, der für alle Schritte gilt

- **Keine Änderung an den Entities, `DATABASE_VERSION` bleibt 12.** Kein Schritt hier braucht
  eine Migration. Wer meint, eine zu brauchen, hält an und fragt nach.
- `CLAUDE.md` im Repo gilt: gemeinsamer Code nach `jvmCommonMain`, UI-Texte deutsch und im
  Code, kein `REPLACE` auf `invoices`/`customers`, kein `withContext(Dispatchers.IO)` um Room.
- **Jeder Fehler bekommt zuerst einen Test, der ihn zeigt**, dann die Korrektur. Wo das nicht
  geht (Android-Verhalten, Dateidialoge), steht es beim Schritt und in der Abnahmeliste unten.
- Je Phase ein Commit, Nachricht im Stil des Repos (deutsch, ohne Umlaute im Betreff).
- Nach jeder Phase: `./gradlew :shared:jvmTest :shared:testAndroidHostTest :composeApp:jvmTest`.
  Am Ende zusätzlich `./gradlew build`.
- JDK 17 nötig. Auf dem Windows-Arbeitslaptop ist keines installiert; dort lässt sich nichts
  bauen. Die Arbeit gehört auf den T14 oder in eine Cloud-Sitzung.

## Entscheidungen, die Julian treffen muss

Für jede steht ein Vorschlag da. Ohne Antwort gilt der Vorschlag; Abweichungen unten unter
„Umsetzung — Abweichungen vom Plan“ eintragen.

1. **Nummer nach dem PDF (Fehler 2).** Vorschlag: nur weiterschalten, wenn es die nächste
   Nummer noch nicht gibt. Wer eine alte Rechnung erneut druckt, bleibt auf ihr stehen.
   Alternative: auf die nächste freie Nummer springen (überspringt aber eine angefangene,
   noch nicht gedruckte Rechnung).
2. **Jahr und Monat der Rechnungsnummer (Fehler 9).** Vorschlag: Der Dialog speichert Jahr
   und Monat nur, wenn das Feld im gewählten Format sichtbar ist **und** der Wert geändert
   wurde. Wer nichts ändert, bleibt beim Kalender. Offen bleibt dann, wie man von einem
   festgelegten Monat zurück zum Kalender kommt — siehe „Bewusst nicht Teil“.
3. **Zeitraum auf dem Deckblatt (kleiner Punkt i).** Heute Monat der ersten Position
   (Entscheidung 6 im `KMP_PLAN.md`). Vorschlag: erster Tag des Monats der ersten bis letzter
   Tag des Monats der letzten Position. Bei Rechnungen innerhalb eines Monats ändert sich
   nichts.
4. **Rechnung löschen (kleiner Punkt d).** Vorschlag: Nach dem Löschen der angezeigten
   Rechnung bleibt die Nummer stehen (sie ist dann einfach wieder leer). Der heutige Sprung
   auf „1“ entfällt.

## Die Fehler

### Phase 1 — PDF (`:shared`, Renderer und Layout)

**Fehler 1 — Zeilenumbruch oder unbekanntes Zeichen lässt das Desktop-PDF scheitern.**

- Ursache: `DataField` (`ui/DataWindow.kt`, ganz unten) ist nicht `singleLine`; ein Enter in
  Name, Straße, Ort oder im Kundendialog wird gespeichert. PDFBox 2.0.30 wirft in
  `PDCIDFontType2.encode` „No glyph for U+000A“, sobald `showText` ein Zeichen bekommt, das
  Roboto nicht hat. Das gilt genauso für Tabulatoren, Emojis und fremde Schriften. Die
  Oberfläche meldet nur „PDF konnte nicht erstellt werden“.
- Korrektur, drei Lagen:
  1. **Layout** (`pdf/InvoiceLayout.kt`): jeden Text, der in ein `DrawCommand.Text` geht,
     vorher glätten — Steuerzeichen und jede Folge von Leerraum zu einem Leerzeichen, außen
     getrimmt. Eine kleine private Funktion, an einer Stelle angewandt (am einfachsten beim
     Erzeugen der Befehle in `rechts`, `links`, `umbrechen` und in der Tabelle). Das deckt
     auch schon gespeicherte Daten ab, ohne Migration.
  2. **Desktop-Renderer** (`pdf/InvoicePdfWriter.jvm.kt`): vor `showText` und vor jeder
     Breitenmessung Zeichen ersetzen, die die Schrift nicht kodieren kann (je Codepoint
     `font.encode(...)` in `runCatching`, Ersatz `?`). Einmal protokollieren, nicht je Zeichen.
     Dieselbe Funktion in `pdfTextMeasurer()` und `kuerzen()` benutzen, sonst stimmen
     Rechtsbündigkeit und Kürzung nicht.
  3. **Eingabe**: `DataField` bekommt `singleLine = true`. Beim Speichern trimmen:
     `saveCompanyData` und `saveCustomer` im `MainViewModel` trimmen alle Textfelder
     (Pfadfelder `signaturePath`/`pdfPath` nicht anfassen).
- Zusätzlich: Die Fehlermeldung in `ui/EntryWindow.kt` (`onGeneratePdf`, `onFailure`) nennt
  den Grund: „PDF konnte nicht erstellt werden: <Meldung>“.
- Tests:
  - `InvoiceLayoutTest`: Kunde und Absender mit `"\n"` und `"\t"` im Namen → kein
    `DrawCommand.Text` enthält ein Steuerzeichen.
  - Neu `shared/src/jvmTest/.../pdf/InvoicePdfWriterTest.kt`: `createInvoicePdf` mit einem
    Fach wie `"Mathe 🙂 数学"` schreibt ein PDF (Länge > 0), ohne zu werfen.
  - `MainViewModelTest`: `saveCustomer` und `saveCompanyData` trimmen.

**Fehler 5 — Gleiche Honorarsätze gelten als verschieden.**

- Ursache: `InvoiceLayout.kt:126` — `invoice.entries.map { it.rate }.distinct()`.
  `BigDecimal.equals` unterscheidet 23 von 23.00. Das Satzfeld unter „Finanzdaten“
  normalisiert nicht (der Kundensatz schon, `ui/Kunden.kt`, `setScale(2)`).
- Korrektur: `distinctBy { it.stripTrailingZeros() }`. Die Normalisierung des allgemeinen
  Satzes kommt mit Fehler 6.
- Test: `InvoiceLayoutTest` — zwei Positionen mit `BigDecimal("23")` und `BigDecimal("23.00")`
  → Deckblatt zeigt die Zeile mit einem Satz, nicht „verschiedene Sätze“.

**Fehler 7, Teil PDF — Unterschrift fehlt nach dem Einlesen einer Sicherung.**

- Ursache: Die Sicherung enthält nur die Datenbank. `signaturePath` zeigt danach auf eine
  Datei, die es auf dem neuen Gerät nicht gibt. Das Layout hält die Unterschrift trotzdem für
  vorhanden: Datum oben entfällt, unten stehen Linie und Ort ohne Bild.
- Korrektur: In `createInvoicePdf` (`pdf/InvoicePdfWriter.kt`) den Pfad nur weiterreichen,
  wenn die Datei existiert (`File(pfad).isFile`); sonst `signaturePath = null` an
  `InvoiceLayout.build`. Der Vorgabewert in `build` selbst bleibt, damit `InvoiceLayoutTest`
  unverändert läuft.
- In `ui/EntryWindow.kt` vor dem Erzeugen prüfen: Pfad gesetzt, Datei fehlt → Meldung „Die
  Unterschrift wurde nicht gefunden. Bitte unter „Daten“ neu auswählen.“ Das PDF entsteht
  trotzdem.
- Test: jvmTest mit einem `CompanyData(signaturePath = "/gibt/es/nicht.png")` → das Modell
  enthält „Rechnungsdatum:“ und keinen `DrawCommand.Image`.

### Phase 2 — ViewModel und Rechnungsnummer (`shared/.../MainViewModel.kt`)

**Fehler 3 — Nach einem Formatwechsel lassen sich alte Rechnungen nicht mehr öffnen.**

- Ursache: `setSelectedInvoiceNumber` (Zeile 415) zerlegt die übergebene Nummer und setzt sie
  im **aktuellen** Format neu zusammen. Die Menüs in `CreateInvoice.kt` und `EntryWindow.kt`
  übergeben aber die fertige Nummer aus der Datenbank. „01“ wird unter „Jahr-Nr.“ zu
  „2026-01“, „2026-11-03“ unter „Nr.“ zu „03“.
- Korrektur: zwei Wege trennen.
  - Neu `openInvoice(exactNumber: String)` für die beiden Rechnungsmenüs. Lässt sich die
    Nummer im aktuellen Format verlustfrei zerlegen (Probe: `formatInvoice(teile…) ==
    exactNumber`), bleibt es beim heutigen Verhalten. Sonst merkt sich das ViewModel die
    Nummer wörtlich in einem neuen `MutableStateFlow<String?>` (Arbeitsname
    `geoeffneteRechnung`), und `formattedInvoiceNumber` liefert diesen Wert statt des
    zusammengesetzten.
  - `setSelectedInvoiceNumber` bleibt für die Übersicht (Plus, Minus, Dialog) und den
    Altdaten-Import und löscht die wörtlich gemerkte Nummer. Ebenso `setInvoiceFormat`,
    `setInvoiceYear`, `setInvoiceMonth` und `incrementInvoiceNumber`.
  - Die wörtlich gemerkte Nummer wird **nicht** in `Settings` gespeichert: nach einem
    Neustart steht wieder die laufende Nummer da.
  - Jahr und Monat, die das Menü beim Öffnen setzt, werden wie bisher nicht gespeichert —
    das ist gewollt, sonst fröre ein Blick in eine alte Rechnung den Monat ein (siehe
    Fehler 9).
- `deleteInvoice` (kleiner Punkt d): vergleicht heute `_selectedInvoiceNumber` (roh, „03“)
  mit der formatierten Nummer („2026-03“) und greift deshalb nur im Format „Nr.“. Künftig
  gegen `formattedInvoiceNumber.value` vergleichen; nach Entscheidung 4 bleibt die Nummer
  stehen und nur die wörtlich gemerkte Nummer wird gelöscht.
- Tests (`MainViewModelTest`): für jedes der drei Formate eine Rechnung in einem **anderen**
  Format anlegen, mit `openInvoice` öffnen, `currentInvoiceWithEntries.value?.invoice
  ?.invoiceNumber` prüfen. Dazu: Plus in der Übersicht verlässt die alte Rechnung wieder.
  Bestehende Tests „zusammengesetzte Nummer setzt Jahr und Monat mit“ und „Format
  YEAR_NUMBER zerlegt Jahr und Nummer“ müssen weiter laufen.

**Fehler 2 — Das PDF einer alten Rechnung verstellt die laufende Nummer.**

- Ursache: `EntryWindow.kt:107` ruft nach jedem PDF `incrementInvoiceNumber()`; das zählt von
  der **angezeigten** Nummer hoch. Rechnung 03 erneut gedruckt → angezeigt wird 04, die es
  schon gibt, und neue Stunden landen dort.
- Korrektur (Entscheidung 1): neue Funktion `nachPdfWeiterschalten()` im ViewModel.
  - Ist eine Nummer wörtlich geöffnet (Fehler 3): nur diese Merkung löschen, nicht zählen.
  - Sonst die nächste Nummer bilden und mit `invoiceDao.getInvoice(...)` prüfen. Gibt es
    sie noch nicht: weiterschalten. Gibt es sie: stehen bleiben.
  - `incrementInvoiceNumber()` bleibt für den bestehenden Test, die Oberfläche ruft die neue
    Funktion.
- Tests: Rechnungen 01 bis 03 anlegen, 02 anzeigen, `nachPdfWeiterschalten()` → Nummer bleibt
  02. Rechnung 03 anzeigen → 04.

**Fehler 9 — Jahr und Monat der Rechnungsnummer frieren ein.**

- Ursache: Ohne gespeicherten Wert folgen `invoiceYear`/`invoiceMonth` dem Kalender
  (Vorgabewert beim Start). Der Dialog in `ui/Dashboard.kt:191` ruft bei jedem „Speichern“
  `onInvoiceYearChange` und `onInvoiceMonthChange` — auch im Format „Nr.“, wo beide Felder
  gar nicht zu sehen sind. Ab da steht der Monat fest.
- Korrektur (Entscheidung 2): Jahr nur speichern, wenn das Format ein Jahr zeigt und
  `y != invoiceYear`; Monat nur, wenn das Format einen Monat zeigt und `m != invoiceMonth`.
- Dazu kleiner Punkt g: Monat nur 1 bis 12 annehmen. Bei ungültigem Wert `isError` am Feld
  und „Speichern“ gesperrt.
- Test: UI-Test in `composeApp/src/jvmTest` nach dem Muster von `RechnungsAngabenUiTest` —
  Dialog öffnen, nur die Nummer ändern, speichern → `InMemorySettings` enthält weder
  `invoice_year` noch `invoice_month`.

**Kleiner Punkt e — Doppeltipp auf „Hinzufügen“ legt den Eintrag zweimal an.**

- Ursache: `addEntryFromForm` leert das Formular erst nach dem Schreiben.
- Korrektur: ein privates Flag, das beim Start gesetzt und im `finally` der Coroutine
  gelöscht wird; steht es, kehrt `addEntryFromForm` sofort zurück.
- Test: `addEntryFromForm()` zweimal direkt hintereinander → genau ein Eintrag.

### Phase 3 — Oberfläche „Daten“ (`composeApp/.../ui/DataWindow.kt`)

**Fehler 4 — Ladezustand bleibt hängen.**

- Ursache: In `onExport` und `onImport` (Zeilen 108 und 123) und in `EntryWindow.kt`
  (`onGeneratePdf`) steht `setLoading(false)` hinter dem Aufruf, nicht in einem `finally`.
  Wird die Coroutine abgebrochen (Android: Activity wird bei offenem Dateidialog neu
  aufgebaut; breite Ansicht: Wechsel des Bereichs), bleibt `isLoading` im ViewModel `true`.
  Der Bereich „Daten“ ist dann gesperrt, bis die App beendet wird.
- Korrektur: an allen drei Stellen `try { … } finally { mainViewModel.setLoading(false) }`.
- Zusätzlich im Manifest (`androidApp/src/main/AndroidManifest.xml`) an der Activity:
  `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboard|keyboardHidden"`.
  Compose kommt mit diesen Änderungen selbst zurecht. Damit geht beim Drehen weder die
  Auswahl im Dateidialog verloren noch die Eingabe im Bereich „Daten“ (kleiner Punkt f).
- Test: nicht automatisierbar ohne Emulator → Abnahmeliste.

**Fehler 6 — Satzfeld ohne Prüfung.**

- Ursache: Zeile 683 übernimmt nur parsbare Eingaben und sagt sonst nichts. Wer „23,00“
  löscht, speichert 2. Null und negative Werte gehen durch.
- Korrektur nach dem Muster des Kundendialogs (`ui/Kunden.kt`, `satzGueltig`):
  gültig ist nur ein Betrag > 0. Ungültig → `isError`, Hinweistext „Bitte einen Betrag wie
  25,00 eingeben.“, der Speichern-Knopf speichert nicht und meldet das. Gespeichert wird
  `setScale(2, RoundingMode.HALF_UP)`. `DataField` braucht dafür optionale Parameter
  `isError` und `supportingText`.
- Test: UI-Test — Feld leeren, speichern → `companyData.value?.rate` unverändert. „24“
  eingeben, speichern → `BigDecimal("24.00")`.

**Fehler 8 — Neue Unterschrift, alte Vorschau.**

- Ursache: Android schreibt immer nach `filesDir/unterschrift.png`, der Desktop nach
  `unterschrift.<endung>`. Der Pfad ändert sich beim Ersetzen nicht, also lädt
  `rememberSignaturePreview` nicht neu. Außerdem ist die alte Datei schon beim Auswählen
  überschrieben; „Verwerfen“ holt sie nicht zurück.
- Korrektur: `pickSignatureImage(gespeicherterPfad: String)` — die Kopie bekommt einen
  eindeutigen Namen (`unterschrift-<Zeitstempel>.<endung>`). Danach alle anderen
  `unterschrift*`-Dateien im Zielordner löschen, **außer** der neuen und der unter
  `gespeicherterPfad`. Der Aufrufer übergibt `savedData?.signaturePath`, nicht den Entwurf.
  Betrifft die Schnittstelle in `ui/platform/PlatformServices.kt` und beide Umsetzungen.
- Test: Desktop-Umsetzung lässt sich wegen des `JFileChooser` nicht direkt testen. Den
  Kopier- und Aufräumteil in eine eigene Funktion ziehen (Quelle, Zielordner, gespeicherter
  Pfad → neuer Pfad) und diese in jvmTest prüfen.

**Kleiner Punkt a — Der Desktop „startet neu“ und ist einfach zu.**

- Ursache: `restartApp()` auf dem Desktop ist `exitProcess(0)`. Rückfrage und Meldung
  sprechen von Neustart, die Meldung ist nie zu sehen.
- Korrektur: `PlatformServices` bekommt `val neustartIstBeenden: Boolean` (Desktop `true`,
  Android `false`). Ist es `true`: Die Rückfrage vor dem Einlesen sagt „HonorarCraft wird
  danach beendet und muss neu geöffnet werden.“ Nach dem Einlesen erscheint ein Dialog
  „Sicherung eingelesen. HonorarCraft wird jetzt beendet.“ mit „OK“, erst dann
  `restartApp()`. Dasselbe im Fehlerfall mit `NeustartNoetigException`.

### Phase 4 — Sicherung und Plattform

**Fehler 7, Teil Ordner — Ablageordner von einem anderen Gerät.**

- Desktop (`ui/platform/PlatformServices.jvm.kt`): Ein `pdfPath`, der mit `content://`
  beginnt, gilt als leer — in `saveInvoicePdf` und in `describePdfFolder`. Heute entsteht
  unter Linux ein Ordner `content:/…`, unter Windows scheitert das PDF.
- Android (`PlatformServices.android.kt`):
  - `describePdfFolder`: ein Pfad, der nicht mit `content://` beginnt, wird wie leer
    beschrieben („Dokumente/HonorarCraft“).
  - `saveInvoicePdf`: Scheitert das Anlegen im gewählten Ordner (fehlendes Zugriffsrecht,
    Ordner gelöscht), in den MediaStore-Zweig weiterlaufen und als Ort
    „Dokumente/HonorarCraft — der gewählte Ordner ist nicht erreichbar, bitte unter „Daten“
    neu wählen“ zurückgeben.
- Test: Desktop-Zweig in jvmTest (`saveInvoicePdf` mit `pdfPath = "content://x"` und
  umgebogenem `user.home` schreibt in den Vorgabeordner). Android → Abnahmeliste.

**Kleiner Punkt b — Sicherung wird beim Einlesen nicht auf Unversehrtheit geprüft.**

- `Backup.pruefe` (`backup/Backup.kt`) prüft Tabellen und Version. Eine abgeschnittene Datei
  besteht das, wenn die ersten Seiten lesbar sind.
- Korrektur: zusätzlich `PRAGMA quick_check`; alles außer genau einer Zeile `ok` → „Die
  Sicherungsdatei ist beschädigt.“
- Die Kopie des alten Stands (`pre_import_backup.db`) wird heute gleich nach dem Tausch
  gelöscht. Künftig eine Generation behalten: neben der Datenbank als
  `<DATABASE_FILE_NAME>.vor-import`, beim nächsten Import überschrieben. Keine Oberfläche
  dafür, nur ein Satz in der README unter „Sicherung und Umzug“.
- Tests (`BackupTest`): abgeschnittene Datei wird abgelehnt und der Bestand bleibt; nach
  einem gelungenen Import liegt die Vor-Import-Kopie da und enthält den alten Stand.

**Kleiner Punkt c — Ergebnis des WAL-Checkpoints wird nicht gelesen.**

- `Backup.checkpoint`: Die erste Spalte der Ergebniszeile ist 1, wenn der Checkpoint
  blockiert war. Dann fehlen der Sicherung die jüngsten Änderungen.
- Korrektur: Spalte lesen; bei 1 bis zu dreimal mit kurzer Pause wiederholen, danach
  `error("Die Sicherung konnte nicht vollständig geschrieben werden. Bitte noch einmal
  versuchen.")`.

**Kleiner Punkt h — Altdaten-Import ist nicht wiederholungssicher.**

- `legacy/LegacyDesktopImport.kt`: Das Ergebnis von `renameTo` wird nicht geprüft. Bleibt der
  Altbestand liegen, legt der nächste Start alle Positionen und den Kunden noch einmal an.
- Korrektur: Nach den Datenbank-Schreibzugriffen und **vor** dem Wegräumen eine Markierung
  `altdaten-uebernommen` im Datenordner anlegen; `hasLegacyData` liefert `false`, solange sie
  liegt. Scheitert ein `renameTo`, protokollieren.
- Test (`LegacyDesktopImportTest`): zweiter Lauf mit liegengebliebenem Altbestand und
  Markierung → `null`, keine zusätzlichen Zeilen.

**Kleiner Punkt i — Zeitraum auf dem Deckblatt** (nur nach Entscheidung 3).

- `InvoiceLayout.kt`: `bis` aus dem Monat der **letzten** Position bilden. Test in
  `InvoiceLayoutTest` mit Positionen aus zwei Monaten; Entscheidung 6 im `KMP_PLAN.md`
  nachführen.

### Phase 5 — Abschluss

- `gradle/libs.versions.toml`: `appVersion = "2.1.1"`, `androidVersionCode = "8"`. Die
  Ergänzung der dritten Stelle in `composeApp/build.gradle.kts` verträgt drei Stellen
  (`take(3)`).
- README-Überschrift auf v2.1.1; Satz zur Vor-Import-Kopie.
- `CLAUDE.md`: Stand-Zeile, `nachPdfWeiterschalten`/`openInvoice` im Abschnitt „Gemeinsames
  ViewModel“, `neustartIstBeenden` und die neue Signatur von `pickSignatureImage` unter
  „Oberfläche“, Vor-Import-Kopie unter „Sicherung“.
- Diesen Plan abschließen: Stand-Zeile oben, Abweichungen unten.
- `./gradlew build` und die drei Test-Tasks grün, dann Push auf `main` — die CI baut die
  Pakete.

## Abnahme von Hand

Was kein Test abdeckt. Desktop auf einem Rechner mit JDK 17, Android im Emulator oder auf
dem Gerät.

- [ ] Desktop: Enter im Feld „Name“ ist nicht mehr möglich; ein Kunde mit Emoji im Namen
      ergibt ein PDF mit `?` an der Stelle.
- [ ] Desktop: Bestand mit bereits gespeichertem Zeilenumbruch (vor dem Update angelegt)
      ergibt ein PDF.
- [ ] Beide: Format „Nr.“ → Rechnung anlegen → Format „Jahr-Monat-Nr.“ → die alte Rechnung
      über das Menü öffnen, Einträge sind da, PDF trägt die alte Nummer.
- [ ] Beide: alte Rechnung erneut als PDF → Nummer bleibt stehen.
- [ ] Android: „Daten sichern“, im Dateidialog das Gerät drehen, zurück → kein Dauer-Ladekreis,
      die Sicherung wird geschrieben.
- [ ] Android: im Bereich „Daten“ tippen, drehen → Eingabe steht noch da.
- [ ] Beide: neue Unterschrift wählen → Vorschau wechselt sofort; „Verwerfen“ → alte
      Unterschrift ist wieder da und steht im nächsten PDF.
- [ ] Sicherung vom Handy **mit gewähltem Ordner** auf dem Desktop einlesen → PDF landet im
      Vorgabeordner; Hinweis auf die fehlende Unterschrift erscheint.
- [ ] Desktop: Sicherung einlesen → Dialog „wird jetzt beendet“, nach „OK“ schließt die App,
      nach dem Öffnen ist der Bestand da.
- [ ] Android-Release-Build (R8) auf dem Emulator: PDF erzeugen, Sicherung schreiben und
      einlesen. Die CI baut kein Release.

## Reihenfolge und Abhängigkeiten

Phase 1 und Phase 4 sind unabhängig von allem anderen. In Phase 2 zuerst Fehler 3, dann
Fehler 2 (baut auf der wörtlich gemerkten Nummer auf). Phase 3 berührt `EntryWindow.kt` an
derselben Stelle wie Fehler 1 (Fehlermeldung) und Fehler 2 (Aufruf nach dem PDF) — deshalb
nach Phase 1 und 2. Alles in einer Sitzung und auf einem Branch; die Phasen teilen sich zu
viele Dateien für parallele Arbeit.

## Bewusst nicht Teil des Vorhabens

- **Unterschrift in der Sicherung.** Das wäre ein neues Dateiformat (Archiv statt
  Datenbankdatei) und gehört zum `WLAN_PLAN.md`. Hier nur der Hinweis, wenn sie fehlt.
- **Rückweg vom festgelegten Monat zum Kalender** in der Rechnungsnummer. Braucht eine
  Entscheidung, wie die Nummer überhaupt gedacht ist (Monat der Leistung, Monat der
  Rechnung, Zähler je Monat).
- **Sortierung der Rechnungsliste** (alphabetisch, „100“ steht zwischen „10“ und „11“) und
  eine leere Rechnung, die nach dem Löschen aller Einträge stehen bleibt.
- **Dateidialoge des Desktops auf dem Swing-Thread.** Laufen heute auf `Dispatchers.IO`, im
  Code so begründet.
- **Temporäre Schriftdateien auf Android** (`File.createTempFile` je Prozess). Liegen im
  Cache, das System räumt sie.

## Umsetzung — Abweichungen vom Plan

(noch leer)
