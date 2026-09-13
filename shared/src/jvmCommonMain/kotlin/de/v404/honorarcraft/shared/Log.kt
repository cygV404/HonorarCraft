package de.v404.honorarcraft.shared

/** Fehlerausgabe je Plattform: Android über Logcat, Desktop über die Standardfehlerausgabe. */
expect fun logError(tag: String, message: String, throwable: Throwable? = null)
