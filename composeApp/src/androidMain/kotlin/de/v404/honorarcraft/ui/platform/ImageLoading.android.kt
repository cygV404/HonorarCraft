package de.v404.honorarcraft.ui.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import de.v404.honorarcraft.shared.logError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual suspend fun loadImageFromFile(path: String, maxEdge: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            // Erst nur die Maße lesen, dann mit passendem Verkleinerungsfaktor dekodieren.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2

            BitmapFactory
                .decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
                ?.asImageBitmap()
        }.onFailure {
            logError("ImageLoading", "Bild konnte nicht geladen werden: $path", it)
        }.getOrNull()
    }
