package app.accounting.accountingapp


import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin


@Composable
fun WavyCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = Color(0xFFEDCDFD),
    waveCount: Int = 6,
    amplitude: Float = 3f,
    animationDuration: Int = 700
) {
    val infiniteTransition = rememberInfiniteTransition()

    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(animationDuration, easing = LinearEasing)
        )
    )

    // waveOffset für leichtes Pulsieren
    // Wenn alles absolut still stehen soll targetValue 0f
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing)
        )
    )

    Canvas(modifier = modifier.size(120.dp)) {
        val center = size / 2f
        val baseRadius = size.minDimension / 2f - 20.dp.toPx()

        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = baseRadius,
            style = Stroke(width = 6.dp.toPx())
        )

        val path = androidx.compose.ui.graphics.Path()
        val startAngle = -90f

        for (i in 0..sweepAngle.toInt()) {
            val currentAngle = startAngle + i.toFloat()
            val angleInRad = Math.toRadians(currentAngle.toDouble()).toFloat()


            // Welle an die Gradzahl des Kreises gebunden
            val wave = sin(currentAngle.toDouble() * waveCount * (Math.PI / 180) + waveOffset).toFloat()
            val dynamicRadius = baseRadius + (wave * amplitude)

            val x = center.width + cos(angleInRad) * dynamicRadius
            val y = center.height + sin(angleInRad) * dynamicRadius

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}