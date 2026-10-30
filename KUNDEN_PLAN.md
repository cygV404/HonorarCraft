# Mehrere Kunden — Arbeitsplan

Stand: 30.10.2026. Umgesetzt in Version 2.1 — offen ist nur die Abnahme auf echten Geräten.

## Ziel

Heute kennt HonorarCraft genau einen Rechnungsempfänger. Künftig sollen beliebig viele
Kunden mit eigenen Stammdaten angelegt werden können, und jede Rechnung gehört zu genau einem
Kunden. Wer nur einen Kunden hat, soll davon nichts merken: sein bisheriger Empfänger wird
beim Update zum ersten Kunden, und alle bestehenden Rechnungen hängen an ihm.

## Ausgangslage

- Rechnungssteller **und** Empfänger stehen gemeinsam in einer Zeile: `company_data`, fest
  `id = 1`. Empfängerfelder: `customerSecondNameOrOrga`, `customerFirstName`, `customerStreet`,
  `customerStreetNumber`, `customerPlz`, `customerMailBox`, `customerCityName`. Dazu
  `eduCenter`, `locationNr`, `schoolType` — stehen im PDF unter der Anschrift.
- `invoices` hat nur die Spalte `invoiceNumber` — keinen Bezug zu einem Empfänger.
- Der Honorarsatz steht in `company_data.rate` als Vorgabe; jede Position trägt ihren
  eigenen `rate` (seit Migration 4→5). Neue Positionen übernehmen die Vorgabe.
- `InvoiceLayout.build(invoice, company, …)` liest Absender und Empfänger aus demselben
  `CompanyData`.
- PDF-Dateiname auf beiden Plattformen `Rechnung_<nummer>.pdf`, je einmal gebildet in
  `PlatformServices.jvm.kt` und `PlatformServices.android.kt`.
- Die Übersicht rechnet `yearlyRevenue` über alle Rechnungen des Jahres.
- `LegacyDesktopImport` schreibt die Empfängerfelder der alten Desktop-Fassung in
  `CompanyData`.

## Entscheidungen (30.10.2026)

1. **Eigene Tabelle `customers`** für die Empfänger-Stammdaten (Name/Organisation, Vorname,
   Anschrift, Postfach). `company_data` behält nur den Rechnungssteller, Finanzdaten,
   Unterschrift, Ablageordner und den allgemeinen Honorarsatz.
2. **Bildungszentrum, Standortnummer und Schulart/Maßnahme wechseln je Rechnung** und
   wandern deshalb nicht zum Kunden, sondern als Spalten an `invoices`. Eine neue Rechnung
   übernimmt sie als Vorschlag von der letzten Rechnung desselben Kunden.
3. **`invoices.customerId`** als Fremdschlüssel auf `customers.id`, `NOT NULL`,
   `ON DELETE RESTRICT`. Eine Rechnung ohne Kunden gibt es nicht; ein Kunde mit Rechnungen
   lässt sich nicht löschen, nur **archivieren** (verschwindet aus der Auswahl, bleibt für
   alte Rechnungen und Umsätze erhalten).
4. **Ein gemeinsamer Rechnungsnummern-Zähler** für alle Kunden, wie bisher. `invoiceNumber`
   bleibt Primärschlüssel und damit über alle Kunden eindeutig.
5. **Honorarsatz je Kunde**, optional: `customers.rate` ist nullable. Gesetzt → Vorgabe für
   neue Positionen dieses Kunden, sonst greift `company_data.rate`. Bestehende Positionen
   behalten ihren Satz.
6. **Kundenname im PDF-Dateinamen:** `Rechnung_<nummer>_<kunde>.pdf`, gebildet von einer
   gemeinsamen Funktion in `:shared`, damit beide Plattformen denselben Namen erzeugen.
   Zeichen, die unter Windows im Dateinamen verboten sind (`\ / : * ? " < > |`), werden
   ersetzt; Umlaute bleiben.
7. **Die ganze Übersicht ist nach Kunde filterbar** („Alle Kunden" oder ein einzelner):
   Jahresumsatz und alles, was die Übersicht sonst zeigt, beziehen sich auf die Auswahl.
   Der Filter wird in `Settings` gemerkt.
8. **Lebendige Verknüpfung, kein Schnappschuss.** Das PDF einer alten Rechnung zeigt die
   *aktuelle* Anschrift ihres Kunden. So ist es heute auch; ein Schnappschuss der Anschrift
   je Rechnung wäre ein eigenes Vorhaben.
9. **Aktiver Kunde** zum Erfassen liegt im ViewModel und wird in `Settings`
   (`selected_customer_id`) gemerkt. Eine neu angelegte Rechnung bekommt den aktiven Kunden.
   Wird eine bestehende Rechnung gewählt, springt der aktive Kunde auf deren Kunden.
   Getrennt davon: der Filter der Übersicht (Entscheidung 7).
10. **Fächervorschläge bleiben global.**

## Datenbank — Schema 12

```sql
CREATE TABLE customers (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    nameOrOrga TEXT NOT NULL, firstName TEXT NOT NULL,
    street TEXT NOT NULL, streetNumber TEXT NOT NULL,
    plz TEXT NOT NULL, mailBox TEXT NOT NULL, cityName TEXT NOT NULL,
    rate TEXT,                      -- BigDecimal über Converters, NULL = allgemeiner Satz
    archived INTEGER NOT NULL DEFAULT 0
);
-- invoices: + customerId (Fremdschlüssel, Index), + eduCenter, locationNr, schoolType
-- company_data: − die sieben Empfängerspalten, − eduCenter, locationNr, schoolType
```

**Migration 11 → 12** (`MIGRATION_11_12`, KMP-Signatur mit `SQLiteConnection`):

1. `customers` anlegen.
2. Aus `company_data` (id 1) **einen** Kunden mit `id = 1` übernehmen. Gibt es keine Zeile
   in `company_data`, aber Rechnungen, trotzdem einen leeren Kunden „Kunde 1" anlegen —
   sonst scheitert der Fremdschlüssel.
3. `invoices` neu aufbauen (SQLite kann keine Fremdschlüssel nachträglich anlegen):
   `invoices_new` mit `customerId`, `eduCenter`, `locationNr`, `schoolType`; Daten kopieren mit
   `customerId = 1` und den drei Werten aus `company_data` für **alle** bestehenden Rechnungen;
   alte Tabelle löschen, umbenennen, Index anlegen.
   **`invoice_entries` hängt per Fremdschlüssel mit `ON DELETE CASCADE` an `invoices`** —
   während des Umbaus `PRAGMA foreign_keys` beachten bzw. `PRAGMA defer_foreign_keys = ON`,
   damit das Löschen der alten Tabelle die Positionen nicht mitnimmt. Das ist die
   gefährlichste Stelle des ganzen Vorhabens und braucht einen eigenen Test mit echten
   Positionen.
4. `company_data` ohne die zehn verschobenen Spalten neu aufbauen und Daten kopieren.

Danach: Schema 12 exportieren, `DATABASE_VERSION = 12`, neuen `identityHash` in CLAUDE.md
eintragen (der Absatz dort zu Version 11 gilt dann nur noch für die Migration).

**Sicherungen:** `Backup.import` hebt `.hcbackup` aus Version 3–11 über die Kette an — ältere
Sicherungen bleiben lesbar. Eine Sicherung aus 2.1 lässt sich in 2.0 **nicht** einlesen; das
fängt die bestehende Prüfung ab („Sicherung stammt aus einer neueren App-Version"). Deshalb
Desktop und Android **gemeinsam** als 2.1 ausliefern.

## Phasen

### Phase 1 — Datenschicht (`:shared`)

- [x] Entity `Customer`, `CustomerDao` (aktive als `Flow`, alle inkl. archivierter,
      einfügen/ändern, archivieren, löschen nur ohne Rechnungen).
- [x] `InvoiceData` um `customerId`, `eduCenter`, `locationNr`, `schoolType`; `CompanyData`
      ohne die verschobenen Felder.
- [x] `InvoiceWithEntries` um den Kunden ergänzen (`@Relation`), damit PDF und Übersicht
      ihn ohne zweite Abfrage haben.
- [x] Abfragen für die Übersicht mit optionalem Kundenfilter
      (`getInvoicesWithEntriesByYear(year, customerId: Long?)`).
- [x] Abfrage „letzte Rechnung eines Kunden" für die Vorschlagswerte aus Entscheidung 2.
- [x] `MIGRATION_11_12` + Schema-Export.
- [x] `MaintenanceDao.clearAllTables()` um `customers` ergänzen (Reihenfolge: Positionen,
      Rechnungen, **dann** Kunden).
- [x] Tests (`jvmTest`): Migration 11→12 mit Rechnungen samt Positionen — alle Positionen
      sind danach noch da, hängen an Kunde 1, Empfänger- und Rechnungsangaben stimmen;
      Migration auf leerer Datenbank; Kette 3→12; `BackupTest` mit einer Sicherung aus
      Version 11.

### Phase 2 — ViewModel

- [x] `customers: StateFlow<List<Customer>>`, `selectedCustomer`, `selectCustomer(id)`,
      `saveCustomer(customer)`, `archiveCustomer(id)`.
- [x] Rechnungsangaben (Bildungszentrum, Standortnummer, Schulart) der aktuellen Rechnung
      lesen und ändern; bei neuer Rechnung aus der letzten Rechnung des Kunden vorbelegen.
- [x] `addEntryFromForm()`: neue Rechnung mit `customerId` des aktiven Kunden; Satz aus
      `customer.rate ?: company.rate`.
- [x] `setSelectedInvoiceNumber()`: gehört die Rechnung einem anderen Kunden, aktiven Kunden
      umschalten.
- [x] Hinzufügen ohne aktiven Kunden verhindern (Meldung „Bitte zuerst einen Kunden anlegen").
- [x] `dashboardCustomerFilter: StateFlow<Long?>` (`null` = alle), in `Settings` gemerkt;
      `yearlyRevenue` und alle anderen Übersichtswerte hängen daran.
- [x] Tests in `MainViewModelTest`: Kundenwechsel, Satzvorgabe, Rechnung wechselt Kunden mit,
      Vorbelegung der Rechnungsangaben, Übersichtsfilter.

### Phase 3 — PDF

- [x] `InvoiceLayout.build(invoice, company, customer, …)` — Empfängerblock aus `customer`,
      Bildungszentrum/Standort/Schulart aus `invoice`.
- [x] Gemeinsame Funktion `pdfDateiname(formattedInvoiceNumber, customer)` in `:shared`;
      `PlatformServices.jvm.kt` und `.android.kt` nutzen sie. Test für verbotene Zeichen.
- [x] `InvoiceLayoutTest` anpassen; das Layout darf sich für bestehende Rechnungen nicht
      ändern.

### Phase 4 — Oberfläche (`:composeApp`, gemeinsam für beide Plattformen)

- [x] **Daten-Seite:** Abschnitt „Kundendaten" wird zu „Kunden": Liste der Kunden mit
      „Kunde hinzufügen"; Antippen öffnet ein Bearbeiten-Formular mit den Anschriftfeldern
      plus optionalem Honorarsatz und „Archivieren". „Meine Daten", „Finanzdaten" usw.
      bleiben unverändert.
- [x] **Erstellen / PDF:** Kundenauswahl (Dropdown) neben der Rechnungsnummer — bei genau
      einem Kunden nur als Beschriftung. Darunter die drei Rechnungsangaben
      (Bildungszentrum, Standortnummer, Schulart/Maßnahme), vorbelegt und änderbar.
- [x] **Übersicht:** Kundenfilter oben („Alle Kunden" + aktive Kunden + archivierte mit
      Umsatz im gewählten Jahr); alle Werte der Übersicht folgen dem Filter.
- [x] Ungespeicherte Änderungen im Kundenformular über den bestehenden
      `hasUnsavedChanges`-Mechanismus abfangen.
- [x] Schmal (Handy) und breit (Desktop) prüfen.

### Phase 5 — Altbestand und Abschluss

- [x] `LegacyDesktopImport` legt den alten Empfänger als Kunden an und schreibt
      Bildungszentrum/Standort/Schulart an jede übernommene Rechnung;
      `LegacyDesktopImportTest` anpassen.
- [x] README (Funktionsliste), CLAUDE.md (Datenschicht, identityHash) nachziehen.
- [x] `appVersion` 2.1, `androidVersionCode` 7.
- [ ] Abnahme: echte 2.0-Datenbank vom Handy und vom Desktop aktualisieren, Rechnungen und
      PDFs vorher/nachher vergleichen; zweiten Kunden anlegen, Rechnung für ihn erzeugen,
      Übersicht filtern; Sicherung zwischen beiden Geräten austauschen.

## Umsetzung — Abweichungen vom Plan

- `InvoiceLayout.build` bekam **keinen** zusätzlichen Kunden-Parameter: `InvoiceWithEntries`
  bringt den Kunden über `@Relation` selbst mit.
- Das Kundenformular ist ein modaler Dialog. Abbrechen verwirft, deshalb braucht es den
  `hasUnsavedChanges`-Mechanismus nicht.
- Im PDF-Bereich steht der Kunde unter der Rechnungsnummer; gewählt wird er unter „Erstellen".
  Eine bestehende Rechnung lässt sich dort auf einen anderen Kunden umhängen.
- Gefunden beim Umbau: `insertInvoice` lief mit `REPLACE`, was bei einer bestehenden Rechnung
  über das `CASCADE` alle Positionen gelöscht hätte. Jetzt `IGNORE` plus `updateInvoice`.
- Vorab geprüft: eine Kopie der echten Desktop-Datenbank (10 Rechnungen, 81 Positionen)
  migriert ohne Verlust — Positionen unverändert, Fremdschlüssel- und Integritätsprüfung `ok`.
- Neu: Oberflächentests in `composeApp/src/jvmTest`, auch in der CI.

## Bewusst nicht Teil des Vorhabens

- Schnappschuss der Kundenanschrift je Rechnung (Entscheidung 8).
- Kundenbezogene Fächervorschläge (Entscheidung 10).
- Getrennte Rechnungsnummernkreise je Kunde (Entscheidung 4).
