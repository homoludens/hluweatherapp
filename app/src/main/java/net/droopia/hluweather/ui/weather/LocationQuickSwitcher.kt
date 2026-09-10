package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.data.model.WeatherLocation

@Composable
fun LocationQuickSwitcher(
    locations: List<WeatherLocation>,
    selectedLocationId: String?,
    trackMeSelected: Boolean,
    onLocationSelected: (WeatherLocation) -> Unit,
    onTrackMeClick: () -> Unit,
    onAddLocationClick: () -> Unit,
    onManageLocationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "Locations",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )

        QuickSwitcherRow(
            title = "Track Me",
            selected = trackMeSelected,
            icon = Icons.Outlined.MyLocation,
            onClick = onTrackMeClick
        )

        locations.forEach { location ->
            QuickSwitcherRow(
                title = location.name,
                selected = location.id == selectedLocationId,
                icon = Icons.Outlined.LocationOn,
                onClick = { onLocationSelected(location) }
            )
        }

        TextButton(
            onClick = onAddLocationClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Add location")
        }
        TextButton(
            onClick = onManageLocationsClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Manage locations")
        }
    }
}

@Composable
private fun QuickSwitcherRow(
    title: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "Select location, $title"
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(
            text = title,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.bodyLarge
        )
        RadioButton(selected = selected, onClick = null)
    }
}
