about locations; use gps location for adding location if gos enabled. use https://openfreemap.org/ to show location and allow user to change and modify "track me" mode for travelers to always show on cutrent location.

Yes — that fits the app very well. I’d change the location model from “saved coordinates only” into two location modes:

```kotlin
enum class LocationMode {
    SAVED_LOCATION,
    TRACK_ME
}
```

In **Saved location** mode, the user can add a place from GPS or choose/edit it on an OpenFreeMap map. In **Track me** mode, the weather screen always uses the phone’s latest location, which is ideal while traveling.

For the map, use **MapLibre Compose + OpenFreeMap**. OpenFreeMap explicitly supports mobile apps via MapLibre Native, requires no API key or registration, and its public instance currently has no request/view limits. ([OpenFreeMap][1]) A good default style is:

```kotlin
MaplibreMap(
    baseStyle = BaseStyle.Uri(
        "https://tiles.openfreemap.org/styles/liberty"
    )
)
```

MapLibre Compose also provides location state, a user-location puck, and camera tracking, so it maps nicely onto the Track Me feature. ([MapLibre][2])

I’d make the Settings location section behave like this:

```text
Locations

○ Track me
  Always use my current position
  GPS updates location while travelling

● Svilajnac
  44.2380, 21.1970

  Belgrade
  44.8176, 20.4633

+ Add location
```

When the user taps **Add location**, open a full-screen map:

```text
┌─────────────────────────────────┐
│ ←  Add location                 │
│                                 │
│         OpenFreeMap             │
│                                 │
│              ⊕                  │
│         selected point          │
│                                 │
│                       ◎ GPS     │
│                                 │
├─────────────────────────────────┤
│ Svilajnac                       │
│ 44.2380, 21.1970                │
│                                 │
│        Save location            │
└─────────────────────────────────┘
```

The marker should stay fixed in the center and the **map moves underneath it**. I prefer that to making users drag a tiny marker. When the camera stops, take the camera center:

```kotlin
data class MapLocationSelection(
    val latitude: Double,
    val longitude: Double
)
```

and show those coordinates immediately.

The GPS button should work like this:

```kotlin
sealed interface GpsResult {

    data class Success(
        val latitude: Double,
        val longitude: Double
    ) : GpsResult

    data object PermissionRequired : GpsResult

    data object LocationDisabled : GpsResult

    data object Unavailable : GpsResult
}
```

On Android you need:

```xml
<uses-permission
    android:name="android.permission.ACCESS_COARSE_LOCATION" />

<uses-permission
    android:name="android.permission.ACCESS_FINE_LOCATION" />
```

I would request permission **only after the user presses GPS or enables Track Me**, not at app startup.

If GPS is available, center the map on the current location:

```kotlin
fun useGpsLocation() {
    viewModelScope.launch {

        val location =
            locationService.currentLocation()

        cameraState.animateTo(
            finalPosition = CameraPosition(
                target = LngLat(
                    longitude = location.longitude,
                    latitude = location.latitude
                ),
                zoom = 12.5
            )
        )
    }
}
```

If location services are disabled, show something like:

```text
Location is turned off

Enable location services to use your
current position.

[ Open location settings ]
```

but still allow map selection manually.

For the saved model, I’d change `WeatherLocation` slightly:

```kotlin
data class WeatherLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int? = null,
    val createdFromGps: Boolean = false
)
```

For naming locations, don’t require users to type the name first. They should be able to move the map, and then you can reverse-geocode the coordinate. OpenFreeMap itself is principally the map/tile layer, so I would keep geocoding as a separate service rather than coupling it to the map.

The flow becomes:

```text
Add location
     │
     ├── GPS
     │    ↓
     │ current coordinates
     │
     └── move map manually
          ↓
       coordinates
          ↓
    reverse geocoder
          ↓
       "Svilajnac"
          ↓
    user may rename
          ↓
         Save
```

And importantly, the user should always be able to override the generated name:

```text
Name
[ Svilajnac               ]

44.23802, 21.19715

[ Save ]
```

## Track Me

I would make this a special pseudo-location rather than continuously rewriting a saved location:

```kotlin
sealed interface ActiveLocation {

    data class Saved(
        val location: WeatherLocation
    ) : ActiveLocation

    data class Current(
        val latitude: Double,
        val longitude: Double
    ) : ActiveLocation
}
```

Then:

```kotlin
class LocationRepository(
    private val settings: SettingsRepository,
    private val deviceLocation: DeviceLocationSource
) {

    val activeLocation: Flow<ActiveLocation> =
        settings.locationMode
            .flatMapLatest { mode ->

                when (mode) {

                    LocationMode.SAVED_LOCATION ->
                        settings.selectedLocation
                            .filterNotNull()
                            .map {
                                ActiveLocation.Saved(it)
                            }

                    LocationMode.TRACK_ME ->
                        deviceLocation.locations
                            .map {
                                ActiveLocation.Current(
                                    latitude = it.latitude,
                                    longitude = it.longitude
                                )
                            }
                }
            }
}
```

Then your weather repository doesn’t care where the coordinates originated:

```kotlin
fun ActiveLocation.coordinates(): Pair<Double, Double> =
    when (this) {

        is ActiveLocation.Saved ->
            location.latitude to location.longitude

        is ActiveLocation.Current ->
            latitude to longitude
    }
```

A key detail: **Track Me should not mean continuous high-accuracy GPS tracking.** A weather app doesn't need a new fix every second.

I would use roughly:

```text
App opened/resumed
        ↓
request current location
        ↓
fetch weather

User moves significantly
        ↓
new location
        ↓
refresh weather
```

For example, refresh when the user has moved around **5–10 km**, or after perhaps **30–60 minutes** if the app is active.

That avoids a lot of battery usage.

Conceptually:

```kotlin
fun shouldRefresh(
    old: GeoPoint,
    new: GeoPoint,
    lastRefresh: Instant
): Boolean {

    val movedEnough =
        distanceKm(old, new) >= 5.0

    val oldEnough =
        Clock.System.now() - lastRefresh >
            30.minutes

    return movedEnough || oldEnough
}
```

I would also show Track Me very clearly on the main screen.

Instead of:

```text
📍 Svilajnac
```

show:

```text
◎ Current location
  Svilajnac, Serbia
```

And while traveling:

```text
◎ Current location
  Budapest, Hungary
```

The weather API request simply follows it.

There is another useful behavior: tapping the location title could open a bottom sheet:

```text
Location

◎ Track me
  Current location

○ Svilajnac

○ Belgrade

────────────────

+ Add location
Manage locations
```

So users don't have to go into Settings just to change city.

For the map itself, I’d support automatic light/dark styling too. OpenFreeMap currently provides several predefined styles including Liberty, Positron and Dark. ([OpenFreeMap][3])

For example:

```kotlin
val styleUrl =
    if (isSystemInDarkTheme()) {
        "https://tiles.openfreemap.org/styles/dark"
    } else {
        "https://tiles.openfreemap.org/styles/liberty"
    }

MaplibreMap(
    baseStyle = BaseStyle.Uri(styleUrl)
)
```

This means your location picker will match the weather app mockups in both themes.

The resulting architecture becomes:

```text
                         ┌─ Saved locations
                         │
Compose UI ─ ViewModel ─ LocationRepository
                         │
                         └─ Device GPS
                              │
                         Track Me mode

                              ↓

                       latitude / longitude
                              │
                  ┌───────────┴───────────┐
                  ↓                       ↓
             Open-Meteo                MET.no
                  └───────────┬───────────┘
                              ↓
                       WeatherForecast


OpenFreeMap / MapLibre
        │
        ├─ show saved location
        ├─ select new location
        ├─ modify location
        └─ visualize Track Me position
```

One additional feature is worth adding now: **offline map caching**. MapLibre Compose supports downloading map regions for offline use on Android. ([MapLibre][4]) You don't necessarily need to expose it in v1, but using MapLibre means you have a clean path later for travelers with poor connectivity.

So for v1 I’d implement **saved places + GPS-to-add + OpenFreeMap picker/editor + Track Me + quick location switcher**, while keeping continuous background location tracking out of the app. That gives the traveler experience without turning the weather app into a battery-hungry navigation app.

[1]: https://openfreemap.org/ "OpenFreeMap"
[2]: https://maplibre.org/maplibre-compose/styling/?utm_source=chatgpt.com "Style the map | MapLibre Compose"
[3]: https://openfreemap.org/quick_start/?utm_source=chatgpt.com "OpenFreeMap Quick Start Guide"
[4]: https://maplibre.org/maplibre-compose/offline/?utm_source=chatgpt.com "Download maps for offline use | MapLibre Compose"
