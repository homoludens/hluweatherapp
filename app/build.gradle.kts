plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseSigningValues = listOf(
    "HLUWEATHER_STORE_FILE",
    "HLUWEATHER_STORE_PASSWORD",
    "HLUWEATHER_KEY_ALIAS",
    "HLUWEATHER_KEY_PASSWORD"
).map { name ->
    providers.gradleProperty(name).orElse(providers.environmentVariable(name)).orNull
}
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }

android {
    namespace = "net.droopia.hluweather"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.droopia.hluweather"
        minSdk = 28
        targetSdk = 36
        versionCode = 32
        versionName = "3.2.0"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningValues[0]!!)
                storePassword = releaseSigningValues[1]
                keyAlias = releaseSigningValues[2]
                keyPassword = releaseSigningValues[3]
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
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
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime.ktx)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.maplibre.compose)
    runtimeOnly(libs.maplibre.compose.runtime.opengl)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.work.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
}
