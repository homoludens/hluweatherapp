package net.droopia.hluweather.ui.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import net.droopia.hluweather.data.model.GeoPoint
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.ThemeMode
import net.droopia.hluweather.navigation.HluNavHost
import net.droopia.hluweather.notifications.AndroidNotificationPermissionChecker
import net.droopia.hluweather.ui.settings.SettingsViewModel
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.WeatherViewModel

@Composable
fun HluWeatherApp(
    weatherViewModel: WeatherViewModel? = null,
    weatherMapContent: (@Composable (WeatherLocation, List<WeatherLocation>, String?, Boolean, () -> Unit) -> Unit)? = null,
    locationPickerMapContent: (@Composable (GeoPoint, Boolean, (GeoPoint) -> Unit) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var notificationsPermissionGranted by remember {
        mutableStateOf(AndroidNotificationPermissionChecker.isGranted(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notificationsPermissionGranted = AndroidNotificationPermissionChecker.isGranted(context) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsPermissionGranted = AndroidNotificationPermissionChecker.isGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
    val state = settingsViewModel.state.collectAsStateWithLifecycle().value
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (state.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    HluWeatherTheme(darkTheme = darkTheme) {
        HluNavHost(
            settingsViewModel = settingsViewModel,
            weatherViewModel = weatherViewModel,
            darkTheme = darkTheme,
            weatherMapContent = weatherMapContent,
            locationPickerMapContent = locationPickerMapContent,
            notificationsPermissionGranted = notificationsPermissionGranted,
            onNotificationPermissionRequest = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsPermissionGranted) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            },
            onOpenNotificationSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                )
            }
        )
    }
}
