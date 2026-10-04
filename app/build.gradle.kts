plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
}

fun releaseVersionName(): String {
    val fromGitHub = System.getenv("GITHUB_REF_NAME")
        ?.takeIf { it.matches(Regex("v\\d+\\.\\d+\\.\\d+(?:[-+].*)?")) }
        ?.removePrefix("v")
    if (fromGitHub != null) return fromGitHub
    return try {
        ProcessBuilder("git", "describe", "--tags", "--abbrev=0")
            .directory(rootDir)
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().use { it.readText() }.trim()
            .removePrefix("v")
            .takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[-+].*)?")) }
            ?: "0.1.0"
    } catch (_: Exception) {
        "0.1.0"
    }
}

val limitsVersionName = releaseVersionName()
val limitsVersionCode = limitsVersionName.substringBefore('-').substringBefore('+').split('.').let { parts ->
    (parts.getOrNull(0)?.toIntOrNull() ?: 0) * 10_000 +
        (parts.getOrNull(1)?.toIntOrNull() ?: 0) * 100 +
        (parts.getOrNull(2)?.toIntOrNull() ?: 0)
}.coerceAtLeast(1)

val isDevelopmentBuild = System.getenv("LIMITS_DEV_BUILD") == "true"
val developmentRunNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0
val appVersionCode = if (isDevelopmentBuild) 1_000_000 + developmentRunNumber else limitsVersionCode
val appVersionName = if (isDevelopmentBuild) {
    "$limitsVersionName-dev.$developmentRunNumber"
} else {
    limitsVersionName
}

android {
    namespace = "app.limits"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "app.limits"
        minSdk = 31
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("ANDROID_KEYSTORE_PATH")
            if (!path.isNullOrBlank()) {
                storeFile = file(path)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (!System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.material)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
}
