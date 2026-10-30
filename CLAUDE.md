# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projekt

HonorarCraft — eine deutschsprachige App für die Honorarabrechnung von Lehrkräften:
Unterrichtsstunden erfassen, Jahresumsatz verfolgen, Rechnung als PDF exportieren, Bestand
sichern. **Kotlin Multiplatform mit Compose Multiplatform, Desktop (JVM) und Android aus einer
Quelle** — Datenschicht, ViewModel, Oberfläche und PDF-Layout sind gemeinsam. Die Android-App
ist im Play Store (`applicationId de.v404.honorarcraft` — darf sich nie ändern).

Alle UI-Texte sind deutsch und direkt im Code hinterlegt (kein Resource-Bundle) — neue Texte
für Nutzer ebenfalls auf Deutsch schreiben. Viele Bezeichner im neueren Code sind ebenfalls
deutsch (`Bereich`, `sofortSchreiben`, …).

Planungsdokumente im Wurzelverzeichnis:
- `KMP_PLAN.md` — der abgeschlossene Umbau zu KMP (Version 2.0). Begründungen für Architektur-,
  Datenhaltungs- und Rechenentscheidungen stehen dort; vor Änderungen in diesen Bereichen lesen.
- `KUNDEN_PLAN.md` — mehrere Kunden (umgesetzt in 2.1); Entscheidungen und Begründungen.
- `WLAN_PLAN.md` — nächstes Vorhaben: Sicherung über das lokale Netz übertragen (kein Sync).

Stand: Gradle 9.5.0, Kotlin 2.4.10, Compose Multiplatform 1.12.0, AGP 9.1.1, Version 2.1,
Paket überall `de.v404.honorarcraft`, `minSdk` 29, `targetSdk` 36, `compileSdk` 37.

## Befehle

JDK 17 (CI, `qodana.yaml` und `.idea` legen alle 17 fest). Configuration Cache und Build Cache
sind aktiv.

```bash
./gradlew :composeApp:run                                  # Desktop-App starten
./gradlew :composeApp:hotRunJvm --auto --mainClass de.v404.honorarcraft.MainKt   # Hot Reload
./gradlew :composeApp:packageDistributionForCurrentOS      # .deb / .msi / .dmg -> composeApp/build/compose/binaries/main/
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
./gradlew :androidApp:assembleDebug                        # Android-APK -> androidApp/build/outputs/apk/debug/
./gradlew build                                            # alles kompilieren

./gradlew :shared:jvmTest                                  # jvmCommonTest + jvmTest gegen den Desktop
./gradlew :shared:testAndroidHostTest                      # jvmCommonTest gegen das Android-Target, ohne Emulator
./gradlew :shared:jvmTest --tests "de.v404.honorarcraft.shared.data.CalculationTest"
./gradlew :composeApp:jvmTest                              # Oberflächentests, laufen ohne Bildschirm
HC_SCREENSHOTS=/pfad ./gradlew :composeApp:jvmTest         # ... und legen dabei Bildschirmfotos ab
```

Die Oberflächentests (`composeApp/src/jvmTest`) nutzen `runDesktopComposeUiTest` mit echtem
`MainViewModel` und echter Datenbank im Temp-Ordner. Felder per `hasSetTextAction() and
hasText(label)` suchen; hinter einem Dialog liegen oft gleich beschriftete Felder, dann
zusätzlich `hasAnyAncestor(isDialog())`.

Für Android-Builds muss `local.properties` ein `sdk.dir` enthalten (gitignored; lokal
`~/Android/Sdk`). Die Plattform heißt im `sdkmanager` `platforms;android-37.0`.

**AGP bleibt auf 9.1.1**, obwohl Neueres verfügbar ist: Das Android-Plugin von IntelliJ IDEA
unterstützt höchstens 9.1.x und verweigert sonst den Gradle-Sync. Anheben erst, wenn die IDE
nachzieht — oder wenn das Projekt nach Android Studio umzieht.

Die CI (`.github/workflows/package.yml`) läuft bei Push auf `main`, bei Pull Requests und von
Hand: erst die drei Test-Tasks oben, dann parallel das Android-Debug-APK und die
Desktop-Pakete für Ubuntu, Windows und macOS. Ein Android-**Release** baut die CI bewusst nicht —
der Signierschlüssel liegt nicht im Repo. `qodana.yaml` konfiguriert den JetBrains-JVM-Linter;
einen lokalen Lint-Task gibt es nicht.

**Die Programmversion steht an genau einer Stelle:** `appVersion` in `gradle/libs.versions.toml`.
Daraus speisen sich `packageVersion` (Desktop, um die dritte Stelle ergänzt), `versionName`
(Android) und `Constants.APP_VERSION` im Info-Dialog (von Gradle als `GENERATED_APP_VERSION`
erzeugt). Bei einem Release zusätzlich anpassen: `androidVersionCode` im selben Katalog und die
README-Überschrift.

Die Desktop-Icons werden aus einer PNG-Quelle erzeugt, nicht von Hand gepflegt:
`java tools/MakeIcons.java <quelle.png> composeApp/src/jvmMain/composeResources/drawable`
(siehe `tools/README.md`).

## Architektur

### Module

- `:shared` — Datenschicht, ViewModel, Rechenlogik, Sicherung, PDF-Layout. Targets Android + JVM.
- `:composeApp` — die gemeinsame Oberfläche in `commonMain`; `jvmMain` enthält `main.kt` und
  die Desktop-Seite der Plattform-Schnittstellen, `androidMain` die Android-Seite.
- `:androidApp` — `MainActivity`, Manifest, `applicationId`. Eigenes Modul, weil AGP 9
  `com.android.application` nicht mehr mit dem KMP-Plugin im selben Modul zulässt; die
  KMP-Module nutzen `com.android.kotlin.multiplatform.library` und `kotlin { android { … } }`
  statt `androidTarget()`.

### Quellsätze im `:shared`-Modul

Gemeinsamer Code liegt **nicht** in `commonMain`, sondern in `jvmCommonMain` (bzw.
`jvmCommonTest`) — einem Zwischen-Quellsatz zwischen `commonMain` und den beiden Targets.
Grund: Entities und Rechenlogik nutzen `java.math.BigDecimal`, `java.io` usw. Ein iOS-Target
ist bewusst und dauerhaft ausgeschlossen, JVM-APIs im gemeinsamen Code sind also in Ordnung.
Neuer gemeinsamer Code gehört nach `jvmCommonMain`; Tests, die eine echte Datenbank brauchen,
nach `jvmTest`.

Der Room-Compiler ist pro Target eingehängt (`kspJvm`, `kspAndroid`), und die Lint-Tasks
brauchen ein explizites `dependsOn` auf die KSP-Tasks, sonst bricht `./gradlew build` mit
"implicit dependency" ab.

### Datenschicht (Room, `shared/.../shared/data/`)

Room 2.8 auf beiden Plattformen, Schemaversion 12, Migrationskette ab 3 in `Migrations.kt`,
exportierte Schemas unter `shared/schemas/`. Kein `fallbackToDestructiveMigration` — ein
fehlender Migrationspfad soll auffallen, statt Rechnungen zu löschen.

- Migrationen nutzen die KMP-Signatur `migrate(connection: SQLiteConnection)`, **nicht**
  `SupportSQLiteDatabase`.
- `@ConstructedBy` plus `expect object AppDatabaseConstructor` ohne eigenes `actual` — Room
  erzeugt die Implementierung je Target.
- Android: `AndroidDatabase.getDatabase(context)` ohne `setDriver` (System-SQLite, wie
  ausgeliefert). Desktop: `DesktopDatabase.get()` → `createDesktopDatabase(pfad)` mit
  `BundledSQLiteDriver`, Datei im Ordner von `desktopAppDataFolder()` (`%APPDATA%/HonorarCraft`,
  `~/Library/Application Support/HonorarCraft`, sonst `~/.honorarcraft`).
- **Jede Änderung an den Entities verändert den `identityHash` und damit das Schema.** Der
  Wert für Version 12 ist `6b190486a38e12a32e765d6bff269eb3` (Version 11, ausgeliefert mit 2.0:
  `ff3c7c472631b050dd51d3586f067f3a`). Ohne neue Migration und neue Version nichts an den
  Entities ändern — sonst sind bestehende Datenbanken und `.hcbackup`-Dateien nicht mehr lesbar.
- **Tabellen:** `company_data` (eine Zeile, der Rechnungssteller), `customers` (Empfänger,
  optional eigener `rate`, `archived`), `invoices` (`customerId` → `customers` mit `RESTRICT`,
  dazu `eduCenter`/`locationNr`/`schoolType`, weil die je Rechnung wechseln),
  `invoice_entries` (`invoiceNumber` → `invoices` mit `CASCADE`), `hidden_subjects`.
- **Nie `OnConflictStrategy.REPLACE` auf `invoices` oder `customers`:** SQLite ersetzt durch
  Löschen und Neuanlegen, und das `CASCADE` nähme alle Positionen der Rechnung mit. Deshalb
  `insertInvoice` mit `IGNORE`, Änderungen über gezielte `UPDATE`-Abfragen
  (`setInvoiceCustomer`, `setInvoiceDetails`), Kunden über `@Upsert`.
- **Eine Elterntabelle umbauen** (SQLite kann Spalten und Fremdschlüssel nicht nachträglich
  ändern): Positionen vorher in eine Hilfstabelle ohne Fremdschlüssel sichern, `DROP`/`RENAME`,
  zurückspielen — siehe `MIGRATION_5_6` und `MIGRATION_11_12`, jeweils mit Test.

**Sicherung** (`shared/.../backup/Backup.kt`, Endung `.hcbackup`): `export` und `import` arbeiten
auf beliebigen Streams; der Import prüft die Datei, sichert den alten Stand, rollt bei Fehlern
zurück und hebt ältere Stände über die Migrationskette. Nach einem Import muss die App neu
starten (`PlatformServices.restartApp()`), weil die Room-Flows an der geschlossenen Verbindung
hängen. Sicherungen sind zwischen Android und Desktop austauschbar — aber nur, wenn beide auf
derselben Schemaversion sind: eine 2.1-Sicherung lehnt eine 2.0-App ab. Desktop und Android
deshalb immer gemeinsam veröffentlichen.

### Geldbeträge und die UE/Stunden-Namensfalle

Das Feld `InvoiceEntry.lessonUnits` heißt zwar so, enthält aber **Zeitstunden**. Erst
`InvoiceWithEntries` rechnet in 45-Minuten-Unterrichtseinheiten um (`× 60 / 45`, intern mit 10
Nachkommastellen). Der Spaltenname ist Teil des Schemas — nicht umbenennen.

Alle Beträge sind `BigDecimal`. **Gerundet wird einmal am Ende** (`setScale(2, HALF_UP)` auf
der Gesamtsumme), nicht je Position — `CalculationTest` hält das fest (3 × 1 h zu 23 € =
92,00 €, nicht 92,01 €). Der Honorarsatz steht je Position (`rate`); eine neue Position
bekommt `customer.rate ?: company.rate`.
Die UE-Spalte im PDF zeigt bewusst gerundete Zwischenwerte; die Summe ist trotzdem exakt.

Rechnungsnummern-Formate (laufend, mit Jahr, mit Jahr und Monat) in `InvoiceNumber.kt`.

### Gemeinsames ViewModel

`shared/.../MainViewModel.kt` hält den Zustand für beide Plattformen. Datenbank und
`Settings` werden hereingereicht — deshalb läuft es im Test ohne Emulator.

- `Settings` ist eine gewöhnliche Schnittstelle, kein `expect/actual`: `AndroidSettings`
  (SharedPreferences, Dateiname "settings" — unverändert, sonst verlieren bestehende Geräte
  ihre Rechnungsnummer), `PreferencesSettings` (`java.util.prefs`, Knoten
  `de/v404/honorarcraft/settings`, schreibt nach jedem `put` sofort per `flush()`) und
  `InMemorySettings`.
- Nutzermeldungen laufen über den `SharedFlow<String>` `messages` und werden in
  `HonorarCraftApp` als Snackbar gezeigt. Der Flow hat **kein** Replay — ein Sammler muss
  stehen, bevor gemeldet wird.
- Fehler gehen über `expect fun logError` (Android: Logcat, Desktop: stderr).
- **Kunden:** `customers`, `selectedCustomer` (= Kunde der angezeigten Rechnung, sonst der
  gewählte aktive), `invoiceDetails` (Angaben der Rechnung bzw. Entwurf vor dem ersten Eintrag,
  vorbelegt von der letzten Rechnung des Kunden) und `dashboardCustomerFilter`. Gewählter
  Kunde und Filter liegen in `Settings`; ein Filter auf eine id, die es nicht (mehr) gibt,
  gilt als „alle“. Ohne Kunden verweigert `addEntryFromForm()`. Änderungen an einer Rechnung
  laufen über `schreibeRechnung` (ein `Mutex`), damit schnelle Tastendrücke sich nicht
  überholen.
- PDF-Erzeugung und Sicherung sind bewusst **nicht** im ViewModel; sie laufen über
  `PlatformServices` und rufen danach `incrementInvoiceNumber()` bzw. `showMessage(...)`.
- Um Room-Aufrufe gehört **kein** `withContext(Dispatchers.IO)` — Room legt suspend-Abfragen
  selbst auf seinen Abfrage-Kontext. Tests reichen diesen über
  `createDesktopDatabase(path, queryContext)` herein, sonst laufen die Abfragen an der Testuhr
  vorbei.

### Oberfläche (`composeApp/src/commonMain/.../ui/`)

`HonorarCraftApp(viewModel)` ist der gemeinsame Rahmen; `main.kt` (Desktop) und `MainActivity`
rufen nur ihn auf. Vier Bereiche — Übersicht (`Dashboard`), Erstellen (`EntryWindow`), PDF
(`CreateInvoice`), Daten (`DataWindow`) —, der aktive Tab liegt im ViewModel
(`selectedTabIndex`, mit Rückfrage bei ungespeicherten Änderungen über `pendingTabIndex`).
Keine Navigations-Bibliothek. Die Navigation hängt allein an der Fensterbreite: ab 900 dp eine
Seitenleiste, darunter ein `HorizontalPager` mit Leiste unten — auch auf dem Desktop, wenn das
Fenster schmal ist.

Die Kunden-Bausteine (Auswahl, Filter, Rechnungsangaben, Liste, Bearbeiten-Dialog) stehen in
`ui/Kunden.kt`.

Plattformunterschiede laufen über `ui/platform/` mit `expect`/`actual`:
- `PlatformServices` (Schnittstelle, per `LocalPlatformServices` bereitgestellt) — Datei-Dialoge,
  PDF ablegen, Sicherung, Neustart. Eine Schnittstelle statt `expect object`, weil Android seine
  Dialoge über `rememberLauncherForActivityResult` in der Komposition anmelden muss.
- `rememberMainViewModel()` — baut das ViewModel; auf dem Desktop startet es außerdem den
  einmaligen Import der Altdaten (siehe unten).
- `pdfPath` enthält auf dem Desktop einen Dateipfad, auf Android eine `content://`-Adresse.

### PDF-Export (`shared/.../pdf/`)

`InvoiceLayout.build(...)` erzeugt plattformunabhängig ein `PdfDocumentModel` (absolute
Koordinaten auf A4); die Renderer zeichnen nur noch: Desktop mit PDFBox, Android mit
`android.graphics.pdf.PdfDocument` (`expect fun writeInvoicePdf` / `pdfTextMeasurer`). Den
Empfänger liest das Layout aus `InvoiceWithEntries.customer`, den Dateinamen bildet
`pdfDateiname()` (`Rechnung_<nummer>_<kunde>.pdf`) für beide Plattformen. Das Layout
braucht die Plattform nur zum Messen von Textbreiten. Beide Renderer laden Roboto aus
`shared/src/{jvmMain,androidMain}/resources/font/` per ClassLoader; fehlt die Datei, fällt der
Desktop auf Helvetica zurück — dann brechen die Umlaute. `InvoiceLayoutTest` prüft das Layout.

### Theme (`composeApp/src/commonMain/.../ui/theme/`)

`HonorarCraftTheme` sitzt **innerhalb** von `HonorarCraftApp`, weil die Darstellung
(`ThemeMode`: SYSTEM/LIGHT/DARK) aus dem ViewModel kommt. Dynamische Systemfarben über
`expect fun dynamicColorSchemeOrNull` (Android ab 12, Desktop `null` → statisches Schema).
Die Schrift Montserrat wird als TTF in `commonMain/composeResources/font/` mitgeliefert, weil
der Google-Fonts-Provider nur auf Android existiert.

### Ressourcen — zwei Mechanismen

Compose Resources in `composeApp/src/commonMain/composeResources/` (Schrift) und
`composeApp/src/jvmMain/composeResources/` (Fenster-Icon, Installer-Icons `.ico`/`.icns`/`.png`
aus `build.gradle.kts` — taugen nicht als Android-Drawables). Erreichbar über `Res.*` im Paket
`de.v404.honorarcraft.resources` (in `composeApp/build.gradle.kts` festgelegt). Die
PDF-Schriften sind dagegen klassische Classpath-Ressourcen im `:shared`-Modul. Nicht zwischen
den Mechanismen verschieben.

### Altbestand der Desktop-Fassung bis 1.2.1 (`shared/src/jvmMain/.../legacy/`)

Die alte Desktop-App speicherte JSON-Dateien (`company.json`, `invoices/`, `totals/`,
`last_invoice_number.txt`) im selben Datenordner und verschlüsselte Identitätsfelder, IBAN, BIC
und Steuernummer per AES — Schlüssel als `vault_key` in `java.util.prefs` unter
`app/accounting/honorarcraft`. `LegacyDesktopImport` übernimmt das beim ersten Start einmalig
in Room (`LegacyVaultCrypto` entschlüsselt dafür, der alte Empfänger wird ein Kunde) und benennt den Altbestand in
`vor-room-<datum>/` um, statt ihn zu löschen. Manuelle Jahres-Startwerte (Schlüssel `"S"` in
`totals/`) haben kein Gegenstück in Room und werden nur gemeldet. Seit 2.0 gibt es **keine**
Feldverschlüsselung mehr; den Preferences-Knoten `app/accounting/honorarcraft` nicht leeren,
solange noch unimportierte Altinstallationen denkbar sind.
