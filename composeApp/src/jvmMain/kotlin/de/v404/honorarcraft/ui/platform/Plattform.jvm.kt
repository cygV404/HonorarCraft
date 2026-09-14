package de.v404.honorarcraft.ui.platform

actual fun istLinux(): Boolean =
    System.getProperty("os.name").orEmpty().lowercase().let {
        !it.contains("win") && !it.contains("mac")
    }
