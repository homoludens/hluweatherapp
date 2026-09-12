package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (compact) Modifier else Modifier.testTag("weather_hero"))
            .height(if (compact) 96.dp else 176.dp)
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
                .statusBarsPadding()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = if (compact) 0.dp else 8.dp
                )
        ) {
            if (!compact) {
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
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .semantics {
                                contentDescription = "Settings"
                            }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = colors.heroText
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))
            }

            NavigationTabs(
                selected = selected,
                onSelected = onSelected,
                compact = compact
            )
        }
    }
}

@Composable
private fun NavigationTabs(
    selected: ForecastMode,
    onSelected: (ForecastMode) -> Unit,
    compact: Boolean
) {
    val items = listOf(
        Triple(ForecastMode.HOURLY, "Hourly", Icons.Outlined.Schedule),
        Triple(ForecastMode.DAILY, "Daily", Icons.Outlined.BarChart),
        Triple(ForecastMode.MAP, "Map", Icons.Outlined.LocationOn)
    )

    Row(
        modifier = Modifier.fillMaxWidth()
    ) {
        items.forEach { (mode, title, icon) ->
            WeatherNavButton(
                text = title,
                icon = icon,
                selected = selected == mode,
                compact = compact,
                onClick = { onSelected(mode) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun WeatherNavButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Surface(
        modifier = Modifier
            .then(modifier)
            .defaultMinSize(minHeight = 48.dp)
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        shape = RoundedCornerShape(28.dp),
        color = if (selected) colors.navSelected else Color.Transparent,
        contentColor = if (selected) colors.navSelectedText else colors.heroSecondaryText
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 16.dp else 4.dp,
                vertical = if (compact) 8.dp else 10.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 2.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (compact) text else null,
                tint = if (selected) {
                    colors.navSelectedText
                } else {
                    colors.heroSecondaryText
                }
            )
            if (!compact) {
                Text(
                    text = text,
                    fontSize = if (compact) 18.sp else 14.sp,
                    color = if (selected) {
                        colors.navSelectedText
                    } else {
                        colors.heroSecondaryText
                    }
                )
            }
        }
    }
}
