package app.accounting.accountingapp

import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class InvoiceEntry(
    val date: String,
    val hours: Double,
    val teachingSubject: String,
    // Die ID wird beim Erstellen automatisch generiert,
    // außer sie wird beim Laden aus dem JSON explizit übergeben.
    val id: Long = System.nanoTime() + Random.nextLong(1000)
)