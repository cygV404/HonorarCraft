package de.v404.honorarcraft.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Ein Rechnungsempfänger.
 *
 * Bildungszentrum, Standortnummer und Schulart gehören bewusst **nicht** hierher: sie wechseln
 * je Rechnung und stehen deshalb in [InvoiceData].
 */
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nameOrOrga: String = "",
    val firstName: String = "",
    val street: String = "",
    val streetNumber: String = "",
    val plz: String = "",
    val mailBox: String = "",
    val cityName: String = "",
    /** Vorgabe für neue Positionen dieses Kunden; `null` heißt: allgemeiner Satz aus [CompanyData]. */
    val rate: BigDecimal? = null,
    /**
     * Archivierte Kunden verschwinden aus der Auswahl beim Erfassen, bleiben aber für ihre
     * Rechnungen erhalten. Löschen geht nur ohne Rechnungen (Fremdschlüssel `RESTRICT`).
     */
    val archived: Boolean = false,
) {
    /** Kurzname für Auswahllisten und Dateinamen. */
    val displayName: String
        get() = listOf(nameOrOrga.trim(), firstName.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .ifEmpty { "Kunde $id" }
}
