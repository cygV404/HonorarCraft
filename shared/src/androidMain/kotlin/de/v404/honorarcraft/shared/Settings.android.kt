package de.v404.honorarcraft.shared

import android.content.Context

/**
 * Einstellungen in den `SharedPreferences` unter dem Namen "settings" — genau dort, wo die
 * ausgelieferte Android-App sie schon hat. Der Name darf sich nicht ändern, sonst verliert
 * ein bestehendes Gerät seine gewählte Rechnungsnummer.
 */
class AndroidSettings(context: Context) : Settings {
    private val prefs = context.applicationContext
        .getSharedPreferences("settings", Context.MODE_PRIVATE)

    override fun getInt(key: String, defaultValue: Int): Int = prefs.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getString(key: String, defaultValue: String): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }
}
