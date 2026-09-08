Below is a complete Jetpack Compose starting point for the design, with automatic light/dark theme support.

It includes the top illustrated header area, Hourly/Daily/Map navigation, current-weather card, day selector, and compact hourly forecast table. I’m keeping the weather data mocked so you can plug in your existing model/API.

```kotlin
@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.hluweather

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ------------------------------------------------------------
// MODEL
// ------------------------------------------------------------

data class HourWeather(
    val time: String,
    val condition: String,
    val temperature: Int,
    val dewPoint: Int,
    val humidity: Int,
    val precipitation: Int,
    val icon: WeatherIcon
)

enum class WeatherIcon {
    Moon,
    Sun,
    PartlyCloudy
}

// ------------------------------------------------------------
// SCREEN
// ------------------------------------------------------------

@Composable
fun WeatherScreen(
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    val forecast = remember {
        listOf(
            HourWeather("00h", "Clear sky", 20, 10, 53, 0, WeatherIcon.Moon),
            HourWeather("01h", "Clear sky", 20, 10, 53, 0, WeatherIcon.Moon),
            HourWeather("02h", "Clear sky", 19, 10, 57, 0, WeatherIcon.Moon),
            HourWeather("03h", "Partly cloudy", 18, 11, 62, 0, WeatherIcon.PartlyCloudy),
            HourWeather("04h", "Partly cloudy", 18, 11, 67, 0, WeatherIcon.PartlyCloudy),
            HourWeather("05h", "Clear sky", 17, 11, 70, 0, WeatherIcon.Sun),
            HourWeather("06h", "Clear sky", 16, 11, 71, 0, WeatherIcon.Sun),
            HourWeather("07h", "Clear sky", 17, 11, 65, 0, WeatherIcon.Sun),
            HourWeather("08h", "Clear sky", 20, 10, 55, 0, WeatherIcon.Sun),
            HourWeather("09h", "Clear sky", 22, 9, 42, 0, WeatherIcon.Sun),
            HourWeather("10h", "Clear sky", 25, 8, 33, 0, WeatherIcon.Sun),
            HourWeather("11h", "Clear sky", 27, 6, 27, 0, WeatherIcon.Sun),
            HourWeather("12h", "Clear sky", 29, 5, 23, 0, WeatherIcon.Sun),
            HourWeather("13h", "Clear sky", 29, 5, 21, 0, WeatherIcon.Sun),
            HourWeather("14h", "Fair", 30, 5, 21, 0, WeatherIcon.PartlyCloudy),
            HourWeather("15h", "Clear sky", 30, 5, 21, 0, WeatherIcon.Sun),
            HourWeather("16h", "Clear sky", 30, 5, 21, 0, WeatherIcon.Sun),
            HourWeather("17h", "Clear sky", 30, 6, 22, 0, WeatherIcon.Sun),
            HourWeather("18h", "Clear sky", 29, 6, 24, 0, WeatherIcon.Sun),
            HourWeather("19h", "Clear sky", 27, 7, 28, 0, WeatherIcon.Sun),
            HourWeather("20h", "Clear sky", 26, 8, 32, 0, WeatherIcon.Sun),
            HourWeather("21h", "Clear sky", 24, 9, 38, 0, WeatherIcon.Moon),
            HourWeather("22h", "Clear sky", 22, 10, 46, 0, WeatherIcon.Moon),
            HourWeather("23h", "Clear sky", 21, 10, 51, 0, WeatherIcon.Moon),
        )
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {

            item {
                WeatherHero()
            }

            item {
                CurrentWeatherCard(
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .offset(y = (-8).dp)
                )
            }

            item {
                ForecastHeader(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 16.dp,
                        bottom = 10.dp
                    )
                )
            }

            item {
                ForecastColumnHeader(
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            items(forecast) { item ->
                ForecastRow(
                    weather = item,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

// ------------------------------------------------------------
// HERO
// ------------------------------------------------------------

@Composable
private fun WeatherHero() {
    val colors = LocalHluColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.heroTop,
                        colors.heroBottom
                    )
                )
            )
    ) {
        // Decorative moon / sun
        Box(
            modifier = Modifier
                .size(82.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-65).dp, y = 65.dp)
                .clip(CircleShape)
                .background(colors.moon)
        )

        // Very simple mountain silhouettes
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.mountain.copy(alpha = 0.25f),
                            colors.mountain.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 24.dp
                )
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "HluWeatherApp",
                        color = colors.heroText,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "Simple weather. Clear view.",
                        color = colors.heroSecondaryText,
                        fontSize = 17.sp
                    )
                }

                IconButton(
                    onClick = {}
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colors.heroText
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            NavigationTabs()
        }
    }
}

@Composable
private fun NavigationTabs() {
    var selected by remember {
        mutableIntStateOf(0)
    }

    val items = listOf(
        Triple("Hourly", Icons.Outlined.Schedule, 0),
        Triple("Daily", Icons.Outlined.BarChart, 1),
        Triple("Map", Icons.Outlined.LocationOn, 2)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { (title, icon, index) ->
            WeatherNavButton(
                text = title,
                icon = icon,
                selected = selected == index,
                onClick = {
                    selected = index
                }
            )
        }
    }
}

@Composable
private fun WeatherNavButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalHluColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (selected) {
            colors.navSelected
        } else {
            Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 20.dp,
                vertical = 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    colors.navSelectedText
                } else {
                    colors.heroSecondaryText
                }
            )

            Text(
                text = text,
                fontSize = 18.sp,
                color = if (selected) {
                    colors.navSelectedText
                } else {
                    colors.heroSecondaryText
                }
            )
        }
    }
}

// ------------------------------------------------------------
// CURRENT WEATHER
// ------------------------------------------------------------

@Composable
private fun CurrentWeatherCard(
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = colors.weatherCard
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 3.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Svilajnac",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Sun, Sep 7, 2026 • 23:00",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "21°",
                            fontSize = 70.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 70.sp
                        )

                        Spacer(Modifier.width(18.dp))

                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = colors.moonAccent,
                            modifier = Modifier.size(62.dp)
                        )
                    }

                    Text(
                        text = "Clear sky",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(145.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WeatherMetric(
                        icon = Icons.Default.WaterDrop,
                        title = "Humidity",
                        value = "51%"
                    )

                    WeatherMetric(
                        icon = Icons.Default.DeviceThermostat,
                        title = "Feels like",
                        value = "21°"
                    )

                    WeatherMetric(
                        icon = Icons.Default.Eco,
                        title = "Dew point",
                        value = "10°"
                    )

                    WeatherMetric(
                        icon = Icons.Default.Umbrella,
                        title = "Precipitation",
                        value = "0 mm"
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )

        Spacer(Modifier.width(10.dp))

        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = value,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ------------------------------------------------------------
// FORECAST
// ------------------------------------------------------------

@Composable
private fun ForecastHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Hourly Forecast",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        DaySelector()
    }
}

@Composable
private fun DaySelector() {
    var selected by remember {
        mutableIntStateOf(1)
    }

    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(3.dp)
        ) {
            DayChip(
                text = "Sun, Sep 7",
                selected = selected == 0,
                onClick = { selected = 0 }
            )

            DayChip(
                text = "Mon, Sep 8",
                selected = selected == 1,
                onClick = { selected = 1 }
            )
        }
    }
}

@Composable
private fun DayChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalHluColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = if (selected) {
            colors.daySelected
        } else {
            Color.Transparent
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 10.dp
            ),
            color = if (selected) {
                colors.daySelectedText
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            }
        )
    }
}

// ------------------------------------------------------------
// TABLE
// ------------------------------------------------------------

@Composable
private fun ForecastColumnHeader(
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(
                RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp
                )
            )
            .background(colors.tableHeader)
            .padding(
                horizontal = 16.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ForecastCell(
            text = "Time",
            weight = 0.72f
        )

        ForecastCell(
            text = "Weather",
            weight = 1.85f
        )

        ForecastCell(
            text = "Temp.",
            weight = 0.8f
        )

        ForecastCell(
            text = "Dew point",
            weight = 1f
        )

        ForecastCell(
            text = "Hum.",
            weight = 0.8f
        )

        ForecastCell(
            text = "Precip.",
            weight = 0.9f
        )
    }
}

@Composable
private fun ForecastRow(
    weather: HourWeather,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tableRow)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            .padding(
                horizontal = 16.dp,
                vertical = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Text(
            text = weather.time,
            modifier = Modifier.weight(0.72f),
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.weight(1.85f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeatherIcon(
                weather.icon
            )

            Spacer(Modifier.width(8.dp))

            Text(
                text = weather.condition,
                maxLines = 1
            )
        }

        Text(
            text = "${weather.temperature}°",
            modifier = Modifier.weight(0.8f),
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = "${weather.dewPoint}°",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = "${weather.humidity}%",
            modifier = Modifier.weight(0.8f)
        )

        Text(
            text = "${weather.precipitation} mm",
            modifier = Modifier.weight(0.9f)
        )
    }
}

@Composable
private fun RowScope.ForecastCell(
    text: String,
    weight: Float
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )
}

@Composable
private fun WeatherIcon(
    type: WeatherIcon
) {
    when (type) {
        WeatherIcon.Moon -> {
            Icon(
                imageVector = Icons.Default.DarkMode,
                contentDescription = null,
                tint = LocalHluColors.current.moonAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        WeatherIcon.Sun -> {
            Icon(
                imageVector = Icons.Default.WbSunny,
                contentDescription = null,
                tint = Color(0xFFFFB300),
                modifier = Modifier.size(24.dp)
            )
        }

        WeatherIcon.PartlyCloudy -> {
            Icon(
                imageVector = Icons.Default.PartlyCloudyDay,
                contentDescription = null,
                tint = LocalHluColors.current.cloudAccent,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ------------------------------------------------------------
// THEME
// ------------------------------------------------------------

private val LightColors = lightColorScheme(
    primary = Color(0xFF5367E8),
    onPrimary = Color.White,

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

private val LightHluColors = HluColors(
    heroTop = Color(0xFF6779ED),
    heroBottom = Color(0xFF4D55C6),

    heroText = Color.White,
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
    daySelectedText = Color.White
)

private val DarkHluColors = HluColors(
    heroTop = Color(0xFF071225),
    heroBottom = Color(0xFF10264B),

    heroText = Color(0xFFF5F7FF),
    heroSecondaryText = Color(0xFFB8C4EA),

    mountain = Color(0xFF020A18),

    moon = Color(0xFFE4D7B7),
    moonAccent = Color(0xFF7789FF),
    cloudAccent = Color(0xFF8493D7),

    navSelected = Color(0xFF9DA8FF),
    navSelectedText = Color(0xFF131A3C),

    weatherCard = Color(0xFF111C2D),

    tableHeader = Color(0xFF1A2740),
    tableRow = Color(0xFF101A2B),

    daySelected = Color(0xFF8492FF),
    daySelectedText = Color(0xFF101632)
)

private val LocalHluColors =
    staticCompositionLocalOf {
        LightHluColors
    }

@Composable
fun HluWeatherTheme(
    darkTheme: Boolean =
        androidx.compose.foundation.isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val materialColors =
        if (darkTheme) {
            DarkColors
        } else {
            LightColors
        }

    val hluColors =
        if (darkTheme) {
            DarkHluColors
        } else {
            LightHluColors
        }

    CompositionLocalProvider(
        LocalHluColors provides hluColors
    ) {
        MaterialTheme(
            colorScheme = materialColors,
            typography = Typography(),
            content = content
        )
    }
}

// ------------------------------------------------------------
// APP
// ------------------------------------------------------------

@Composable
fun HluWeatherApp() {
    HluWeatherTheme {
        WeatherScreen()
    }
}

// ------------------------------------------------------------
// PREVIEWS
// ------------------------------------------------------------

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun LightPreview() {
    HluWeatherTheme(
        darkTheme = false
    ) {
        WeatherScreen()
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DarkPreview() {
    HluWeatherTheme(
        darkTheme = true
    ) {
        WeatherScreen()
    }
}
```

You’ll also want these Material icons dependencies available. With recent Compose BOM setups, `material-icons-extended` is the easiest option:

```kotlin
dependencies {
    implementation(
        "androidx.compose.material:material-icons-extended"
    )
}
```

The theme automatically follows Android dark mode:

```kotlin
HluWeatherTheme {
    WeatherScreen()
}
```

Or you can force either appearance:

```kotlin
HluWeatherTheme(darkTheme = false) {
    WeatherScreen()
}
```

```kotlin
HluWeatherTheme(darkTheme = true) {
    WeatherScreen()
}
```

For the **real moon phase**, don’t hard-code `DarkMode`. Your weather/domain layer should calculate a `moonPhase` value from the date, then render something like `NEW_MOON`, `WAXING_CRESCENT`, `FIRST_QUARTER`, `WAXING_GIBBOUS`, `FULL_MOON`, etc. The screenshot design is especially suited to replacing the simple icon with an actual Compose-drawn moon disc.

If you want, I can next make you a **production-ready version split into `WeatherScreen.kt`, `Theme.kt`, `WeatherModels.kt`, and reusable components**, including a proper moon-phase composable and ViewModel state.
