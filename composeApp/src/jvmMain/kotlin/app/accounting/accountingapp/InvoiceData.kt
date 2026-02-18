package app.accounting.accountingapp


import java.math.BigDecimal
import java.math.RoundingMode


data class InvoiceData(
    val invoiceNumber: String,
    val entries: List<InvoiceEntry>,
    val hourRate: BigDecimal
) {

    // 1. Zentrale Funktion für die Umrechnung
    fun calculateCorrectedHours(hours: Double): BigDecimal {
        return hours.toBigDecimal()
            .multiply(BigDecimal("60"))
            .divide(BigDecimal("45"), 2, RoundingMode.HALF_UP)
    }

    // alternativ
    fun calculateCorrectedLessonUnits(hours: Double): BigDecimal {
        return hours.toBigDecimal()

    }


    // 2. Gesamtkosten: Summe der bereits gerundeten Einzelwerte
    val totalCostsHours: BigDecimal
        get() = entries.sumOf { entry ->
            val ue = calculateCorrectedHours(entry.hours)
            ue.multiply(hourRate).setScale(2, RoundingMode.HALF_UP)

        }

    // 2. alternativ
    val totalCostsLessonUnits: BigDecimal
        get() = entries.sumOf { entry ->
            val ue = calculateCorrectedLessonUnits(entry.hours)
            ue.multiply(hourRate).setScale(2, RoundingMode.HALF_UP)

        }

    // 3. UE gesamt
    val totalLessonUnit: BigDecimal
        get() = entries.sumOf { entry ->
            calculateCorrectedHours(entry.hours)
        }


    // Alternativ Stunden gesamt
    val totalHours: BigDecimal
        get() = entries.sumOf { entry ->
            calculateCorrectedLessonUnits(entry.hours)
        }
}




