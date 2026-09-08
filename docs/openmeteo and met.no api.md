use openmeteo api and met.no api configurable in the settings together with locations.


Yes. I’d structure it as a small production-style Compose app where both providers normalize into the same `WeatherForecast` model, while Settings controls the active provider and saved locations.

Open-Meteo is particularly convenient because its forecast endpoint already exposes temperature, apparent temperature, humidity, dew point, precipitation and WMO weather codes, and it can return daily moon phase directly. ([Open-Meteo][1]) MET.no’s `locationforecast/2.0/compact` endpoint is also a good fit, but your Android client must send an identifying `User-Agent`; generic values such as `okhttp` can result in HTTP 403. ([MET Weather API][2])

I’d use this layout:

```text
app/
├── data/
│   ├── model/
│   │   ├── Location.kt
│   │   ├── WeatherForecast.kt
│   │   └── WeatherProvider.kt
│   ├── remote/
│   │   ├── OpenMeteoApi.kt
│   │   ├── MetNoApi.kt
│   │   └── dto/
│   ├── repository/
│   │   └── WeatherRepository.kt
│   └── settings/
│       └── SettingsRepository.kt
├── ui/
│   ├── weather/
│   │   ├── WeatherScreen.kt
│   │   └── WeatherViewModel.kt
│   ├── settings/
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   ├── components/
│   │   ├── MoonPhase.kt
│   │   ├── WeatherIcon.kt
│   │   └── ForecastTable.kt
│   └── theme/
│       ├── Color.kt
│       └── Theme.kt
└── MainActivity.kt
```

### 1. Dependencies

```kotlin
dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.08.00"))

    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.lifecycle:lifecycle-runtime-compose")

    implementation("androidx.navigation:navigation-compose")

    implementation("androidx.datastore:datastore-preferences:1.1.7")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

    implementation("com.squareup.okhttp3:logging-interceptor:5.1.0")
}
```

Enable serialization:

```kotlin
plugins {
    kotlin("plugin.serialization")
}
```

And:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

### 2. Common models

```kotlin
enum class WeatherProvider(
    val title: String
) {
    OPEN_METEO("Open-Meteo"),
    MET_NO("MET Norway")
}
```

```kotlin
data class WeatherLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int? = null
)
```

```kotlin
enum class WeatherCondition {
    CLEAR,
    MOSTLY_CLEAR,
    PARTLY_CLOUDY,
    CLOUDY,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    THUNDERSTORM,
    UNKNOWN
}
```

```kotlin
data class HourForecast(
    val time: Instant,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int,
    val dewPoint: Double?,
    val precipitation: Double,
    val precipitationProbability: Int?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)
```

```kotlin
data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int,
    val dewPoint: Double?,
    val precipitation: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)
```

```kotlin
data class WeatherForecast(
    val location: WeatherLocation,
    val provider: WeatherProvider,
    val current: CurrentWeather,
    val hourly: List<HourForecast>,
    val moonPhase: Double?
)
```

I would represent moon phase as `0.0..1.0`:

```text
0.00 new moon
0.25 first quarter
0.50 full moon
0.75 last quarter
1.00 new moon
```

That makes drawing it very straightforward.

### 3. Open-Meteo Retrofit API

Open-Meteo's `/v1/forecast` accepts coordinates plus lists of hourly/current/daily variables. ([Open-Meteo][1])

```kotlin
interface OpenMeteoApi {

    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude")
        latitude: Double,

        @Query("longitude")
        longitude: Double,

        @Query("current")
        current: String =
            "temperature_2m," +
            "relative_humidity_2m," +
            "apparent_temperature," +
            "precipitation," +
            "weather_code," +
            "is_day",

        @Query("hourly")
        hourly: String =
            "temperature_2m," +
            "relative_humidity_2m," +
            "dew_point_2m," +
            "apparent_temperature," +
            "precipitation_probability," +
            "precipitation," +
            "weather_code," +
            "is_day",

        @Query("daily")
        daily: String =
            "weather_code," +
            "temperature_2m_max," +
            "temperature_2m_min," +
            "sunrise," +
            "sunset," +
            "moon_phase",

        @Query("forecast_days")
        forecastDays: Int = 7,

        @Query("timezone")
        timezone: String = "auto"
    ): OpenMeteoResponse
}
```

Retrofit:

```kotlin
val openMeteoRetrofit =
    Retrofit.Builder()
        .baseUrl("https://api.open-meteo.com/")
        .addConverterFactory(
            Json {
                ignoreUnknownKeys = true
            }.asConverterFactory(
                "application/json".toMediaType()
            )
        )
        .build()

val openMeteoApi =
    openMeteoRetrofit.create(OpenMeteoApi::class.java)
```

A trimmed DTO:

```kotlin
@Serializable
data class OpenMeteoResponse(
    val timezone: String,

    val current: CurrentDto,

    val hourly: HourlyDto,

    val daily: DailyDto
)
```

```kotlin
@Serializable
data class CurrentDto(
    val time: String,

    @SerialName("temperature_2m")
    val temperature: Double,

    @SerialName("relative_humidity_2m")
    val humidity: Int,

    @SerialName("apparent_temperature")
    val apparentTemperature: Double,

    val precipitation: Double,

    @SerialName("weather_code")
    val weatherCode: Int,

    @SerialName("is_day")
    val isDay: Int
)
```

```kotlin
@Serializable
data class HourlyDto(
    val time: List<String>,

    @SerialName("temperature_2m")
    val temperature: List<Double>,

    @SerialName("relative_humidity_2m")
    val humidity: List<Int>,

    @SerialName("dew_point_2m")
    val dewPoint: List<Double>,

    @SerialName("apparent_temperature")
    val apparentTemperature: List<Double>,

    @SerialName("precipitation_probability")
    val precipitationProbability: List<Int?>,

    val precipitation: List<Double>,

    @SerialName("weather_code")
    val weatherCode: List<Int>,

    @SerialName("is_day")
    val isDay: List<Int>
)
```

```kotlin
@Serializable
data class DailyDto(
    val time: List<String>,

    @SerialName("moon_phase")
    val moonPhase: List<Double> = emptyList()
)
```

### 4. WMO code mapping

```kotlin
fun weatherConditionFromWmo(
    code: Int
): WeatherCondition =
    when (code) {

        0 ->
            WeatherCondition.CLEAR

        1 ->
            WeatherCondition.MOSTLY_CLEAR

        2 ->
            WeatherCondition.PARTLY_CLOUDY

        3 ->
            WeatherCondition.CLOUDY

        45, 48 ->
            WeatherCondition.FOG

        51, 53, 55,
        56, 57 ->
            WeatherCondition.DRIZZLE

        61, 63, 65,
        66, 67,
        80, 81, 82 ->
            WeatherCondition.RAIN

        71, 73, 75, 77,
        85, 86 ->
            WeatherCondition.SNOW

        95, 96, 99 ->
            WeatherCondition.THUNDERSTORM

        else ->
            WeatherCondition.UNKNOWN
    }
```

### 5. MET.no API

MET recommends the compact endpoint for most clients. Coordinates are mandatory, while altitude is optional but recommended when you know it. ([MET Weather API][2])

```kotlin
interface MetNoApi {

    @GET("weatherapi/locationforecast/2.0/compact")
    suspend fun forecast(
        @Query("lat")
        latitude: Double,

        @Query("lon")
        longitude: Double,

        @Query("altitude")
        altitude: Int? = null
    ): MetNoResponse
}
```

For MET.no I would use a separate OkHttp client because of the required application identification:

```kotlin
val metNoClient =
    OkHttpClient.Builder()
        .addInterceptor { chain ->

            val request =
                chain.request()
                    .newBuilder()
                    .header(
                        "User-Agent",
                        "HluWeatherApp/1.0 " +
                            "https://github.com/YOUR_ACCOUNT/HluWeather"
                    )
                    .build()

            chain.proceed(request)
        }
        .build()
```

Don't leave the default OkHttp User-Agent in production. MET explicitly rejects missing or generic identifiers. ([MET Weather API][2])

```kotlin
val metNoRetrofit =
    Retrofit.Builder()
        .baseUrl("https://api.met.no/")
        .client(metNoClient)
        .addConverterFactory(
            Json {
                ignoreUnknownKeys = true
            }.asConverterFactory(
                "application/json".toMediaType()
            )
        )
        .build()
```

### 6. MET.no DTO

The interesting structure is:

```text
properties
  timeseries[]
    time
    data
      instant.details
      next_1_hours.summary
      next_1_hours.details
```

So:

```kotlin
@Serializable
data class MetNoResponse(
    val properties: MetNoProperties
)

@Serializable
data class MetNoProperties(
    val timeseries: List<MetNoTimeseries>
)

@Serializable
data class MetNoTimeseries(
    val time: String,
    val data: MetNoData
)

@Serializable
data class MetNoData(
    val instant: MetNoInstant,

    @SerialName("next_1_hours")
    val next1Hours: MetNoPeriod? = null
)

@Serializable
data class MetNoInstant(
    val details: MetNoDetails
)
```

```kotlin
@Serializable
data class MetNoDetails(

    @SerialName("air_temperature")
    val temperature: Double,

    @SerialName("relative_humidity")
    val humidity: Double,

    @SerialName("dew_point_temperature")
    val dewPoint: Double? = null
)
```

```kotlin
@Serializable
data class MetNoPeriod(
    val summary: MetNoSummary? = null,
    val details: MetNoPeriodDetails? = null
)

@Serializable
data class MetNoSummary(

    @SerialName("symbol_code")
    val symbolCode: String
)

@Serializable
data class MetNoPeriodDetails(

    @SerialName("precipitation_amount")
    val precipitation: Double? = null
)
```

MET symbols can normalize like:

```kotlin
fun metSymbolToCondition(
    symbol: String?
): WeatherCondition {

    if (symbol == null)
        return WeatherCondition.UNKNOWN

    return when {

        "clearsky" in symbol ->
            WeatherCondition.CLEAR

        "fair" in symbol ->
            WeatherCondition.MOSTLY_CLEAR

        "partlycloudy" in symbol ->
            WeatherCondition.PARTLY_CLOUDY

        "cloudy" in symbol ->
            WeatherCondition.CLOUDY

        "fog" in symbol ->
            WeatherCondition.FOG

        "thunder" in symbol ->
            WeatherCondition.THUNDERSTORM

        "snow" in symbol ||
        "sleet" in symbol ->
            WeatherCondition.SNOW

        "rain" in symbol ->
            WeatherCondition.RAIN

        else ->
            WeatherCondition.UNKNOWN
    }
}
```

### 7. Repository abstraction

This is the important part: your UI should know nothing about either API.

```kotlin
interface WeatherDataSource {

    suspend fun forecast(
        location: WeatherLocation
    ): WeatherForecast
}
```

Then:

```kotlin
class WeatherRepository(
    private val openMeteo: OpenMeteoDataSource,
    private val metNo: MetNoDataSource,
    private val settingsRepository: SettingsRepository
) {

    suspend fun getForecast(
        location: WeatherLocation
    ): WeatherForecast {

        return when (
            settingsRepository.provider.first()
        ) {

            WeatherProvider.OPEN_METEO ->
                openMeteo.forecast(location)

            WeatherProvider.MET_NO ->
                metNo.forecast(location)
        }
    }
}
```

This means switching providers does not require restarting or changing screens.

### 8. One issue: moon phase with MET.no

MET.no Locationforecast doesn't need to own your astronomy data.

I would deliberately keep moon calculation separate from the weather provider:

```text
Weather provider
    ↓
weather

AstronomyCalculator
    ↓
moon phase

            ↓

WeatherForecast
```

Open-Meteo currently exposes `moon_phase` as a daily variable, so you can use that response directly. ([Open-Meteo][1])

For MET.no, calculate it locally.

That also means your moon is available offline.

For example:

```kotlin
object MoonPhaseCalculator {

    fun phase(
        date: LocalDate
    ): Double {

        val year = date.year
        val month = date.monthNumber
        val day = date.dayOfMonth

        var y = year
        var m = month

        if (m < 3) {
            y--
            m += 12
        }

        ++m

        val c = 365.25 * y
        val e = 30.6 * m

        var jd =
            c.toLong() +
            e.toLong() +
            day -
            694039.09

        jd /= 29.5305882

        var phase =
            jd - kotlin.math.floor(jd)

        if (phase < 0)
            phase += 1.0

        return phase
    }
}
```

For display I'd actually always use the local astronomical calculation and treat the API moon value as optional validation. That way switching between Open-Meteo and MET.no doesn't visually change your moon.

### 9. Compose moon renderer

You can now draw the actual illuminated fraction instead of using a crescent emoji/icon.

```kotlin
@Composable
fun MoonPhaseIcon(
    phase: Double,
    modifier: Modifier = Modifier
) {

    Canvas(modifier) {

        val radius =
            size.minDimension / 2f

        val center = this.center

        drawCircle(
            color = Color(0xFF28334A),
            radius = radius,
            center = center
        )

        val illumination =
            (1 - cos(phase * 2 * Math.PI)) / 2

        val width =
            radius * 2 * illumination.toFloat()

        drawOval(
            color = Color(0xFFFFF3D0),
            topLeft = Offset(
                center.x - width / 2,
                center.y - radius
            ),
            size = Size(
                width,
                radius * 2
            )
        )
    }
}
```

I'd refine the geometry a little further for waxing/waning direction, but this gives you the architecture.

### 10. Settings state

```kotlin
data class AppSettings(
    val provider: WeatherProvider =
        WeatherProvider.OPEN_METEO,

    val locations: List<WeatherLocation> =
        emptyList(),

    val selectedLocationId: String? =
        null,

    val theme: ThemeMode =
        ThemeMode.SYSTEM
)
```

```kotlin
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
```

DataStore keys:

```kotlin
private val PROVIDER =
    stringPreferencesKey("provider")

private val LOCATIONS =
    stringPreferencesKey("locations")

private val SELECTED_LOCATION =
    stringPreferencesKey("selected_location")

private val THEME =
    stringPreferencesKey("theme")
```

### 11. Settings screen

I'd make your Settings page look like this:

```text
Settings

Weather provider
┌────────────────────────────────┐
│ ● Open-Meteo                   │
│ ○ MET Norway                   │
└────────────────────────────────┘

Locations

┌────────────────────────────────┐
│ 📍 Svilajnac                   │
│    44.2380, 21.1970            │
│                         ⋮      │
└────────────────────────────────┘

┌────────────────────────────────┐
│ 📍 Belgrade                    │
│    44.8176, 20.4633            │
│                         ⋮      │
└────────────────────────────────┘

          + Add location

Appearance
○ System
○ Light
○ Dark
```

Compose:

```kotlin
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onProviderSelected: (WeatherProvider) -> Unit,
    onLocationSelected: (WeatherLocation) -> Unit,
    onAddLocation: () -> Unit,
    onDeleteLocation: (WeatherLocation) -> Unit
) {

    LazyColumn(
        modifier =
            Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(20.dp)
    ) {

        item {
            Text(
                "Settings",
                style =
                    MaterialTheme.typography.headlineMedium,
                fontWeight =
                    FontWeight.Bold
            )
        }

        item {
            SettingsSection(
                title = "Weather provider"
            ) {

                WeatherProvider.entries.forEach { provider ->

                    ProviderRow(
                        provider = provider,
                        selected =
                            state.provider == provider,
                        onClick = {
                            onProviderSelected(provider)
                        }
                    )
                }
            }
        }

        item {
            Row(
                modifier =
                    Modifier.fillMaxWidth(),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Text(
                    "Locations",
                    style =
                        MaterialTheme.typography.titleLarge,
                    modifier =
                        Modifier.weight(1f)
                )

                FilledTonalButton(
                    onClick = onAddLocation
                ) {
                    Icon(
                        Icons.Default.Add,
                        null
                    )

                    Spacer(
                        Modifier.width(6.dp)
                    )

                    Text("Add")
                }
            }
        }

        items(state.locations) { location ->

            LocationCard(
                location = location,
                selected =
                    location.id ==
                    state.selectedLocationId,

                onClick = {
                    onLocationSelected(location)
                },

                onDelete = {
                    onDeleteLocation(location)
                }
            )
        }
    }
}
```

### 12. Location card

```kotlin
@Composable
fun LocationCard(
    location: WeatherLocation,
    selected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {

    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth(),
        onClick = onClick,
        shape =
            RoundedCornerShape(20.dp)
    ) {

        Row(
            modifier =
                Modifier.padding(18.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Default.LocationOn,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme.primary
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    location.name,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    "%.4f, %.4f".format(
                        location.latitude,
                        location.longitude
                    ),
                    style =
                        MaterialTheme.typography.bodySmall,
                    color =
                        MaterialTheme.colorScheme
                            .onSurfaceVariant
                )
            }

            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint =
                        MaterialTheme.colorScheme.primary
                )
            }

            IconButton(
                onClick = onDelete
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription =
                        "Delete location"
                )
            }
        }
    }
}
```

### 13. Add-location dialog

For version one, I wouldn't introduce Google Maps or any map SDK at all.

Just support:

```text
Location name
[ Svilajnac                   ]

Latitude
[ 44.2380                     ]

Longitude
[ 21.1970                     ]

Altitude (optional)
[ 105                         ]

Cancel                    Save
```

You can later add:

```text
Use current GPS location
Search city
Choose on map
```

without altering your weather architecture.

### 14. Weather ViewModel

```kotlin
data class WeatherUiState(
    val loading: Boolean = false,
    val location: WeatherLocation? = null,
    val forecast: WeatherForecast? = null,
    val error: String? = null
)
```

```kotlin
class WeatherViewModel(
    private val repository: WeatherRepository,
    private val settings: SettingsRepository
) : ViewModel() {

    private val _state =
        MutableStateFlow(
            WeatherUiState()
        )

    val state =
        _state.asStateFlow()

    init {
        observeLocation()
    }

    private fun observeLocation() {

        viewModelScope.launch {

            settings.selectedLocation
                .filterNotNull()
                .collectLatest { location ->

                    load(location)
                }
        }
    }

    fun refresh() {

        val location =
            state.value.location
                ?: return

        load(location)
    }

    private fun load(
        location: WeatherLocation
    ) {

        viewModelScope.launch {

            _state.update {
                it.copy(
                    loading = true,
                    location = location,
                    error = null
                )
            }

            runCatching {
                repository.getForecast(location)
            }
                .onSuccess { forecast ->

                    _state.update {
                        it.copy(
                            loading = false,
                            forecast = forecast
                        )
                    }
                }
                .onFailure { error ->

                    _state.update {
                        it.copy(
                            loading = false,
                            error =
                                error.message
                                    ?: "Weather request failed"
                        )
                    }
                }
        }
    }
}
```

### 15. Navigation

```kotlin
sealed class Screen(
    val route: String
) {

    data object Weather :
        Screen("weather")

    data object Settings :
        Screen("settings")
}
```

```kotlin
NavHost(
    navController = navController,
    startDestination = Screen.Weather.route
) {

    composable(
        Screen.Weather.route
    ) {

        WeatherScreen(
            onSettingsClick = {
                navController.navigate(
                    Screen.Settings.route
                )
            }
        )
    }

    composable(
        Screen.Settings.route
    ) {

        SettingsScreen(...)
    }
}
```

### 16. One architectural change I'd make from the mockup

I would **not make `Hourly / Daily / Map` three completely separate screens**.

I'd keep:

```text
WeatherScreen

    CurrentConditions

    ForecastMode:
        Hourly
        Daily
        Map
```

So your top navigation is just state:

```kotlin
enum class ForecastMode {
    HOURLY,
    DAILY,
    MAP
}
```

and:

```kotlin
when (mode) {

    ForecastMode.HOURLY ->
        HourlyForecast(...)

    ForecastMode.DAILY ->
        DailyForecast(...)

    ForecastMode.MAP ->
        WeatherMap(...)
}
```

That keeps your current large weather card fixed while the lower content changes.

The most important implementation detail is the provider abstraction:

```text
             OpenMeteoDataSource ─┐
                                  │
UI → ViewModel → Repository ──────┼→ WeatherForecast
                                  │
             MetNoDataSource ─────┘
```

Once you have that, switching **Open-Meteo ↔ MET.no** becomes a settings change rather than an app rewrite.

For the first usable version, I'd implement **Open-Meteo + MET.no, multiple manually entered locations, light/dark/system themes, local moon-phase calculation, offline caching of the last forecast, pull-to-refresh, and provider/location switching**. Open-Meteo can supply up to 16 forecast days, while MET Locationforecast supplies roughly nine days, so the normalized UI should tolerate different provider horizons. ([Open-Meteo][1])

If you give this architecture to your coding agent, it is enough to implement the app without having to infer how the two APIs should fit together.

[1]: https://open-meteo.com/en/docs?utm_source=chatgpt.com "Weather Forecast API - Open-Meteo.com"
[2]: https://api.met.no/weatherapi/locationforecast/2.0/documentation?utm_source=chatgpt.com "Locationforecast"
