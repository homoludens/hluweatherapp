package net.droopia.hluweather.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBackClick: () -> Unit,
    onProviderChange: (WeatherProvider) -> Unit,
    onTrackMeChange: (Boolean) -> Unit,
    onLocationSelect: (WeatherLocation) -> Unit,
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
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
