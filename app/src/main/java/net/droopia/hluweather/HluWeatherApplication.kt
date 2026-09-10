package net.droopia.hluweather

import android.app.Application
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import net.droopia.hluweather.data.device.AndroidDeviceLocationSource
import net.droopia.hluweather.data.device.DeviceLocationSource
import net.droopia.hluweather.data.network.KtorMetNoApi
import net.droopia.hluweather.data.network.KtorNominatimApi
import net.droopia.hluweather.data.network.KtorOpenMeteoApi
import net.droopia.hluweather.data.repository.NominatimReverseGeocoder
import net.droopia.hluweather.data.repository.LocationRepository
import net.droopia.hluweather.data.repository.MetNoWeatherRepository
import net.droopia.hluweather.data.repository.OpenMeteoWeatherRepository
import net.droopia.hluweather.data.repository.ReverseGeocoder
import net.droopia.hluweather.data.repository.WeatherRepository
import net.droopia.hluweather.data.repository.locationRepository
import net.droopia.hluweather.ui.settings.SettingsRepository
import net.droopia.hluweather.ui.settings.settingsRepository

class HluWeatherApplication : Application() {

    private val httpClient: HttpClient by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            expectSuccess = false
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
            }
        }
    }

    val weatherRepository: WeatherRepository by lazy {
        OpenMeteoWeatherRepository(KtorOpenMeteoApi(httpClient))
    }

    val metNoWeatherRepository: WeatherRepository by lazy {
        MetNoWeatherRepository(KtorMetNoApi(httpClient))
    }

    val settingsRepository: SettingsRepository by lazy {
        settingsRepository(this)
    }

    val locationRepository: LocationRepository by lazy {
        locationRepository(this)
    }

    val deviceLocationSource: DeviceLocationSource by lazy {
        AndroidDeviceLocationSource(this)
    }

    val reverseGeocoder: ReverseGeocoder by lazy {
        NominatimReverseGeocoder(KtorNominatimApi(httpClient))
    }
}
