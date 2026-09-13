package de.v404.honorarcraft.shared

actual fun logError(tag: String, message: String, throwable: Throwable?) {
    System.err.println("[$tag] $message")
    throwable?.printStackTrace()
}
