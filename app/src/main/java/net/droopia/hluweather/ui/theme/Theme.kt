package net.droopia.hluweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5367E8),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF13162A),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF171A2C),
    surfaceVariant = Color(0xFFEDEFFC),
    onSurfaceVariant = Color(0xFF5D6278),
    outlineVariant = Color(0xFFDDE0EB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA7FF),
    onPrimary = Color(0xFF101532),
    background = Color(0xFF09111E),
    onBackground = Color(0xFFE8EDFF),
    surface = Color(0xFF101A2B),
    onSurface = Color(0xFFF2F4FF),
    surfaceVariant = Color(0xFF18243A),
    onSurfaceVariant = Color(0xFFB7C0D9),
    outlineVariant = Color(0xFF26334B)
)

val LocalHluColors = staticCompositionLocalOf<HluColors> {
    error("HluColors not provided")
}

@Composable
fun HluWeatherTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val materialColors = if (darkTheme) DarkColors else LightColors
    val hluColors = if (darkTheme) DarkHluColors else LightHluColors

    CompositionLocalProvider(LocalHluColors provides hluColors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
