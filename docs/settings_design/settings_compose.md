Yep — here’s a **Jetpack Compose / Material 3 implementation** of the settings screen we designed. It is state-driven, so you can connect it directly to your `SettingsViewModel`/DataStore later.

```kotlin
package com.hluweather.ui.settings

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------
// Models
// ---------------------------------------------------------

enum class WeatherProvider {
    OPEN_METEO,
    MET_NO
}

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class TemperatureUnit {
    CELSIUS,
    FAHRENHEIT
}

enum class WindUnit {
    KMH,
    MPH
}

enum class DistanceUnit {
    KM,
    MILES
}

enum class PrecipitationUnit {
    MM,
    INCH
}

data class WeatherLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class SettingsUiState(
    val provider: WeatherProvider = WeatherProvider.OPEN_METEO,

    val locations: List<WeatherLocation> = emptyList(),
    val selectedLocationId: String? = null,

    val trackMeEnabled: Boolean = false,

    val themeMode: ThemeMode = ThemeMode.SYSTEM,

    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
    val windUnit: WindUnit = WindUnit.KMH,
    val distanceUnit: DistanceUnit = DistanceUnit.KM,
    val precipitationUnit: PrecipitationUnit = PrecipitationUnit.MM,

    val weatherAlerts: Boolean = true,
    val dailySummary: Boolean = false,
    val tripAlerts: Boolean = false
)

// ---------------------------------------------------------
// Screen
// ---------------------------------------------------------

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,

    onProviderChange: (WeatherProvider) -> Unit,

    onTrackMeChange: (Boolean) -> Unit,

    onLocationSelect: (WeatherLocation) -> Unit,
    onLocationMenuClick: (WeatherLocation) -> Unit,
    onAddLocationClick: () -> Unit,

    onThemeChange: (ThemeMode) -> Unit,

    onTemperatureUnitChange: (TemperatureUnit) -> Unit,
    onWindUnitChange: (WindUnit) -> Unit,
    onDistanceUnitChange: (DistanceUnit) -> Unit,
    onPrecipitationUnitChange: (PrecipitationUnit) -> Unit,

    onWeatherAlertsChange: (Boolean) -> Unit,
    onDailySummaryChange: (Boolean) -> Unit,
    onTripAlertsChange: (Boolean) -> Unit,

    onClearCacheClick: () -> Unit,

    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // Header
        item {
            SettingsHeader(
                onBackClick = onBackClick
            )
        }

        // Provider
        item {
            SettingsCard {

                SectionHeader(
                    icon = Icons.Outlined.Cloud,
                    title = "Weather Provider",
                    subtitle = "Choose your data source"
                )

                HorizontalDivider()

                ProviderRow(
                    title = "Open-Meteo",
                    subtitle = "Free, no API key, high quality forecasts",
                    selected = state.provider == WeatherProvider.OPEN_METEO,
                    recommended = true,
                    onClick = {
                        onProviderChange(
                            WeatherProvider.OPEN_METEO
                        )
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp)
                )

                ProviderRow(
                    title = "MET.no",
                    subtitle = "Norwegian Meteorological Institute",
                    selected = state.provider == WeatherProvider.MET_NO,
                    onClick = {
                        onProviderChange(
                            WeatherProvider.MET_NO
                        )
                    }
                )
            }
        }

        // Locations
        item {
            SettingsCard {

                SectionHeader(
                    icon = Icons.Outlined.LocationOn,
                    title = "Locations",
                    subtitle = "Manage your locations and tracking"
                )

                HorizontalDivider()

                TrackMeRow(
                    enabled = state.trackMeEnabled,
                    onChange = onTrackMeChange
                )

                state.locations.forEach { location ->

                    HorizontalDivider(
                        modifier =
                            Modifier.padding(start = 56.dp)
                    )

                    LocationRow(
                        location = location,
                        selected =
                            !state.trackMeEnabled &&
                            state.selectedLocationId == location.id,

                        onClick = {
                            onLocationSelect(location)
                        },

                        onMenuClick = {
                            onLocationMenuClick(location)
                        }
                    )
                }

                HorizontalDivider()

                AddLocationRow(
                    onClick = onAddLocationClick
                )
            }
        }

        // Appearance
        item {
            SettingsCard {

                SectionHeader(
                    icon = Icons.Outlined.Palette,
                    title = "Appearance",
                    subtitle = "Choose how the app looks"
                )

                Spacer(
                    Modifier.height(12.dp)
                )

                ThemeSelector(
                    selected = state.themeMode,
                    onSelected = onThemeChange
                )
            }
        }

        // Units
        item {
            SettingsCard {

                SectionHeader(
                    icon = Icons.Outlined.Thermostat,
                    title = "Units",
                    subtitle = "Choose measurement units"
                )

                HorizontalDivider()

                UnitRow(
                    icon = Icons.Outlined.Thermostat,
                    title = "Temperature"
                ) {

                    TwoOptionSelector(
                        first = "°C",
                        second = "°F",
                        firstSelected =
                            state.temperatureUnit ==
                                TemperatureUnit.CELSIUS,
                        onFirst = {
                            onTemperatureUnitChange(
                                TemperatureUnit.CELSIUS
                            )
                        },
                        onSecond = {
                            onTemperatureUnitChange(
                                TemperatureUnit.FAHRENHEIT
                            )
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp)
                )

                UnitRow(
                    icon = Icons.Outlined.Air,
                    title = "Wind speed"
                ) {

                    TwoOptionSelector(
                        first = "km/h",
                        second = "mph",
                        firstSelected =
                            state.windUnit == WindUnit.KMH,
                        onFirst = {
                            onWindUnitChange(
                                WindUnit.KMH
                            )
                        },
                        onSecond = {
                            onWindUnitChange(
                                WindUnit.MPH
                            )
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp)
                )

                UnitRow(
                    icon = Icons.Outlined.Straighten,
                    title = "Distance"
                ) {

                    TwoOptionSelector(
                        first = "km",
                        second = "mi",
                        firstSelected =
                            state.distanceUnit ==
                                DistanceUnit.KM,
                        onFirst = {
                            onDistanceUnitChange(
                                DistanceUnit.KM
                            )
                        },
                        onSecond = {
                            onDistanceUnitChange(
                                DistanceUnit.MILES
                            )
                        }
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp)
                )

                UnitRow(
                    icon = Icons.Outlined.WaterDrop,
                    title = "Precipitation"
                ) {

                    TwoOptionSelector(
                        first = "mm",
                        second = "in",
                        firstSelected =
                            state.precipitationUnit ==
                                PrecipitationUnit.MM,
                        onFirst = {
                            onPrecipitationUnitChange(
                                PrecipitationUnit.MM
                            )
                        },
                        onSecond = {
                            onPrecipitationUnitChange(
                                PrecipitationUnit.INCH
                            )
                        }
                    )
                }
            }
        }

        // Notifications
        item {
            SettingsCard {

                SectionHeader(
                    icon = Icons.Outlined.Notifications,
                    title = "Notifications",
                    subtitle = "Weather alerts and updates"
                )

                HorizontalDivider()

                ToggleRow(
                    icon = Icons.Outlined.NotificationImportant,
                    title = "Weather alerts",
                    subtitle =
                        "Severe weather, rain, snow and more",
                    checked = state.weatherAlerts,
                    onCheckedChange =
                        onWeatherAlertsChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp)
                )

                ToggleRow(
                    icon = Icons.Outlined.WbTwilight,
                    title = "Daily summary",
                    subtitle = "Get a daily weather summary",
                    checked = state.dailySummary,
                    onCheckedChange =
                        onDailySummaryChange
                )

                HorizontalDivider(
                    modifier = Modifier.padding(start = 56.dp)
                )

                ToggleRow(
                    icon = Icons.Outlined.Route,
                    title = "Trip alerts",
                    subtitle =
                        "Weather alerts along planned routes",
                    checked = state.tripAlerts,
                    onCheckedChange =
                        onTripAlertsChange
                )
            }
        }

        // Cache
        item {
            SettingsCard {

                SectionHeader(
                    icon = Icons.Outlined.Storage,
                    title = "Data & Cache",
                    subtitle = "Offline data and storage"
                )

                HorizontalDivider()

                ClickableSettingsRow(
                    icon = Icons.Outlined.DeleteSweep,
                    title = "Clear cache",
                    subtitle = "Remove stored weather data",
                    onClick = onClearCacheClick
                )
            }
        }
    }
}

// ---------------------------------------------------------
// Header
// ---------------------------------------------------------

@Composable
private fun SettingsHeader(
    onBackClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 8.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        IconButton(
            onClick = onBackClick
        ) {
            Icon(
                imageVector =
                    Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }

        Spacer(
            Modifier.width(4.dp)
        )

        Column {

            Text(
                text = "Settings",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "Customize your weather experience",
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------
// Card
// ---------------------------------------------------------

@Composable
private fun SettingsCard(
    content: @Composable ColumnScope.() -> Unit
) {

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(22.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surface
            ),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = 1.dp
            )
    ) {

        Column(
            modifier =
                Modifier.fillMaxWidth(),
            content = content
        )
    }
}

// ---------------------------------------------------------
// Section title
// ---------------------------------------------------------

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    subtitle: String
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 14.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme.primary,
            modifier =
                Modifier.size(27.dp)
        )

        Spacer(
            Modifier.width(14.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                text = subtitle,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------
// Provider
// ---------------------------------------------------------

@Composable
private fun ProviderRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    recommended: Boolean = false,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = 18.dp,
                    vertical = 14.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        RadioButton(
            selected = selected,
            onClick = onClick
        )

        Spacer(
            Modifier.width(10.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight =
                        FontWeight.SemiBold
                )

                if (recommended) {

                    Spacer(
                        Modifier.width(8.dp)
                    )

                    Surface(
                        color =
                            MaterialTheme.colorScheme
                                .primaryContainer,
                        shape =
                            RoundedCornerShape(8.dp)
                    ) {

                        Text(
                            text = "Recommended",
                            modifier =
                                Modifier.padding(
                                    horizontal = 8.dp,
                                    vertical = 3.dp
                                ),
                            color =
                                MaterialTheme.colorScheme
                                    .onPrimaryContainer,
                            style =
                                MaterialTheme.typography
                                    .labelMedium
                        )
                    }
                }
            }

            Text(
                text = subtitle,
                style =
                    MaterialTheme.typography.bodyMedium,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        Icon(
            imageVector =
                Icons.Outlined.Info,
            contentDescription =
                "Information",
            tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------
// Track me
// ---------------------------------------------------------

@Composable
private fun TrackMeRow(
    enabled: Boolean,
    onChange: (Boolean) -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 12.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                Icons.Outlined.MyLocation,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme.primary
        )

        Spacer(
            Modifier.width(14.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = "Track me",
                fontWeight =
                    FontWeight.SemiBold,
                style =
                    MaterialTheme.typography.titleMedium
            )

            Text(
                text =
                    "Always use my current GPS location",
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        Switch(
            checked = enabled,
            onCheckedChange = onChange
        )
    }
}

// ---------------------------------------------------------
// Location
// ---------------------------------------------------------

@Composable
private fun LocationRow(
    location: WeatherLocation,
    selected: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    start = 18.dp,
                    end = 6.dp,
                    top = 11.dp,
                    bottom = 11.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector =
                Icons.Outlined.LocationOn,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            Modifier.width(14.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = location.name,
                style =
                    MaterialTheme.typography.titleMedium,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text =
                    "%.4f, %.4f".format(
                        location.latitude,
                        location.longitude
                    ),
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        RadioButton(
            selected = selected,
            onClick = onClick
        )

        IconButton(
            onClick = onMenuClick
        ) {

            Icon(
                imageVector =
                    Icons.Default.MoreVert,
                contentDescription =
                    "Location options"
            )
        }
    }
}

// ---------------------------------------------------------
// Add location
// ---------------------------------------------------------

@Composable
private fun AddLocationRow(
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(16.dp),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Surface(
            shape =
                RoundedCornerShape(14.dp),
            color =
                MaterialTheme.colorScheme
                    .primaryContainer
        ) {

            Icon(
                imageVector =
                    Icons.Default.Add,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier.padding(10.dp)
            )
        }

        Spacer(
            Modifier.width(14.dp)
        )

        Text(
            text = "Add Location",
            style =
                MaterialTheme.typography.titleMedium,
            color =
                MaterialTheme.colorScheme.primary,
            fontWeight =
                FontWeight.SemiBold,
            modifier =
                Modifier.weight(1f)
        )

        Icon(
            imageVector =
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null
        )
    }
}

// ---------------------------------------------------------
// Appearance
// ---------------------------------------------------------

@Composable
private fun ThemeSelector(
    selected: ThemeMode,
    onSelected: (ThemeMode) -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 14.dp,
                    vertical = 4.dp
                ),
        horizontalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {

        ThemeButton(
            title = "System",
            icon =
                Icons.Outlined.DesktopWindows,
            selected =
                selected == ThemeMode.SYSTEM,
            onClick = {
                onSelected(ThemeMode.SYSTEM)
            },
            modifier =
                Modifier.weight(1f)
        )

        ThemeButton(
            title = "Light",
            icon =
                Icons.Outlined.LightMode,
            selected =
                selected == ThemeMode.LIGHT,
            onClick = {
                onSelected(ThemeMode.LIGHT)
            },
            modifier =
                Modifier.weight(1f)
        )

        ThemeButton(
            title = "Dark",
            icon =
                Icons.Outlined.DarkMode,
            selected =
                selected == ThemeMode.DARK,
            onClick = {
                onSelected(ThemeMode.DARK)
            },
            modifier =
                Modifier.weight(1f)
        )
    }

    Spacer(
        Modifier.height(10.dp)
    )
}

@Composable
private fun ThemeButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {

    val background =
        if (selected)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surfaceVariant

    val border =
        if (selected)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.outlineVariant

    Surface(
        onClick = onClick,
        modifier = modifier,
        color = background,
        shape =
            RoundedCornerShape(26.dp),
        border =
            BorderStroke(
                1.dp,
                border
            )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 12.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.Center
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier =
                    Modifier.size(19.dp)
            )

            Spacer(
                Modifier.width(7.dp)
            )

            Text(
                text = title,
                fontWeight =
                    if (selected)
                        FontWeight.SemiBold
                    else
                        FontWeight.Normal
            )
        }
    }
}

// ---------------------------------------------------------
// Units
// ---------------------------------------------------------

@Composable
private fun UnitRow(
    icon: ImageVector,
    title: String,
    selector: @Composable () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 9.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant,
            modifier =
                Modifier.size(21.dp)
        )

        Spacer(
            Modifier.width(16.dp)
        )

        Text(
            text = title,
            modifier =
                Modifier.weight(1f),
            style =
                MaterialTheme.typography.bodyLarge
        )

        selector()
    }
}

@Composable
private fun TwoOptionSelector(
    first: String,
    second: String,
    firstSelected: Boolean,
    onFirst: () -> Unit,
    onSecond: () -> Unit
) {

    Surface(
        shape =
            RoundedCornerShape(22.dp),
        color =
            MaterialTheme.colorScheme
                .surfaceVariant
    ) {

        Row {

            SelectorOption(
                text = first,
                selected =
                    firstSelected,
                onClick = onFirst
            )

            SelectorOption(
                text = second,
                selected =
                    !firstSelected,
                onClick = onSecond
            )
        }
    }
}

@Composable
private fun SelectorOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {

    Surface(
        onClick = onClick,
        shape =
            RoundedCornerShape(22.dp),
        color =
            if (selected)
                MaterialTheme.colorScheme.primary
            else
                Color.Transparent
    ) {

        Text(
            text = text,
            modifier =
                Modifier.padding(
                    horizontal = 18.dp,
                    vertical = 8.dp
                ),
            color =
                if (selected)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme
                        .onSurfaceVariant,
            fontWeight =
                if (selected)
                    FontWeight.SemiBold
                else
                    FontWeight.Normal
        )
    }
}

// ---------------------------------------------------------
// Notifications
// ---------------------------------------------------------

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 18.dp,
                    vertical = 10.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            Modifier.width(14.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = title,
                style =
                    MaterialTheme.typography.titleSmall,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text = subtitle,
                maxLines = 1,
                overflow =
                    TextOverflow.Ellipsis,
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        Switch(
            checked = checked,
            onCheckedChange =
                onCheckedChange
        )
    }
}

// ---------------------------------------------------------
// Generic clickable row
// ---------------------------------------------------------

@Composable
private fun ClickableSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(
                    horizontal = 18.dp,
                    vertical = 13.dp
                ),
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        Icon(
            imageVector = icon,
            contentDescription = null
        )

        Spacer(
            Modifier.width(14.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Text(
                text = title,
                fontWeight =
                    FontWeight.SemiBold
            )

            Text(
                text = subtitle,
                style =
                    MaterialTheme.typography.bodySmall,
                color =
                    MaterialTheme.colorScheme
                        .onSurfaceVariant
            )
        }

        Icon(
            imageVector =
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint =
                MaterialTheme.colorScheme
                    .onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------
// Preview
// ---------------------------------------------------------

private val PreviewState =
    SettingsUiState(
        provider =
            WeatherProvider.OPEN_METEO,

        trackMeEnabled = true,

        locations =
            listOf(
                WeatherLocation(
                    id = "svilajnac",
                    name = "Svilajnac",
                    latitude = 44.2380,
                    longitude = 21.1970
                ),
                WeatherLocation(
                    id = "belgrade",
                    name = "Belgrade",
                    latitude = 44.8176,
                    longitude = 20.4633
                ),
                WeatherLocation(
                    id = "trieste",
                    name = "Trieste",
                    latitude = 45.6495,
                    longitude = 13.7768
                )
            ),

        selectedLocationId =
            "svilajnac"
    )

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun SettingsPreview() {

    MaterialTheme {

        SettingsScreen(
            state = PreviewState,

            onBackClick = {},

            onProviderChange = {},

            onTrackMeChange = {},

            onLocationSelect = {},
            onLocationMenuClick = {},
            onAddLocationClick = {},

            onThemeChange = {},

            onTemperatureUnitChange = {},
            onWindUnitChange = {},
            onDistanceUnitChange = {},
            onPrecipitationUnitChange = {},

            onWeatherAlertsChange = {},
            onDailySummaryChange = {},
            onTripAlertsChange = {},

            onClearCacheClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode =
        Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SettingsDarkPreview() {

    MaterialTheme(
        colorScheme =
            darkColorScheme()
    ) {

        SettingsScreen(
            state = PreviewState,

            onBackClick = {},

            onProviderChange = {},

            onTrackMeChange = {},

            onLocationSelect = {},
            onLocationMenuClick = {},
            onAddLocationClick = {},

            onThemeChange = {},

            onTemperatureUnitChange = {},
            onWindUnitChange = {},
            onDistanceUnitChange = {},
            onPrecipitationUnitChange = {},

            onWeatherAlertsChange = {},
            onDailySummaryChange = {},
            onTripAlertsChange = {},

            onClearCacheClick = {}
        )
    }
}
```

You'll want Material Icons Extended:

```kotlin
dependencies {
    implementation(
        "androidx.compose.material:material-icons-extended"
    )
}
```

### Connect it to your existing screen

Your `SettingsViewModel` should expose something like:

```kotlin
val uiState: StateFlow<SettingsUiState>
```

Then:

```kotlin
@Composable
fun SettingsRoute(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onAddLocation: () -> Unit
) {

    val state by
        viewModel.uiState.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,

        onBackClick = onBack,

        onProviderChange =
            viewModel::setWeatherProvider,

        onTrackMeChange =
            viewModel::setTrackMe,

        onLocationSelect =
            viewModel::selectLocation,

        onLocationMenuClick = {
            viewModel.openLocationOptions(it)
        },

        onAddLocationClick =
            onAddLocation,

        onThemeChange =
            viewModel::setTheme,

        onTemperatureUnitChange =
            viewModel::setTemperatureUnit,

        onWindUnitChange =
            viewModel::setWindUnit,

        onDistanceUnitChange =
            viewModel::setDistanceUnit,

        onPrecipitationUnitChange =
            viewModel::setPrecipitationUnit,

        onWeatherAlertsChange =
            viewModel::setWeatherAlerts,

        onDailySummaryChange =
            viewModel::setDailySummary,

        onTripAlertsChange =
            viewModel::setTripAlerts,

        onClearCacheClick =
            viewModel::clearWeatherCache
    )
}
```

One behavioral change I recommend: when **Track me** is enabled, don't show one of the saved-location radio buttons as active. `Track me` itself becomes the active location source. When disabled, the previous saved location becomes active again.

The next screen I'd implement is **Add/Edit Location with MapLibre + OpenFreeMap**, because then the Settings screen becomes fully functional: GPS → map centered on current position → move map/marker → name location → save.
