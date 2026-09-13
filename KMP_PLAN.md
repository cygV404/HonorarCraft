# Umbau zu einem KMP-Projekt — Arbeitsplan

Stand: 13.09.2026. Phase 0 und Phase 1 sind erledigt, gearbeitet wird auf dem Branch `kmp`.

## Getroffene Entscheidungen (13.09.2026)

1. **Basis ist dieses Repo.** Der Android-Code wandert per `git subtree` herein, seine History
   bleibt erhalten; `HonorarCraftAndroid` wird danach Archiv.
2. **Package überall `de.v404.honorarcraft`**, Gradle-Rootname jetzt `HonorarCraft`
   (war `accountingapp`). Erledigt für den Desktop-Code.
3. **Room auch auf dem Desktop.** Fällt der Spike durch, SQLite über JDBC hinter demselben
   Repository-Interface — nicht bei JSON bleiben.
5. **Eine gemeinsame, adaptive Oberfläche** (Breiten-Abfrage statt zweier Implementierungen).
8. **iOS bleibt außen vor.** Kein iOS-Target, auch nicht vorbereitend in der Struktur.
   Damit ist die Nutzung von JVM-APIs im gemeinsamen Code (`jvmCommonMain`, `java.math.BigDecimal`)
   eine dauerhafte Entscheidung und keine Notlösung mehr.

Offen bleiben die Fragen 4, 6 und 7 am Ende dieser Datei.

## Ausgangslage

| | Desktop (dieses Repo) | Android (`~/StudioProjects/HonorarCraftAndroid`) |
|---|---|---|
| Version | 1.2.1 | 1.5 (`versionCode` 5, im Play Store) |
| Package | `app.accounting.accountingapp` | `de.v404.honorarcraftandroid`, appId `de.v404.honorarcraft` |
| Kotlin / Compose | 2.2.21 / CMP 1.9.3 | 2.4.10 / Compose BOM 2026.08.00, AGP 9.3.2 |
| Daten | JSON + `java.util.prefs` | Room 2.8.4 + KSP, Migrationen, `app/schemas/` |
| State | `remember` im Composable | `MainViewModel` + StateFlow |
| Tests | keine | CalculationTest, MigrationTest, BackupTest |
| PDF | PDFBox 2.0.30 | `android.graphics.pdf.PdfDocument` |

Die Android-App ist die reifere Codebasis. **Richtung des Umbaus: Android-Logik wird zur
gemeinsamen Wahrheit, der Desktop übernimmt sie** — nicht umgekehrt.

Wichtig, weil es Beträge ändert: Die beiden Apps rechnen heute unterschiedlich
(55 h × 23 € → Desktop 1.686,59 €, Android 1.686,67 €; Details in der Analyse vom 13.09.).
Android ist korrekt. Nach dem Umbau rechnet der Desktop anders als bisher — bereits
erzeugte PDFs bleiben davon unberührt, aber der angezeigte Jahresumsatz kann sich um
wenige Cent verschieben.

## Zielstruktur

```
HonorarCraft/                     (dieses Repo, umbenanntes Root-Projekt)
├── shared/          jvmCommonMain Modelle, Room-Entities+DAOs, Rechenlogik,
│                                 Rechnungsnummern-Format, ViewModel, Repository
│                    androidMain  Room-Driver Android, Datei-/Share-Zugriff
│                    jvmMain      Room-Driver JVM, Datei-Zugriff
│                    commonTest   CalculationTest (aus der Android-App übernommen)
├── composeApp/      commonMain   gemeinsame Screens + Theme + Compose-Resources
│                    androidMain  android-spezifische Composables
│                    jvmMain      main.kt, PDFBox-Renderer
└── androidApp/                   MainActivity, Manifest, Splashscreen, applicationId
```

**`androidApp` ist zwingend, nicht optional.** Ab AGP 9 lassen sich `com.android.application`
und `com.android.library` nicht mehr mit dem KMP-Plugin im selben Modul kombinieren
(harter Fehler beim Anwenden des Plugins). KMP-Module nutzen stattdessen
`com.android.kotlin.multiplatform.library` mit dem `kotlin { androidLibrary { … } }`-Block und
haben kein `androidTarget()`. Die eigentliche Android-Anwendung braucht deshalb ein eigenes,
nicht-multiplattformfähiges Modul. Ein `kotlin-android`-Plugin wird dort nicht mehr angewendet —
AGP 9 bringt Kotlin-Unterstützung mit.

## Phase 0 — Vorbereitung (vor allem anderen)

- [x] GitHub-Actions-Build bei Push abgeschaltet (`.github/workflows/package.yml`,
      jetzt nur noch `workflow_dispatch`). Rückbau: die drei auskommentierten Zeilen wieder aktivieren.
- [x] **Beide Repos gesichert**: annotierter Tag `pre-kmp` in diesem Repo und in
      `HonorarCraftAndroid`. Beide sind noch nicht gepusht.
- [x] Desktop-Nutzdaten gesichert: `~/honorarcraft-backups/desktop-data-2026-09-13.tar.gz`
      (gesamtes `~/.honorarcraft`, 18 Dateien).
- [ ] **Offen, von Hand zu erledigen:** ein `.hcbackup` aus der Android-App exportieren und
      neben das Desktop-Archiv legen. Das ist die Abnahmeprobe für Phase 2.
- [x] Arbeitsbranch `kmp` angelegt.
- [ ] Zu beachten: Im Android-Repo liegen uncommittete Änderungen auf dem Branch
      `fix/datenverlust-und-pdf-export` (u. a. `DataWindow.kt`, `MainActivity.kt`). Die müssen
      committet sein, **bevor** der Subtree-Import läuft — der Tag `pre-kmp` erfasst sie nicht.

## Phase 1 — Build-Gerüst

Abgeschlossen. Gewählte Versionen:

| | vorher | jetzt |
|---|---|---|
| Gradle | 8.14.3 | 9.5.0 (AGP 9 verlangt Gradle 9) |
| Kotlin | 2.2.21 | 2.4.10 (wie Android) |
| Compose Multiplatform | 1.9.3 | 1.12.0 |
| Compose Hot Reload | 1.0.0 | 1.2.0 |
| Lifecycle (jetbrains) | 2.9.6 | 2.11.0 |
| kotlinx-serialization | 1.6.0, Plugin hart 1.9.0 | 1.11.0, Plugin an Kotlin gebunden |
| AGP / KSP / Room / sqlite | — | 9.3.2 / 2.3.11 / 2.8.4 / 2.6.2 |

- [x] Kotlin, Compose und Gradle gehoben; Serialization-Plugin hängt jetzt an der
      Kotlin-Version statt auf 1.9.0 festgenagelt zu sein.
- [x] Alle Versionen in `gradle/libs.versions.toml`, auch `compileSdk`/`minSdk`/`targetSdk`
      (37/29/36) und die bisher hart notierten Abhängigkeiten (PDFBox, JNA).
- [x] `shared`-Modul (Android + JVM) und `androidApp`-Modul angelegt, `composeApp` um das
      Android-Target erweitert, `settings.gradle.kts` erweitert, `local.properties` mit
      `sdk.dir` geschrieben (die Keystore-Zeilen aus dem Android-Repo fehlen hier noch —
      nötig erst, wenn hier Release-APKs gebaut werden).
- [x] PDFBox und JNA von `commonMain` nach `jvmMain` verschoben; sie hätten sonst am
      Android-Target gehangen.
- [x] Paketumbenennung `app.accounting.accountingapp` → `de.v404.honorarcraft`, dazu
      `mainClass` und macOS-`bundleID`.
- [x] Abnahme: `./gradlew build` grün, `:androidApp:assembleDebug` erzeugt ein APK,
      `:composeApp:run` startet den Desktop unverändert.

**Zwei Fallstricke, die dabei auftraten:**

- Die generierte `Res`-Klasse lag im Paket `accountingapp.composeapp.generated.resources` —
  abgeleitet vom Gradle-Rootnamen. Mit dessen Umbenennung brach der Import. Jetzt ist das
  Paket über `compose.resources { packageOfResClass = "de.v404.honorarcraft.resources" }`
  festgenagelt und hängt nicht mehr am Projektnamen.
- Der Preferences-Knoten `app/accounting/honorarcraft` in `CompanyData.kt` wurde **nicht**
  mit umbenannt und darf es auch nicht werden: dort liegt der AES-Schlüssel bestehender
  Installationen. Eine Umbenennung würde alle verschlüsselten Felder still leeren
  (`decrypt` schluckt jede Exception). Im Code steht jetzt ein Kommentar dazu.
- Die macOS-`bundleID` hat sich dagegen geändert — auf dem Mac gilt die App damit als neue
  App. Für den Desktop unkritisch (kein Store), aber bewusst so entschieden.

## Phase 2 — Datenschicht teilen (das technische Risiko)

- [x] **Spike bestanden.** Room 2.8.4 läuft mit `androidx.sqlite:sqlite-bundled` 2.6.2 auf dem
      JVM-Desktop: `SpikeItem` / `SpikeDao` / `SpikeDatabase` in `shared/commonMain`, der Test
      `SpikeDatabaseTest` in `shared/src/jvmTest` legt eine Datei an, schreibt zwei Zeilen,
      liest sie über einen `Flow` und summiert. Grün. **Die Rückfallebene SQLite/JDBC wird
      nicht gebraucht.** Der Wegwerf-Code verschwindet mit dem Umzug der echten Entities.

      Das gehörte dazu, damit es baut:
      - `room-runtime` 2.8.4 ist ein echtes KMP-Artefakt mit eigener `jvm`-Variante.
      - Der Room-Compiler muss **pro Target** eingehängt werden (`add("kspJvm", …)` und
        `add("kspAndroid", …)`); ein gemeinsames `ksp(...)` greift im KMP-Modul nicht.
      - Die Datenbank braucht `@ConstructedBy` mit einem `expect object …
        RoomDatabaseConstructor<…>` ohne eigenes `actual` — die generiert Room je Target selbst.
      - Auf dem Desktop wird die Datenbank über `Room.databaseBuilder<T>(name = pfad)` plus
        `.setDriver(BundledSQLiteDriver())` gebaut, also ohne System-SQLite.
      - `withHostTest {}` im `androidLibrary`-Block, sonst läuft `commonTest` nicht gegen das
        Android-Target (wichtig ab Phase 3 für den `CalculationTest`).
      - Die Lint-Tasks von AGP lesen das KSP-Ausgabeverzeichnis, ohne die Abhängigkeit zu
        kennen; `./gradlew build` bricht ohne die `dependsOn`-Zeilen am Ende von
        `shared/build.gradle.kts` mit "implicit dependency" ab. Dasselbe Muster gibt es im
        Android-Repo schon für `mergeDebugAndroidTestAssets`.
- [x] Android-Repo per `git subtree` nach `android-legacy/` importiert, History vollständig
      (43 Commits). Das Verzeichnis ist ein **Zwischenlager**, aus dem Stück für Stück
      umgezogen wird, und verschwindet am Ende von Phase 5.
      Achtung bei der History: `git log -- <pfad>` zeigt wegen der Pfadverschiebung nur den
      Import-Commit. Was wirklich funktioniert:
      `git log --full-history -- android-legacy/<neuer pfad> <alter pfad>`.
- [x] `InvoiceData`, `InvoiceEntry`, `CompanyData`, `HiddenSubject`, `Converters`,
      `Constants`, `AppDatabase`, `Migrations` liegen jetzt unter
      `shared/src/jvmCommonMain/.../shared/data/`, Paket `de.v404.honorarcraft.shared.data`.
      `@Keep` und `androidx.annotation` sind raus.

      **Nicht `commonMain`, sondern ein Zwischen-Quellsatz `jvmCommonMain`.** Die Entities und
      die Rechenlogik hängen an `java.math.BigDecimal`, und `java.*` ist in `commonMain` nicht
      verfügbar. Da Android und Desktop beide JVM-Ziele sind, hängt zwischen `commonMain` und
      den Targets jetzt `jvmCommonMain` (analog `jvmCommonTest`). Da iOS außen vor bleibt
      (Entscheidung 8), ist das keine Notlösung, sondern der Zielzustand — `BigDecimal` und der
      Rest der JVM-Bibliothek stehen im gemeinsamen Code dauerhaft zur Verfügung.
- [x] Migrationen auf die KMP-API portiert: `Migration.migrate(SupportSQLiteDatabase)` gibt es
      in `commonMain` nicht, die Signatur ist jetzt `migrate(connection: SQLiteConnection)`.
      Das SQL selbst ist unverändert geblieben.
- [x] `AppDatabase` aufgeteilt: DAOs, `@Database` und die Migrationsliste gemeinsam,
      die Erzeugung je Plattform. **Android bleibt bewusst beim Android-eigenen SQLite**
      (kein `setDriver`), damit die ausgelieferte App ihr heutiges Verhalten behält; der
      Desktop nutzt `BundledSQLiteDriver`, weil es dort kein System-SQLite gibt.
      Offen: ob Android später ebenfalls auf das gebündelte SQLite umgestellt wird — das
      wäre identischeres Verhalten, aber eine Änderung an einer App mit echten Nutzerdaten.
- [x] `app/schemas/` mitgenommen nach `shared/schemas/de.v404.honorarcraft.shared.data.AppDatabase/`
      (Versionen 3 bis 11). **Abnahme bestanden: der portierte Code erzeugt ein bit-identisches
      Schema 11**, `identityHash` `ff3c7c472631b050dd51d3586f067f3a` auf beiden Targets — also
      genau der Wert der Android-App. Bestehende Datenbanken und `.hcbackup`-Dateien bleiben
      damit lesbar. Geprüft, indem `11.json` gelöscht und neu erzeugt wurde.
      Nicht mitgenommen: `com.juliandobrodolac.honorarcraftandroid.AppDatabase/3.json` aus der
      Zeit vor der Paketumbenennung — liegt weiterhin in `android-legacy/` und in der History.
- [x] `DesktopDatabaseTest` (4 Tests, grün) belegt auf dem Desktop: Rechnung mit Positionen
      anlegen, Summen rechnen (55 h × 23 € → **1.686,67 €**, der korrekte Android-Wert),
      Fachvorschläge nach Häufigkeit und Ausblenden ohne Belegverlust, Jahresfilter über das
      Datum, `BigDecimal`-Satz verlustfrei durch den TypeConverter.
- [x] Der Spike-Code ist entfernt.
- [ ] Datenbankdatei-Pfad als `expect/actual`: Android `getDatabasePath`, Desktop
      `getAppDataFolder()` (heute `Dashboard.kt:34`).
- [x] **Importer** gebaut: `LegacyDesktopImport` in `shared/src/jvmMain/.../legacy/`.
      Liest `company.json`, `invoices/*.json`, `totals/year_*.json` und
      `last_invoice_number.txt`, schreibt nach Room und verschiebt den Altbestand
      anschließend nach `vor-room-<datum>/` — gelöscht wird nichts.
      Die AES-Entschlüsselung der Identitätsfelder liegt als `LegacyVaultCrypto` daneben;
      sie liest denselben Preferences-Knoten wie früher und legt bewusst **keinen** neuen
      Schlüssel an (ohne Schlüssel gibt es nichts zu entschlüsseln).
      Zwei Fallen beim Abbilden der Daten:
      - Die alte Fassung speicherte Stunden als `Double`. Die Umwandlung läuft über
        `BigDecimal(hours.toString())`, nicht über den `Double`-Wert — sonst würde aus
        1,5 h die binäre Näherung 1,5000000000000002.
      - Die alte Fassung kannte nur **einen** Honorarsatz für alles. Beim Import bekommt
        jede Position den damaligen globalen Satz aus `hourRate`.
- [x] **Abnahme am echten Bestand** (aus `~/honorarcraft-backups/desktop-data-2026-09-13.tar.gz`):
      10 Rechnungen, 64 Positionen und die Firmendaten übernommen, die verschlüsselten Felder
      sauber entschlüsselt.

      **Der Jahresumsatz 2026 verschiebt sich dabei von 4.217,28 € auf 4.216,67 €, also um
      61 Cent nach unten.** Das ist genau der erwartete Effekt: die alte Desktop-Fassung
      rundete je Rechnung, die übernommene Android-Logik rundet einmal am Ende. Die
      Android-Rechnung ist die richtige, bereits erzeugte PDFs sind nicht betroffen.
- [ ] **Offen und eine eigene Entscheidung: die Jahres-Startwerte.** Der reservierte Schlüssel
      `"S"` in `totals/year_*.json` ist ein von Hand eingetragener Startwert, für den es im
      Room-Modell keine Entsprechung gibt. Im echten Bestand stehen dort **2024: 271,90 €**
      und **2025: 4.353,69 €**. Beide Jahre haben keine einzige Position, zeigen nach dem
      Import also 0,00 € statt der eingetragenen Beträge. Der Importer meldet die Werte in
      seinem Bericht, und die Dateien liegen weiter im Archivordner — verloren sind sie also
      nicht, aber die App zeigt sie nicht mehr.
      Um sie zu behalten, bräuchte es eine eigene Tabelle und damit Schemaversion 12 samt
      Migration — also auch eine neue Fassung der Play-Store-App. Deshalb hier nicht
      im Vorbeigehen entschieden.
- [ ] Abnahme: Ein `.hcbackup` vom Handy lässt sich auf dem Desktop öffnen und zeigt
      dieselben Zahlen.

## Phase 3 — Fachlogik und Tests teilen

- [x] `CalculationTest.kt` liegt in `shared/src/jvmCommonTest` und läuft damit gegen **beide**
      Plattformen: 14 Tests, grün unter `:shared:jvmTest` und `:shared:testAndroidHostTest`.
      JUnit-Importe durch `kotlin.test` ersetzt (gleiche `assertEquals`-Semantik, `BigDecimal`
      wird weiterhin skalengenau verglichen).
- [x] `InvoiceFormat` und `formatInvoice` aus `MainViewModel.kt` herausgelöst nach
      `shared/.../data/InvoiceNumber.kt` — der `CalculationTest` braucht sie, und sie hingen
      nur zufällig im ViewModel.
- [x] Rechenlogik (`totalSum`, `totalLessonUnit`), `InvoiceFormat`/`formatInvoice` und
      `Constants` liegen in `:shared`.
- [ ] **Offen:** Die Desktop-Varianten `totalCostsHours` / `totalCostsLessonUnits` mit der
      vertauschten Benennung streichen. Geht erst, wenn die Desktop-Oberfläche auf die
      gemeinsame Logik umgestellt ist — bis dahin hängt die laufende App daran.
- [x] `MainViewModel` liegt in `shared/src/jvmCommonMain`. Es erbt jetzt von
      `androidx.lifecycle.ViewModel` statt von `AndroidViewModel`; **Datenbank und
      Einstellungsspeicher werden hereingereicht**, statt aus einem `Application`-Objekt
      gezogen zu werden. Damit läuft es im Test ohne Emulator.
      - `SharedPreferences` ist durch das Interface `Settings` ersetzt. Kein `expect/actual`,
        sondern eine normale Schnittstelle mit drei Implementierungen: `AndroidSettings`
        (SharedPreferences, Dateiname "settings" unverändert — sonst verliert ein bestehendes
        Gerät seine Rechnungsnummer), `PreferencesSettings` (`java.util.prefs`, eigener Knoten
        neben dem alten AES-Knoten) und `InMemorySettings` für Tests.
      - `Toast` ist einem `SharedFlow<String>` namens `messages` gewichen; ob daraus ein Toast
        oder eine Snackbar wird, entscheidet die Oberfläche. `showMessage` steht der
        plattformeigenen Schicht offen.
      - `Log.e` ist ein `expect fun logError` (Android: Logcat, Desktop: stderr).
      - `exportData`, `importData` und `generatePdf` sind **nicht** mitgekommen — sie hängen an
        `Uri`, `Backup` und `createInvoicePdf` und gehören nach Phase 6. Die Oberfläche ruft
        danach `incrementInvoiceNumber()` bzw. `showMessage(...)` auf.
      - `clearAllTables()` gibt es in Room-KMP nicht; dafür gibt es jetzt einen
        `MaintenanceDao` mit expliziten Löschabfragen in einer Transaktion.
      - Die `withContext(Dispatchers.IO)`-Klammern um die Room-Aufrufe sind raus: Room führt
        seine suspend-Abfragen ohnehin auf dem Abfrage-Kontext aus. Sie waren doppelt gemoppelt
        und haben den Ablauf untestbar gemacht.
      - `createDesktopDatabase` nimmt den Abfrage-Kontext jetzt als Parameter (Vorgabe
        `Dispatchers.IO`), damit Tests ihren Test-Dispatcher hereinreichen können.
- [x] Jahresumsatz wird im gemeinsamen ViewModel nur noch aus den Positionen abgeleitet
      (`date.endsWith(jahr)`). Die `totals/year_*.json` des Desktops entfallen mit dem
      Importer — die Doppelzählung über den Jahreswechsel ist damit erledigt.
- [x] `MainViewModelTest` (7 Tests) deckt die verzwickte Zerlegung der Rechnungsnummer ab,
      das Hochzählen, den Honorarsatz aus den Firmendaten, das Trimmen der Eingaben, die
      Meldung bei ungültiger Stundenzahl und das Zurücksetzen.
- [x] Abnahme (Teil 1): 25 Tests grün — 14 Rechen-Tests auf beiden Plattformen, 4 Datenbank-
      und 7 ViewModel-Tests auf dem Desktop.
- [ ] Abnahme (Teil 2): Desktop zeigt für dieselben Daten dieselben Summen wie das Handy —
      steht noch aus, weil die Desktop-Oberfläche die gemeinsame Schicht noch nicht nutzt.

## Phase 4 — Assets und Theme übernehmen

- [x] `montserrat_bold.ttf` liegt in `composeApp/src/commonMain/composeResources/font/` und
      wird über `Res.font.montserrat_bold` geladen. **Der Google-Fonts-Provider ist ersatzlos
      raus** — es gibt ihn nur auf Android, und die App nutzt ohnehin nur den fetten Schnitt.
      Damit wurde auch `res/values/font_certs.xml` (die Zertifikatsliste des Providers)
      überflüssig und ist gelöscht.
- [x] `Color.kt`, `Theme.kt`, `Type.kt` liegen in `composeApp/src/commonMain/.../ui/theme/`.
      Die einzige android-spezifische Stelle waren die dynamischen Systemfarben; sie sind
      jetzt ein `expect fun dynamicColorSchemeOrNull(darkTheme)` — Android liefert ab API 31
      ein Schema, der Desktop `null`, und dann greift das statische Schema.
- [x] **Farbschema aufs Branding ausgerichtet.** Das Vorlagen-Lila ist weg; beide Schemata
      leiten sich aus `BrandCyan` (#18FFFF) und `BrandGreen` (#00E676) ab. Im Dunkelmodus
      stehen die Markenfarben unverändert, so wie das Icon auf dem schwarzen Startbildschirm;
      im Hellmodus wären sie unlesbar (Cyan auf Weiß), dort werden dunkle Töne derselben
      Farbtöne verwendet und die hellen Originale tauchen als Container-Farben wieder auf.
      **`dynamicColor` ist jetzt standardmäßig aus** — Androids dynamische Farben hätten das
      Markenschema auf jedem Gerät überschrieben, also genau das verhindert, wofür das
      Branding da ist.
- [x] `values-night`-Farben und die Launcher-Ressourcen sind nach `androidApp/src/main/res/`
      umgezogen; das Manifest verweist jetzt auf `@mipmap/ic_launcher`, `@string/app_name`
      und `@style/Theme.HonorarCraftAndroid`.
- [x] App-Icon vereinheitlicht. Die Desktop-Icons trugen bisher ein **anderes Bild** als
      Android (blaues Buch mit Euro-Zeichen statt des „HC"-Markenzeichens); sie sind jetzt
      alle aus `~/Schreibtisch/HonorarCraft_Assets/honorarcraft_icon.png` erzeugt.
      Auf dem Rechner war kein Icon-Werkzeug vorhanden (kein ImageMagick, kein `icotool`,
      kein Pillow), deshalb liegt der Generator als
      `tools/MakeIcons.java` im Repo: `java tools/MakeIcons.java <quelle.png> <zielordner>`
      schreibt `.ico` (7 Größen), `.icns` (9 Einträge) und `iconDeb.png`.
      Einschränkung: Die Quelle ist 512×512, der macOS-Eintrag `ic10` (1024×1024) fehlt daher.
      Für ein schärferes Icon auf Retina-Displays bräuchte es eine größere Quelldatei —
      `favicon.svg` liegt im Asset-Ordner und wäre der bessere Ausgangspunkt.
- [x] Das Theme hängt jetzt auf beiden Plattformen (`HonorarCraftTheme`), auf dem Desktop
      ersetzt `MaterialTheme.colorScheme.background` das harte `Color.White`.
- [ ] **Bewusst noch nicht: Dunkelmodus auf dem Desktop.** Die Screens dieses Moduls tragen
      rund drei Dutzend fest verdrahtete Farben; mit `isSystemInDarkTheme()` wäre die
      Oberfläche auf einem dunkel eingestellten System halb unlesbar. In `main.kt` steht
      deshalb `HonorarCraftTheme(darkTheme = false)`. Sobald die Screens in Phase 5 durch die
      gemeinsame Oberfläche ersetzt sind, kann der Parameter entfallen — der Vorgabewert ist
      bereits `isSystemInDarkTheme()`.
- [ ] Die PDF-Schriften (`Roboto-*.ttf` unter `jvmMain/resources/font/`) bleiben, wo sie
      sind — die lädt PDFBox über den ClassLoader, das ist ein anderer Mechanismus als
      Compose Resources.

## Phase 5 — UI

Umfang hängt an Frage 5. Grobrichtung:

**Entschieden:** Dieselben vier Bereiche auf beiden Plattformen (Übersicht, Erstellen, PDF,
Daten). Handy: Pager mit Tabs am unteren Rand wie bisher. Desktop: dauerhaft sichtbare
Seitenleiste statt Pager. Ein Satz Screens, der Unterschied liegt nur im Rahmen.

- [x] `AboutDialog.kt`, `Dashboard.kt` und `CreateInvoice.kt` liegen in
      `composeApp/src/commonMain/.../ui/`. Sie hatten **keine** Android-Importe und mussten
      nur an drei Stellen angefasst werden:
      - `androidx.compose.ui.tooling.preview.Preview` → `org.jetbrains.compose...Preview`.
        Die multiplattformfähige Fassung kennt keine Parameter, `showBackground`/`widthDp`/
        `heightDp` sind deshalb entfallen.
      - `HonorarCraftAndroidTheme` → `HonorarCraftTheme`.
      - `BuildConfig.VERSION_NAME` gibt es auf dem Desktop nicht; die angezeigte Version steht
        jetzt als `Constants.APP_VERSION` im gemeinsamen Modul. **Dritte Stelle, die bei einem
        Release mitgepflegt werden muss** — neben `packageVersion` und `versionName`.
- [ ] `EntryWindow.kt` (513 Zeilen) — braucht nur den Ersatz von `Toast` und einen Haken für
      die PDF-Erzeugung.
- [ ] `DataWindow.kt` (729 Zeilen) — der harte Brocken: Datei-Auswahl über
      `ActivityResultContracts`, `Intent`, `BitmapFactory` für die Unterschriftsvorschau,
      `Uri` für Sicherungen. Hängt an Phase 6 (Datei-Dialoge, Backup) und kann erst danach
      vollständig umziehen.
- [ ] Der adaptive Rahmen (`HonorarCraftApp`) mit Seitenleiste ab ca. 900 dp und Pager
      darunter. Braucht alle vier Screens.
- [ ] Desktop-Navigation (`String`-State in `main.kt`) durch dieselbe Tab-/Pager-Logik wie
      auf Android ersetzen.
- [ ] `minimumSize` von 1440×900 senken, sonst ist das Fenster auf kleinen Notebooks unbedienbar.
- [ ] Dialoge, die es nur auf einer Seite gibt, angleichen: `AboutDialog` (Android) und
      der Werkseinstellungen-Dialog (Desktop).

## Phase 6 — Plattformspezifisches

- [ ] PDF-Erzeugung als `expect/actual`: gemeinsames Layout-Modell (Positionen, Beträge,
      Adressblock), zwei Renderer (PDFBox / `PdfDocument`). Ziel: beide Plattformen erzeugen
      dasselbe Dokument.
- [ ] Datei-Dialoge: `JFileChooser` (Desktop) vs. Storage Access Framework (Android).
- [x] Backup/Restore liegt in `shared/.../backup/Backup.kt` und gilt für beide Plattformen.
      Da Android und Desktop beide JVM sind, brauchte es **keine** `Uri`-Abstraktion: die
      gemeinsame Schicht nimmt fertige `InputStream`/`OutputStream` entgegen, die Auswahl der
      Datei bleibt plattformeigen.
      - Die Prüfung öffnet die Kandidatendatei jetzt über `BundledSQLiteDriver` statt über
        Androids `SQLiteDatabase` — dieselben Regeln auf beiden Seiten, damit eine Sicherung
        vom Handy auf dem Desktop gleich beurteilt wird. Schemaversion kommt über
        `PRAGMA user_version`.
      - Der WAL-Checkpoint läuft über `database.useWriterConnection { usePrepared(...) }`;
        `execSQL` reicht nicht, weil `PRAGMA wal_checkpoint` eine Zeile zurückliefert.
      - `AppDatabase.getDatabase(context)` und `context.cacheDir` sind zu Parametern geworden
        (`databaseFile`, `workDir`, `closeDatabase`).
- [x] `BackupTest` (4 Tests) prüft den Rundlauf und vor allem die beiden Abweisungen: eine
      Textdatei und eine echte, aber fremde SQLite-Datenbank werden zurückgewiesen, **bevor**
      irgendetwas angefasst wird — die vorhandenen Rechnungen bleiben in beiden Fällen stehen.
- [ ] Verschlüsselung klären (Frage 7).
- [ ] CI erst ganz am Schluss wieder scharf schalten, dann mit Android-Build + `ubuntu-latest`
      in der Matrix.

## Was bewusst nicht Teil des Umbaus ist

Play-Store-Release, Signierung, neue Features. `applicationId` bleibt `de.v404.honorarcraft` —
die darf sich nie ändern, sonst ist es für den Store eine neue App. Der `namespace` darf.

## Offene Fragen für die nächste Session

~~1., 2., 3. und 5.~~ — beantwortet, siehe "Getroffene Entscheidungen" oben.

~~4.~~ — beantwortet: **Importer bauen.** Die 12 Desktop-Rechnungen wandern beim ersten Start
in die Room-Datenbank, der Altbestand wird danach umbenannt statt gelöscht.
6. **Honorarsatz pro Position** und die **Rechnungsnummern-Formate** aus der Android-App auch
   auf dem Desktop — heißt: das Desktop-PDF ändert sich. Einverstanden?
7. **Verschlüsselung:** Desktop verschlüsselt IBAN/Steuernummer/Name mit AES im
   `CryptoHelper`, Android verlässt sich auf `allowBackup="false"`. Gemeinsam gelöst wird das
   entweder gar nicht mehr (Feldverschlüsselung fällt weg) oder für beide (z. B. SQLCipher).
   Was ist dir lieber?
~~8.~~ — beantwortet: iOS bleibt außen vor, siehe "Getroffene Entscheidungen" oben.
