# Widget reference previews

These PNGs are offline Robolectric renders of the widgets' production Glance layouts, **not device screenshots**. The test starts with normalized `WeatherForecast` fixtures, loads them through the cache-only `WeatherWidgetRepository` path with a fixed clock, translates the production small/medium Glance content to Android `RemoteViews`, and captures the rendered views. It does not use a Compose mockup, live service, network, or credentials. Assertions check the shared condition labels and exact shared weather-icon vectors/tints, including night icons in the dark Trieste card and all five night-hour icons in the medium card while its current clear-weather icon stays in daytime mode. The examples reflect the host-adaptive native text introduced in `eefc396`.

| Preview | Contents |
| --- | --- |
| `small-light-zajecar.png` | Light, clear Zaječar; 23°C, feels like 24°C; shared label “Clear sky”. |
| `small-dark-trieste.png` | Dark, partly-cloudy Trieste; 18°C, feels like 17°C; shared night icon. |
| `medium-zajecar-five-hours.png` | Zaječar with current clear-day weather; 22:00–02:00 at 20/18/17/16/15°C and 0/0/10/20/60% precipitation probability using night-hour icons. |

Re-render and validate the examples using the focused Google debug unit test:

```sh
WEATHER_WIDGET_EXAMPLES_DIR="$PWD/docs/widgets/examples" \
ANDROID_HOME=/home/homoludens/Android/Sdk \
ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk \
./gradlew :app:testGoogleDebugUnitTest \
  --tests net.droopia.hluweather.widget.WeatherWidgetReferenceExamplesTest \
  --rerun-tasks
```

The renderer creates only the three named PNGs when they are absent; if an artifact already exists, the test checks that its bytes match the render rather than overwriting it. The test uses Robolectric 4.16.1 with SDK 28 native graphics and the project's Java 21 toolchain. Intentional production layout or text changes can change the images, and native graphics or toolchain differences may also affect bytes; investigate a mismatch rather than treating it as a pass.

To review an intentional update, point `WEATHER_WIDGET_EXAMPLES_DIR` at a new empty directory instead, render in independent runs, compare the resulting bytes, and inspect the three images and fixture assertions. Only then replace the three tracked examples and rerun the command above against the checked-in directory. Never remove the byte assertion to accept a mismatch.
