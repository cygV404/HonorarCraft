# Sicherung über WLAN übertragen — Arbeitsplan

Stand: 14.09.2026. Umfang bewusst klein gehalten: **das ist kein Sync.**

## Was es ist und was nicht

Heute geht eine Sicherung nur über eine Datei — Kabel, Cloud oder USB-Stick. Dieser Plan
ersetzt den Transportweg durch das lokale Netz. **Die Semantik bleibt unverändert:** ein Gerät
schickt seinen kompletten Stand, das andere ersetzt seinen damit, nach Rückfrage.

Was es **nicht** ist: kein Zusammenführen, kein paralleles Arbeiten auf beiden Geräten. Wer
auf dem Handy und auf dem Rechner gleichzeitig erfasst, verliert eine Seite. Für echtes
Synchronisieren fehlen dem Datenmodell UUIDs, Zeitstempel, Grabsteine und ein gemeinsamer
Rechnungsnummern-Zähler — siehe „Warum nicht mehr" am Ende.

## Warum das billig ist

Der gefährliche Teil existiert schon und ist durch fünf Tests abgedeckt:

- `Backup.export(database, databaseFile, out: OutputStream)` schreibt in **einen beliebigen
  Strom** — also auch in einen Socket.
- `Backup.import(databaseFile, workDir, source: InputStream, closeDatabase)` liest aus
  **einem beliebigen Strom** — also auch aus einem Socket.
- Prüfung der Datei, Sicherung des alten Standes, Rückrollen bei Fehlschlag und die Migration
  älterer Stände laufen unverändert weiter.

Neu ist ausschließlich der Transport. Es braucht **keine** Änderung an der Datenbank, am
Schema oder an der Migrationskette.

## Aufbau

**Der Empfänger öffnet, der Sender schickt.** Wer Daten abgeben will, drückt auf „Senden";
wer sie annehmen will, drückt auf „Empfangen". Das gilt in beide Richtungen, Handy wie Desktop
können beides.

```
Empfänger                             Sender
  ServerSocket auf zufälligem Port      verbindet sich
  zeigt "192.168.1.42 : 7431"           Adresse eingeben
  zeigt Kopplungscode "4718"            Code eingeben
  ─────────────────── prüft Code ───────────────────
  Backup.import(socket.inputStream)  ←  Backup.export(socket.outputStream)
  Rückfrage, dann Neustart
```

### Warum kein mDNS zur Gerätesuche

Wäre bequemer, kostet aber eine Abhängigkeit auf der Desktop-Seite (JmDNS) und auf Android ab
Tiramisu die Berechtigung `NEARBY_WIFI_DEVICES`. Adresse und Code ablesen und eintippen ist
zumutbar, weil der Vorgang selten ist. Kann später nachgerüstet werden, ohne das Protokoll zu
ändern.

### Sicherheit

Ein offener Port im Netz, der die Buchhaltung ersetzen kann, ist kein Detail.

- Der Server läuft **nur, solange der Dialog offen ist**, und schließt danach sofort.
- **Vierstelliger Kopplungscode**, bei jedem Öffnen neu gewürfelt. Der Sender schickt ihn als
  erstes; stimmt er nicht, wird die Verbindung ohne Antwort geschlossen.
- **Nur eine Verbindung**, danach schließt der Server. Kein Wiederholungsversuch, kein
  Durchprobieren des Codes.
- Gebunden wird auf die lokale Adresse, nicht auf `0.0.0.0` über alle Schnittstellen.
- Die Übertragung ist **unverschlüsselt**. Im eigenen WLAN vertretbar; in einem fremden Netz
  sollte man es lassen, und genau das muss im Dialog stehen.

### Protokoll

Bewusst primitiv, kein HTTP, kein Framework:

```
→  "HCBACKUP1 <code>\n"      Sender meldet sich an
←  "OK\n" oder Verbindung zu  Empfänger bestätigt
→  <die Datenbankdatei>       roh, bis der Strom endet
```

## Schritte

- [ ] **`shared/jvmCommonMain`:** `BackupTransfer` mit `empfange(port, code, ...)` und
      `sende(host, port, code, ...)`. Beides nutzt `java.net.ServerSocket`/`Socket` — die gibt
      es auf **beiden** Plattformen, der Code ist also gemeinsam. Er reicht die Ströme nur an
      das bestehende `Backup` weiter.
- [ ] **Test in `jvmTest`:** Sender und Empfänger im selben Prozess über `localhost`, mit
      einer echten Datenbank. Deckt ab: erfolgreicher Rundlauf, falscher Code wird abgewiesen,
      abgebrochene Verbindung lässt den Bestand unangetastet.
- [ ] **Android-Manifest:** `INTERNET`-Berechtigung. **Das ist der sichtbare Preis** — siehe
      unten.
- [ ] **Lokale Adresse ermitteln:** `expect fun localeNetzAdresse(): String?`. Desktop über
      `NetworkInterface`, Android ebenso; die Schnittstelle ohne `INTERNET` nicht abfragbar.
- [ ] **Oberfläche:** auf der Daten-Seite unter „Sicherung" zwei Knöpfe, „Über WLAN senden"
      und „Über WLAN empfangen", jeweils mit einem Dialog für Adresse und Code. Der
      Empfänger-Dialog zeigt zusätzlich den Warnhinweis zum fremden Netz.
- [ ] **Nach dem Empfang** greift der bestehende Weg: Rückfrage, Import, Neustart der App.
- [ ] Abnahme: Handy und Desktop im selben WLAN, Bestand in beide Richtungen übertragen und
      die Zahlen vergleichen.

## Aufwand

Etwa ein Tag. Der Transport ist überschaubar; die Zeit geht in den Dialog, die
Fehlerbehandlung (falsches Netz, Zeitüberschreitung, abgebrochene Verbindung) und den Test.

## Was es kostet

**Die App verliert ihr stärkstes Datenschutz-Argument.** Sie hat heute *keine einzige*
Berechtigung im Manifest; in der README steht „Es gibt keine Netzwerkberechtigung und keinen
Server". Mit `INTERNET` ist das vorbei, auch wenn die App weiterhin nichts ins Internet
schickt. Im Play Store ist die Änderung sichtbar, und die README muss ehrlich nachgezogen
werden.

Das ist kein Gegenargument, aber eine bewusste Entscheidung — und sie sollte vor dem ersten
Zeile Code fallen, nicht danach.

## Warum nicht mehr

Echtes Synchronisieren bräuchte am Datenmodell:

- **UUIDs statt `@PrimaryKey(autoGenerate = true)`** — sonst vergeben beide Geräte
  unabhängig dieselbe `id` für verschiedene Positionen.
- **`updatedAt` je Zeile** — ohne Zeitstempel lässt sich nicht entscheiden, welche Fassung
  gewinnt.
- **Grabsteine für Gelöschtes** — sonst kommen gelöschte Rechnungen beim nächsten Abgleich
  zurück.
- **Den Rechnungsnummern-Zähler in die Datenbank** — er liegt heute in den Einstellungen,
  beide Geräte würden „22" vergeben und zwei Rechnungen bekämen dieselbe Nummer.

Das ist Schemaversion 12 mit Migrationen auf beiden Plattformen, eine neue Play-Store-Fassung
und ein Fehlerbild, das im Zweifel Rechnungen kostet. Lohnt sich erst, wenn wirklich auf
beiden Geräten parallel erfasst wird.
