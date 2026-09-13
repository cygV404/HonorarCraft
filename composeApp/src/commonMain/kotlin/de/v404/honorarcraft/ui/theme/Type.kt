package de.v404.honorarcraft.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.v404.honorarcraft.resources.Res
import de.v404.honorarcraft.resources.montserrat_bold
import org.jetbrains.compose.resources.Font
import androidx.compose.runtime.Composable

/**
 * Montserrat wird als Schriftdatei mitgeliefert, statt über den Google-Fonts-Provider
 * nachgeladen zu werden: den Provider gibt es nur auf Android, der Desktop hätte sonst
 * keine Schrift. Die App nutzt ohnehin nur den fetten Schnitt.
 */
@Composable
fun montserratFontFamily(): FontFamily = FontFamily(
    Font(Res.font.montserrat_bold, FontWeight.Bold)
)

@Composable
fun honorarCraftTypography(): Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
