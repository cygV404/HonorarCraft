package de.v404.honorarcraft.shared.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Kopf einer Rechnung.
 *
 * Bildungszentrum, Standortnummer und Schulart/Maßnahme stehen hier und nicht am Kunden, weil
 * sie von Rechnung zu Rechnung wechseln. Eine neue Rechnung übernimmt sie als Vorschlag von der
 * letzten Rechnung desselben Kunden.
 *
 * Der Kunde lässt sich nicht löschen, solange Rechnungen an ihm hängen (`RESTRICT`).
 */
@Entity(
    tableName = "invoices",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.RESTRICT,
        )
    ],
    indices = [Index(value = ["customerId"])],
)
data class InvoiceData(
    @PrimaryKey val invoiceNumber: String,
    val customerId: Long,
    val eduCenter: String = "",
    val locationNr: String = "",
    val schoolType: String = "",
)

/** Die drei Angaben, die je Rechnung wechseln. */
data class InvoiceDetails(
    val eduCenter: String = "",
    val locationNr: String = "",
    val schoolType: String = "",
)

val InvoiceData.details: InvoiceDetails get() = InvoiceDetails(eduCenter, locationNr, schoolType)

/**
 * Represents the one-to-many relationship between an Invoice and its Entries.
 * This class is used to query an invoice with all its corresponding line items.
 */
data class InvoiceWithEntries(
    @Embedded val invoice: InvoiceData,
    @Relation(
        parentColumn = "invoiceNumber",
        entityColumn = "invoiceNumber"
    )
    val entries: List<InvoiceEntry>,
    @Relation(
        parentColumn = "customerId",
        entityColumn = "id"
    )
    val customer: Customer?,
) {
    val totalSum: BigDecimal
        get() = entries.fold(BigDecimal.ZERO) { acc, entry ->
            val ueValue = entry.lessonUnits.multiply(BigDecimal("60"))
                .divide(BigDecimal("45"), 10, RoundingMode.HALF_UP)
            acc.add(ueValue.multiply(entry.rate))
        }.setScale(2, RoundingMode.HALF_UP)


    val totalLessonUnit: BigDecimal
        get() = entries.fold(BigDecimal.ZERO) { acc, entry ->
            val ueValue = entry.lessonUnits.multiply(BigDecimal("60"))
                .divide(BigDecimal("45"), 10, RoundingMode.HALF_UP)
            acc.add(ueValue)
        }.setScale(2, RoundingMode.HALF_UP)

}
