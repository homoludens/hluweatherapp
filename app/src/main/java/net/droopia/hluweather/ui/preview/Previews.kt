package net.droopia.hluweather.ui.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.CurrentWeatherCard
import net.droopia.hluweather.ui.weather.DailyForecastList
import net.droopia.hluweather.ui.weather.HourlyForecast
import net.droopia.hluweather.ui.weather.WeatherHero

private val previewForecast = buildMockForecast(
    location = Svilajnac,
    baseTime = Instant.fromEpochSeconds(0L)
)

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun WeatherHeroLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        WeatherHero(
            selected = ForecastMode.HOURLY,
            onSelected = {},
            onSettingsClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun WeatherHeroDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        WeatherHero(
            selected = ForecastMode.HOURLY,
            onSelected = {},
            onSettingsClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun CurrentWeatherCardLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        CurrentWeatherCard(
            location = previewForecast.location,
            forecast = previewForecast
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CurrentWeatherCardDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        CurrentWeatherCard(
            location = previewForecast.location,
            forecast = previewForecast
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun HourlyForecastLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        HourlyForecast(
            forecast = previewForecast,
            selectedDayIndex = 0,
            onDaySelected = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HourlyForecastDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        HourlyForecast(
            forecast = previewForecast,
            selectedDayIndex = 0,
            onDaySelected = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun DailyForecastLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        DailyForecastList(
            forecast = previewForecast,
            onDaySelected = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DailyForecastDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        DailyForecastList(
            forecast = previewForecast,
            onDaySelected = {}
        )
    }
}
