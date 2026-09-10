package net.droopia.hluweather.ui.theme

import androidx.compose.ui.graphics.Color

data class HluColors(
    val heroTop: Color,
    val heroBottom: Color,
    val heroText: Color,
    val heroSecondaryText: Color,
    val mountain: Color,
    val moon: Color,
    val moonAccent: Color,
    val cloudAccent: Color,
    val navSelected: Color,
    val navSelectedText: Color,
    val weatherCard: Color,
    val tableHeader: Color,
    val tableRow: Color,
    val daySelected: Color,
    val daySelectedText: Color
)

val LightHluColors = HluColors(
    heroTop = Color(0xFF6779ED),
    heroBottom = Color(0xFF4D55C6),
    heroText = Color(0xFFFFFFFF),
    heroSecondaryText = Color(0xFFE4E7FF),
    mountain = Color(0xFF242B7A),
    moon = Color(0xFFFFF0BD),
    moonAccent = Color(0xFF5865DA),
    cloudAccent = Color(0xFF9FADEB),
    navSelected = Color(0xFFF5F6FF),
    navSelectedText = Color(0xFF3040A7),
    weatherCard = Color(0xFFFBFBFE),
    tableHeader = Color(0xFFEFF1FA),
    tableRow = Color(0xFFFAFBFD),
    daySelected = Color(0xFF5969E9),
    daySelectedText = Color(0xFFFFFFFF)
)

val DarkHluColors = HluColors(
    heroTop = Color(0xFF071225),
    heroBottom = Color(0xFF10264B),
    heroText = Color(0xFFF5F7FF),
    heroSecondaryText = Color(0xFFB8C4EA),
    mountain = Color(0xFF020A18),
    moon = Color(0xFFE4D7B7),
    moonAccent = Color(0xFF7789FF),
    cloudAccent = Color(0xFF8493D7),
    navSelected = Color(0xFF1B3569),
    navSelectedText = Color(0xFFF5F7FF),
    weatherCard = Color(0xFF111C2D),
    tableHeader = Color(0xFF1A2740),
    tableRow = Color(0xFF101A2B),
    daySelected = Color(0xFF234A86),
    daySelectedText = Color(0xFFF5F7FF)
)
