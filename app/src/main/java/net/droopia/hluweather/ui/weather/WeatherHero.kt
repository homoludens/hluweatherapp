package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun WeatherHero(
    selected: ForecastMode,
    onSelected: (ForecastMode) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Box(
        modifier = modifier
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
        Box(
            modifier = Modifier
                .size(82.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-65).dp, y = 65.dp)
                .clip(CircleShape)
                .background(colors.moon)
        )

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

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colors.heroText
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            NavigationTabs(
                selected = selected,
                onSelected = onSelected
            )
        }
    }
}

@Composable
private fun NavigationTabs(
    selected: ForecastMode,
    onSelected: (ForecastMode) -> Unit
) {
    val items = listOf(
        Triple(ForecastMode.HOURLY, "Hourly", Icons.Outlined.Schedule),
        Triple(ForecastMode.DAILY, "Daily", Icons.Outlined.BarChart),
        Triple(ForecastMode.MAP, "Map", Icons.Outlined.LocationOn)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { (mode, title, icon) ->
            WeatherNavButton(
                text = title,
                icon = icon,
                selected = selected == mode,
                onClick = { onSelected(mode) }
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
        color = if (selected) colors.navSelected else Color.Transparent
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
