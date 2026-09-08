package net.droopia.hluweather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun MapPlaceholder(
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.tableRow)
            .testTag("map_placeholder"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Map preview",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
