# Weather On Route Planner

## Overview

The `/weather-route` page is a fully functional route planner that displays hourly weather conditions along a journey. Users can enter start and end locations, set their driving speed and departure time, and see an interactive map with weather markers and a timeline showing conditions at each hour of the trip.

All weather calculations are performed client-side, with the route fetched from OpenRouteService API and weather data from multiple weather providers via a flexible provider system.

---

## Features

### User Inputs

| Field | Range | Default |
|-------|-------|---------|
| Start Location | Any city/place name | Empty |
| End Location | Any city/place name | Empty |
| Speed | 50-240 km/h | 80 km/h |
| Date & Time | Any future/past time | Next full hour |
| Weather Provider | Open-Meteo / MET Norway / VisualCrossing | Open-Meteo |

### Weather Providers

The system supports multiple weather providers with automatic fallback:

| Provider | ID | Coverage | API Key | Best For |
|----------|----|-----------|---------|----------|
| **Open-Meteo** | `open-meteo` | Global | Not required | General use, default |
| **MET Norway** | `met-no` | Norway/Nordic | Not required | Nordic routes |
| **VisualCrossing** | `visualcrossing` | Global | Required | Comprehensive weather data |
| **OpenWeatherMap** | `openweather` | Global | Required | Coming soon |

**Provider Features:**
- **Automatic Fallback**: If primary provider fails, automatically tries other providers
- **1-Hour Cache**: Reduces redundant API calls
- **Consistent Output**: All providers normalize to WMO weather codes

### Core Functionality

1. **Route Calculation**: Uses OpenRouteService Directions API to calculate driving route between locations
2. **Weather Along Route**: Calculates hourly weather points based on driving speed and departure time
3. **Interactive Map**: Leaflet map with color-coded weather markers at each hour
4. **Weather Timeline**: Scrollable list showing weather conditions for each hour
5. **Bidirectional Selection**: Clicking a map marker highlights the timeline card and vice versa
6. **Share Feature**: Copy URL with all parameters to share the route with others
7. **URL Parameter Loading**: Auto-calculates route when opening a shared URL

### Weather Categories

| Color | Weather Codes | Conditions |
|-------|---------------|------------|
| Green | 0-2 | Clear, mainly clear, partly cloudy |
| Yellow | 3, 45, 48, 51-57 | Overcast, fog, drizzle |
| Orange | 61-67, 71-77, 80-86 | Rain, snow, showers |
| Red | 95-99 | Thunderstorm |
| Gray | - | Weather unavailable (API failure) |

---

## Page Structure

```
┌────────────────────────────────────────────────┐
│  Weather Route Planner                         │
├────────────────────────────────────────────────┤
│                                                │
│  ┌─ Input Form ───────────────────────────┐   │
│  │ Start: [___________]  (Enter to submit) │   │
│  │ End:   [___________]  (Enter to submit) │   │
│  │ Speed: [80] km/h  (50-240)              │   │
│  │ Start: [📅 2025-01-20 15:00]           │   │
│  │ Provider: [Open-Meteo ▼]                │   │
│  │   Global weather coverage with no API   │   │
│  │   key required.                          │   │
│  │ [Calculate Route with Weather] 📋 Copy │   │
│  └───────────────────────────────────────┘   │
│                                                │
│  [OR PLACEHOLDER - if no route yet]           │
│                                                │
│  ┌─ Route Summary ────────────────────────┐   │
│  │ From: Belgrade → To: Novi Sad            │   │
│  │ Distance: 92.5 km | Duration: 1h 9min    │   │
│  │ Departure: Mon 15:00 → Arrival: 16:09    │   │
│  └───────────────────────────────────────┘   │
│                                                │
│  ┌─ Weather Map (500px height) ────────────┐   │
│  │  [Map with blue route + colored markers]  │   │
│  └───────────────────────────────────────┘   │
│                                                │
│  ┌─ Weather Timeline (max-h:500px) ─────────┐ │
│  │ 📍 15:00 · ☀️ Clear · 18°C · 💨 12km/h    │ │
│  │    80 km from start                       │ │
│  │ 📍 16:00 · ⛅ Partly Cloudy · 17°C       │ │
│  │    160 km from start                      │ │
│  │ 📍 17:00 · 🌧️ Rain · 15°C · 🏁          │ │
│  │    240 km from start (Destination)        │ │
│  │ [scrollable...]                          │ │
│  └───────────────────────────────────────┘   │
│                                                │
└────────────────────────────────────────────────┘
```

---

## Component Architecture

### 1. `app/weather-route/page.tsx`

Main page component containing:

**State Management:**
```typescript
const [startLocation, setStartLocation] = useState("");
const [endLocation, setEndLocation] = useState("");
const [speed, setSpeed] = useState(80);
const [startTime, setStartTime] = useState(getNextFullHour());
const [weatherProvider, setWeatherProvider] = useState<WeatherProviderType>("open-meteo");
const [routeData, setRouteData] = useState<RouteData | null>(null);
const [weatherPoints, setWeatherPoints] = useState<WeatherPoint[]>([]);
const [selectedPointIndex, setSelectedPointIndex] = useState<number | null>(null);
const [isLoading, setIsLoading] = useState(false);
const [error, setError] = useState("");
const [copied, setCopied] = useState(false);
const [startLabel, setStartLabel] = useState("");
const [endLabel, setEndLabel] = useState("");
```

**Key Functions:**
- `calculateRouteWithWeather()`: Main async function orchestrating the full workflow
- `geocodeLocation(location)`: Converts place names to coordinates via `/api/geocode`
- `handleShare()`: Copies URL with all parameters to clipboard
- `useEffect()`: Parses URL parameters on mount for shared routes

**Placeholder Design:**
```jsx
<div className="text-center py-16 text-gray-500">
  <p className="text-6xl mb-4">🗺️</p>
  <p className="text-xl font-semibold">Plan a route to see weather conditions along the way</p>
  <p className="mt-2">Enter start and end locations above, adjust your speed and departure time</p>
</div>
```

---

### 2. `app/components/WeatherRouteMap.tsx`

Interactive Leaflet map displaying route and weather.

**Props Interface:**
```typescript
interface WeatherRouteMapProps {
  routeCoordinates: [number, number][];
  weatherPoints: WeatherPoint[];
  selectedPointIndex: number | null;
  onPointClick: (index: number) => void;
}
```

**Visual Features:**
- Blue route polyline (weight: 4, opacity: 0.7)
- Custom circular markers with weather emojis
- Color-coded based on weather severity
- Selected marker: 48px with pulsing animation
- Normal marker: 36px
- Popups on hover showing time, temperature, wind, distance
- Auto-fits bounds to show entire route

**Weather Color Function:**
```typescript
function getWeatherColorCategory(code: number): 'green' | 'yellow' | 'orange' | 'red' {
  if (code <= 2) return 'green';      // Clear, partly cloudy
  if (code <= 57) return 'yellow';     // Overcast, fog, drizzle
  if (code <= 86) return 'orange';     // Rain, snow
  return 'red';                        // Storms
}
```

---

### 3. `app/components/WeatherTimeline.tsx`

Vertical scrollable list of weather cards.

**Props Interface:**
```typescript
interface WeatherTimelineProps {
  weatherPoints: WeatherPoint[];
  selectedPointIndex: number | null;
  onPointSelect: (index: number) => void;
}
```

**Card Display:**
- Time (HH:MM format)
- Weather emoji and description
- Temperature in Celsius
- Wind speed in km/h
- Distance from start
- Destination marker (🏁) for last point
- Color-coded left border matching weather category

**Selection Styling:**
- Normal: White with gray hover
- Selected: Blue background with ring-2 ring-blue-300
- Border color: Matches weather category (green/yellow/orange/red/gray)

**Error Handling:**
- Shows "❓ Weather unavailable" for failed API calls
- Gray styling for unavailable weather
- Continues displaying successful points

---

## Integration with Existing Code

### Weather Provider System

The weather provider system is located in `lib/weather/providers/`:

#### Files

| File | Purpose |
|------|---------|
| `types.ts` | Provider interfaces and type definitions |
| `registry.ts` | Provider registry and factory with caching |
| `OpenMeteoProvider.ts` | Open-Meteo API implementation |
| `MetNoProvider.ts` | MET Norway API implementation |
| `VisualCrossingProvider.ts` | VisualCrossing API implementation |

#### Provider Interface

All providers implement the `WeatherProvider` interface:

```typescript
interface WeatherProvider {
  getConfig(): WeatherProviderConfig;
  fetchWeather(lat, lon, time): Promise<WeatherDataResult | null>;
  isAvailable(): boolean;
  getCacheKey(): string;
}
```

#### Adding a New Provider

1. Create a new provider class in `lib/weather/providers/`:

```typescript
// Example: OpenWeatherMapProvider.ts
import { WeatherProvider, WeatherDataResult } from "./types";

export class OpenWeatherMapProvider implements WeatherProvider {
  private readonly apiKey = process.env.NEXT_PUBLIC_OPENWEATHER_API_KEY;

  getConfig() {
    return {
      name: "OpenWeatherMap",
      id: "openweather",
      requiresApiKey: true,
      description: "Global weather with extensive features",
    };
  }

  isAvailable() {
    return !!this.apiKey;
  }

  getCacheKey() {
    return "openweather";
  }

  async fetchWeather(lat, lon, time) {
    // Fetch from OpenWeatherMap API
    // Normalize to WMO codes
    // Return WeatherDataResult or null
  }
}
```

2. Register the provider in `registry.ts`:

```typescript
import { OpenWeatherMapProvider } from "./OpenWeatherMapProvider";

constructor() {
  // ... existing providers
  this.registerProvider("openweather", new OpenWeatherMapProvider());
}
```

3. Add the provider name to `types.ts`:

```typescript
export const WEATHER_PROVIDER_NAMES: Record<WeatherProviderType, string> = {
  "open-meteo": "Open-Meteo (Default)",
  "met-no": "MET Norway",
  "visualcrossing": "VisualCrossing",
  "openweather": "OpenWeatherMap",
};
```

### Utilities from `lib/weather.ts`

| Function | Description |
|----------|-------------|
| `calculateWeatherAlongRoute()` | Main function calculating weather points along route |
| `getNextFullHour()` | Returns next full hour for default datetime |
| `formatTime(date)` | Formats time as HH:MM |
| `formatDate(date)` | Formats date for display |
| `getWeatherProviders()` | Get all registered providers |
| `getAvailableWeatherProviders()` | Get available providers (that pass isAvailable check) |
| `clearWeatherCache()` | Clear the weather cache |

**Weather Caching:** The weather utility includes a 1-hour TTL cache to reduce redundant API calls for the same location and time. Cache is shared across all providers with provider-specific keys.

### Types from `types/weather.ts`

| Function/Type | Description |
|---------------|-------------|
| `WeatherPoint` interface | Type for weather point data |
| `getWeatherInfo(code)` | Returns description and emoji for WMO weather code |
| `WEATHER_CODES` | Complete WMO weather code mappings |

### API Routes

| Endpoint | Purpose |
|----------|---------|
| `/api/geocode?text=...` | Geocodes location names to coordinates |
| `/api/directions?start=...&end=...` | Returns driving route between two points |

### Shared Components

- `LoadingSpinner`: Displayed during route/weather calculations

---

## Data Flow

```
User Input (including weather provider selection)
    ↓
Geocode Start Location → /api/geocode
    ↓
Geocode End Location → /api/geocode
    ↓
Get Route → /api/directions
    ↓
Extract Coordinates [lon,lat] → Convert to [lat lon]
    ↓
Calculate Weather Points → calculateWeatherAlongRoute(provider)
    ↓
For each hour:
    - Calculate position along route
    - Check cache (provider-specific key)
    - If cache miss: Fetch from selected provider
    - If provider fails: Try fallback providers
    - Cache results (1-hour TTL)
    ↓
Display Results
    - Map with markers
    - Timeline with cards
    - Bidirectional selection
```

---

## URL Sharing Feature

### Share Button
When user clicks "📋 Copy Link":
1. Constructs URL with all parameters: `/weather-route?start=X&end=Y&speed=Z&time=T&provider=P`
2. Copies to clipboard
3. Shows "Copied!" confirmation for 2 seconds

### URL Parameter Loading
On page load (`useEffect`):
1. Parses URL parameters for start, end, speed, time, provider
2. Auto-fills form fields
3. Automatically calls `calculateRouteWithWeather()`

Example shared URL:
```
/weather-route?start=Belgrade&end=Novi%20Sad&speed=100&time=2025-01-20T15:00&provider=met-no
```

---

## Error Handling

### Geocoding Failures
- Error message: "Could not find start location" or "Could not find end location"
- Clears route data
- Allows retry with different inputs

### Directions API Failures
- Error message: "Failed to get directions. Please try again."
- Clears route data
- Allows retry

### Partial Weather Failures
- Successfully fetched points display normally
- Failed points show "Weather unavailable" with gray styling
- Map shows gray markers with "❓" emoji
- Continues displaying all available points
- Logs errors to console without blocking UI

---

## Data Structures

### RouteData (from directions API)
```typescript
interface RouteData {
  features: Array<{
    geometry: {
      coordinates: [number, number][]; // [lon, lat] format
    };
    properties: {
      summary: {
        distance: number; // meters
        duration: number; // seconds
      };
    };
  }>;
}
```

### WeatherPoint
```typescript
interface WeatherPoint {
  position: [number, number]; // [lat, lon]
  time: Date;
  temperature: number; // Celsius
  weatherCode: number; // WMO code
  windSpeed: number; // km/h
  distanceFromStart: number; // km
  isDay: number; // 0 or 1
}
```

### GeocodingResult
```typescript
interface GeocodingResult {
  geometry: {
    coordinates: [number, number]; // [lon, lat]
  };
  properties: {
    label: string;
  };
}
```

---

## Technical Implementation Details

### Dynamic Import
Map components use dynamic imports with `ssr: false` to avoid Leaflet SSR issues:

```typescript
const WeatherRouteMap = dynamic(() => import('../components/WeatherRouteMap'), {
  ssr: false,
  loading: () => (
    <div className="h-96 flex items-center justify-center bg-gray-100 rounded-lg">
      <LoadingSpinner />
    </div>
  ),
});
```

### Leaflet Icon Setup
```typescript
useEffect(() => {
  delete (L.Icon.Default.prototype as any)._getIconUrl;
  L.Icon.Default.mergeOptions({
    iconRetinaUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png",
    iconUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png",
    shadowUrl: "https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png",
  });
}, []);
```

### Coordinate Conversion
Directions API returns `[lon, lat]`, Leaflet expects `[lat, lon]`:

```typescript
const routeCoordinates = routeData.features[0].geometry.coordinates
  .map((coord) => [coord[1], coord[0]]) as [number, number][];
```

---

## Styling

Following existing patterns from `route-planner/page.tsx`:

| Element | Classes |
|---------|---------|
| Cards | `bg-white rounded-lg shadow-lg p-6` |
| Inputs | `w-full px-4 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500` |
| Buttons | `bg-green-500 hover:bg-green-600 text-white font-bold py-3 px-6 rounded-lg` |
| Errors | `bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded` |
| Timeline cards | `border-l-4 p-4 cursor-pointer hover:bg-gray-50 transition-all` |
| Selected card | `bg-blue-50 border-l-4 ring-2 ring-blue-300` |

---

## Implementation Status

### ✅ Completed Features

#### Phase 1: Core Page Structure
- ✅ `app/weather-route/page.tsx` with input form
- ✅ All input fields (start, end, speed, datetime, provider)
- ✅ Placeholder design for empty state
- ✅ State management
- ✅ Error message display

#### Phase 2: Route Calculation
- ✅ `geocodeLocation()` function
- ✅ `calculateRouteWithWeather()` async function
- ✅ Loading states with LoadingSpinner
- ✅ Route coordinate extraction
- ✅ Integration with `calculateWeatherAlongRoute()`

#### Phase 3: Route Summary Display
- ✅ Total distance and duration display
- ✅ Start/end location labels
- ✅ Journey summary section
- ✅ Departure and arrival time formatting

#### Phase 4: Weather Map Component
- ✅ `WeatherRouteMap.tsx` component
- ✅ MapContainer with proper centering
- ✅ Route polyline
- ✅ `getWeatherColorCategory()` helper
- ✅ Custom marker icons with emojis
- ✅ Markers for each weather point
- ✅ Selection state with pulsing animation
- ✅ Popups with weather details
- ✅ Click event handling

#### Phase 5: Weather Timeline Component
- ✅ `WeatherTimeline.tsx` component
- ✅ Scrollable container (max-height: 500px)
- ✅ Weather card layout
- ✅ Color-coded borders
- ✅ Selection highlighting
- ✅ Click event handling
- ✅ Time, distance, weather info formatting
- ✅ "Weather unavailable" handling

#### Phase 6: Integration & Polish
- ✅ Map and timeline selection state sync
- ✅ End-to-end workflow testing
- ✅ Responsive design
- ✅ Consistent styling with existing pages
- ✅ Error scenario testing
- ✅ API integration verification

#### Phase 7: Additional Features
- ✅ Share functionality (copy URL with parameters)
- ✅ URL parameter parsing and auto-calculation
- ✅ Weather caching (1-hour TTL)
- ✅ Keyboard support (Enter to submit)
- ✅ Destination marker (🏁) on last point
- ✅ Enhanced error handling for partial failures

#### Phase 8: Multi-Provider Weather System
- ✅ Provider interface and types (`lib/weather/providers/types.ts`)
- ✅ Provider registry with caching (`lib/weather/providers/registry.ts`)
- ✅ Open-Meteo provider implementation
- ✅ MET Norway provider implementation
- ✅ VisualCrossing provider implementation
- ✅ Automatic fallback between providers
- ✅ Provider selection dropdown in UI
- ✅ Provider persistence in shared URLs
- ✅ Real-time provider switching (auto-recalculates weather)

---

## Testing Scenarios

### Happy Path
1. Enter valid locations (e.g., "Belgrade" to "Novi Sad")
2. Adjust speed (50-240 km/h)
3. Select departure time or use default
4. Click "Calculate Route with Weather"
5. Route displays with summary, map, and timeline
6. Weather points show in both map and timeline
7. Clicking timeline card highlights map marker
8. Clicking map marker highlights timeline card

### Share Feature
1. Calculate a route
2. Click "📋 Copy Link"
3. Paste URL in new tab/window
4. Route auto-calculates with same parameters

### Error Scenarios
| Scenario | Result |
|----------|--------|
| Invalid start location | "Could not find start location" |
| Invalid end location | "Could not find end location" |
| Directions API fails | "Failed to get directions. Please try again." |
| Partial weather failure | Shows available points, "Weather unavailable" for failures |
| Network error | Generic error message, allows retry |

### Edge Cases
| Condition | Behavior |
|-----------|----------|
| Short route (< 1 hour) | Shows start and end points only |
| Long route (> 24 hours) | Shows all hourly points with dates |
| Minimum speed (50 km/h) | More hours, more weather points |
| Maximum speed (240 km/h) | Fewer hours, fewer weather points |
| All weather points fail | Shows route with gray markers, "Weather unavailable" |

---

## Future Enhancements

Potential features for future development:

- Export route with weather data to GPX/KML
- Save favorite routes for quick access
- Compare multiple routes and their weather conditions
- Historical weather analysis for past trips
- Weather alerts and warnings along route
- Route optimization based on weather conditions
- Weather-based departure time suggestions
- Multi-stop route support
---

# Android Weather On Route

## Overview

Weather On Route is the native Android route planner in HluWeather. It calculates
weather conditions at expected arrival points along a driving route. The planner
uses the route geometry returned by the routing service, a user-selected average
speed, and the selected local departure time. Route results are kept in memory
only.

The approved architecture is summarized below because the design-spec file is
not present in this branch.

## Approved Architecture Summary

The planner uses a dedicated `weather_route` navigation destination and
`WeatherRouteViewModel`. The ViewModel owns planner inputs, validation, request
cancellation, retries, and in-memory results without changing the existing
weather forecast flow.

The data layer uses replaceable source interfaces:

- `RoutingSource` supplies a driving polyline, distance, and provider estimate;
  `OsrmRoutingSource` is the v1 implementation.
- `PlaceSearchSource` supplies normalized place results; Photon is the default
  and Open-Meteo geocoding is selectable.
- `RouteWeatherSource` enriches ordered route samples; Open-Meteo is the only
  v1 implementation.

The shared route models flow from these sources through the ViewModel to the
Compose route screen, MapLibre map, and timeline. This keeps routing and search
providers replaceable without changing the planner UI or sampling logic.

## Opening The Planner

1. Open the main weather screen with a loaded location forecast.
2. Select **Map** in the Hourly, Daily, and Map navigation tabs.
3. Tap **Weather on route**.

The planner opens as the full-screen `weather_route` navigation destination.
Back returns to the weather screen. Selecting route endpoints does not create,
edit, or change saved locations.

## Inputs And Endpoint Selection

| Input | Behavior | Default or limit |
| --- | --- | --- |
| Start | Search, saved location, current GPS location, or map picker | Empty |
| Destination | Search, saved location, or map picker | Empty |
| Search provider | Planner-only selector between Photon and Open-Meteo geocoding | Photon |
| Departure | Android local date and time controls | Next full local hour |
| Average speed | Entered driving speed in km/h | 80 km/h; 50-240 km/h |

Search text does not become an endpoint until a result is selected. Search
results are normalized to a label and coordinate, and invalid or duplicate
coordinates are discarded. The current-location action is available only for
the start endpoint. The destination has no GPS shortcut.

The Calculate action requires both resolved endpoints. On calculation, the
speed must be an integer from 50 through 240 km/h, the departure cannot be in
the past, and the expected arrival must be within the available forecast
horizon. The planner's speed value is always km/h even when other app units are
changed in Settings.

## Providers

### Place Search

Photon is the default route-planner search provider. Open-Meteo geocoding can
be selected for either endpoint from the planner's provider selector. This
selector does not change the app-wide weather provider setting.

The Android clients use these public services:

| Purpose | Provider | Request |
| --- | --- | --- |
| Place search | Photon | `https://photon.komoot.io/api/` |
| Alternative place search | Open-Meteo geocoding | `https://geocoding-api.open-meteo.com/v1/search` |

Both searches request up to eight English results. Blank queries produce no
request and no results. Network or HTTP failures leave the current endpoint
unchanged and expose a concise error that can be retried or resolved by
switching provider.

### Routing

OSRM is the initial routing provider. The Android client requests a full GeoJSON
driving route from the public OSRM service and adapts its longitude/latitude
coordinates into the shared `GeoPoint` model. The route result contains the
OSRM provider name, driving polyline, distance, and provider travel estimate.

The planner's expected travel duration is calculated independently as:

```text
route distance / entered average speed
```

The OSRM travel estimate is not used to place weather samples. A routing
failure does not silently switch to another routing provider; the inputs remain
available and **Retry** is offered.

### Route Weather

Open-Meteo is the only route-weather provider in v1. It receives all sampled
coordinates in one batched forecast request and returns hourly temperature,
weather code, wind speed, and precipitation probability. Route weather does not
follow the app-wide Open-Meteo/MET.no provider selection.

The request uses a 16-day forecast, GMT timestamps, Celsius temperature, km/h
wind, and millimetres for precipitation. Each route sample is matched with the
nearest returned hourly forecast time. If an individual response is missing or
malformed, that sample is shown as unavailable while other samples remain
usable.

## Route Sampling

Samples are generated from the actual OSRM polyline, not from a straight line
between endpoints. The planner uses constant average speed and interpolates
each sample over cumulative route geometry distance. It creates samples at:

- Departure.
- Every elapsed travel hour before arrival.
- Expected arrival at the destination.

The complete trip, including expected arrival, must be no later than the
planner's current-time-plus-16-days forecast boundary. A departure in the past
and an arrival outside that boundary are rejected. A short route still has
distinct departure and destination samples.

## Results

After routing, the screen contains:

- A route summary with the resolved endpoints, route distance, entered average
  speed, routing provider label, OSRM duration, local departure, and expected
  arrival.
- A MapLibre map using the app's OpenFreeMap light or dark map style.
- The OSRM route line and start/destination markers.
- A weather marker for every route sample.
- A scrollable timeline containing local sample time, travelled distance,
  condition, temperature, wind, and precipitation probability.
- A visible `Destination` label on the final timeline item.

Weather markers use these normalized severity groups:

| Severity | Conditions |
| --- | --- |
| Favorable | Clear through partly cloudy |
| Caution | Cloudy, fog, and drizzle |
| Adverse | Rain and snow |
| Severe | Thunderstorms |
| Unavailable | Missing or unusable weather |

Selecting a timeline item selects the corresponding map marker and centers the
map on that sample. Selecting a map marker selects the corresponding timeline
item. Temperature, wind, distance, and precipitation display using the existing
app unit settings, while the average-speed input remains in km/h.

## Error And Retry States

- **Incomplete endpoints:** Calculate remains disabled until both endpoints are
  selected.
- **Invalid speed:** The planner reports that speed must be from 50 to 240
  km/h, without calling the routing service.
- **Past departure:** The planner reports that departure cannot be in the past.
- **Arrival beyond forecast:** The planner reports that arrival is outside the
  forecast range.
- **No search results:** No selectable result is shown and the existing
  endpoint is not replaced.
- **GPS denied or unavailable:** A concise location status is shown; saved and
  map endpoint selection remain available.
- **Routing failure:** The route result is cleared, inputs are preserved, and
  **Retry** is offered.
- **Complete route-weather failure:** The route geometry and summary remain;
  weather values render as unavailable and **Retry weather** is offered.
- **Partial route-weather failure:** Successful samples remain populated and
  failed samples render as unavailable.
- **Map style or tile failure:** The map displays **Map tiles unavailable**;
  route and timeline data are separate from map tile loading.

Changing an endpoint, search input/provider, speed, or departure invalidates
in-flight work. After a completed calculation, changing relevant inputs marks
the displayed result outdated until Calculate is run again.

## Explicit V1 Exclusions

The Android v1 planner does not provide:

- Persisted or shared route results.
- Multi-stop routes.
- Traffic-aware arrival estimates.
- Rerouting or route alternatives.
- Historical weather.
- Weather-based route optimization.
- Automatic routing or weather-provider fallback.
