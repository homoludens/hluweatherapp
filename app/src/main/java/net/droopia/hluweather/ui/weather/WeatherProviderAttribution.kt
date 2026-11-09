package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherProvider

private const val VISUAL_CROSSING_URL = "https://www.visualcrossing.com/"
private const val BRIGHT_SKY_URL = "https://brightsky.dev/"
private const val DWD_OPEN_DATA_URL = "https://www.dwd.de/EN/ourservices/opendata/opendata.html"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WeatherProviderAttribution(
    forecast: WeatherForecast,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    // The forecast carries its source identity, including for stale cached content.
    when (forecast.provider) {
        WeatherProvider.VISUAL_CROSSING -> FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AttributionLink(
                text = "Weather data provided by Visual Crossing Weather",
                onClickLabel = "Open Visual Crossing Weather source",
                testTag = "weather_provider_attribution_visual_crossing",
                onClick = { uriHandler.openUri(VISUAL_CROSSING_URL) }
            )
        }

        WeatherProvider.BRIGHT_SKY -> FlowRow(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            AttributionLink(
                text = "Weather data via Bright Sky",
                onClickLabel = "Open Bright Sky weather source",
                testTag = "weather_provider_attribution_bright_sky",
                onClick = { uriHandler.openUri(BRIGHT_SKY_URL) }
            )
            AttributionLink(
                text = "Source: Deutscher Wetterdienst",
                onClickLabel = "Open Deutscher Wetterdienst Open Data",
                testTag = "weather_provider_attribution_dwd",
                onClick = { uriHandler.openUri(DWD_OPEN_DATA_URL) }
            )
        }

        WeatherProvider.OPEN_METEO,
        WeatherProvider.MET_NO -> Unit
    }
}

@Composable
private fun AttributionLink(
    text: String,
    onClickLabel: String,
    testTag: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        modifier = Modifier
            .clickable(
                onClickLabel = onClickLabel,
                role = Role.Button,
                onClick = onClick
            )
            .testTag(testTag),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelSmall.copy(
            textDecoration = TextDecoration.Underline
        )
    )
}
