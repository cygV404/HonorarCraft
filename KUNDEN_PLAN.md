# Mehrere Kunden — Arbeitsplan

Stand: 30.10.2026. Noch nichts umgesetzt.

## Ziel

Heute kennt HonorarCraft genau einen Rechnungsempfänger. Künftig sollen beliebig viele
Kunden mit eigenen Stammdaten angelegt werden können, und jede Rechnung gehört zu genau einem
Kunden. Wer nur einen Kunden hat, soll davon nichts merken: sein bisheriger Empfänger wird
beim Update zum ersten Kunden, und alle bestehenden Rechnungen hängen an ihm.

## Ausgangslage

- Rechnungssteller **und** Empfänger stehen gemeinsam in einer Zeile: `company_data`, fest
  `id = 1`. Empfängerfelder: `customerSecondNameOrOrga`, `customerFirstName`, `customerStreet`,
  `customerStreetNumber`, `customerPlz`, `customerMailBox`, `customerCityName` sowie
  `eduCenter`, `locationNr`, `schoolType` (stehen im PDF unter der Anschrift und gehören
  fachlich zum Auftraggeber).
- `invoices` hat nur die Spalte `invoiceNumber` — keinen Bezug zu einem Empfänger.
- Der Honorarsatz steht in `company_data.rate` als Vorgabe; jede Position trägt ihren
  eigenen `rate` (seit Migration 4→5). Neue Positionen übernehmen die Vorgabe.
- `InvoiceLayout.build(invoice, company, …)` liest Absender und Empfänger aus demselben
  `CompanyData`.
- Die Daten-Seite hat einen Abschnitt „Kundendaten" mit den Feldern oben.
- `LegacyDesktopImport` schreibt die Empfängerfelder der alten Desktop-Fassung in
  `CompanyData`.

## Entscheidungen (Vorschlag — vor dem Start bestätigen)

1. **Eigene Tabelle `customers`**, die Empfängerfelder wandern aus `company_data` dorthin.
   `company_data` behält nur den Rechnungssteller, Finanzdaten, Unterschrift, Ablageordner
   und den allgemeinen Honorarsatz.
2. **`invoices.customerId`** als Fremdschlüssel auf `customers.id`, `NOT NULL`,
   `ON DELETE RESTRICT`. Eine Rechnung ohne Kunden gibt es nicht; ein Kunde mit Rechnungen
   lässt sich nicht löschen, nur **archivieren** (verschwindet aus der Auswahl, bleibt für
   alte Rechnungen und Umsätze erhalten).
3. **Rechnungsnummern bleiben global und eindeutig** — ein Zähler für alle Kunden, wie
   bisher. `invoiceNumber` bleibt Primärschlüssel. Getrennte Zähler je Kunde wären
   steuerlich nur mit Kundenkürzel in der Nummer sauber und brächten Kollisionen beim
   Primärschlüssel; das lohnt den Aufwand nicht.
4. **Honorarsatz je Kunde**, optional: `customers.rate` ist nullable. Gesetzt → Vorgabe für
   neue Positionen dieses Kunden, sonst greift `company_data.rate`. Bestehende Positionen
   behalten ihren Satz.
5. **Lebendige Verknüpfung, kein Schnappschuss.** Das PDF einer alten Rechnung zeigt die
   *aktuelle* Anschrift ihres Kunden. So ist es heute auch; ein Schnappschuss der Anschrift
   je Rechnung wäre ein eigenes Vorhaben.
6. **Aktiver Kunde** liegt im ViewModel und wird in `Settings` (`selected_customer_id`)
   gemerkt. Eine neu angelegte Rechnung bekommt den aktiven Kunden. Wird eine bestehende
   Rechnung gewählt, springt der aktive Kunde auf deren Kunden.
7. **Fächervorschläge bleiben global.** Kundenbezogene Vorschläge erst, wenn sich das im
   Alltag als störend erweist.

## Datenbank — Schema 12

```sql
CREATE TABLE customers (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    nameOrOrga TEXT NOT NULL, firstName TEXT NOT NULL,
    street TEXT NOT NULL, streetNumber TEXT NOT NULL,
    plz TEXT NOT NULL, mailBox TEXT NOT NULL, cityName TEXT NOT NULL,
    eduCenter TEXT NOT NULL, locationNr TEXT NOT NULL, schoolType TEXT NOT NULL,
    rate TEXT,                      -- BigDecimal über Converters, NULL = allgemeiner Satz
    archived INTEGER NOT NULL DEFAULT 0
);
-- invoices bekommt customerId (Fremdschlüssel, Index)
-- company_data verliert die zehn Empfängerspalten
```

**Migration 11 → 12** (`MIGRATION_11_12`, KMP-Signatur mit `SQLiteConnection`):

1. `customers` anlegen.
2. Aus `company_data` (id 1) **einen** Kunden mit `id = 1` übernehmen. Gibt es keine Zeile
   in `company_data` (frische Installation), trotzdem einen leeren Kunden „Kunde 1" anlegen,
   sobald Rechnungen existieren — sonst scheitert der Fremdschlüssel.
3. `invoices` neu aufbauen (SQLite kann keine Fremdschlüssel nachträglich anlegen):
   `invoices_new` mit `customerId`, Daten mit `customerId = 1` kopieren, alte Tabelle löschen,
   umbenennen, Index anlegen. **`invoice_entries` hängt per Fremdschlüssel an `invoices`** —
   während des Umbaus `PRAGMA foreign_keys` beachten bzw. `PRAGMA defer_foreign_keys = ON`,
   damit `ON DELETE CASCADE` die Positionen nicht mitnimmt. Das ist die gefährlichste Stelle
   des ganzen Vorhabens und braucht einen eigenen Test mit echten Positionen.
4. `company_data` ohne die Empfängerspalten neu aufbauen und Daten kopieren.

Danach: Schema 12 exportieren, `DATABASE_VERSION = 12`, neuer `identityHash` in CLAUDE.md
eintragen (der Absatz dort zu Version 11 gilt dann nur noch für die Migration).

**Sicherungen:** `Backup.import` hebt `.hcbackup` aus Version 3–11 über die Kette an — ältere
Sicherungen bleiben lesbar. Eine Sicherung aus 2.1 lässt sich in 2.0 **nicht** einlesen; das
fängt die bestehende Prüfung ab („Sicherung stammt aus einer neueren App-Version"). Deshalb
Desktop und Android **gemeinsam** als 2.1 ausliefern.

## Phasen

### Phase 1 — Datenschicht (`:shared`)

- [ ] Entity `Customer`, `CustomerDao` (alle aktiven als `Flow`, alle inkl. archivierter,
      einfügen/ändern, archivieren, löschen nur ohne Rechnungen).
- [ ] `InvoiceData.customerId`, `CompanyData` ohne Empfängerfelder.
- [ ] `InvoiceWithEntries` um den Kunden ergänzen (`@Relation`), damit PDF und Übersicht
      ihn ohne zweite Abfrage haben.
- [ ] `MIGRATION_11_12` + Schema-Export.
- [ ] `MaintenanceDao.clearAllTables()` um `customers` ergänzen (Reihenfolge: Positionen,
      Rechnungen, **dann** Kunden).
- [ ] Tests (`jvmTest`): Migration 11→12 mit einer Rechnung samt Positionen — alle
      Positionen sind danach noch da, hängen an Kunde 1, Empfängerdaten stimmen;
      Migration auf leerer Datenbank; Kette 3→12; `BackupTest` mit einer Sicherung aus
      Version 11.

### Phase 2 — ViewModel

- [ ] `customers: StateFlow<List<Customer>>`, `selectedCustomer: StateFlow<Customer?>`,
      `selectCustomer(id)`, `saveCustomer(customer)`, `archiveCustomer(id)`.
- [ ] `addEntryFromForm()`: neue Rechnung mit `customerId` des aktiven Kunden; Satz aus
      `customer.rate ?: company.rate`.
- [ ] `setSelectedInvoiceNumber()`: gehört die Rechnung einem anderen Kunden, aktiven Kunden
      umschalten.
- [ ] Hinzufügen ohne aktiven Kunden verhindern (Meldung „Bitte zuerst einen Kunden anlegen").
- [ ] `yearlyRevenue` bleibt die Gesamtsumme; neu `yearlyRevenueByCustomer`.
- [ ] Tests in `MainViewModelTest`: Kundenwechsel, Satzvorgabe, Rechnung wechselt Kunden mit.

### Phase 3 — PDF

- [ ] `InvoiceLayout.build(invoice, company, customer, …)` — Empfängerblock aus `customer`.
- [ ] `PlatformServices.saveInvoicePdf` und beide Plattformseiten nachziehen.
- [ ] Dateiname des PDFs um einen Kundenkürzel ergänzen? (siehe offene Fragen)
- [ ] `InvoiceLayoutTest` anpassen; Layout darf sich für einen einzelnen Kunden nicht ändern.

### Phase 4 — Oberfläche (`:composeApp`, gemeinsam für beide Plattformen)

- [ ] **Daten-Seite:** Abschnitt „Kundendaten" wird zu „Kunden": Liste der Kunden mit
      „Kunde hinzufügen"; Antippen öffnet ein Bearbeiten-Formular mit den bisherigen Feldern
      plus optionalem Honorarsatz und „Archivieren". „Meine Daten", „Finanzdaten" usw.
      bleiben unverändert.
- [ ] **Erstellen / PDF:** Kundenauswahl (Dropdown) neben der Rechnungsnummer. Bei genau
      einem Kunden nur als Beschriftung, ohne Auswahl.
- [ ] **Übersicht:** Jahresumsatz wie bisher, darunter Umsatz je Kunde.
- [ ] Ungespeicherte Änderungen im Kundenformular über den bestehenden
      `hasUnsavedChanges`-Mechanismus abfangen.
- [ ] Schmal (Handy) und breit (Desktop) prüfen.

### Phase 5 — Altbestand und Abschluss

- [ ] `LegacyDesktopImport` legt den alten Empfänger als Kunden an statt in `CompanyData`;
      `LegacyDesktopImportTest` anpassen.
- [ ] README (Funktionsliste), CLAUDE.md (Datenschicht, identityHash) nachziehen.
- [ ] `appVersion` 2.1, `androidVersionCode` 7.
- [ ] Abnahme: echte 2.0-Datenbank vom Handy und vom Desktop aktualisieren, Rechnungen und
      PDFs vorher/nachher vergleichen; zweiten Kunden anlegen, Rechnung für ihn erzeugen;
      Sicherung zwischen beiden Geräten austauschen.

## Offene Fragen

1. **Honorarsatz je Kunde** (Entscheidung 4) — gewünscht, oder reicht der eine Satz?
2. **Rechnungsnummer:** ein globaler Zähler (Vorschlag) oder je Kunde eigene Nummern, dann
   zwingend mit Kundenkürzel in der Nummer?
3. **PDF-Dateiname:** heute ohne Kunde. Kundenname ergänzen (`Rechnung_2026-05_Muster.pdf`),
   damit der Ablageordner bei mehreren Kunden lesbar bleibt?
4. **Übersicht:** reicht Umsatz je Kunde als Liste, oder soll die ganze Übersicht nach
   Kunde filterbar sein?
5. Gehören „Bildungszentrum", „Standortnummer" und „Schulart/Maßnahme" wirklich zum Kunden
   (Vorschlag) — oder sind das Angaben, die je Rechnung wechseln?
