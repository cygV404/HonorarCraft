# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Aktueller Umbau

Das Projekt wird gerade zu einem KMP-Projekt ausgebaut, das sich Code mit der Android-App
unter `~/StudioProjects/HonorarCraftAndroid` teilt. **Arbeitsplan, getroffene Entscheidungen und
offene Fragen stehen in `KMP_PLAN.md` — vor Änderungen an Architektur, Datenhaltung oder
Rechenlogik dort nachsehen.** Gearbeitet wird auf dem Branch `kmp`; der GitHub-Actions-Build bei
Push ist währenddessen abgeschaltet (nur noch `workflow_dispatch`).

Phase 0 und 1 sind erledigt: Gradle 9.5.0, Kotlin 2.4.10, Compose Multiplatform 1.12.0, AGP 9.3.2,
drei Module (`shared`, `composeApp`, `androidApp`), Paket überall `de.v404.honorarcraft`.
Die Datenschicht (Phase 2) ist noch die alte Desktop-JSON-Variante.

## Projekt

HonorarCraft — eine deutschsprachige App (Compose Multiplatform) für die Honorarabrechnung von
Lehrkräften: Unterrichtsstunden erfassen, Jahresumsatz verfolgen, Rechnung als PDF exportieren.
Gradle-Rootprojekt und Produktname sind beide `HonorarCraft`. Ziel ist Desktop **und** Android aus
einer Quelle; heute läuft die vollständige Anwendung nur auf dem Desktop.

Alle UI-Texte sind deutsch und direkt im Code hinterlegt (kein Resource-Bundle) — neue Texte für Nutzer ebenfalls
auf Deutsch schreiben.

## Befehle

JDK 17 (CI, `qodana.yaml` und `.idea/misc.xml` legen alle 17 fest). Gradle Configuration Cache und Build Cache sind aktiv.

```bash
./gradlew :composeApp:run                                  # Desktop-App starten
./gradlew :composeApp:hotRunJvm --auto --mainClass de.v404.honorarcraft.MainKt   # Hot Reload
./gradlew :composeApp:packageDistributionForCurrentOS      # .deb / .msi / .dmg -> composeApp/build/compose/binaries/main/
./gradlew :composeApp:packageReleaseDistributionForCurrentOS
./gradlew :androidApp:assembleDebug                        # Android-APK -> androidApp/build/outputs/apk/debug/
./gradlew build                                            # alles kompilieren
./gradlew :composeApp:jvmTest                              # Tests (bisher existieren keine Testquellen)
./gradlew :composeApp:jvmTest --tests "de.v404.honorarcraft.SomeTest"
```

Für Android-Builds muss `local.properties` ein `sdk.dir` enthalten (die Datei ist gitignored).

Die CI (`.github/workflows/package.yml`) paketiert bei Push auf `main` nur auf `windows-latest` und `macos-latest`;
das Linux-`.deb` wird nicht von der CI gebaut. `qodana.yaml` konfiguriert den JetBrains-JVM-Linter — einen lokalen
Lint-Task gibt es nicht.

Bei einer neuen Version müssen `packageVersion` in `composeApp/build.gradle.kts` **und** die README-Überschrift
angepasst werden.

## Architektur

### Module

- `:shared` — plattformübergreifende Logik, Targets Android + JVM. Enthält bisher nur den
  bestandenen Room-Spike (`db/Spike*`, Wegwerf-Code); Modelle und Rechenlogik folgen in
  Phase 2/3. Der Room-Compiler ist dort pro Target eingehängt (`kspJvm`, `kspAndroid`), und
  die Lint-Tasks brauchen ein explizites `dependsOn` auf die KSP-Tasks, sonst bricht
  `./gradlew build` mit "implicit dependency" ab.
- `:composeApp` — Oberfläche. Targets Android + JVM. **Der gesamte Anwendungscode liegt weiterhin
  in `src/jvmMain/kotlin/de/v404/honorarcraft/`**, `commonMain` enthält nur Abhängigkeiten.
- `:androidApp` — `MainActivity`, Manifest, `applicationId de.v404.honorarcraft`. Eigenes Modul,
  weil AGP 9 `com.android.application` nicht mehr mit dem KMP-Plugin im selben Modul zulässt;
  KMP-Module nutzen stattdessen `com.android.kotlin.multiplatform.library` und den Block
  `kotlin { androidLibrary { … } }` statt `androidTarget()`.

### Navigation

`main.kt` öffnet ein einziges `Window` und wechselt die Screens über einen `String`-State innerhalb eines
`AnimatedContent`: `"second"` → `Dashboard`, `"data"` → `DataWindowContent`, `"third"` → `InvoiceGenerator`.
Es gibt keine Navigations-Bibliothek; ein neuer Screen bedeutet einen neuen Zweig in diesem `when` plus einen von
`main.kt` durchgereichten Callback. Die Screens sind einfache `@Composable`-Funktionen mit eigenem `remember`-State —
trotz der Lifecycle-Abhängigkeiten werden keine ViewModels verwendet.

### Persistenz (zwei Speicher, beide außerhalb des Projekts)

`getAppDataFolder()` (`Dashboard.kt`) ermittelt das betriebssystemabhängige Datenverzeichnis:
`%APPDATA%/HonorarCraft`, `~/Library/Application Support/HonorarCraft`, sonst `~/.honorarcraft`. Darin:

- `company.json` — alle Einstellungen, atomar über `.tmp` + Rename geschrieben (`CompanyData.kt`)
- `last_invoice_number.txt` — Rechnungszähler, wird nach jedem PDF automatisch erhöht
- `invoices/invoice_<nr>.json` — die `InvoiceEntry`-Liste einer Rechnung
- `totals/year_<jahr>.json` — flache Map Rechnungsnummer → Summe; der reservierte Key `"S"` enthält den manuell
  eingetragenen Jahres-Startwert und wird von `calculateYearlyTotal` mit aufsummiert

Der Rest liegt in `java.util.prefs.Preferences`, und zwar unter **zwei voneinander unabhängigen Nodes**:
`app/accounting/honorarcraft` (der AES-`vault_key`; der Knotenname ist trotz der Paketumbenennung
absichtlich unverändert geblieben — sonst wären bestehende Installationen ihre verschlüsselten
Felder los) und `app/honorarcraft/teaching_suggestions` (zuletzt genutzte
Fächer-Vorschläge mit 40-Tage-Verfall, serialisiert als `fach|timestamp`, verbunden mit `;;;`, plus der Schalter
`show_ue_label`).

`SuggestionsManager.resetToFactorySettings()` löscht den kompletten App-Ordner und leert den Vorschlags-Node.

### Verschlüsselung

`CryptoHelper` (`CompanyData.kt`) verwendet AES mit einem zufälligen 16-Byte-Schlüssel, der Base64-kodiert in den
Preferences liegt. Die Identitätsfelder des Rechnungsstellers sowie IBAN, BIC und Steuernummer werden verschlüsselt
gespeichert. `encrypt` und `decrypt` schlucken beide jede Exception und geben `""` zurück — ein verlorener oder
ausgetauschter Schlüssel leert die Felder also still, statt einen Fehler zu melden. **Die verschlüsselten Felder sind
in `loadCompanyData` und `saveCompanyData` von Hand aufgelistet** — ein neues Feld muss in beiden Listen ergänzt
werden, sonst wird es im Klartext geschrieben bzw. unbrauchbar zurückgelesen.

### Geldbeträge und die UE/Stunden-Namensfalle

Alle Beträge sind `BigDecimal` mit `setScale(2, HALF_UP)`, und die Summen addieren bereits gerundete Einzelwerte
(siehe `InvoiceData.kt`) — das muss so bleiben, die README bewirbt die Cent-Genauigkeit als Feature.

Die Namen in `InvoiceData` sind gegenüber der UI vertauscht: `calculateCorrectedHours` rechnet Zeitstunden in
45-Minuten-Unterrichtseinheiten um (`×60/45`), `calculateCorrectedLessonUnits` ist die Identitätsfunktion. Folglich
nutzt der Code bei **aktivem** `showUELabel`-Schalter (Nutzer gibt direkt Unterrichtseinheiten ein)
`totalCostsLessonUnits` / `totalHours` und bei **inaktivem** Schalter `totalCostsHours` / `totalLessonUnit`. Vor jeder
Änderung an diesen Werten den tatsächlichen Aufruf nachverfolgen.

Das Jahr, unter dem eine Summe abgelegt wird, stammt aus `date.takeLast(4)` des `dd.MM.yyyy`-Strings des Eintrags,
mit dem aktuellen Jahr als Fallback.

### PDF-Export

`createInvoicePdf.kt` baut die Rechnung mit PDFBox 2.0.30 über absolute Koordinaten auf einer A4-Seite auf
(lokale Hilfsfunktionen wie `drawTextWrapped` übernehmen den Zeilenumbruch). Die Roboto-TTFs werden über den
**ClassLoader** aus `src/jvmMain/resources/font/` geladen; fehlen sie, greift der Fallback auf Helvetica — und damit
brechen die Umlaute. Das Unterschriftsbild kommt aus dem konfigurierten `signaturePath`, die Ausgabe landet in
`companyData.pdfPath` (Standard `~/Dokumente/Honorarabrechnungen`, unter Windows `Documents/...`).

### Ressourcen — zwei unterschiedliche Mechanismen

`src/jvmMain/composeResources/drawable/` sind Compose Resources (App-Icon, Hintergrund, die in der
`build.gradle.kts` referenzierten Installer-Icons), erreichbar über das generierte `Res.drawable.*`
im Paket `de.v404.honorarcraft.resources` — dieses Paket ist in `composeApp/build.gradle.kts`
festgelegt, sonst leitet Compose es vom Gradle-Projektnamen ab. `src/jvmMain/resources/` ist
ein klassischer Classpath-Ressourcen-Root, den PDFBox für die Schriftarten nutzt. Dateien nicht zwischen beiden
verschieben.
