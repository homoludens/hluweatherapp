# Home-screen widgets: usage and launcher QA

## Add and use

Long-press an empty area of the home screen, open **Widgets**, search for HluWeather (shown as **HluWeatherApp** on the tested Pixel Launcher), and add either preview:

- Provider labels declared by the app: **HluWeather · Current weather** (2×2 target; current conditions only) and **HluWeather · Hourly forecast** (4×2 target; current conditions plus three to five upcoming hours).
- On the tested API 35 Pixel Launcher, the picker groups both under the app label **HluWeatherApp** and shows **2 × 2** and **4 × 3**. The medium provider declares `targetCellHeight=2`, but this launcher displayed 4 × 3, so its picker result did not match the 4×2 target; this is recorded, not counted as a pass. Verify on the target launcher.

Both are resizable and need no configuration screen. They share the app's selected provider, location, temperature unit, and theme. Change those in HluWeather; installed widgets re-render from matching cached weather. Explicit Light/Dark app themes take precedence over Android appearance; System follows it. Tapping the card or location opens the main weather screen. The separate refresh control requests an update without opening the app.

Widgets render matching cached weather first. Refresh attempts preserve the forecast's actual update time; offline or failed refreshes keep matching data and identify stale/failed status. Shared periodic work is nominally every 60 minutes with a connected-network constraint; Doze, connectivity, and Android scheduling can delay it. The interval is approximate, not a deadline.

In Track Me mode, the widget uses a fix the app already obtained, including a passively persisted last-known fix after process death. It never requests GPS or starts tracking for a widget. If no valid fix exists, it falls back to the selected saved location, then the first saved location; with none, it prompts the user to choose a location in the app.

## Launcher QA checklist

- [ ] **Picker and placement:** confirm both previews, the actual launcher labels, descriptions, and sizes; compare with the declared provider labels above. Add one of each and confirm neither launches a configuration step.
- [ ] **Resize:** exercise the small widget's minimum (110×110 dp) and compact/normal/wide branches; keep location, icon, temperature, and refresh visible, with secondary rows dropped before clipping. Resize medium down to its compact 260×110 dp minimum (after provider metadata permits that height) and across three-, four-, and five-hour branches (about 260, 320, and 400 dp wide at 1× font scale); verify dividers and no horizontal overflow. The small widget has no hourly strip.
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
| API 28 launcher | **NOT RUN** — no API 28 device, AVD, platform, or system image was available locally; no download was attempted. |
| API 35 Pixel Launcher | **PARTIAL — Pixel 2 AVD, 2026-10-29.** Installed the existing Google debug APK in a fresh local Google APIs image. The picker showed both previews; its app/group label was `HluWeatherApp`, with sizes 2 × 2 and 4 × 3 (medium-size discrepancy above). Added both without a configuration screen; both rendered the no-location prompt. Tapping small-widget refresh left the launcher foreground. Captures: [picker](launcher/api35-picker.png), [small empty state](launcher/api35-small-empty-state.png), [medium empty state](launcher/api35-medium-empty-state.png). No saved location/cache was set, so no provider request was made. |

The API 35 runtime did **not** exercise resizing branches, TalkBack/font scaling, theme or settings synchronization, offline cache/failure, Track Me fallback, process death/reboot, or last-widget work cancellation. Use the checklist above for those device checks; Robolectric renders/tests are not substitutes.
