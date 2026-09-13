package de.v404.honorarcraft.shared.legacy

import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Entschlüsselt die Felder der alten Desktop-Datei `company.json`.
 *
 * Die frühere Desktop-Fassung hat Name, Anschrift, IBAN, BIC und Steuernummer mit AES
 * verschlüsselt abgelegt; der Schlüssel liegt Base64-kodiert in `java.util.prefs` unter
 * `app/accounting/honorarcraft`. **Der Knotenname ist historisch und darf sich nicht
 * ändern** — ein anderer Knoten bedeutet einen anderen Schlüssel und damit leere Felder.
 *
 * Wird nur noch vom Importer gebraucht. Neue Daten liegen unverschlüsselt in der Datenbank
 * (siehe offene Frage 7 im KMP_PLAN.md).
 */
class LegacyVaultCrypto(
    private val key: ByteArray?,
) {
    companion object {
        private const val ALGORITHM = "AES"
        const val NODE = "app/accounting/honorarcraft"
        private const val KEY_NAME = "vault_key"

        /** Liest den vorhandenen Schlüssel. Legt bewusst keinen neuen an: Ohne Schlüssel
         *  gibt es auch nichts zu entschlüsseln. */
        fun fromPreferences(nodeName: String = NODE): LegacyVaultCrypto {
            val stored = Preferences.userRoot().node(nodeName).get(KEY_NAME, null)
            return LegacyVaultCrypto(stored?.let { Base64.getDecoder().decode(it) })
        }

        /** Nur für Tests: erzeugt einen Zufallsschlüssel ohne Berührung der Preferences. */
        fun withRandomKey(): LegacyVaultCrypto =
            LegacyVaultCrypto(ByteArray(16).also { SecureRandom().nextBytes(it) })
    }

    val hasKey: Boolean get() = key != null

    fun decrypt(value: String): String {
        if (value.isBlank() || key == null) return ""
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, ALGORITHM))
            String(cipher.doFinal(Base64.getDecoder().decode(value)))
        } catch (e: Exception) {
            ""
        }
    }

    /** Gegenstück zu [decrypt]; wird gebraucht, um im Test eine Altdatei zu erzeugen. */
    fun encrypt(value: String): String {
        if (value.isBlank() || key == null) return ""
        return try {
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, ALGORITHM))
            Base64.getEncoder().encodeToString(cipher.doFinal(value.toByteArray()))
        } catch (e: Exception) {
            ""
        }
    }
}
