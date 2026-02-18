package app.accounting.accountingapp


import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.SecureRandom
import java.util.*
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

object CryptoHelper {
    private const val ALGORITHM = "AES"
    private val prefs = Preferences.userRoot().node("app/accounting/honorarcraft")

    private val KEY: ByteArray by lazy {
        getOrGenerateKey()
    }

    private fun getOrGenerateKey(): ByteArray {
        val keyName = "vault_key"
        val existingKey = prefs.get(keyName, null)

        return if (existingKey != null) {
            Base64.getDecoder().decode(existingKey)
        } else {
            val newKey = ByteArray(16)
            SecureRandom().nextBytes(newKey)
            val encodedKey = Base64.getEncoder().encodeToString(newKey)
            prefs.put(keyName, encodedKey)
            prefs.flush() // Sofort ins System schreiben
            newKey
        }
    }

    fun encrypt(value: String): String {
        if (value.isBlank()) return ""
        return try {
            val secretKey = SecretKeySpec(KEY, ALGORITHM)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray()))
        } catch (e: Exception) {
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
            ""
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
    var customerStreetNumber: String = "",
    var billerSecondName: String = "",
    var billerFirstName: String = "",
    var billerStreetName: String = "",
    var billerStreetNumber: String = "",
    var billerPlzNumber: String = "",
    var billerCityName: String = "",
    var taxNumber: String = "", // Wird verschlüsselt
    var billerIban: String = "", // Wird verschlüsselt
    var billerBIC: String = "",  // Wird verschlüsselt
    var hourRate: String = "",
    var signaturePath: String = "",
    var pdfPath: String = ""
)


private val dataFile = File(getAppDataFolder(), "company.json")

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
        pdfPath = if (System.getProperty("os.name").lowercase().contains("win")) {
            File(System.getProperty("user.home"), "Documents/Honorarabrechnungen").absolutePath
        } else {
            File(System.getProperty("user.home"), "Dokumente/Honorarabrechnungen").absolutePath
        }
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
            billerBIC = CryptoHelper.decrypt(encryptedData.billerBIC),
            billerSecondName = CryptoHelper.decrypt(encryptedData.billerSecondName),
            billerFirstName = CryptoHelper.decrypt(encryptedData.billerFirstName),
            billerStreetName = CryptoHelper.decrypt(encryptedData.billerStreetName),
            billerStreetNumber = CryptoHelper.decrypt(encryptedData.billerStreetNumber),
            billerPlzNumber = CryptoHelper.decrypt(encryptedData.billerPlzNumber),
            billerCityName = CryptoHelper.decrypt(encryptedData.billerCityName),

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
            billerBIC = CryptoHelper.encrypt(data.billerBIC),
            billerSecondName = CryptoHelper.encrypt(data.billerSecondName),
            billerFirstName = CryptoHelper.encrypt(data.billerFirstName),
            billerStreetName = CryptoHelper.encrypt(data.billerStreetName),
            billerStreetNumber = CryptoHelper.encrypt(data.billerStreetNumber),
            billerPlzNumber = CryptoHelper.encrypt(data.billerPlzNumber),
            billerCityName = CryptoHelper.encrypt(data.billerCityName),

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