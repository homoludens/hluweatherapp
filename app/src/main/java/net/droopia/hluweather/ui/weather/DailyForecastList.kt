package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.model.label
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureText
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun DailyForecastList(
    forecast: WeatherForecast,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val displayZone = ZoneId.of(forecast.timezone)
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("daily_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(forecast.daily.size) { index ->
            DailyRow(
                day = forecast.daily[index],
                displayZone = displayZone,
                onClick = { onDaySelected(index) }
            )
        }
    }
}

@Composable
private fun DailyRow(
    day: DayForecast,
    displayZone: ZoneId,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = LocalHluColors.current.weatherCard
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = day.date.dayText(displayZone),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = day.condition.label(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HluWeatherIcon(
                condition = day.condition,
                isDay = true,
                modifier = Modifier.width(28.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = "${day.temperatureMin.temperatureText()} – ${day.temperatureMax.temperatureText()}",
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = day.precipitation.precipitationText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
