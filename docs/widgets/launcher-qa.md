# Home-screen widgets: usage and launcher QA

## Add and use

Long-press an empty area of the home screen, open **Widgets**, search for HluWeather (shown as **HluWeatherApp** on the tested Pixel Launcher), and add either preview:

- Provider labels declared by the app: **HluWeather · Current weather** (2×2 target; current conditions only) and **HluWeather · Hourly forecast** (4×2 target; current conditions plus three to five upcoming hours).
- On the tested API 35 Pixel Launcher, both entries are named **HluWeatherApp**; their descriptions and **2 × 2** / **4 × 2** size labels distinguish them. Actual medium 4×2 placement is verified. The earlier 4×3 result came from stale Android provider metadata, resolved without another layout change; see the dated evidence below.

Both providers are declared resizable and have no configuration activity. They share the app's selected provider, location, temperature unit, and theme. Change those in HluWeather; installed widgets re-render from matching cached weather. Explicit Light/Dark app themes take precedence over Android appearance; System follows it. Tapping the card or location opens the main weather screen. The separate refresh control requests an update without opening the app.

Widgets render matching cached weather first. Refresh attempts preserve the forecast's actual update time; offline or failed refreshes keep matching data and identify stale/failed status. Shared periodic work is nominally every 60 minutes with a connected-network constraint; Doze, connectivity, and Android scheduling can delay it. The interval is approximate, not a deadline.

In Track Me mode, the widget uses a fix the app already obtained, including a passively persisted last-known fix after process death. It never requests GPS or starts tracking for a widget. If no valid fix exists, it falls back to the selected saved location, then the first saved location; with none, it prompts the user to choose a location in the app.

## Launcher QA checklist

- [ ] **Picker and placement:** confirm both previews, the actual launcher labels, descriptions, and sizes; compare with the declared provider labels above. Add one of each and confirm neither launches a configuration step.
- [ ] **Resize:** exercise the small widget's minimum (110×110 dp) and compact/normal/wide branches; keep location, icon, temperature, and refresh visible, with secondary rows dropped before clipping. Resize medium down to its compact 260×110 dp minimum and across three-, four-, and five-hour branches (about 260, 320, and 400 dp wide at 1× font scale); verify dividers and no horizontal overflow. Launcher cells are not fixed dp sizes: two rows on the tested grid are taller than 110 dp. The small widget has no hourly strip.
- [ ] **Names and font scale:** use a long saved-location name and larger Android font/display size; confirm end ellipsis leaves refresh available, temperatures remain legible, and lower-priority content yields space.
- [ ] **TalkBack:** check location, condition and unit-bearing temperature, hourly time/temperature/probability, and the “Refresh weather” action; decorative icons should not be announced twice. Missing precipitation must not sound like 0%.
- [ ] **Settings synchronization:** change provider, saved location/Track Me mode, Celsius/Fahrenheit, and Light/Dark/System. Confirm correct identity/units/colors update from cache without requiring a successful fetch; in System mode, change Android appearance and confirm both widget sizes follow it.
- [ ] **Actions and refresh:** tap the card and location to reach the main weather screen. Tap refresh and confirm the app does not open; repeat taps during a request and confirm they coalesce, loading clears, and the fetched timestamp changes only on a fresh result.
- [ ] **Offline/failure cache:** with a matching forecast already cached, disconnect network and refresh or recreate the process. Confirm weather remains, stale/failure status is visible, and the displayed weather timestamp does not advance to the attempt time. Restore connectivity and confirm a later successful refresh recovers.
- [ ] **Track Me without GPS:** with Track Me selected, verify a previously obtained fix (including after process death/reboot) is used without a widget location prompt or GPS acquisition. With no fix, verify selected-saved then first-saved fallback; do not clear app data for this check.
- [ ] **Process death/reboot:** force-stop or reboot without clearing app data; confirm placed instances and cached content return and background refresh can resume.
- [ ] **Instance lifecycle:** place multiple instances across both sizes. Remove one size and confirm the other remains scheduled; remove the last instance and confirm widget periodic and pending work is canceled.

## Rendered examples and runtime evidence

The committed [example renders](examples/README.md) link to [small light Zaječar](examples/small-light-zajecar.png), [small dark Trieste](examples/small-dark-trieste.png), and [medium Zaječar with five hours](examples/medium-zajecar-five-hours.png). These are deterministic Robolectric renders of production Glance content translated to `RemoteViews`—**not launcher/device screenshots**. Actual API 35 emulator captures are linked separately in the runtime results. Re-render/validate the examples with:

```sh
WEATHER_WIDGET_EXAMPLES_DIR="$PWD/docs/widgets/examples" \
ANDROID_HOME=/home/homoludens/Android/Sdk \
ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk \
./gradlew :app:testGoogleDebugUnitTest \
  --tests net.droopia.hluweather.widget.WeatherWidgetReferenceExamplesTest \
  --rerun-tasks
```

Runtime checks are recorded separately below; “not run” means no device-level verification. Do not treat the example renders or unit tests as launcher verification.

| Runtime target | Result |
| --- | --- |
| API 28 launcher | **NOT RUN** — no API 28 device, AVD, platform, or system image is installed locally; no download was attempted. |
| API 35 Pixel Launcher | **4×2 picker/placement PASS; overall QA BLOCKED** by the refresh-snapshot and font-scale findings below (2026-10-29 Sol fallback). Existing task AVD `hluweather-task6-5-api35`, Pixel 2 profile, Google APIs, 1080×1920, 420 dpi, 5×5 grid; Pixel Launcher version 15 / code 907. Same Google debug APK as task 6.4, package `net.droopia.hluweather`, version 3.5.1 / code 38. SHA-256: `77405d803e53d3650e63d1da95fd68ee29f2c92d307ea6de4e4d64bb499c865f`. No Kotlin/XML changes, rebuild, uninstall, or data clear. |

### 4×3 investigation: stale metadata, not another layout fix

The on-device `base.apk`, the previously pulled APK, and the local build all had the SHA-256 above. The installed package had one base APK, not an old split. However, before reinstalling, `dumpsys appwidget` held medium `min=(66561x36865)` / `minResize=(66561x36865)`, while that APK's XML encoded 260×110 dp. These service numbers are **Android complex dimension encodings, not pixels or literal dp**: `36865 = 0x9001` encodes 144 dp; `28161 = 0x6e01` encodes 110 dp.

Booted with SDK platform-tools first on `PATH` and isolated ADB server port 5057, waited for boot, then used `/home/homoludens/Android/Sdk/platform-tools/adb -P 5057 -s emulator-5562 install -r app/build/outputs/apk/google/debug/app-google-debug.apk`. This identical-APK reinstall changed the service values to `min=(66561x28161)` / `minResize=(66561x28161)`. They remained correct after another reboot. No existing app data or launcher database was cleared. The exact reason the earlier install left the old metadata is unproven; the previous ADB version mismatch is not proof of causation.

Android 15's [provider parser](https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/services/appwidget/java/com/android/server/appwidget/AppWidgetServiceImpl.java) stores `TypedValue.data`; its [launcher span calculation](https://android.googlesource.com/platform/packages/apps/Launcher3/+/android-15.0.0_r1/src/com/android/launcher3/widget/LauncherAppWidgetProviderInfo.java) accepts Android 12+ target cell attributes only within the calculated resize limits. Declaring `targetCellHeight=2` alone is therefore not a runtime verification. Here the corrected service metadata, actual [picker](launcher/api35-picker.png), direct placement, and launcher model `span(4,2) minSpan(4,2)` agree. Existing 4×3 instances kept their positions until manually resized, as expected. Selected command output is retained in [API 35 evidence](launcher/api35-evidence.txt).

### Observed results and remaining checks

| Check | Actual result |
| --- | --- |
| Picker / no configuration / empty state | **PASS.** Both previews/descriptions visible; small 2×2 and medium 4×2. Medium **Add** placed directly without configuration. Existing small remained 2×2. [Small empty state](launcher/api35-small-empty-state.png), [new 4×2 medium empty state](launcher/api35-medium-empty-state.png). |
| Resize and hourly columns | **PARTIAL PASS.** Resized an older medium from 4×3 to [4×2 with three hours](launcher/api35-medium-two-rows.png), then [5×2 with four hours](launcher/api35-medium-wide.png). Current weather, dividers, hours, temperatures and probabilities fit at 100% font scale. Host bounds were 768×484 and 968×484 pixels respectively (about 293×184 and 369×184 dp, before host padding). Exact 260×110 dp minimum, small minimum resizing, and the 400 dp/five-hour branch are **NOT RUN**: this portrait grid did not supply those dimensions. |
| Live weather and refresh | **FAILED prompt snapshot update.** Added Zaječar through the existing app location-search/save UI and fetched Open-Meteo weather through the app's normal repository. WorkManager logged selection refresh success at 03:29:05 and manual success at 03:29:39, but at 03:30 both visible cards still showed [no weather / Refreshing](launcher/api35-refresh-snapshot-lag.png). Later session recreation/re-rendering displayed cached weather, sometimes still with an old “Updating” status. This is not a clean refresh/loading-clear pass. Repeated-tap coalescing was **NOT RUN**. |
| Actions | **PARTIAL PASS.** Refresh taps left `com.google.android.apps.nexuslauncher/.NexusLauncherActivity` foreground, including offline. Location-header taps opened the main weather screen. Card-background taps after a resize/removal gesture did not reliably open the app; that hit area needs a manual retest, not a claimed pass. |
| Long name / 130% font scale | **FAILED medium vertical fit.** Location ellipsis preserved refresh, and the small temperature/icon remained visible, but the medium apparent-temperature text ellipsized and precipitation text was visibly clipped at the bottom of its row. [Actual 130% capture](launcher/api35-font-130-clipping.png). Restored font scale to 1.0. Larger display size and other accessibility scales are **NOT RUN**. |
| Explicit dark / Fahrenheit / offline cache | **PARTIAL PASS.** Disabled this AVD's Wi-Fi and mobile data, changed app theme to Dark and units to Fahrenheit, then refreshed. A later render showed dark cards, 49°F with apparent 47°F, retained hourly values, stale status, and the original 03:32 forecast timestamp—not the attempt time. This demonstrates cached presentation, not prompt settings synchronization; the snapshot delay above also affected initial settings updates. Wi-Fi/mobile data were restored afterward. System-theme switching, Light override, provider/selected-location switching, and timed online recovery are **NOT RUN**. |
| Reboot / process death | **PASS for cached restoration.** An AVD reboot while offline preserved all four placed instances, weather, dark/Fahrenheit presentation and the 03:32 timestamp; [offline reboot capture](launcher/api35-offline-reboot.png). Provider metadata remained 110 dp and the new medium remained 4×2. Also killed only this background app process with `am kill`; later refresh re-created it without clearing data. Exact periodic execution timing after reboot is **NOT RUN**. |
| TalkBack | **NOT RUN.** No audible screen-reader interaction was performed. UIAutomator exposed location, unit-bearing temperatures, hourly condition/probability and “Refresh weather” descriptions; this is semantic inspection, not a TalkBack pass. |
| Track Me without GPS | **NOT RUN.** The test used a saved location and did not create/grant a current-location fix. No widget GPS/passive-fix fallback claim is made. |
| Last-widget removal / work cancellation | **NOT RUN / unverified.** ADB drag-and-drop attempts entered resize mode instead of removing the widget. All four QA instances remain; neither last-instance cancellation nor mixed-size removal is claimed. |

The initial System UI “isn't responding” prompt recovered after **Wait**. A fresh task-only API 35 AVD was created from the already-local image as an unused diagnostic fallback; it was not booted or used as evidence. No API 28 image was downloaded and no physical device or unrelated ADB server was touched.

**Snapshot follow-up:** task 4.4 now observes cache-only snapshots inside active Glance compositions. Glance 1.2.0 documents that `update` and `updateAll` do not restart an already-running `provideGlance`, explaining why invalidations alone could leave its captured snapshot visible. Automated long-lived session/RemoteViews regression coverage is separate from the historical device failure above; **launcher verification of the fix is pending**. The APK identified above predates the fix.

**Task 6.5 stays open:** the 4×2 blocker is resolved, but the snapshot fix needs runtime reverification and the font-scale failure remains unresolved. Task 6.3 also stays open: its previously recorded full-suite result is 617/618, not green. The earlier 119 passing widget unit tests are historical, not a newly rerun full widget suite.
