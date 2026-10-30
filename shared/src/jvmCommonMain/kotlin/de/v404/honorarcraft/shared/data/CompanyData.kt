package de.v404.honorarcraft.shared.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

/**
 * Der Rechnungssteller: eigene Anschrift, Finanzdaten, Unterschrift, Ablageordner und der
 * allgemeine Honorarsatz. Genau eine Zeile (`id = 1`). Die Empfänger stehen in [Customer].
 */
@Entity(tableName = "company_data")
data class CompanyData(
    @PrimaryKey val id: Int = 1, // Fixed ID for single entry
    var billerSecondName: String = "Mustermann",
    var billerFirstName: String = "Max",
    var billerStreetName: String = "Musterstraße",
    var billerStreetNumber: String = "12",
    var billerPlzNumber: String = "77500",
    var billerCityName: String = "Musterstadt",
    var taxNumber: String = "12/345/678/912",
    var billerIban: String = "DE12 3456 7810 1112 1314 15",
    var billerBIC: String = "XXX",
    var rate: BigDecimal = Constants.DEFAULT_RATE,
    var signaturePath: String = "",
    var pdfPath: String = ""
)
