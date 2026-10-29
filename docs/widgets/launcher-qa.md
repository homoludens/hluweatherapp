# Home-screen widgets: usage and launcher QA

## Add and use

Long-press an empty area of the home screen, open **Widgets**, search for HluWeather (shown as **HluWeatherApp** on the tested Pixel Launcher), and add either preview:

- Provider labels declared by the app: **HluWeather · Current weather** (2×2 target; current conditions only) and **HluWeather · Hourly forecast** (4×2 target; current conditions plus three to five upcoming hours).
- On API 35 Pixel Launcher, the picker groups both under **HluWeatherApp**. The current medium provider declares a 4×2 target and 260×110 dp minimum, but the launcher still shows and places it as **4 × 3** after installing the corrected APK and rebooting the AVD. This is an unresolved runtime mismatch, not a 4×2 pass; see the dated result below.

Both providers are declared resizable and have no configuration activity. They share the app's selected provider, location, temperature unit, and theme. Change those in HluWeather; installed widgets re-render from matching cached weather. Explicit Light/Dark app themes take precedence over Android appearance; System follows it. Tapping the card or location opens the main weather screen. The separate refresh control requests an update without opening the app.

Widgets render matching cached weather first. Refresh attempts preserve the forecast's actual update time; offline or failed refreshes keep matching data and identify stale/failed status. Shared periodic work is nominally every 60 minutes with a connected-network constraint; Doze, connectivity, and Android scheduling can delay it. The interval is approximate, not a deadline.

In Track Me mode, the widget uses a fix the app already obtained, including a passively persisted last-known fix after process death. It never requests GPS or starts tracking for a widget. If no valid fix exists, it falls back to the selected saved location, then the first saved location; with none, it prompts the user to choose a location in the app.

## Launcher QA checklist

- [ ] **Picker and placement:** confirm both previews, the actual launcher labels, descriptions, and sizes; compare with the declared provider labels above. Add one of each and confirm neither launches a configuration step.
- [ ] **Resize:** exercise the small widget's minimum (110×110 dp) and compact/normal/wide branches; keep location, icon, temperature, and refresh visible, with secondary rows dropped before clipping. Resize medium down to its compact 260×110 dp minimum and across three-, four-, and five-hour branches (about 260, 320, and 400 dp wide at 1× font scale); verify dividers and no horizontal overflow. API 35 currently reports 4×3 for medium and has not verified compact-height or width branches; see runtime result. The small widget has no hourly strip.
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
| API 35 Pixel Launcher | **BLOCKED — required 4×2 runtime size failed on 2026-10-29.** Used the existing task AVD `hluweather-task6-5-api35` (Pixel 2 profile, Google APIs, API 35). Installed `app/build/outputs/apk/google/debug/app-google-debug.apk` with `adb install -r` (no uninstall or data clear); APK SHA-256: `77405d803e53d3650e63d1da95fd68ee29f2c92d307ea6de4e4d64bb499c865f`. After a normal AVD reboot, the actual Pixel Launcher picker showed **2 × 2** for small and **4 × 3** for medium. The medium picker's **Add** action placed it directly, without configuration, at 4×3—not the required 4×2. This remains a genuine mismatch even though the installed provider XML is `minWidth=260dp`, `minHeight=minResizeHeight=110dp`, and `targetCellWidth=4`/`targetCellHeight=2`; `dumpsys appwidget` continued to report a 144 dp medium minimum after reboot. Do not treat the packaged XML or preview as a runtime pass. Captures: [actual picker](launcher/api35-picker.png), [small empty state](launcher/api35-small-empty-state.png), and [placed medium empty state](launcher/api35-medium-empty-state.png). |

The existing small instance remained visible at 2×2 after the APK update. The placed medium widget rendered the no-location state without visible overflow at its actual 4×3 size. Tapping its refresh control left `com.google.android.apps.nexuslauncher/.NexusLauncherActivity` in the foreground. The AVD reboot preserved the pre-existing widget instances. A medium resize overlay appeared, but ADB drag attempts did not change the bounds; compact two-row resizing and the available 260/320/400 dp width branches are **NOT RUN / unverified**. The emulator log showed its startup helper using `/home/homoludens/bin/adb` v36 while SDK platform-tools v41 was in use, causing an ADB server-version mismatch; repeat resize checks with one consistent ADB client before attributing that gesture result to the widget. No saved location was configured, so live weather, cache/offline/failure, settings/theme/unit sync, and Track Me checks were **NOT RUN**; no provider request was made. TalkBack/font scaling and last-widget work cancellation are also **NOT RUN**. An initial Android System UI “isn't responding” prompt recovered after choosing **Wait**; later launcher checks proceeded. Robolectric examples/tests are not substitutes for these device checks.
