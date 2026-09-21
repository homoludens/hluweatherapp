Redesign the CURRENT WEATHER CARD in my existing Android weather app to match the attached reference design.

IMPORTANT:
- This is a UI redesign only.
- Do NOT change existing weather fetching, providers, ViewModels, repositories, navigation, location handling, caching, or business logic.
- Reuse the existing weather data/state already available to this screen.
- Do not introduce fake/static weather data except inside @Preview.
- Preserve all existing functionality.
- Follow the existing project's architecture and naming conventions.
- Use Jetpack Compose and Material 3.
- Reuse the app's existing theme, colors, icons and typography where appropriate.

GOAL

Replace the current oversized/simple weather card with a cleaner, information-dense card with strong visual hierarchy.

The hierarchy should be:

1. Location + timestamp
2. Current temperature + condition + weather illustration/icon
3. Compact row of four weather metrics

The card should feel modern, calm and minimal rather than looking like several unrelated text elements.

--------------------------------------------------
CARD
--------------------------------------------------

Use one main rounded card.

Suggested values:
- Corner radius: 20dp
- Internal padding: 16–20dp
- Subtle elevation/shadow
- Avoid heavy borders
- Width: fillMaxWidth()
- Height should wrap content
- Do NOT hard-code a large card height

The component must work on normal Android phone widths (~320dp and above).

Avoid excessive empty vertical space.

--------------------------------------------------
HEADER
--------------------------------------------------

At the top:

[location icon]  Чубура, Градска општина
                 Врачар

                 Mon, Sep 21, 2026 • 10:50

Optionally keep the existing overflow/menu action on the right if the current card already has one.

Location:
- prominent but not oversized
- ~20–22sp
- SemiBold/Bold
- max 2 lines
- ellipsis if necessary

Timestamp:
- ~14sp
- secondary/onSurfaceVariant color
- visually subordinate to location

Use Material Icons / Material Symbols rather than custom bitmap icons where possible.

--------------------------------------------------
CURRENT WEATHER
--------------------------------------------------

Below the header create the main weather area.

LEFT:

21°C

Cloudy

RIGHT:

Large weather condition icon/illustration.

Temperature:
- approximately 60–72sp depending on available width
- Bold
- very prominent
- compact line height

Condition:
- ~20sp
- Medium/SemiBold

The degree symbol should visually belong to the temperature.

Do not let "°C" become detached or wrap.

The weather illustration should use the existing weather icon system if the project already has one.

Do NOT replace existing weather-code → icon logic.

If only standard icons exist, use those rather than adding a new dependency just for illustrations.

Use a responsive Row with weights so temperature and weather icon fit smaller displays.

--------------------------------------------------
METRICS PANEL
--------------------------------------------------

At the bottom create one rounded secondary container containing four metrics horizontally:

Humidity
43%

Dew point
8°C

Feels like
19°C

Precipitation
0 mm

Each metric consists of:

    icon
    label
    value

Use four equal-width columns.

Suggested icons:

Humidity:
water_drop

Dew point:
eco / humidity equivalent already used by the app

Feels like:
thermostat

Precipitation:
umbrella / rainy

Values should have stronger emphasis than labels.

Suggested typography:

Label:
12–14sp
onSurfaceVariant

Value:
16–18sp
Bold/SemiBold

Metric icon:
20–24dp

Optional icon background:
36–40dp circular subtle tinted container

Use subtle vertical dividers between metric columns.

Divider:
- 1dp
- low opacity
- don't visually dominate

Keep the panel compact.

Do not create four separate Cards.

--------------------------------------------------
COLORS
--------------------------------------------------

Respect MaterialTheme.colorScheme.

Do NOT hardcode colors that break dark mode.

Light theme:
- bright card surface
- dark primary text
- muted secondary text
- subtle blue accent

Dark theme:
- dark surface/container
- high-contrast primary text
- muted secondary text
- slightly brighter weather/accent icons

Metric icons may have restrained semantic accents such as:
- humidity: blue
- dew point: green
- feels like: warm/orange
- precipitation: purple/blue

However, derive/adapt these for dark mode and keep saturation subtle.

Do not make the card look multicolored or playful.

--------------------------------------------------
RESPONSIVE BEHAVIOR
--------------------------------------------------

The card must work correctly on:

320dp
360dp
400dp+
large font settings where reasonably possible

Avoid fixed pixel positioning.

Use:
Row
Column
Box
weight()
Arrangement
Alignment

rather than absolute positioning.

Long location names must not push the rest of the layout outside the screen.

Metrics must remain readable on smaller devices.

If necessary:
- reduce metric label font slightly
- allow labels to use two lines
- keep values on one line

Do not horizontally scroll the metrics.

--------------------------------------------------
ACCESSIBILITY
--------------------------------------------------

Maintain adequate text contrast.

Weather information should remain understandable without relying solely on icon color.

Add useful contentDescription values where icons convey meaning.

Decorative icons/illustrations can use null contentDescription when adjacent text already communicates the same information.

Respect reasonable system font scaling.

--------------------------------------------------
DATA
--------------------------------------------------

Use the EXISTING values already supplied by the app:

location
current time / observation time
temperature
condition
humidity
dew point
apparent/feels-like temperature
precipitation
weather code/icon

Do not add API calls.

Do not modify Open-Meteo or MET Norway integration.

Do not duplicate weather models.

Adapt the UI to the project's existing model rather than creating a parallel model simply to satisfy this design.

--------------------------------------------------
COMPONENT STRUCTURE
--------------------------------------------------

Prefer breaking the UI into small composables, for example:

CurrentWeatherCard(...)
WeatherHeader(...)
CurrentConditions(...)
WeatherMetrics(...)
WeatherMetric(...)

Do not over-engineer this.

A possible conceptual API is:

@Composable
fun CurrentWeatherCard(
    ...
    modifier: Modifier = Modifier
)

But inspect the existing code first and adapt the existing composable rather than blindly introducing a duplicate component.

--------------------------------------------------
PREVIEWS
--------------------------------------------------

Add/update @Preview showing approximately:

Location:
"Чубура, Градска општина Врачар"

Temperature:
21°C

Condition:
Cloudy

Humidity:
43%

Dew point:
8°C

Feels like:
19°C

Precipitation:
0 mm

Provide:

@Preview
light theme

and

@Preview
dark theme

if the project already has preview/theme infrastructure.

--------------------------------------------------
IMPORTANT DESIGN DETAILS
--------------------------------------------------

The biggest improvements over the existing card should be:

- stronger typography hierarchy
- less unused space
- consistent alignment
- compact metric presentation
- consistent padding
- cleaner icon treatment
- better dark mode
- responsive sizing
- visually grouping related information

Avoid nested cards everywhere.

Avoid gradients unless the existing app already uses them.

Avoid excessive shadows.

Avoid giant rounded corners.

Avoid making every piece of information the same visual weight.

The temperature should be the strongest visual element.

The location should be the second strongest.

Metric values should be easy to scan.

--------------------------------------------------
IMPLEMENTATION PROCESS
--------------------------------------------------

Before editing:

1. Inspect the existing current-weather composable.
2. Identify where each displayed weather value comes from.
3. Identify the app's existing theme, typography and weather icons.
4. Identify any click handlers/menu actions attached to the current card.
5. Preserve those behaviors.

Then implement the redesign with the smallest reasonable change set.

Do not rewrite unrelated files.

Do not change APIs or state management just to accommodate the redesign.

After implementation:

1. Run formatting.
2. Compile the Android app.
3. Fix any Compose/compiler errors.
4. Run existing relevant tests.
5. Check both light and dark theme.
6. Check the layout at narrow phone width.
7. Ensure no text or metric is clipped.

At the end, report:

- files changed
- main UI changes
- whether any existing behavior was modified
- build/test result

Existing behavior should ideally be reported as:
"No functional behavior changed; UI only."
