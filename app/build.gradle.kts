plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val releaseSigningEnvironmentNames = listOf(
    "HLUWEATHER_STORE_FILE",
    "HLUWEATHER_STORE_PASSWORD",
    "HLUWEATHER_KEY_ALIAS",
    "HLUWEATHER_KEY_PASSWORD"
)
val releaseSigningValues = releaseSigningEnvironmentNames.map { name ->
    providers.environmentVariable(name).orNull
}
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }
val releaseSigningStoreFile = releaseSigningValues.firstOrNull()?.let(::file)

if (hasReleaseSigning) {
    check(releaseSigningStoreFile?.isFile == true) {
        "${releaseSigningEnvironmentNames.first()} must point to an existing keystore file"
    }
}

android {
    namespace = "net.droopia.hluweather"
    compileSdk = 37

    defaultConfig {
        applicationId = "net.droopia.hluweather"
        minSdk = 28
        targetSdk = 36
        versionCode = 34
        versionName = "3.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    flavorDimensions += "distribution"

    productFlavors {
        create("google") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

androidComponents {
    onVariants(selector().withBuildType("release").withFlavor("distribution", "google")) { variant ->
        if (hasReleaseSigning) {
            variant.signingConfig.setConfig(android.signingConfigs.getByName("release"))
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    add("googleImplementation", libs.play.services.location)

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

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)

    debugImplementation(libs.compose.ui.tooling)
}
