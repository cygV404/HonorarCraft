package de.v404.honorarcraft.ui.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import de.v404.honorarcraft.shared.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

actual suspend fun loadImageFromFile(path: String, maxEdge: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val datei = File(path)
            if (!datei.isFile) return@runCatching null
            val original = ImageIO.read(datei) ?: return@runCatching null

            val laengsteKante = maxOf(original.width, original.height)
            val bild = if (laengsteKante <= maxEdge) {
                original
            } else {
                val faktor = maxEdge.toDouble() / laengsteKante
                val breite = (original.width * faktor).toInt().coerceAtLeast(1)
                val hoehe = (original.height * faktor).toInt().coerceAtLeast(1)
                BufferedImage(breite, hoehe, BufferedImage.TYPE_INT_ARGB).also { ziel ->
                    ziel.createGraphics().run {
                        setRenderingHint(
                            RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                        )
                        drawImage(original, 0, 0, breite, hoehe, null)
                        dispose()
                    }
                }
            }
            bild.toComposeImageBitmap()
        }.onFailure {
            logError("ImageLoading", "Bild konnte nicht geladen werden: $path", it)
        }.getOrNull()
    }
