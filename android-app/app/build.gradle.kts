import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

fun loadLocalProperties(fileName: String) = Properties().apply {
    val propertiesFile = rootProject.file(fileName)
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use(::load)
    }
}

val localProperties = loadLocalProperties("local.properties")
val devProperties = loadLocalProperties("local.properties.dev")

fun envValue(name: String): String? = providers.environmentVariable(name).orNull?.takeIf { it.isNotBlank() }

fun localValue(name: String): String? =
    devProperties.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val localBackendBaseUrl = localValue("backend.base.url")
val localMinioImageBaseUrl = localValue("minio.image.base.url")

android {
    namespace = "com.urbansidequest.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.urbansidequest.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        val backendBaseUrl = envValue("BACKEND_BASE_URL")
            ?: localBackendBaseUrl
            ?: "http://10.0.2.2:8080"
        val minioImageBaseUrl = envValue("MINIO_IMAGE_BASE_URL")
            ?: localMinioImageBaseUrl
            ?: "http://10.0.2.2:9000"
        val amapApiKey = envValue("AMAP_API_KEY")
            ?: localValue("amap.api.key")
            ?: ""
        buildConfigField("String", "BACKEND_BASE_URL", "\"$backendBaseUrl\"")
        buildConfigField("String", "MINIO_IMAGE_BASE_URL", "\"$minioImageBaseUrl\"")
        manifestPlaceholders["AMAP_API_KEY"] = amapApiKey

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
