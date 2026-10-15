import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.adulthub.tv"
    compileSdk = 35
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.adulthub.tv"
        minSdk = 28
        targetSdk = 35
        versionCode = 61
        versionName = "0.1.60"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        val updateOwner = providers.environmentVariable("UPDATE_GITHUB_OWNER")
            .orElse(providers.gradleProperty("updateGithubOwner"))
            .orElse("backwaterXXX")
            .get()
        val updateRepo = providers.environmentVariable("UPDATE_GITHUB_REPO")
            .orElse(providers.gradleProperty("updateGithubRepo"))
            .orElse("adult-hub-tv")
            .get()
        buildConfigField("String", "UPDATE_GITHUB_OWNER", "\"$updateOwner\"")
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"$updateRepo\"")
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val properties = Properties().apply {
                    FileInputStream(keystorePropertiesFile).use { load(it) }
                }
                storeFile = rootProject.file(properties.getProperty("storeFile", "release.jks"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }

    lint {
        lintConfig = file("lint.xml")
        abortOnError = true
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.compose.material.icons.extended)

    // Koin
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    // Image loading
    implementation(libs.coil.compose)

    // Media3 / ExoPlayer
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.session)
    implementation(libs.media3.datasource.okhttp)

    // Jellyfin SDK
    implementation(libs.jellyfin.core)

    // Coroutines & Serialization
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Networking / Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)

    // SLF4J Android logging binding for Jellyfin SDK
    implementation(libs.slf4j.android)

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

}
