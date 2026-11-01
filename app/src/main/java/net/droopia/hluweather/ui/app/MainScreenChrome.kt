package net.droopia.hluweather.ui.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.droopia.hluweather.navigation.MainDestination
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun MainScreenHeader(
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(colors.heroTop, colors.heroBottom)))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .testTag("main_header")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 16.dp)
                .testTag("main_header_content"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HluWeatherApp",
                modifier = Modifier.weight(1f).testTag("main_header_title"),
                color = colors.heroText,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "Settings" }
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = colors.heroText
                )
            }
        }
    }
}

@Composable
fun MainBottomNavigationBar(
    selected: MainDestination,
    onDestinationSelected: (MainDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    NavigationBar(
        modifier = modifier.testTag("main_bottom_navigation"),
        containerColor = colorScheme.surface,
        contentColor = colorScheme.onSurface,
        tonalElevation = 0.dp
    ) {
        mainDestinations.forEach { item ->
            NavigationBarItem(
                selected = selected == item.destination,
                onClick = { onDestinationSelected(item.destination) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colorScheme.onPrimaryContainer,
                    selectedTextColor = colorScheme.onSurface,
                    indicatorColor = colorScheme.primaryContainer,
                    unselectedIconColor = colorScheme.onSurfaceVariant,
                    unselectedTextColor = colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.testTag("main_destination_${item.destination.name.lowercase()}")
            )
        }
    }
}

private data class MainDestinationItem(
    val destination: MainDestination,
    val label: String,
    val icon: ImageVector
)

private val mainDestinations = listOf(
    MainDestinationItem(MainDestination.HOURLY, "Hourly", Icons.Outlined.Schedule),
    MainDestinationItem(MainDestination.DAILY, "Daily", Icons.Outlined.BarChart),
    MainDestinationItem(MainDestination.MAP, "Map", Icons.Outlined.Map),
    MainDestinationItem(MainDestination.ROUTE, "Route", Icons.Outlined.DirectionsCar)
)
