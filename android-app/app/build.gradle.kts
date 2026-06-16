import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}

val localBackendBaseUrl = localProperties.getProperty("backend.base.url")
    ?.takeIf { it.isNotBlank() }

android {
    namespace = "com.urbansidequest.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.urbansidequest.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        val backendBaseUrl = providers.environmentVariable("BACKEND_BASE_URL")
            .orElse(localBackendBaseUrl ?: "http://10.0.2.2:8080")
            .get()
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        manifestPlaceholders["AMAP_API_KEY"] = localProperties.getProperty("amap.api.key", "")

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(files("libs/AMap3DMap_11.2.000_AMapSearch_9.8.0_AMapLocation_11.2.000_20260529.jar"))

    debugImplementation("androidx.compose.ui:ui-tooling")
}
