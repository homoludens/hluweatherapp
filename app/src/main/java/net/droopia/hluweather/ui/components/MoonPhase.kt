package net.droopia.hluweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.Preview
import net.droopia.hluweather.ui.theme.LocalHluColors
import kotlin.math.abs
import kotlin.math.cos

private val MoonDark = Color(0xFF28334A)

@Composable
fun MoonPhase(
    phase: Double,
    modifier: Modifier = Modifier
) {
    val litColor = LocalHluColors.current.moon

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = this.center
        val angle = phase * 2.0 * Math.PI
        val cosine = cos(angle).toFloat()
        val waxing = phase <= 0.5
        val bounds = Rect(
            left = center.x - radius,
            top = center.y - radius,
            right = center.x + radius,
            bottom = center.y + radius
        )

        drawCircle(MoonDark, radius, center)

        val litHalf = Path().apply {
            if (waxing) {
                arcTo(bounds, -90f, 180f, true)
            } else {
                arcTo(bounds, 90f, 180f, true)
            }
            close()
        }
        drawPath(litHalf, litColor)

        val ellipseWidth = 2f * radius * abs(cosine)
        if (ellipseWidth > 0.1f) {
            val ellipseRect = Rect(
                left = center.x - ellipseWidth / 2f,
                top = center.y - radius,
                right = center.x + ellipseWidth / 2f,
                bottom = center.y + radius
            )
            val ellipseColor = if (cosine > 0f) MoonDark else litColor
            drawOval(
                color = ellipseColor,
                topLeft = Offset(ellipseRect.left, ellipseRect.top),
                size = Size(ellipseRect.width, ellipseRect.height)
            )
        }
    }
}

@Preview
@Composable
private fun MoonPhasePreview() {
    MoonPhase(phase = 0.25)
}
