Yes. I’d implement the three generated screens as separate composables sharing one `TripWeatherUiState`. The 72-hour slider changes `departureOffsetHours`, and your ViewModel can recalculate route weather whenever it changes.

Below is a production-friendly starting point.

### Suggested files

```text
ui/trip/
├── TripModels.kt
├── TripWeatherSetupScreen.kt
├── TripWeatherOverviewScreen.kt
├── TripWeatherDetailsScreen.kt
├── TripComponents.kt
└── TripWeatherViewModel.kt
```

## 1. `TripModels.kt`

```kotlin
package com.hluweather.ui.trip

import java.time.LocalDateTime

enum class WeatherKind {
    CLEAR_DAY,
    CLEAR_NIGHT,
    PARTLY_CLOUDY,
    CLOUDY,
    RAIN,
    SNOW,
    THUNDERSTORM,
    WIND
}

data class RouteWeatherPoint(
    val time: LocalDateTime,
    val place: String,
    val distanceKm: Int,
    val temperatureC: Int,
    val condition: String,
    val weather: WeatherKind,
    val latitude: Double,
    val longitude: Double,
    val precipitationMm: Double = 0.0,
    val windKmh: Int = 0
)

data class RouteWarning(
    val title: String,
    val description: String,
    val location: String,
    val weather: WeatherKind
)

data class TripWeatherUiState(
    val from: String = "Zaječar",
    val to: String = "Trieste",

    val speedKmh: Int = 80,

    val baseDeparture: LocalDateTime =
        LocalDateTime.of(
            2026, 9, 11, 23, 0
        ),

    val departureOffsetHours: Int = 0,

    val provider: String = "Open-Meteo",

    val useRouteEstimate: Boolean = false,

    val distanceKm: Int = 836,
    val estimatedMinutes: Int = 627,

    val highestTemperature: Int = 28,
    val rainHours: Int = 3,
    val strongestWindKmh: Int = 46,

    val points: List<RouteWeatherPoint> =
        previewRoutePoints,

    val warnings: List<RouteWarning> =
        listOf(
            RouteWarning(
                title = "Rain expected",
                description = "04:00 – 07:00",
                location = "Zagreb – Ljubljana area",
                weather = WeatherKind.RAIN
            ),
            RouteWarning(
                title = "Strong wind possible",
                description = "Gusts up to 46 km/h",
                location = "Slovenian coast",
                weather = WeatherKind.WIND
            )
        )
) {
    val departure: LocalDateTime
        get() = baseDeparture.plusHours(
            departureOffsetHours.toLong()
        )
}

val previewRoutePoints =
    listOf(
        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 11, 23, 0),
            "Zaječar",
            0,
            20,
            "Clear",
            WeatherKind.CLEAR_NIGHT,
            43.9042,
            22.2847
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 0, 0),
            "Niš area",
            80,
            21,
            "Clear",
            WeatherKind.CLEAR_NIGHT,
            43.3209,
            21.8958
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 1, 0),
            "South Morava valley",
            160,
            21,
            "Mostly clear",
            WeatherKind.CLEAR_NIGHT,
            43.5,
            21.7
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 2, 0),
            "Belgrade",
            240,
            22,
            "Clear",
            WeatherKind.CLEAR_NIGHT,
            44.8125,
            20.4612
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 3, 0),
            "Sremska Mitrovica",
            320,
            19,
            "Partly cloudy",
            WeatherKind.PARTLY_CLOUDY,
            44.9764,
            19.6122
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 4, 0),
            "Zagreb area",
            510,
            16,
            "Light rain",
            WeatherKind.RAIN,
            45.815,
            15.9819,
            precipitationMm = 1.2
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 5, 0),
            "Karlovac",
            580,
            15,
            "Rain",
            WeatherKind.RAIN,
            45.4929,
            15.5553,
            precipitationMm = 2.8
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 6, 0),
            "Ljubljana area",
            690,
            14,
            "Rain",
            WeatherKind.RAIN,
            46.0569,
            14.5058,
            precipitationMm = 2.1
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 7, 0),
            "Postojna",
            760,
            15,
            "Cloudy",
            WeatherKind.CLOUDY,
            45.774,
            14.213
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 9, 0),
            "Trieste outskirts",
            830,
            19,
            "Partly cloudy",
            WeatherKind.PARTLY_CLOUDY,
            45.67,
            13.78
        ),

        RouteWeatherPoint(
            LocalDateTime.of(2026, 9, 12, 10, 0),
            "Trieste",
            836,
            20,
            "Partly cloudy",
            WeatherKind.PARTLY_CLOUDY,
            45.6495,
            13.7768,
            windKmh = 14
        )
    )
```

## 2. Shared components

```kotlin
package com.hluweather.ui.trip

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TripCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun WeatherGlyph(
    kind: WeatherKind,
    modifier: Modifier = Modifier
) {
    val emoji =
        when (kind) {
            WeatherKind.CLEAR_DAY -> "☀️"
            WeatherKind.CLEAR_NIGHT -> "🌙"
            WeatherKind.PARTLY_CLOUDY -> "🌤️"
            WeatherKind.CLOUDY -> "☁️"
            WeatherKind.RAIN -> "🌧️"
            WeatherKind.SNOW -> "🌨️"
            WeatherKind.THUNDERSTORM -> "⛈️"
            WeatherKind.WIND -> "💨"
        }

    Text(
        text = emoji,
        modifier = modifier,
        fontSize = 27.sp
    )
}

@Composable
fun TripTopBar(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 12.dp,
                vertical = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Back"
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Text(
                subtitle,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        IconButton(onClick = {}) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Options"
            )
        }
    }
}
```

---

# 3. Trip setup screen

This corresponds to:

`trip_weather_setup_screen.png`

```kotlin
@Composable
fun TripWeatherSetupScreen(
    state: TripWeatherUiState,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSwap: () -> Unit,
    onSpeedChange: (Int) -> Unit,
    onDepartureOffsetChange: (Int) -> Unit,
    onProviderChange: (String) -> Unit,
    onRouteEstimateChange: (Boolean) -> Unit,
    onShowTrip: () -> Unit,
    onBack: () -> Unit
) {

    Scaffold(
        bottomBar = {
            TripBottomNavigation(
                selected = TripBottomItem.TRIP
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding =
                PaddingValues(16.dp),
            verticalArrangement =
                Arrangement.spacedBy(14.dp)
        ) {

            item {
                TripTopBar(
                    title = "Trip weather",
                    subtitle =
                        "Plan your journey. Know the weather ahead.",
                    onBack = onBack
                )
            }

            item {
                RouteInputCard(
                    state,
                    onFromChange,
                    onToChange,
                    onSwap,
                    onSpeedChange
                )
            }

            item {
                DepartureSliderCard(
                    state = state,
                    onOffsetChanged =
                        onDepartureOffsetChange
                )
            }

            item {
                TripOptionsCard(
                    state,
                    onProviderChange,
                    onRouteEstimateChange,
                    onShowTrip
                )
            }

            item {
                RecentTripsCard()
            }
        }
    }
}
```

### Route form

```kotlin
@Composable
private fun RouteInputCard(
    state: TripWeatherUiState,
    onFromChange: (String) -> Unit,
    onToChange: (String) -> Unit,
    onSwap: () -> Unit,
    onSpeedChange: (Int) -> Unit
) {

    TripCard {

        OutlinedTextField(
            value = state.from,
            onValueChange = onFromChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("From") },
            leadingIcon = {
                Icon(
                    Icons.Default.LocationOn,
                    null
                )
            },
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = state.to,
                onValueChange = onToChange,
                modifier = Modifier.weight(1f),
                label = { Text("To") },
                leadingIcon = {
                    Icon(
                        Icons.Default.LocationOn,
                        null
                    )
                },
                singleLine = true
            )

            IconButton(
                onClick = onSwap
            ) {
                Icon(
                    Icons.Default.SwapVert,
                    contentDescription =
                        "Swap locations"
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.DirectionsCar,
                null
            )

            Spacer(Modifier.width(12.dp))

            Text(
                "Travel speed",
                modifier = Modifier.weight(1f)
            )

            OutlinedTextField(
                value =
                    state.speedKmh.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let(
                        onSpeedChange
                    )
                },
                modifier =
                    Modifier.width(110.dp),
                suffix = {
                    Text("km/h")
                },
                singleLine = true
            )
        }

        Spacer(Modifier.height(16.dp))

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Schedule,
                null
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text("Departure")

                Text(
                    state.departure
                        .format(
                            DateTimeFormatter.ofPattern(
                                "EEE, dd MMM yyyy"
                            )
                        ),
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            Text(
                state.departure.format(
                    DateTimeFormatter.ofPattern(
                        "HH:mm"
                    )
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

---

# 4. 72-hour departure slider

This is the important interactive part.

```kotlin
@Composable
private fun DepartureSliderCard(
    state: TripWeatherUiState,
    onOffsetChanged: (Int) -> Unit
) {

    TripCard {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.Schedule,
                null,
                tint =
                    MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    "Start time preview",
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "Slide to preview route weather for the next 72 hours",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            shape = RoundedCornerShape(12.dp),
            color =
                MaterialTheme.colorScheme
                    .primaryContainer
        ) {

            Text(
                text =
                    state.departure.format(
                        DateTimeFormatter.ofPattern(
                            "EEE, dd MMM yyyy · HH:mm"
                        )
                    ),

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(12.dp),

                textAlign =
                    TextAlign.Center,

                fontWeight =
                    FontWeight.Bold,

                color =
                    MaterialTheme.colorScheme
                        .primary
            )
        }

        Slider(
            value =
                state.departureOffsetHours.toFloat(),

            onValueChange = {
                onOffsetChanged(
                    it.roundToInt()
                )
            },

            valueRange =
                0f..72f,

            steps = 71
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {
            Text("Now")
            Text("+24h")
            Text("+48h")
            Text("+72h")
        }
    }
}
```

You can recalculate weather immediately in your ViewModel:

```kotlin
fun setDepartureOffset(
    hours: Int
) {
    _state.update {
        it.copy(
            departureOffsetHours = hours
        )
    }

    refreshTripWeatherDebounced()
}
```

I recommend around **250–400 ms debounce**, otherwise sliding across 72 hours could issue dozens of weather requests.

---

# 5. Options card

```kotlin
@Composable
private fun TripOptionsCard(
    state: TripWeatherUiState,
    onProviderChange: (String) -> Unit,
    onRouteEstimateChange: (Boolean) -> Unit,
    onShowTrip: () -> Unit
) {

    TripCard {

        Text(
            "Weather provider",
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        var expanded by remember {
            mutableStateOf(false)
        }

        Box {

            OutlinedButton(
                onClick = {
                    expanded = true
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                WeatherGlyph(
                    WeatherKind.PARTLY_CLOUDY
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    state.provider,
                    modifier =
                        Modifier.weight(1f),
                    textAlign =
                        TextAlign.Start
                )

                Icon(
                    Icons.Default
                        .KeyboardArrowDown,
                    null
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = {
                    expanded = false
                }
            ) {

                listOf(
                    "Open-Meteo",
                    "MET.no"
                ).forEach {

                    DropdownMenuItem(
                        text = {
                            Text(it)
                        },
                        onClick = {
                            expanded = false
                            onProviderChange(it)
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text("Use route estimates")

                Text(
                    "Use actual driving time from routing",
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            Switch(
                checked =
                    state.useRouteEstimate,
                onCheckedChange =
                    onRouteEstimateChange
            )
        }

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = onShowTrip,
            modifier =
                Modifier.fillMaxWidth()
        ) {
            Text("Show trip weather")

            Spacer(Modifier.width(8.dp))

            Icon(
                Icons.Default.ArrowForward,
                null
            )
        }
    }
}
```

---

# 6. Route overview screen

Corresponds to:

`trip_weather_route_overview_screen.png`

```kotlin
@Composable
fun TripWeatherOverviewScreen(
    state: TripWeatherUiState,
    onBack: () -> Unit,
    onOpenDetails: () -> Unit
) {

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 30.dp
            ),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {
            TripTopBar(
                title =
                    "${state.from} → ${state.to}",
                subtitle =
                    "${
                        state.departure.format(
                            DateTimeFormatter.ofPattern(
                                "EEE, dd MMM yyyy, HH:mm"
                            )
                        )
                    } · ${state.speedKmh} km/h",

                onBack = onBack
            )
        }

        item {
            RouteMapCard(
                points = state.points
            )
        }

        item {
            DeparturePreviewBanner(
                state
            )
        }

        item {
            TripStatistics(
                state
            )
        }

        item {
            WeatherAlongRouteCard(
                state
            )
        }

        state.warnings.firstOrNull()?.let {
            item {
                WarningCard(it)
            }
        }

        item {
            ElevationCard()
        }

        item {
            HighlightsCard(
                state.points
            )
        }

        item {
            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(12.dp)
            ) {

                OutlinedButton(
                    onClick = {},
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.BookmarkBorder,
                        null
                    )

                    Spacer(Modifier.width(8.dp))

                    Text("Save trip")
                }

                Button(
                    onClick =
                        onOpenDetails,
                    modifier =
                        Modifier.weight(1f)
                ) {
                    Text("Details")

                    Spacer(Modifier.width(8.dp))

                    Icon(
                        Icons.Default.ArrowForward,
                        null
                    )
                }
            }
        }
    }
}
```

---

# 7. Map with weather icons

For now make the map a reusable integration component:

```kotlin
@Composable
fun RouteMapCard(
    points: List<RouteWeatherPoint>
) {

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(310.dp),

        shape =
            RoundedCornerShape(22.dp)
    ) {

        Box(
            modifier =
                Modifier.fillMaxSize()
        ) {

            /*
             Replace this Box with your existing
             MapLibre/OpenFreeMap MaplibreMap.

             Draw route GeoJSON as LineLayer.

             For each point add a weather marker:
                 point.longitude
                 point.latitude
                 WeatherGlyph(point.weather)
            */

            Surface(
                modifier =
                    Modifier.fillMaxSize(),
                color =
                    MaterialTheme.colorScheme
                        .surfaceVariant
            ) {}

            Column(
                modifier =
                    Modifier
                        .align(
                            Alignment.Center
                        )
                        .padding(20.dp),
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    "OpenFreeMap / MapLibre",
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(12.dp)
                ) {

                    points
                        .filterIndexed {
                            index, _ ->
                            index % 2 == 0
                        }
                        .take(6)
                        .forEach {

                            WeatherGlyph(
                                it.weather
                            )
                        }
                }
            }
        }
    }
}
```

In the actual MapLibre implementation, your marker model is already ready:

```kotlin
RouteWeatherPoint(
    latitude = ...,
    longitude = ...,
    weather = WeatherKind.RAIN
)
```

So the map can display:

```text
🌙──────🌙──────☁️──────🌧️────🌧️────🌤️
Zaječar                                  Trieste
```

---

# 8. Stats

```kotlin
@Composable
private fun TripStatistics(
    state: TripWeatherUiState
) {

    TripCard {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceAround
        ) {

            TripStat(
                Icons.Default.Route,
                "Distance",
                "${state.distanceKm} km"
            )

            TripStat(
                Icons.Default.Schedule,
                "Est. time",
                "${state.estimatedMinutes / 60} h ${
                    state.estimatedMinutes % 60
                } min"
            )

            TripStat(
                Icons.Default.DirectionsCar,
                "Avg. speed",
                "${state.speedKmh} km/h"
            )
        }
    }
}

@Composable
private fun TripStat(
    icon: ImageVector,
    title: String,
    value: String
) {

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {
        Icon(icon, null)

        Text(
            title,
            style =
                MaterialTheme.typography.labelMedium,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Text(
            value,
            fontWeight =
                FontWeight.Bold
        )
    }
}
```

---

# 9. Route weather summary

```kotlin
@Composable
private fun WeatherAlongRouteCard(
    state: TripWeatherUiState
) {

    Column {

        Text(
            "Weather along the route",
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        TripCard {

            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceAround
            ) {

                WeatherSummary(
                    WeatherKind.CLEAR_DAY,
                    "${state.points.first().temperatureC}°C",
                    "Start"
                )

                WeatherSummary(
                    WeatherKind.PARTLY_CLOUDY,
                    "${state.highestTemperature}°C",
                    "Daytime high"
                )

                WeatherSummary(
                    WeatherKind.RAIN,
                    "Rain",
                    "${state.rainHours} h"
                )

                WeatherSummary(
                    WeatherKind.WIND,
                    "Wind",
                    "up to ${state.strongestWindKmh} km/h"
                )
            }
        }
    }
}

@Composable
private fun WeatherSummary(
    kind: WeatherKind,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        WeatherGlyph(kind)

        Text(
            value,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            label,
            style =
                MaterialTheme.typography.labelSmall,
            color =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}
```

---

# 10. Highlights list

```kotlin
@Composable
private fun HighlightsCard(
    points: List<RouteWeatherPoint>
) {

    TripCard {

        Text(
            "Highlights",
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight =
                FontWeight.Bold
        )

        points
            .filterIndexed {
                index, _ ->
                index % 2 == 0
            }
            .forEach {

                HorizontalDivider()

                Row(
                    modifier =
                        Modifier.padding(
                            vertical = 10.dp
                        ),
                    verticalAlignment =
                        Alignment.CenterVertically
                ) {

                    WeatherGlyph(
                        it.weather
                    )

                    Spacer(
                        Modifier.width(12.dp)
                    )

                    Text(
                        it.time.format(
                            DateTimeFormatter.ofPattern(
                                "HH:mm"
                            )
                        ),
                        fontWeight =
                            FontWeight.Bold,
                        modifier =
                            Modifier.width(60.dp)
                    )

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            it.place,
                            fontWeight =
                                FontWeight.SemiBold
                        )

                        Text(
                            "${it.condition}, ${it.temperatureC}°C",
                            color =
                                MaterialTheme.colorScheme
                                    .onSurfaceVariant
                        )
                    }

                    Text(
                        "${it.distanceKm} km",
                        color =
                            MaterialTheme.colorScheme
                                .onSurfaceVariant
                    )
                }
            }
    }
}
```

---

# 11. Details / timeline screen

Corresponds to:

`trip_weather_details_timeline_screen.png`

```kotlin
enum class TripDetailsTab {
    TIMELINE,
    MAP,
    TABLE
}

@Composable
fun TripWeatherDetailsScreen(
    state: TripWeatherUiState,
    onBack: () -> Unit
) {

    var selectedTab by remember {
        mutableStateOf(
            TripDetailsTab.TIMELINE
        )
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
    ) {

        TripTopBar(
            title = "Trip details",
            subtitle =
                "${state.from} → ${state.to}",
            onBack = onBack
        )

        TabRow(
            selectedTabIndex =
                selectedTab.ordinal
        ) {

            TripDetailsTab.entries.forEach {
                Tab(
                    selected =
                        selectedTab == it,

                    onClick = {
                        selectedTab = it
                    },

                    text = {
                        Text(
                            when (it) {
                                TripDetailsTab.TIMELINE ->
                                    "Timeline"

                                TripDetailsTab.MAP ->
                                    "Map"

                                TripDetailsTab.TABLE ->
                                    "Table"
                            }
                        )
                    }
                )
            }
        }

        when (selectedTab) {

            TripDetailsTab.TIMELINE ->
                TripTimeline(
                    state
                )

            TripDetailsTab.MAP ->
                RouteMapCard(
                    state.points
                )

            TripDetailsTab.TABLE ->
                TripWeatherTable(
                    state.points
                )
        }
    }
}
```

---

# 12. Timeline

```kotlin
@Composable
private fun TripTimeline(
    state: TripWeatherUiState
) {

    LazyColumn(
        contentPadding =
            PaddingValues(16.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        item {

            DeparturePreviewBanner(
                state
            )
        }

        item {

            Text(
                state.departure.format(
                    DateTimeFormatter.ofPattern(
                        "EEE, dd MMM yyyy"
                    )
                ),
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight =
                    FontWeight.Bold
            )
        }

        items(state.points) {

            TimelineWeatherRow(it)
        }

        item {

            DestinationWeatherCard(
                state.points.last()
            )
        }

        item {

            Text(
                "Route warnings",
                style =
                    MaterialTheme.typography.titleLarge,
                fontWeight =
                    FontWeight.Bold
            )
        }

        items(state.warnings) {

            WarningCard(it)
        }
    }
}
```

### Timeline row

```kotlin
@Composable
private fun TimelineWeatherRow(
    point: RouteWeatherPoint
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Box(
                Modifier
                    .size(10.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    )
            )

            Box(
                Modifier
                    .width(2.dp)
                    .height(58.dp)
                    .background(
                        MaterialTheme.colorScheme
                            .primaryContainer
                    )
            )
        }

        Spacer(
            Modifier.width(12.dp)
        )

        WeatherGlyph(
            point.weather,
            Modifier.width(38.dp)
        )

        Spacer(
            Modifier.width(10.dp)
        )

        Text(
            point.time.format(
                DateTimeFormatter.ofPattern(
                    "HH:mm"
                )
            ),
            modifier =
                Modifier.width(55.dp),
            fontWeight =
                FontWeight.SemiBold
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                point.place,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                point.condition,
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        Column(
            horizontalAlignment =
                Alignment.End
        ) {

            Text(
                "${point.temperatureC}°C",
                fontWeight =
                    FontWeight.Bold,
                fontSize = 18.sp
            )

            Text(
                "${point.distanceKm} km",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}
```

---

# 13. Weather table tab

```kotlin
@Composable
private fun TripWeatherTable(
    points: List<RouteWeatherPoint>
) {

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(16.dp)
    ) {

        items(points) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            vertical = 10.dp
                        ),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    it.time.format(
                        DateTimeFormatter.ofPattern(
                            "HH:mm"
                        )
                    ),
                    modifier =
                        Modifier.width(55.dp)
                )

                WeatherGlyph(it.weather)

                Spacer(
                    Modifier.width(8.dp)
                )

                Text(
                    it.place,
                    modifier =
                        Modifier.weight(1f)
                )

                Text(
                    "${it.temperatureC}°"
                )

                Spacer(
                    Modifier.width(15.dp)
                )

                Text(
                    "${it.precipitationMm} mm"
                )
            }

            HorizontalDivider()
        }
    }
}
```

---

# 14. Destination card

```kotlin
@Composable
private fun DestinationWeatherCard(
    point: RouteWeatherPoint
) {

    TripCard {

        Text(
            "Weather at destination",
            style =
                MaterialTheme.typography.titleLarge,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            point.place,
            fontWeight =
                FontWeight.SemiBold
        )

        Spacer(
            Modifier.height(12.dp)
        )

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            WeatherGlyph(
                point.weather
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    "${point.temperatureC}°C",
                    fontSize = 30.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    point.condition
                )
            }

            Column {
                Text(
                    "Precipitation  ${point.precipitationMm} mm"
                )

                Text(
                    "Wind  ${point.windKmh} km/h"
                )
            }
        }
    }
}
```

---

# 15. Warning cards

```kotlin
@Composable
private fun WarningCard(
    warning: RouteWarning
) {

    TripCard {

        Row(
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            WeatherGlyph(
                warning.weather
            )

            Spacer(
                Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    warning.title,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    warning.description,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )

                Text(
                    warning.location,
                    style =
                        MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
```

## ViewModel behavior for the slider

The important bit for your app is to **not hit Open-Meteo/MET.no on every pixel movement**.

```kotlin
private val departureOffsets =
    MutableStateFlow(0)

init {

    viewModelScope.launch {

        departureOffsets
            .debounce(300)
            .distinctUntilChanged()
            .collectLatest {
                refreshRouteWeather()
            }
    }
}

fun setDepartureOffset(
    hours: Int
) {

    _uiState.update {
        it.copy(
            departureOffsetHours = hours
        )
    }

    departureOffsets.value =
        hours
}
```

That gives the behavior you wanted:

```text
Now ─────●──────── +24h ───── +48h ───── +72h
         ↑
       slide

              ↓

departure changes
              ↓
arrival times along route recalculated
              ↓
weather forecasts selected for new arrival times
              ↓
map icons update
timeline updates
warnings update
temperature/rain summary updates
```

For an even smoother UX, I would **download the next ~4 days of hourly weather for all sampled route points once**, then changing the slider can update the entire screen locally with no API request at all. That will make the 72-hour slider feel essentially instantaneous.
