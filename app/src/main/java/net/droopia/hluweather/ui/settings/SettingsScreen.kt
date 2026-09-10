package net.droopia.hluweather.ui.settings

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationImportant
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Straighten
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider
import net.droopia.hluweather.R
import kotlinx.datetime.LocalTime

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onProviderChange: (WeatherProvider) -> Unit,
    onProviderInfoClick: (WeatherProvider) -> Unit,
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
    onDailySummaryTimeChange: (LocalTime) -> Unit,
    onClearCacheClick: () -> Unit,
    notificationsPermissionGranted: Boolean = true,
    onOpenNotificationSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .background(MaterialTheme.colorScheme.background)
            .testTag("settings_scroll"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 32.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SettingsHeader(onBackClick)
        }

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
                    tag = "settings_provider_open_meteo",
                    recommended = true,
                    onInfoClick = { onProviderInfoClick(WeatherProvider.OPEN_METEO) },
                    onClick = { onProviderChange(WeatherProvider.OPEN_METEO) }
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                ProviderRow(
                    title = "MET.no",
                    subtitle = "Norwegian Meteorological Institute",
                    selected = state.provider == WeatherProvider.MET_NO,
                    tag = "settings_provider_met_no",
                    onInfoClick = { onProviderInfoClick(WeatherProvider.MET_NO) },
                    onClick = { onProviderChange(WeatherProvider.MET_NO) }
                )
            }
        }

        item {
            SettingsCard {
                SectionHeader(
                    icon = Icons.Outlined.LocationOn,
                    title = "Locations",
                    subtitle = "Manage your locations and tracking"
                )
                HorizontalDivider()
                TrackMeRow(state.trackMeEnabled, onTrackMeChange)
                state.locations.forEach { location ->
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    LocationRow(
                        location = location,
                        selected = !state.trackMeEnabled && state.selectedLocationId == location.id,
                        onClick = { onLocationSelect(location) },
                        onMenuClick = { onLocationMenuClick(location) }
                    )
                }
                HorizontalDivider()
                AddLocationRow(onAddLocationClick)
            }
        }

        item {
            SettingsCard {
                SectionHeader(
                    icon = Icons.Outlined.Palette,
                    title = "Appearance",
                    subtitle = "Choose how the app looks"
                )
                Spacer(modifier = Modifier.size(12.dp))
                ThemeSelector(state.themeMode, onThemeChange)
                Spacer(modifier = Modifier.size(10.dp))
            }
        }

        item {
            SettingsCard {
                SectionHeader(
                    icon = Icons.Outlined.Thermostat,
                    title = "Units",
                    subtitle = "Choose measurement units"
                )
                HorizontalDivider()
                UnitRow(Icons.Outlined.Thermostat, "Temperature") {
                    TwoOptionSelector(
                        first = "°C",
                        second = "°F",
                        firstSelected = state.temperatureUnit == TemperatureUnit.CELSIUS,
                        firstTag = "settings_temperature_celsius",
                        secondTag = "settings_temperature_fahrenheit",
                        onFirst = { onTemperatureUnitChange(TemperatureUnit.CELSIUS) },
                        onSecond = { onTemperatureUnitChange(TemperatureUnit.FAHRENHEIT) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                UnitRow(Icons.Outlined.Air, "Wind speed") {
                    TwoOptionSelector(
                        first = "km/h",
                        second = "mph",
                        firstSelected = state.windUnit == WindUnit.KMH,
                        firstTag = "settings_wind_kmh",
                        secondTag = "settings_wind_mph",
                        onFirst = { onWindUnitChange(WindUnit.KMH) },
                        onSecond = { onWindUnitChange(WindUnit.MPH) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                UnitRow(Icons.Outlined.Straighten, "Distance") {
                    TwoOptionSelector(
                        first = "km",
                        second = "mi",
                        firstSelected = state.distanceUnit == DistanceUnit.KM,
                        firstTag = "settings_distance_km",
                        secondTag = "settings_distance_miles",
                        onFirst = { onDistanceUnitChange(DistanceUnit.KM) },
                        onSecond = { onDistanceUnitChange(DistanceUnit.MILES) }
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                UnitRow(Icons.Outlined.WaterDrop, "Precipitation") {
                    TwoOptionSelector(
                        first = "mm",
                        second = "in",
                        firstSelected = state.precipitationUnit == PrecipitationUnit.MM,
                        firstTag = "settings_precipitation_mm",
                        secondTag = "settings_precipitation_inch",
                        onFirst = { onPrecipitationUnitChange(PrecipitationUnit.MM) },
                        onSecond = { onPrecipitationUnitChange(PrecipitationUnit.INCH) }
                    )
                }
            }
        }

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
                    subtitle = "Severe weather, rain, snow and more",
                    checked = state.weatherAlerts,
                    tag = "settings_weather_alerts",
                    onCheckedChange = onWeatherAlertsChange
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                ToggleRow(
                    icon = Icons.Outlined.WbTwilight,
                    title = "Daily summary",
                    subtitle = "Get a daily weather summary",
                    checked = state.dailySummary,
                    tag = "settings_daily_summary",
                    onCheckedChange = onDailySummaryChange
                )
                HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                SummaryTimeRow(
                    time = state.dailySummaryTime,
                    onTimeChange = onDailySummaryTimeChange
                )
                if ((state.weatherAlerts || state.dailySummary) && !notificationsPermissionGranted) {
                    HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
                    Text(
                        text = stringResource(R.string.notifications_blocked_title),
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    ClickableSettingsRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.notifications_open_settings),
                        subtitle = stringResource(R.string.notifications_blocked_summary),
                        tag = "settings_notification_permission",
                        onClick = onOpenNotificationSettings
                    )
                }
            }
        }

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

@Composable
private fun SettingsHeader(onBackClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text("Settings", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Customize your weather experience",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content
    )
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, subtitle: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(27.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ProviderRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    tag: String,
    recommended: Boolean = false,
    onInfoClick: () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (recommended) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "Recommended",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onInfoClick) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Information about $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrackMeRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Track me", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
            Text(
                "Always use my current GPS location",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onChange,
            modifier = Modifier.testTag("settings_track_me")
        )
    }
}

@Composable
private fun LocationRow(
    location: WeatherLocation,
    selected: Boolean,
    onClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag("settings_location_${location.id}")
            .padding(start = 18.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(location.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "%.4f, %.4f".format(location.latitude, location.longitude),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(selected = selected, onClick = null)
        IconButton(onClick = onMenuClick) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Location options ${location.name}")
        }
    }
}

@Composable
private fun AddLocationRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(10.dp)
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            "Add Location",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
private fun ThemeSelector(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ThemeButton("System", Icons.Outlined.DesktopWindows, selected == ThemeMode.SYSTEM, "settings_theme_system", { onSelected(ThemeMode.SYSTEM) }, Modifier.weight(1f))
        ThemeButton("Light", Icons.Outlined.LightMode, selected == ThemeMode.LIGHT, "settings_theme_light", { onSelected(ThemeMode.LIGHT) }, Modifier.weight(1f))
        ThemeButton("Dark", Icons.Outlined.DarkMode, selected == ThemeMode.DARK, "settings_theme_dark", { onSelected(ThemeMode.DARK) }, Modifier.weight(1f))
    }
}

@Composable
private fun ThemeButton(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(26.dp),
        border = BorderStroke(
            1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(modifier = Modifier.width(7.dp))
            Text(title, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun UnitRow(icon: ImageVector, title: String, selector: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        selector()
    }
}

@Composable
private fun TwoOptionSelector(
    first: String,
    second: String,
    firstSelected: Boolean,
    firstTag: String,
    secondTag: String,
    onFirst: () -> Unit,
    onSecond: () -> Unit
) {
    Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row {
            SelectorOption(first, firstSelected, firstTag, onFirst)
            SelectorOption(second, !firstSelected, secondTag, onSecond)
        }
    }
}

@Composable
private fun SelectorOption(text: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .testTag(tag),
        shape = RoundedCornerShape(22.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked, onCheckedChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun SummaryTimeRow(time: LocalTime, onTimeChange: (LocalTime) -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = "Set daily summary time",
                role = Role.Button
            ) {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onTimeChange(LocalTime(hour, minute)) },
                    time.hour,
                    time.minute,
                    true
                ).show()
            }
            .testTag("settings_daily_summary_time")
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Outlined.WbTwilight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Daily summary time", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Best effort; delivery may be delayed by Android.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            formatSummaryTime(time),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatSummaryTime(time: LocalTime): String =
    "${time.hour.toString().padStart(2, '0')}:${time.minute.toString().padStart(2, '0')}"

@Composable
private fun ClickableSettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .then(if (tag == null) Modifier else Modifier.testTag(tag))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
