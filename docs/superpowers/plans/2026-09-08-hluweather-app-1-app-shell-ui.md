# HluWeatherApp Plan 1: App Shell and Mocked Weather UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a compilable Android app that matches the HluWeatherApp light and dark design using deterministic mock weather data.

**Architecture:** Single Gradle `app` module with a Compose UI layer, a small data layer containing mock forecast logic, and `StateFlow`-based ViewModels. This plan produces a working app shell, theme, mocked Hourly and Daily UI, Map placeholder, and Settings navigation.

**Tech Stack:** Kotlin 2.4.20, AGP 8.13.2, Gradle 8.14.3, Compose BOM 2026.06.01, Material 3, Navigation Compose 2.9.8, Lifecycle 2.10.0, kotlinx-datetime 0.8.0, JUnit 4, Robolectric 4.16.1.

**Spec:** `/home/homoludens/projekti/hluweatherapp/docs/superpowers/specs/2026-09-08-hluweather-app-design.md`

## Global Constraints

- Application ID: `net.droopia.hluweather`
- `minSdk = 28`
- compileSdk and `targetSdk = 36`
- Single Android module named `app`
- Package root: `net.droopia.hluweather`
- Android SDK location: `/home/homoludens/Android/Sdk`
- No networking in this plan
- No location permissions in this plan
- No API keys
- English strings only
- Temperatures are Celsius and precipitation is millimetres
- Every task must leave `./gradlew :app:testDebugUnitTest` passing
- Commit after every verified task

This is Plan 1 of a three-plan decomposition:

1. App shell and mocked weather UI
2. Real weather providers, settings, persistence, and cache
3. Locations, MapLibre/OpenFreeMap, GPS, reverse geocoding, and Track Me

---

### Task 1: Scaffold Gradle project and smoke-test app

**Files:**
- Create: `.gitignore`
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `local.properties`
- Create: `app/build.gradle.kts`
- Create: `app/proguard-rules.pro`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/java/net/droopia/hluweather/MainActivity.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/theme/Theme.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt`

**Interfaces:**
- Consumes: nothing
- Produces: `HluWeatherApp()`, `HluWeatherTheme(darkTheme: Boolean, content: @Composable () -> Unit)`, and a Gradle project that runs JVM Compose tests through Robolectric

- [ ] **Step 1: Initialize git**

```bash
cd /home/homoludens/projekti/hluweatherapp
git init -b main
```

- [ ] **Step 2: Create `.gitignore`**

```text
.gradle/
build/
local.properties
.idea/
.DS_Store
captures/
.externalNativeBuild/
.cxx/
*.apk
*.aab
*.apk.idsig
```

- [ ] **Step 3: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "HluWeatherApp"
include(":app")
```

- [ ] **Step 4: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 5: Create `gradle.properties`**

```text
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

- [ ] **Step 6: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.13.2"
kotlin = "2.4.20"
composeBom = "2026.06.01"
activityCompose = "1.13.0"
coreKtx = "1.18.0"
lifecycle = "2.10.0"
navigationCompose = "2.9.8"
coroutines = "1.11.0"
kotlinxDateTime = "0.8.0"
junit = "4.13.2"
robolectric = "4.16.1"
androidxTestCore = "1.7.0"

[libraries]
core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-graphics = { module = "androidx.compose.ui:ui-graphics" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinxDateTime" }
junit = { module = "junit:junit", version.ref = "junit" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-test-core = { module = "androidx.test:core-ktx", version.ref = "androidxTestCore" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 7: Create `local.properties`**

```text
sdk.dir=/home/homoludens/Android/Sdk
```

- [ ] **Step 8: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "net.droopia.hluweather"
    compileSdk = 36

    defaultConfig {
        applicationId = "net.droopia.hluweather"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
}
```

- [ ] **Step 9: Create `app/proguard-rules.pro`**

Create an empty file.

- [ ] **Step 10: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="true"
        android:icon="@android:drawable/sym_def_app_icon"
        android:label="@string/app_name"
        android:theme="@style/Theme.HluWeather">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 11: Create `app/src/main/res/values/strings.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">HluWeatherApp</string>
</resources>
```

- [ ] **Step 12: Create `app/src/main/res/values/themes.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.HluWeather" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 13: Create `app/src/main/java/net/droopia/hluweather/MainActivity.kt`**

```kotlin
package net.droopia.hluweather

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import net.droopia.hluweather.ui.app.HluWeatherApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HluWeatherApp()
        }
    }
}
```

- [ ] **Step 14: Create `app/src/main/java/net/droopia/hluweather/ui/theme/Theme.kt`**

```kotlin
package net.droopia.hluweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme()
private val DarkColors = darkColorScheme()

@Composable
fun HluWeatherTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
```

- [ ] **Step 15: Create `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt`**

```kotlin
package net.droopia.hluweather.ui.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import net.droopia.hluweather.ui.theme.HluWeatherTheme

@Composable
fun HluWeatherApp() {
    HluWeatherTheme(darkTheme = false) {
        Text(
            text = "HluWeatherApp",
            modifier = Modifier.fillMaxSize()
        )
    }
}
```

- [ ] **Step 16: Create `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt`**

```kotlin
package net.droopia.hluweather.ui.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HluWeatherAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun displays_app_title() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluWeatherApp()
            }
        }
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }
}
```

- [ ] **Step 17: Generate Gradle wrapper**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
curl -fsSL https://services.gradle.org/distributions/gradle-8.14.3-bin.zip -o /tmp/gradle-8.14.3-bin.zip
unzip -q -o /tmp/gradle-8.14.3-bin.zip -d /tmp/gradle-distribution
/tmp/gradle-distribution/gradle-8.14.3/bin/gradle wrapper --gradle-version 8.14.3 --distribution-type bin
```

Expected: Gradle wrapper files are created.

- [ ] **Step 18: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL and `HluWeatherAppTest` passes.

- [ ] **Step 19: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "chore: scaffold HluWeatherApp"
```

---

### Task 2: Implement exact light and dark theme palettes

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ComposeTestActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/java/net/droopia/hluweather/ui/theme/Color.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/theme/Theme.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/theme/ThemeTest.kt`

**Interfaces:**
- Consumes: `HluWeatherTheme`
- Produces: `HluColors`, `LocalHluColors`, `LightHluColors`, `DarkHluColors`, `ComposeTestActivity`

- [ ] **Step 0: Add a non-exported Compose test activity**

Create `app/src/main/java/net/droopia/hluweather/ComposeTestActivity.kt`:

```kotlin
package net.droopia.hluweather

import androidx.activity.ComponentActivity

class ComposeTestActivity : ComponentActivity()
```

Add to `app/src/main/AndroidManifest.xml` inside `<application>`:

```xml
<activity
    android:name=".ComposeTestActivity"
    android:exported="false" />
```

This activity is the host for all unit-test Compose rules that call `setContent`. `MainActivity` must remain the production launcher activity and must always install `HluWeatherApp` without test-environment branches.

- [ ] **Step 1: Write failing theme test**

Create `app/src/test/java/net/droopia/hluweather/ui/theme/ThemeTest.kt`:

```kotlin
package net.droopia.hluweather.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import net.droopia.hluweather.ComposeTestActivity
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThemeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    private var capturedHluColors: HluColors? = null
    private var capturedScheme: ColorScheme? = null

    @Test
    fun light_theme_uses_expected_primary_and_hero_colors() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                captureColors { hluColors, colorScheme ->
                    capturedHluColors = hluColors
                    capturedScheme = colorScheme
                }
            }
        }

        assertEquals(Color(0xFF5367E8), capturedScheme?.primary)
        assertEquals(Color(0xFF6779ED), capturedHluColors?.heroTop)
        assertEquals(Color(0xFF4D55C6), capturedHluColors?.heroBottom)
    }

    @Test
    fun dark_theme_uses_expected_primary_and_hero_colors() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = true) {
                captureColors { hluColors, colorScheme ->
                    capturedHluColors = hluColors
                    capturedScheme = colorScheme
                }
            }
        }

        assertEquals(Color(0xFF9AA7FF), capturedScheme?.primary)
        assertEquals(Color(0xFF071225), capturedHluColors?.heroTop)
        assertEquals(Color(0xFF10264B), capturedHluColors?.heroBottom)
    }

    @Composable
    private fun captureColors(
        onColors: (HluColors, ColorScheme) -> Unit
    ) {
        onColors(LocalHluColors.current, MaterialTheme.colorScheme)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.theme.ThemeTest
```

Expected: FAIL because `HluColors` and `LocalHluColors` do not exist.

- [ ] **Step 3: Create `app/src/main/java/net/droopia/hluweather/ui/theme/Color.kt`**

```kotlin
package net.droopia.hluweather.ui.theme

import androidx.compose.ui.graphics.Color

data class HluColors(
    val heroTop: Color,
    val heroBottom: Color,
    val heroText: Color,
    val heroSecondaryText: Color,
    val mountain: Color,
    val moon: Color,
    val moonAccent: Color,
    val cloudAccent: Color,
    val navSelected: Color,
    val navSelectedText: Color,
    val weatherCard: Color,
    val tableHeader: Color,
    val tableRow: Color,
    val daySelected: Color,
    val daySelectedText: Color
)

val LightHluColors = HluColors(
    heroTop = Color(0xFF6779ED),
    heroBottom = Color(0xFF4D55C6),
    heroText = Color(0xFFFFFFFF),
    heroSecondaryText = Color(0xFFE4E7FF),
    mountain = Color(0xFF242B7A),
    moon = Color(0xFFFFF0BD),
    moonAccent = Color(0xFF5865DA),
    cloudAccent = Color(0xFF9FADEB),
    navSelected = Color(0xFFF5F6FF),
    navSelectedText = Color(0xFF3040A7),
    weatherCard = Color(0xFFFBFBFE),
    tableHeader = Color(0xFFEFF1FA),
    tableRow = Color(0xFFFAFBFD),
    daySelected = Color(0xFF5969E9),
    daySelectedText = Color(0xFFFFFFFF)
)

val DarkHluColors = HluColors(
    heroTop = Color(0xFF071225),
    heroBottom = Color(0xFF10264B),
    heroText = Color(0xFFF5F7FF),
    heroSecondaryText = Color(0xFFB8C4EA),
    mountain = Color(0xFF020A18),
    moon = Color(0xFFE4D7B7),
    moonAccent = Color(0xFF7789FF),
    cloudAccent = Color(0xFF8493D7),
    navSelected = Color(0xFF9DA8FF),
    navSelectedText = Color(0xFF131A3C),
    weatherCard = Color(0xFF111C2D),
    tableHeader = Color(0xFF1A2740),
    tableRow = Color(0xFF101A2B),
    daySelected = Color(0xFF8492FF),
    daySelectedText = Color(0xFF101632)
)
```

- [ ] **Step 4: Replace `app/src/main/java/net/droopia/hluweather/ui/theme/Theme.kt`**

```kotlin
package net.droopia.hluweather.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF5367E8),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF13162A),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF171A2C),
    surfaceVariant = Color(0xFFEDEFFC),
    onSurfaceVariant = Color(0xFF5D6278),
    outlineVariant = Color(0xFFDDE0EB)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AA7FF),
    onPrimary = Color(0xFF101532),
    background = Color(0xFF09111E),
    onBackground = Color(0xFFE8EDFF),
    surface = Color(0xFF101A2B),
    onSurface = Color(0xFFF2F4FF),
    surfaceVariant = Color(0xFF18243A),
    onSurfaceVariant = Color(0xFFB7C0D9),
    outlineVariant = Color(0xFF26334B)
)

val LocalHluColors = staticCompositionLocalOf<HluColors> {
    error("HluColors not provided")
}

@Composable
fun HluWeatherTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val materialColors = if (darkTheme) DarkColors else LightColors
    val hluColors = if (darkTheme) DarkHluColors else LightHluColors

    CompositionLocalProvider(LocalHluColors provides hluColors) {
        MaterialTheme(
            colorScheme = materialColors,
            content = content
        )
    }
}
```

- [ ] **Step 5: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.theme.ThemeTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add HluWeather light and dark theme"
```

---

### Task 3: Add shared forecast models, moon calculation, and formatters

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/model/WeatherLocation.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/model/WeatherProvider.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/model/WeatherCondition.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/model/ForecastMode.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/model/ThemeMode.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/model/WeatherForecast.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/MoonPhaseCalculator.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/Format.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/MoonPhaseCalculatorTest.kt`
- Create: `app/src/test/java/net/droopia/hluweather/data/FormatTest.kt`

**Interfaces:**
- Consumes: kotlinx-datetime
- Produces: `WeatherLocation`, `WeatherProvider`, `WeatherCondition`, `ForecastMode`, `ThemeMode`, `CurrentWeather`, `HourForecast`, `DayForecast`, `WeatherForecast`, `MoonPhaseCalculator.phase`, and formatter extensions

- [ ] **Step 1: Write failing moon-phase tests**

Create `app/src/test/java/net/droopia/hluweather/data/MoonPhaseCalculatorTest.kt`:

```kotlin
package net.droopia.hluweather.data

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoonPhaseCalculatorTest {

    @Test
    fun known_new_moon_is_zero() {
        val newMoon = Instant.fromEpochSeconds(947_182_440L)

        assertEquals(0.0, MoonPhaseCalculator.phase(newMoon), 0.001)
    }

    @Test
    fun known_full_moon_is_about_one_half() {
        val fullMoon = Instant.fromEpochSeconds(948_472_200L)

        assertEquals(0.5, MoonPhaseCalculator.phase(fullMoon), 0.03)
    }

    @Test
    fun phase_stays_between_zero_and_one() {
        val start = Instant.fromEpochSeconds(0L)

        repeat(100) { index ->
            val instant = Instant.fromEpochSeconds(start.epochSeconds + index * 86_400L)
            val phase = MoonPhaseCalculator.phase(instant)
            assertTrue(phase >= 0.0)
            assertTrue(phase < 1.0)
        }
    }
}
```

- [ ] **Step 2: Write failing formatter tests**

Create `app/src/test/java/net/droopia/hluweather/data/FormatTest.kt`:

```kotlin
package net.droopia.hluweather.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId

class FormatTest {

    @Test
    fun temperature_rounds_to_whole_degrees() {
        assertEquals("21°", 21.4.temperatureText())
        assertEquals("22°", 21.6.temperatureText())
    }

    @Test
    fun missing_optional_value_shows_dash() {
        val value: Double? = null
        assertEquals("—", value.temperatureText())
    }

    @Test
    fun humidity_formats_percent() {
        assertEquals("51%", 51.percentText())
    }

    @Test
    fun precipitation_formats_millimetres() {
        assertEquals("0 mm", 0.2.precipitationText())
        assertEquals("1 mm", 0.8.precipitationText())
    }

    @Test
    fun hour_formats_in_requested_zone() {
        val instant = Instant.fromEpochSeconds(0L)

        assertEquals("00h", instant.hourText(ZoneId.of("UTC")))
    }

    @Test
    fun day_formats_in_english() {
        val date = LocalDate(2026, 9, 7)

        assertEquals("Mon, Sep 7", date.dayText(ZoneId.of("UTC")))
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests "net.droopia.hluweather.data.*"
```

Expected: FAIL because model and formatter classes do not exist.

- [ ] **Step 4: Create model files**

Create `app/src/main/java/net/droopia/hluweather/data/model/WeatherLocation.kt`:

```kotlin
package net.droopia.hluweather.data.model

data class WeatherLocation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int? = null
)
```

Create `app/src/main/java/net/droopia/hluweather/data/model/WeatherProvider.kt`:

```kotlin
package net.droopia.hluweather.data.model

enum class WeatherProvider(
    val title: String
) {
    OPEN_METEO("Open-Meteo"),
    MET_NO("MET Norway")
}
```

Create `app/src/main/java/net/droopia/hluweather/data/model/WeatherCondition.kt`:

```kotlin
package net.droopia.hluweather.data.model

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

fun WeatherCondition.label(): String = when (this) {
    WeatherCondition.CLEAR -> "Clear sky"
    WeatherCondition.MOSTLY_CLEAR -> "Fair"
    WeatherCondition.PARTLY_CLOUDY -> "Partly cloudy"
    WeatherCondition.CLOUDY -> "Cloudy"
    WeatherCondition.FOG -> "Fog"
    WeatherCondition.DRIZZLE -> "Drizzle"
    WeatherCondition.RAIN -> "Rain"
    WeatherCondition.SNOW -> "Snow"
    WeatherCondition.THUNDERSTORM -> "Thunderstorm"
    WeatherCondition.UNKNOWN -> "Unknown"
}
```

Create `app/src/main/java/net/droopia/hluweather/data/model/ForecastMode.kt`:

```kotlin
package net.droopia.hluweather.data.model

enum class ForecastMode {
    HOURLY,
    DAILY,
    MAP
}
```

Create `app/src/main/java/net/droopia/hluweather/data/model/ThemeMode.kt`:

```kotlin
package net.droopia.hluweather.data.model

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
```

Create `app/src/main/java/net/droopia/hluweather/data/model/WeatherForecast.kt`:

```kotlin
package net.droopia.hluweather.data.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class CurrentWeather(
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)

data class HourForecast(
    val time: Instant,
    val temperature: Double,
    val apparentTemperature: Double?,
    val humidity: Int?,
    val dewPoint: Double?,
    val precipitation: Double,
    val precipitationProbability: Int?,
    val condition: WeatherCondition,
    val isDay: Boolean?
)

data class DayForecast(
    val date: LocalDate,
    val condition: WeatherCondition,
    val temperatureMin: Double,
    val temperatureMax: Double,
    val precipitation: Double?,
    val sunrise: Instant?,
    val sunset: Instant?
)

data class WeatherForecast(
    val location: WeatherLocation,
    val provider: WeatherProvider,
    val fetchedAt: Instant,
    val current: CurrentWeather,
    val hourly: List<HourForecast>,
    val daily: List<DayForecast>,
    val moonPhase: Double
)
```

- [ ] **Step 5: Create `app/src/main/java/net/droopia/hluweather/data/MoonPhaseCalculator.kt`**

```kotlin
package net.droopia.hluweather.data

import kotlinx.datetime.Instant

object MoonPhaseCalculator {

    private val NEW_MOON = Instant.fromEpochSeconds(947_182_440L)
    private const val SYNODIC_MONTH_SECONDS = 29.53058867 * 86_400.0

    fun phase(now: Instant): Double {
        val deltaSeconds =
            (now.toEpochMilliseconds() - NEW_MOON.toEpochMilliseconds()) / 1000.0
        val shifted = (deltaSeconds % SYNODIC_MONTH_SECONDS + SYNODIC_MONTH_SECONDS) %
            SYNODIC_MONTH_SECONDS
        return shifted / SYNODIC_MONTH_SECONDS
    }
}
```

- [ ] **Step 6: Create `app/src/main/java/net/droopia/hluweather/data/Format.kt`**

```kotlin
package net.droopia.hluweather.data

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val hourFormatter =
    DateTimeFormatter.ofPattern("HH'h'", java.util.Locale.US)

private val dateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d, yyyy • HH:mm", java.util.Locale.US)

private val dayFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d", java.util.Locale.US)

fun Double?.temperatureText(): String =
    this?.roundToInt()?.let { "$it°" } ?: "—"

fun Int?.percentText(): String =
    this?.let { "$it%" } ?: "—"

fun Double?.precipitationText(): String =
    this?.let { "${it.roundToInt()} mm" } ?: "—"

fun Instant.hourText(zone: ZoneId = ZoneId.systemDefault()): String =
    toJavaInstant().atZone(zone).format(hourFormatter)

fun Instant.dateTimeText(zone: ZoneId = ZoneId.systemDefault()): String =
    toJavaInstant().atZone(zone).format(dateTimeFormatter)

fun LocalDate.dayText(zone: ZoneId = ZoneId.systemDefault()): String =
    toJavaDate().atStartOfDay(zone).format(dayFormatter)

fun Instant.toAppLocalDate(): LocalDate =
    toLocalDateTime(TimeZone.currentSystemDefault()).date

private fun Instant.toJavaInstant(): java.time.Instant =
    java.time.Instant.ofEpochMilli(toEpochMilliseconds())

private fun LocalDate.toJavaDate(): java.time.LocalDate =
    java.time.LocalDate.of(year, monthNumber, dayOfMonth)
```

- [ ] **Step 7: Run tests**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests "net.droopia.hluweather.data.*"
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add weather models, moon phase, and formatters"
```

---

### Task 4: Add mock repository and weather ViewModel

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/WeatherRepository.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/MockWeatherRepository.kt`
- Create: `app/src/main/java/net/droopia/hluweather/data/repository/MockForecast.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt`

**Interfaces:**
- Consumes: weather models and `MoonPhaseCalculator`
- Produces: `WeatherRepository`, `MockWeatherRepository`, `buildMockForecast`, `WeatherUiState`, and `WeatherViewModel`

- [ ] **Step 1: Write failing ViewModel test**

Create `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherViewModelTest.kt`:

```kotlin
package net.droopia.hluweather.ui.weather

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.repository.MockWeatherRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherViewModelTest {

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loads_mock_forecast_for_default_location() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        assertNotNull(viewModel.state.value.forecast)
        assertEquals(false, viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun changes_forecast_mode() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        viewModel.onForecastModeSelected(ForecastMode.DAILY)

        assertEquals(ForecastMode.DAILY, viewModel.state.value.forecastMode)
    }

    @Test
    fun day_selection_returns_to_hourly_mode() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        viewModel.onForecastModeSelected(ForecastMode.DAILY)
        viewModel.onDaySelected(2)

        assertEquals(ForecastMode.HOURLY, viewModel.state.value.forecastMode)
        assertEquals(2, viewModel.state.value.selectedDayIndex)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest
```

Expected: FAIL because `WeatherViewModel` and `MockWeatherRepository` do not exist.

- [ ] **Step 3: Create `app/src/main/java/net/droopia/hluweather/data/repository/WeatherRepository.kt`**

```kotlin
package net.droopia.hluweather.data.repository

import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation

interface WeatherRepository {
    suspend fun getForecast(location: WeatherLocation): WeatherForecast
}
```

- [ ] **Step 4: Create `app/src/main/java/net/droopia/hluweather/data/repository/MockForecast.kt`**

```kotlin
package net.droopia.hluweather.data.repository

import kotlin.time.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import net.droopia.hluweather.data.MoonPhaseCalculator
import net.droopia.hluweather.data.model.CurrentWeather
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.model.WeatherProvider

val Svilajnac = WeatherLocation(
    id = "svilajnac",
    name = "Svilajnac",
    latitude = 44.2380,
    longitude = 21.1970,
    altitude = 105
)

fun buildMockForecast(
    location: WeatherLocation,
    baseTime: Instant = Clock.System.now()
): WeatherForecast {
    val startDate =
        baseTime.toLocalDateTime(TimeZone.currentSystemDefault()).date

    val daily = (0 until 7).map { offset ->
        DayForecast(
            date = startDate.plus(offset, DateTimeUnit.DAY),
            condition = WeatherCondition.CLEAR,
            temperatureMin = 16.0,
            temperatureMax = 30.0,
            precipitation = 0.0,
            sunrise = null,
            sunset = null
        )
    }

    val hourly = (0 until 7 * 24).map { index ->
        val temperature = when (index % 24) {
            in 0..5 -> 17.0
            in 6..8 -> 20.0
            in 9..11 -> 25.0
            in 12..15 -> 30.0
            in 16..19 -> 27.0
            else -> 22.0
        }
        HourForecast(
            time = Instant.fromEpochSeconds(baseTime.epochSeconds + index * 3600L),
            temperature = temperature,
            apparentTemperature = temperature,
            humidity = 50,
            dewPoint = 10.0,
            precipitation = 0.0,
            precipitationProbability = null,
            condition = if (index % 24 == 14) {
                WeatherCondition.PARTLY_CLOUDY
            } else {
                WeatherCondition.CLEAR
            },
            isDay = (index % 24) in 6..19
        )
    }

    return WeatherForecast(
        location = location,
        provider = WeatherProvider.OPEN_METEO,
        fetchedAt = baseTime,
        current = CurrentWeather(
            temperature = 21.0,
            apparentTemperature = 21.0,
            humidity = 51,
            dewPoint = 10.0,
            precipitation = 0.0,
            condition = WeatherCondition.CLEAR,
            isDay = false
        ),
        hourly = hourly,
        daily = daily,
        moonPhase = MoonPhaseCalculator.phase(baseTime)
    )
}
```

- [ ] **Step 5: Create `app/src/main/java/net/droopia/hluweather/data/repository/MockWeatherRepository.kt`**

```kotlin
package net.droopia.hluweather.data.repository

import kotlin.time.Clock
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation

class MockWeatherRepository(
    private val baseTime: Instant = Clock.System.now()
) : WeatherRepository {

    override suspend fun getForecast(location: WeatherLocation): WeatherForecast =
        buildMockForecast(location, baseTime)
}
```

- [ ] **Step 6: Create `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`**

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.WeatherRepository

data class WeatherUiState(
    val activeLocation: WeatherLocation? = null,
    val forecast: WeatherForecast? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val forecastMode: ForecastMode = ForecastMode.HOURLY,
    val selectedDayIndex: Int = 0
)

class WeatherViewModel(
    private val repository: WeatherRepository,
    private val location: WeatherLocation = Svilajnac
) : ViewModel() {

    private val _state = MutableStateFlow(
        WeatherUiState(
            activeLocation = location,
            isLoading = true
        )
    )

    val state = _state.asStateFlow()

    init {
        load()
    }

    fun refresh() {
        load()
    }

    fun onForecastModeSelected(mode: ForecastMode) {
        _state.update { it.copy(forecastMode = mode) }
    }

    fun onDaySelected(index: Int) {
        _state.update {
            it.copy(
                forecastMode = ForecastMode.HOURLY,
                selectedDayIndex = index.coerceIn(0, 6)
            )
        }
    }

    private fun load() {
        val currentLocation = location
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    activeLocation = currentLocation,
                    error = null
                )
            }
            runCatching {
                repository.getForecast(currentLocation)
            }.onSuccess { forecast ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        forecast = forecast,
                        error = null
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error.message ?: "Weather request failed"
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 7: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.WeatherViewModelTest
```

Expected: PASS.

- [ ] **Step 8: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add mocked weather ViewModel"
```

---

### Task 5: Build weather hero and forecast-mode navigation

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherHero.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherHeroTest.kt`

**Interfaces:**
- Consumes: `ForecastMode` and `HluWeatherTheme`
- Produces: `WeatherHero(selected: ForecastMode, onSelected: (ForecastMode) -> Unit, onSettingsClick: () -> Unit)`

- [ ] **Step 1: Write failing hero test**

Create `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherHeroTest.kt`:

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherHeroTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_title_and_tabs() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = {},
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithText("Simple weather. Clear view.").assertIsDisplayed()
        composeRule.onNodeWithText("Hourly").assertIsDisplayed()
        composeRule.onNodeWithText("Daily").assertIsDisplayed()
        composeRule.onNodeWithText("Map").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun tab_click_reports_selected_mode() {
        var selected: ForecastMode? = null

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherHero(
                    selected = ForecastMode.HOURLY,
                    onSelected = { selected = it },
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Daily").performClick()
        assertEquals(ForecastMode.DAILY, selected)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.WeatherHeroTest
```

Expected: FAIL because `WeatherHero` does not exist.

- [ ] **Step 3: Create `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherHero.kt`**

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun WeatherHero(
    selected: ForecastMode,
    onSelected: (ForecastMode) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.heroTop,
                        colors.heroBottom
                    )
                )
            )
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .align(Alignment.TopEnd)
                .offset(x = (-65).dp, y = 65.dp)
                .clip(CircleShape)
                .background(colors.moon)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.mountain.copy(alpha = 0.25f),
                            colors.mountain.copy(alpha = 0.75f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 24.dp,
                    end = 24.dp,
                    top = 24.dp
                )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "HluWeatherApp",
                        color = colors.heroText,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Simple weather. Clear view.",
                        color = colors.heroSecondaryText,
                        fontSize = 17.sp
                    )
                }

                IconButton(onClick = onSettingsClick) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = colors.heroText
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            NavigationTabs(
                selected = selected,
                onSelected = onSelected
            )
        }
    }
}

@Composable
private fun NavigationTabs(
    selected: ForecastMode,
    onSelected: (ForecastMode) -> Unit
) {
    val items = listOf(
        Triple(ForecastMode.HOURLY, "Hourly", Icons.Outlined.Schedule),
        Triple(ForecastMode.DAILY, "Daily", Icons.Outlined.BarChart),
        Triple(ForecastMode.MAP, "Map", Icons.Outlined.LocationOn)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        items.forEach { (mode, title, icon) ->
            WeatherNavButton(
                text = title,
                icon = icon,
                selected = selected == mode,
                onClick = { onSelected(mode) }
            )
        }
    }
}

@Composable
private fun WeatherNavButton(
    text: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalHluColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = if (selected) colors.navSelected else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 20.dp,
                vertical = 14.dp
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    colors.navSelectedText
                } else {
                    colors.heroSecondaryText
                }
            )
            Text(
                text = text,
                fontSize = 18.sp,
                color = if (selected) {
                    colors.navSelectedText
                } else {
                    colors.heroSecondaryText
                }
            )
        }
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.WeatherHeroTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add weather hero and forecast tabs"
```

---

### Task 6: Build moon-phase graphic and current-weather card

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/components/MoonPhase.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/components/WeatherIcon.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/CurrentWeatherCard.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weather/CurrentWeatherCardTest.kt`

**Interfaces:**
- Consumes: `WeatherForecast`, `WeatherLocation`, and formatters
- Produces: `MoonPhase`, `HluWeatherIcon`, and `CurrentWeatherCard`

- [ ] **Step 1: Write failing current-card test**

Create `app/src/test/java/net/droopia/hluweather/ui/weather/CurrentWeatherCardTest.kt`:

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CurrentWeatherCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_current_conditions() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                CurrentWeatherCard(
                    location = forecast.location,
                    forecast = forecast
                )
            }
        }

        composeRule.onNodeWithText("Svilajnac").assertIsDisplayed()
        composeRule.onNodeWithText("21°").assertIsDisplayed()
        composeRule.onNodeWithText("Clear sky").assertIsDisplayed()
        composeRule.onNodeWithText("51%").assertIsDisplayed()
        composeRule.onNodeWithText("0 mm").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.CurrentWeatherCardTest
```

Expected: FAIL because `CurrentWeatherCard` does not exist.

- [ ] **Step 3: Create `app/src/main/java/net/droopia/hluweather/ui/components/MoonPhase.kt`**

```kotlin
package net.droopia.hluweather.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.Preview
import net.droopia.hluweather.ui.theme.LocalHluColors
import kotlin.math.abs
import kotlin.math.cos

private val MoonDark = Color(0xFF28334A)

@Composable
fun MoonPhase(
    phase: Double,
    modifier: Modifier = Modifier
) {
    val litColor = LocalHluColors.current.moon

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = this.center
        val angle = phase * 2.0 * Math.PI
        val cosine = cos(angle).toFloat()
        val waxing = phase <= 0.5
        val bounds = Rect(
            left = center.x - radius,
            top = center.y - radius,
            right = center.x + radius,
            bottom = center.y + radius
        )

        drawCircle(MoonDark, radius, center)

        val litHalf = Path().apply {
            if (waxing) {
                arcTo(bounds, -90f, 180f, true)
            } else {
                arcTo(bounds, 90f, 180f, true)
            }
            close()
        }
        drawPath(litHalf, litColor)

        val ellipseWidth = 2f * radius * abs(cosine)
        if (ellipseWidth > 0.1f) {
            val ellipseRect = Rect(
                left = center.x - ellipseWidth / 2f,
                top = center.y - radius,
                right = center.x + ellipseWidth / 2f,
                bottom = center.y + radius
            )
            val ellipseColor = if (cosine > 0f) MoonDark else litColor
            drawOval(
                color = ellipseColor,
                topLeft = Offset(ellipseRect.left, ellipseRect.top),
                size = Size(ellipseRect.width, ellipseRect.height)
            )
        }
    }
}

@Preview
@Composable
private fun MoonPhasePreview() {
    MoonPhase(phase = 0.25)
}
```

- [ ] **Step 4: Create `app/src/main/java/net/droopia/hluweather/ui/components/WeatherIcon.kt`**

```kotlin
package net.droopia.hluweather.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Foggy
import androidx.compose.material.icons.filled.PartlyCloudyDay
import androidx.compose.material.icons.filled.PartlyCloudyNight
import androidx.compose.material.icons.filled.Rainy
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import net.droopia.hluweather.data.model.WeatherCondition
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun HluWeatherIcon(
    condition: WeatherCondition,
    isDay: Boolean?,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current
    val sunColor = Color(0xFFFFB300)
    val nightColor = colors.moonAccent
    val cloudColor = colors.cloudAccent
    val rainColor = Color(0xFF5367E8)

    val imageVector: ImageVector
    val tint: Color

    when (condition) {
        WeatherCondition.CLEAR,
        WeatherCondition.MOSTLY_CLEAR -> {
            imageVector = if (isDay == false) {
                Icons.Default.DarkMode
            } else {
                Icons.Default.WbSunny
            }
            tint = if (isDay == false) nightColor else sunColor
        }
        WeatherCondition.PARTLY_CLOUDY -> {
            imageVector = if (isDay == false) {
                Icons.Default.PartlyCloudyNight
            } else {
                Icons.Default.PartlyCloudyDay
            }
            tint = cloudColor
        }
        WeatherCondition.CLOUDY -> {
            imageVector = Icons.Default.Cloud
            tint = cloudColor
        }
        WeatherCondition.FOG -> {
            imageVector = Icons.Default.Foggy
            tint = cloudColor
        }
        WeatherCondition.DRIZZLE,
        WeatherCondition.RAIN -> {
            imageVector = Icons.Default.Rainy
            tint = rainColor
        }
        WeatherCondition.SNOW -> {
            imageVector = Icons.Default.AcUnit
            tint = Color(0xFFB9C6FF)
        }
        WeatherCondition.THUNDERSTORM -> {
            imageVector = Icons.Default.Thunderstorm
            tint = Color(0xFFFFB300)
        }
        WeatherCondition.UNKNOWN -> {
            imageVector = Icons.Default.Cloud
            tint = cloudColor
        }
    }

    Icon(
        imageVector = imageVector,
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}
```

- [ ] **Step 5: Create `app/src/main/java/net/droopia/hluweather/ui/weather/CurrentWeatherCard.kt`**

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.droopia.hluweather.data.dateTimeText
import net.droopia.hluweather.data.label
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.model.WeatherLocation
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureText
import net.droopia.hluweather.ui.components.MoonPhase
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun CurrentWeatherCard(
    location: WeatherLocation,
    forecast: WeatherForecast,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = LocalHluColors.current.weatherCard
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier.padding(22.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = forecast.fetchedAt.dateTimeText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = forecast.current.temperature.temperatureText(),
                            fontSize = 70.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 70.sp
                        )
                        Spacer(Modifier.width(18.dp))
                        MoonPhase(
                            phase = forecast.moonPhase,
                            modifier = Modifier.size(62.dp)
                        )
                    }
                    Text(
                        text = forecast.current.condition.label(),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(145.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                Spacer(Modifier.width(18.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    WeatherMetric(
                        icon = Icons.Default.WaterDrop,
                        title = "Humidity",
                        value = forecast.current.humidity.percentText()
                    )
                    WeatherMetric(
                        icon = Icons.Default.DeviceThermostat,
                        title = "Feels like",
                        value = forecast.current.apparentTemperature.temperatureText()
                    )
                    WeatherMetric(
                        icon = Icons.Default.Eco,
                        title = "Dew point",
                        value = forecast.current.dewPoint.temperatureText()
                    )
                    WeatherMetric(
                        icon = Icons.Default.Umbrella,
                        title = "Precipitation",
                        value = forecast.current.precipitation.precipitationText()
                    )
                }
            }
        }
    }
}

@Composable
private fun WeatherMetric(
    icon: ImageVector,
    title: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(21.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontWeight = FontWeight.SemiBold
        )
    }
}
```

- [ ] **Step 6: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.CurrentWeatherCardTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add current weather card and moon phase"
```

---

### Task 7: Build hourly forecast table

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weather/HourlyForecastTest.kt`

**Interfaces:**
- Consumes: `WeatherForecast`, formatters, and `HluWeatherIcon`
- Produces: `HourlyForecast(forecast: WeatherForecast, selectedDayIndex: Int, onDaySelected: (Int) -> Unit)`

- [ ] **Step 1: Write failing hourly test**

Create `app/src/test/java/net/droopia/hluweather/ui/weather/HourlyForecastTest.kt`:

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zz-UTC")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HourlyForecastTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_hourly_table_header_and_rows() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HourlyForecast(
                    forecast = forecast,
                    selectedDayIndex = 0,
                    onDaySelected = {}
                )
            }
        }

        composeRule.onNodeWithText("Hourly Forecast").assertIsDisplayed()
        composeRule.onNodeWithText("Time").assertIsDisplayed()
        composeRule.onNodeWithText("Weather").assertIsDisplayed()
        composeRule.onNodeWithText("Temp.").assertIsDisplayed()
        composeRule.onNodeWithText("Dew point").assertIsDisplayed()
        composeRule.onNodeWithText("Hum.").assertIsDisplayed()
        composeRule.onNodeWithText("Precip.").assertIsDisplayed()
        composeRule.onNodeWithText("00h").assertIsDisplayed()
        composeRule.onNodeWithTag("hourly_table").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.HourlyForecastTest
```

Expected: FAIL because `HourlyForecast` does not exist.

- [ ] **Step 3: Create `app/src/main/java/net/droopia/hluweather/ui/weather/HourlyForecast.kt`**

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.droopia.hluweather.data.hourText
import net.droopia.hluweather.data.label
import net.droopia.hluweather.data.model.HourForecast
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.percentText
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureText
import net.droopia.hluweather.data.toAppLocalDate
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun HourlyForecast(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedDate = forecast.daily[selectedDayIndex].date
    val dayHours = remember(forecast, selectedDayIndex) {
        forecast.hourly.filter { it.time.toAppLocalDate() == selectedDate }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = 16.dp,
                    bottom = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hourly Forecast",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            DaySelector(
                forecast = forecast,
                selectedDayIndex = selectedDayIndex,
                onDaySelected = onDaySelected
            )
        }

        ForecastColumnHeader(
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .testTag("hourly_table")
        ) {
            items(dayHours) { hour ->
                ForecastRow(
                    weather = hour,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun DaySelector(
    forecast: WeatherForecast,
    selectedDayIndex: Int,
    onDaySelected: (Int) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            forecast.daily.forEachIndexed { index, day ->
                DayChip(
                    text = day.date.dayText(),
                    selected = index == selectedDayIndex,
                    onClick = { onDaySelected(index) }
                )
            }
        }
    }
}

@Composable
private fun DayChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalHluColors.current

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(26.dp),
        color = if (selected) colors.daySelected else Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 10.dp
            ),
            color = if (selected) {
                colors.daySelectedText
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ForecastColumnHeader(
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(colors.tableHeader)
            .padding(
                horizontal = 16.dp,
                vertical = 13.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ForecastCell("Time", 0.72f)
        ForecastCell("Weather", 1.85f)
        ForecastCell("Temp.", 0.8f)
        ForecastCell("Dew point", 1f)
        ForecastCell("Hum.", 0.8f)
        ForecastCell("Precip.", 0.9f)
    }
}

@Composable
private fun RowScope.ForecastCell(
    text: String,
    weight: Float
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp
    )
}

@Composable
private fun ForecastRow(
    weather: HourForecast,
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.tableRow)
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant
            )
            .padding(
                horizontal = 16.dp,
                vertical = 9.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = weather.time.hourText(),
            modifier = Modifier.weight(0.72f),
            fontWeight = FontWeight.SemiBold
        )

        Row(
            modifier = Modifier.weight(1.85f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HluWeatherIcon(
                condition = weather.condition,
                isDay = weather.isDay,
                modifier = Modifier.width(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = weather.condition.label(),
                maxLines = 1
            )
        }

        Text(
            text = weather.temperature.temperatureText(),
            modifier = Modifier.weight(0.8f),
            fontWeight = FontWeight.SemiBold
        )

        Text(
            text = weather.dewPoint.temperatureText(),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text(
            text = weather.humidity.percentText(),
            modifier = Modifier.weight(0.8f)
        )

        Text(
            text = weather.precipitation.precipitationText(),
            modifier = Modifier.weight(0.9f)
        )
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.HourlyForecastTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add hourly forecast table"
```

---

### Task 8: Build daily forecast list

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/DailyForecastList.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weather/DailyForecastListTest.kt`

**Interfaces:**
- Consumes: `WeatherForecast`, formatters, and `HluWeatherIcon`
- Produces: `DailyForecastList(forecast: WeatherForecast, onDaySelected: (Int) -> Unit)`

- [ ] **Step 1: Write failing daily test**

Create `app/src/test/java/net/droopia/hluweather/ui/weather/DailyForecastListTest.kt`:

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zz-UTC")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DailyForecastListTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_daily_rows() {
        val forecast = buildMockForecast(
            location = Svilajnac,
            baseTime = Instant.fromEpochSeconds(0L)
        )

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                DailyForecastList(
                    forecast = forecast,
                    onDaySelected = {}
                )
            }
        }

        composeRule.onNodeWithTag("daily_list").assertIsDisplayed()
        composeRule.onNodeWithText("Clear sky").assertIsDisplayed()
        composeRule.onNodeWithText("16° – 30°").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.DailyForecastListTest
```

Expected: FAIL because `DailyForecastList` does not exist.

- [ ] **Step 3: Create `app/src/main/java/net/droopia/hluweather/ui/weather/DailyForecastList.kt`**

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.droopia.hluweather.data.dayText
import net.droopia.hluweather.data.label
import net.droopia.hluweather.data.model.DayForecast
import net.droopia.hluweather.data.model.WeatherForecast
import net.droopia.hluweather.data.precipitationText
import net.droopia.hluweather.data.temperatureText
import net.droopia.hluweather.ui.components.HluWeatherIcon
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun DailyForecastList(
    forecast: WeatherForecast,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("daily_list"),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 4.dp,
            bottom = 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(forecast.daily.indices) { index ->
            DailyRow(
                day = forecast.daily[index],
                onClick = { onDaySelected(index) }
            )
        }
    }
}

@Composable
private fun DailyRow(
    day: DayForecast,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = LocalHluColors.current.weatherCard
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Text(
                    text = day.date.dayText(),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = day.condition.label(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HluWeatherIcon(
                condition = day.condition,
                isDay = true,
                modifier = Modifier.width(28.dp)
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = "${day.temperatureMin.temperatureText()} – ${day.temperatureMax.temperatureText()}",
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = day.precipitation.precipitationText(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.DailyForecastListTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add daily forecast list"
```

---

### Task 9: Add Map placeholder

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/map/MapPlaceholder.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/map/MapPlaceholderTest.kt`

**Interfaces:**
- Consumes: theme colors
- Produces: `MapPlaceholder()`

- [ ] **Step 1: Write failing map placeholder test**

Create `app/src/test/java/net/droopia/hluweather/ui/map/MapPlaceholderTest.kt`:

```kotlin
package net.droopia.hluweather.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MapPlaceholderTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Test
    fun renders_map_placeholder() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                MapPlaceholder()
            }
        }

        composeRule.onNodeWithTag("map_placeholder").assertIsDisplayed()
        composeRule.onNodeWithText("Map preview").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.map.MapPlaceholderTest
```

Expected: FAIL because `MapPlaceholder` does not exist.

- [ ] **Step 3: Create `app/src/main/java/net/droopia/hluweather/ui/map/MapPlaceholder.kt`**

```kotlin
package net.droopia.hluweather.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import net.droopia.hluweather.ui.theme.LocalHluColors

@Composable
fun MapPlaceholder(
    modifier: Modifier = Modifier
) {
    val colors = LocalHluColors.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.tableRow)
            .testTag("map_placeholder"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Map preview",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
```

- [ ] **Step 4: Run test**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.map.MapPlaceholderTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add map placeholder"
```

---

### Task 10: Integrate weather screen, settings navigation, and app entry point

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt`
- Create: `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt`
- Create: `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherViewModel.kt`
- Modify: `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt`
- Create: `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`
- Create: `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt`

**Interfaces:**
- Consumes: all UI components from previous tasks and `WeatherViewModel`
- Produces: `WeatherScreen`, `SettingsScreen`, `HluNavHost`, and `WeatherViewModel.Factory`

- [ ] **Step 1: Write failing weather screen test**

Create `app/src/test/java/net/droopia/hluweather/ui/weather/WeatherScreenTest.kt`:

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.data.repository.MockWeatherRepository
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zz-UTC")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WeatherScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun renders_hourly_content_by_default() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithTag("hourly_table").assertIsDisplayed()
        composeRule.onNodeWithText("Svilajnac").assertIsDisplayed()
    }

    @Test
    fun switches_to_daily_content() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Daily").performClick()
        composeRule.onNodeWithTag("daily_list").assertIsDisplayed()
    }

    @Test
    fun switches_to_map_placeholder() {
        val viewModel = WeatherViewModel(MockWeatherRepository())

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = {}
                )
            }
        }

        composeRule.onNodeWithText("Map").performClick()
        composeRule.onNodeWithTag("map_placeholder").assertIsDisplayed()
    }

    @Test
    fun settings_icon_reports_click() {
        val viewModel = WeatherViewModel(MockWeatherRepository())
        var settingsClicked = false

        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                WeatherScreen(
                    viewModel = viewModel,
                    onSettingsClick = { settingsClicked = true }
                )
            }
        }

        composeRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(settingsClicked)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests net.droopia.hluweather.ui.weather.WeatherScreenTest
```

Expected: FAIL because `WeatherScreen` does not exist.

- [ ] **Step 3: Add ViewModel factory to `WeatherViewModel.kt`**

Add these imports:

```kotlin
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
```

Add this companion object inside `WeatherViewModel`:

```kotlin
companion object {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer {
            WeatherViewModel(MockWeatherRepository())
        }
    }
}
```

- [ ] **Step 4: Create `app/src/main/java/net/droopia/hluweather/ui/weather/WeatherScreen.kt`**

```kotlin
package net.droopia.hluweather.ui.weather

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.ui.map.MapPlaceholder

@Composable
fun WeatherScreen(
    viewModel: WeatherViewModel = viewModel(factory = WeatherViewModel.Factory),
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val forecast = state.forecast
    val location = state.activeLocation

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (forecast == null || location == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.isLoading -> {
                        CircularProgressIndicator()
                    }
                    state.error != null -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = state.error ?: "Weather request failed",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Button(onClick = viewModel::refresh) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> Unit
                }
            }
            return
        }

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            WeatherHero(
                selected = state.forecastMode,
                onSelected = viewModel::onForecastModeSelected,
                onSettingsClick = onSettingsClick
            )

            CurrentWeatherCard(
                location = location,
                forecast = forecast,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-8).dp)
            )

            when (state.forecastMode) {
                ForecastMode.HOURLY -> {
                    HourlyForecast(
                        forecast = forecast,
                        selectedDayIndex = state.selectedDayIndex,
                        onDaySelected = viewModel::onDaySelected,
                        modifier = Modifier.weight(1f)
                    )
                }
                ForecastMode.DAILY -> {
                    DailyForecastList(
                        forecast = forecast,
                        onDaySelected = viewModel::onDaySelected,
                        modifier = Modifier.weight(1f)
                    )
                }
                ForecastMode.MAP -> {
                    MapPlaceholder(
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 5: Create `app/src/main/java/net/droopia/hluweather/ui/settings/SettingsScreen.kt`**

```kotlin
package net.droopia.hluweather.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}
```

- [ ] **Step 6: Create `app/src/main/java/net/droopia/hluweather/navigation/HluNavHost.kt`**

```kotlin
package net.droopia.hluweather.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import net.droopia.hluweather.ui.settings.SettingsScreen
import net.droopia.hluweather.ui.weather.WeatherScreen

@Composable
fun HluNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "weather"
    ) {
        composable("weather") {
            WeatherScreen(
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}
```

- [ ] **Step 7: Replace `app/src/main/java/net/droopia/hluweather/ui/app/HluWeatherApp.kt`**

```kotlin
package net.droopia.hluweather.ui.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import net.droopia.hluweather.navigation.HluNavHost
import net.droopia.hluweather.ui.theme.HluWeatherTheme

@Composable
fun HluWeatherApp() {
    val darkTheme = isSystemInDarkTheme()

    HluWeatherTheme(darkTheme = darkTheme) {
        HluNavHost()
    }
}
```

- [ ] **Step 8: Create `app/src/test/java/net/droopia/hluweather/navigation/HluNavHostTest.kt`**

```kotlin
package net.droopia.hluweather.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zz-UTC")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HluNavHostTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun navigates_from_weather_to_settings_and_back() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluNavHost()
            }
        }

        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }
}
```

- [ ] **Step 9: Make app smoke test dispatcher-safe**

Replace `app/src/test/java/net/droopia/hluweather/ui/app/HluWeatherAppTest.kt` with:

```kotlin
package net.droopia.hluweather.ui.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import net.droopia.hluweather.ComposeTestActivity
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zz-UTC")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HluWeatherAppTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComposeTestActivity>()

    @Before
    fun setup() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    @Test
    fun displays_app_title() {
        composeRule.setContent {
            HluWeatherTheme(darkTheme = false) {
                HluWeatherApp()
            }
        }
        composeRule.onNodeWithText("HluWeatherApp").assertIsDisplayed()
    }
}
```

- [ ] **Step 10: Run tests**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon --tests "net.droopia.hluweather.*"
```

Expected: PASS.

- [ ] **Step 11: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: integrate weather screen and navigation"
```

---

### Task 11: Add previews and full quality gate

**Files:**
- Create: `app/src/main/java/net/droopia/hluweather/ui/preview/Previews.kt`

**Interfaces:**
- Consumes: all main-source composables
- Produces: Studio previews and final verification that the app builds, unit tests pass, and lint passes

- [ ] **Step 1: Create `app/src/main/java/net/droopia/hluweather/ui/preview/Previews.kt`**

```kotlin
package net.droopia.hluweather.ui.preview

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.datetime.Instant
import net.droopia.hluweather.data.model.ForecastMode
import net.droopia.hluweather.data.repository.Svilajnac
import net.droopia.hluweather.data.repository.buildMockForecast
import net.droopia.hluweather.ui.theme.HluWeatherTheme
import net.droopia.hluweather.ui.weather.CurrentWeatherCard
import net.droopia.hluweather.ui.weather.DailyForecastList
import net.droopia.hluweather.ui.weather.HourlyForecast
import net.droopia.hluweather.ui.weather.WeatherHero

private val previewForecast = buildMockForecast(
    location = Svilajnac,
    baseTime = Instant.fromEpochSeconds(0L)
)

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun WeatherHeroLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        WeatherHero(
            selected = ForecastMode.HOURLY,
            onSelected = {},
            onSettingsClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun WeatherHeroDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        WeatherHero(
            selected = ForecastMode.HOURLY,
            onSelected = {},
            onSettingsClick = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun CurrentWeatherCardLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        CurrentWeatherCard(
            location = previewForecast.location,
            forecast = previewForecast
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun CurrentWeatherCardDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        CurrentWeatherCard(
            location = previewForecast.location,
            forecast = previewForecast
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun HourlyForecastLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        HourlyForecast(
            forecast = previewForecast,
            selectedDayIndex = 0,
            onDaySelected = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun HourlyForecastDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        HourlyForecast(
            forecast = previewForecast,
            selectedDayIndex = 0,
            onDaySelected = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915
)
@Composable
private fun DailyForecastLightPreview() {
    HluWeatherTheme(darkTheme = false) {
        DailyForecastList(
            forecast = previewForecast,
            onDaySelected = {}
        )
    }
}

@Preview(
    showBackground = true,
    widthDp = 412,
    heightDp = 915,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DailyForecastDarkPreview() {
    HluWeatherTheme(darkTheme = true) {
        DailyForecastList(
            forecast = previewForecast,
            onDaySelected = {}
        )
    }
}
```

- [ ] **Step 2: Run full unit test suite**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Build debug APK**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run lint**

```bash
cd /home/homoludens/projekti/hluweatherapp
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME
./gradlew :app:lintDebug --no-daemon
```

Expected: BUILD SUCCESSFUL. If lint reports missing app icon as the only issue, replace the manifest icon with a project-owned launcher icon before completing the next plan; do not weaken the lint rule.

- [ ] **Step 5: Commit**

```bash
cd /home/homoludens/projekti/hluweatherapp
git add -A
git commit -m "feat: add previews and verify app shell"
```

## Plan 1 Completion Checklist

- Repository initializes on `main`
- Gradle wrapper works
- `./gradlew :app:testDebugUnitTest` passes
- `./gradlew :app:assembleDebug` passes
- Light and dark theme palettes match the spec
- Mock weather screen renders Hero, Current card, Hourly table, Daily list, and Map placeholder
- Settings navigation works
- Every task has been committed

After Plan 1 is complete, write Plan 2 for real weather providers, settings, persistence, and cache. After Plan 2 is complete, write Plan 3 for locations, MapLibre/OpenFreeMap, GPS, reverse geocoding, and Track Me.
