package de.v404.honorarcraft.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Ein vom Nutzer ausgeblendeter Fachvorschlag.
 *
 * Die Vorschlagsliste wird weiterhin aus den tatsächlich gebuchten Positionen
 * abgeleitet (nach Häufigkeit sortiert); diese Tabelle blendet einzelne Einträge
 * daraus nur aus. Dadurch bleibt das Ausblenden eines Vorschlags von den
 * Rechnungsdaten getrennt und kann keine Belege löschen.
 */
@Entity(tableName = "hidden_subjects")
data class HiddenSubject(
    @PrimaryKey val name: String
)
