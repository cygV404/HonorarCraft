package app.accounting.accountingapp

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Hilfsobjekt für die AES-Verschlüsselung sensibler Daten.
 * Der Schlüssel wird dynamisch aus dem System-Usernamen generiert.
 */
object CryptoHelper {
    private const val ALGORITHM = "AES"

    // Generiert einen 16-Byte Schlüssel basierend System (Hardware-Binding)
    private val KEY: ByteArray by lazy {
        val salt = "AccountingApp2026" // Ein fester Zusatz für die Sicherheit
        val userName = System.getProperty("user.name") ?: "DefaultUser"
        // Wir nehmen exakt 16 Bytes der Kombination (userName + salt)
        (userName + salt).padEnd(16, '0').substring(0, 16).toByteArray()
    }  //   implementation(libs.androidx.animation.desktop)

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val secretKey = SecretKeySpec(KEY, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray()))
        } catch (e: Exception) {
            println("Verschlüsselungsfehler: ${e.message}")
            ""
        }
    }

    fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val secretKey = SecretKeySpec(KEY, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            String(cipher.doFinal(Base64.getDecoder().decode(value)))
        } catch (e: Exception) {
            // Falls Entschlüsselung fehlschlägt (z.B. falscher Key oder Klartext in Datei)
            "FEHLER"
        }
    }
}

@Serializable
data class CompanyData(
    var eduCenter: String = "",
    var locationNr: String = "",
    var schoolType: String = "",
    var customerSecondNameOrOrga: String = "",
    var customerFirstName: String = "",
    var customerPlz: String = "",
    var customerCityName: String = "",
    var customerMailBox: String = "",
    var customerStreet: String = "",
    var billerSecondName: String = "",
    var billerFirstName: String = "",
    var billerStreetName: String = "",
    var billerPlzNumber: String = "",
    var billerCityName: String = "",
    var taxNumber: String = "", // Wird verschlüsselt
    var billerIban: String = "", // Wird verschlüsselt
    var billerBIC: String = "",  // Wird verschlüsselt
    var hourRate: String = "",
    var signaturePath: String = "",
    var pdfPath: String = ""
)

private val dataFile = File(System.getProperty("user.home"), ".accountingapp/company.json")

fun loadCompanyData(): CompanyData {
    // Default-Werte für den ersten Start
    val defaultData = CompanyData(
        eduCenter = "Biberach - Ehingen",
        locationNr = "40 - 381",
        schoolType = "AsA flex",
        customerSecondNameOrOrga = "Kolping Berufsbildung gGmbH",
        customerPlz = "70010",
        customerCityName = "Stuttgart",
        customerMailBox = "10 11 61",
        hourRate = "23.0",
        pdfPath = File(System.getProperty("user.home"), "Dokumente/Honorarabrechnungen").absolutePath
    )

    if (!dataFile.exists() || dataFile.length() == 0L) {
        return defaultData
    }

    return try {
        val jsonContent = dataFile.readText()
        val encryptedData = Json.decodeFromString<CompanyData>(jsonContent)

        // entschlüsseln
        encryptedData.copy(
            billerIban = CryptoHelper.decrypt(encryptedData.billerIban),
            taxNumber = CryptoHelper.decrypt(encryptedData.taxNumber),
            billerBIC = CryptoHelper.decrypt(encryptedData.billerBIC)
        )
    } catch (e: Exception) {
        println("Fehler beim Laden oder Entschlüsseln: ${e.message}")
        defaultData
    }
}

fun saveCompanyData(data: CompanyData) {
    try {
        // Kopie mit verschlüsselten Feldern für die Datei
        val dataToSave = data.copy(
            billerIban = CryptoHelper.encrypt(data.billerIban),
            taxNumber = CryptoHelper.encrypt(data.taxNumber),
            billerBIC = CryptoHelper.encrypt(data.billerBIC)
        )

        dataFile.parentFile?.mkdirs()

        // Atomares Speichern über eine temporäre Datei
        val tempFile = File(dataFile.absolutePath + ".tmp")
        tempFile.writeText(Json.encodeToString(dataToSave))

        if (tempFile.exists()) {
            tempFile.renameTo(dataFile)
        }
    } catch (e: Exception) {
        println("Fehler beim Speichern: ${e.message}")
    }
}