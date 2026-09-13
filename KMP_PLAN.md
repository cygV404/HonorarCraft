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

Offen bleiben die Fragen 4, 6, 7 und 8 am Ende dieser Datei; sie werden erst in Phase 2 bzw. 4
gebraucht.

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
├── shared/          commonMain   Modelle, Room-Entities+DAOs, Rechenlogik,
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
- [ ] `InvoiceData`, `InvoiceEntry`, `CompanyData`, `HiddenSubject`, `Converters`,
      `AppDatabase`, `Migrations` nach `shared/commonMain` kopieren; `@Keep` und
      android-spezifische Importe entfernen.
- [ ] `app/schemas/` mitnehmen — die Migrationskette ab Version 3 ist der Grund, warum alte
      `.hcbackup`-Dateien weiterhin lesbar sind. Nicht neu anfangen.
- [ ] Datenbankdatei-Pfad als `expect/actual`: Android `getDatabasePath`, Desktop
      `getAppDataFolder()` (heute `Dashboard.kt:34`).
- [ ] **Importer** für die alten Desktop-JSONs (`company.json`, `invoices/*.json`,
      `totals/year_*.json`, `last_invoice_number.txt`) → Room, einmalig beim ersten Start,
      danach Altbestand umbenennen statt löschen.
- [ ] Abnahme: Ein `.hcbackup` vom Handy lässt sich auf dem Desktop öffnen und zeigt
      dieselben Zahlen.

## Phase 3 — Fachlogik und Tests teilen

- [ ] `CalculationTest.kt` als Erstes nach `shared/commonTest` — er ist die Abnahme für alles
      Weitere.
- [ ] Rechenlogik (`totalSum`, `totalLessonUnit`), `InvoiceFormat`/`formatInvoice`,
      `Constants` nach `commonMain`. Die Desktop-Varianten (`totalCostsHours` &
      `totalCostsLessonUnits` mit der vertauschten Benennung) ersatzlos streichen.
- [ ] `MainViewModel` nach `commonMain` (`androidx.lifecycle.ViewModel` ist multiplatform-fähig);
      `SharedPreferences` durch eine `expect/actual`-Einstellungsschicht ersetzen
      (Android: SharedPreferences, Desktop: `java.util.prefs` oder eine Settings-Tabelle in Room).
- [ ] Jahresumsatz nur noch aus den Positionen ableiten (`date.endsWith(jahr)`), die
      `totals/year_*.json` entfallen — behebt die Doppelzählung über den Jahreswechsel.
- [ ] Abnahme: alle Tests grün, Desktop zeigt für dieselben Daten dieselben Summen wie das Handy.

## Phase 4 — Assets und Theme übernehmen

- [ ] Aus der Android-App nach `composeApp/src/commonMain/composeResources/`:
      `montserrat_bold.ttf` (Desktop hat keinen Google-Fonts-Provider → die TTF muss
      gebündelt werden, Montserrat in weiteren Schnitten ggf. nachladen),
      `ui/theme/Color.kt` + `Theme.kt` + `Type.kt`, `res/drawable/`, `values-night`-Farben.
- [ ] App-Icon vereinheitlichen: `ic_launcher-playstore.png` und
      `~/Schreibtisch/HonorarCraft_Assets/honorarcraft_icon.png` als Quelle für die
      Desktop-Icons (`.ico`, `.icns`, `.png` in `composeResources/drawable/`).
- [ ] Dunkles Farbschema auch auf dem Desktop aktivieren (heute hart `Color.White` in `main.kt`).
- [ ] Die PDF-Schriften (`Roboto-*.ttf` unter `jvmMain/resources/font/`) bleiben, wo sie
      sind — die lädt PDFBox über den ClassLoader, das ist ein anderer Mechanismus als
      Compose Resources.

## Phase 5 — UI

Umfang hängt an Frage 5. Grobrichtung:

- [ ] Gemeinsame Screens in `composeApp/commonMain`, Layout-Unterschiede über eine
      Breiten-Abfrage (Handy: Pager mit 4 Tabs; Desktop: breite Ansicht) statt über zwei
      getrennte Implementierungen.
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
- [ ] Backup/Restore aus `Backup.kt` auf den Desktop ziehen (Dateiauswahl plattformabhängig,
      WAL-Checkpoint und Prüflogik gemeinsam).
- [ ] Verschlüsselung klären (Frage 7).
- [ ] CI erst ganz am Schluss wieder scharf schalten, dann mit Android-Build + `ubuntu-latest`
      in der Matrix.

## Was bewusst nicht Teil des Umbaus ist

Play-Store-Release, Signierung, neue Features. `applicationId` bleibt `de.v404.honorarcraft` —
die darf sich nie ändern, sonst ist es für den Store eine neue App. Der `namespace` darf.

## Offene Fragen für die nächste Session

~~1., 2., 3. und 5.~~ — beantwortet, siehe "Getroffene Entscheidungen" oben.

4. **Alte Desktop-Daten:** Importer bauen (deine 12 Rechnungen aus `~/.honorarcraft`) oder
   reicht es, einmalig ein `.hcbackup` vom Handy einzuspielen und den Desktop-Altbestand
   fallen zu lassen?
6. **Honorarsatz pro Position** und die **Rechnungsnummern-Formate** aus der Android-App auch
   auf dem Desktop — heißt: das Desktop-PDF ändert sich. Einverstanden?
7. **Verschlüsselung:** Desktop verschlüsselt IBAN/Steuernummer/Name mit AES im
   `CryptoHelper`, Android verlässt sich auf `allowBackup="false"`. Gemeinsam gelöst wird das
   entweder gar nicht mehr (Feldverschlüsselung fällt weg) oder für beide (z. B. SQLCipher).
   Was ist dir lieber?
8. **iOS-Target** schon jetzt in der Struktur vorsehen (kostet in Phase 1 wenig, in Phase 6
   viel) oder erst einmal ausklammern?
