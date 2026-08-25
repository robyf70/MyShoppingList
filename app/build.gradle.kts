import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing credentials, from keystore.properties (git-ignored) or, for CI,
// from the environment. Neither the keystore nor its passwords belong in the repo.
// When both are absent the release build is simply left unsigned instead of failing,
// so a fresh clone still builds.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun credential(key: String, environmentVariable: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(environmentVariable)

val releaseStoreFile = credential("storeFile", "MSL_STORE_FILE")

android {
    namespace = "it.robertofichera.myshoppinglist"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "it.robertofichera.myshoppinglist"
        minSdk = 31
        targetSdk = 37
        versionCode = 13
        versionName = "1.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = rootProject.file(releaseStoreFile)
                storePassword = credential("storePassword", "MSL_STORE_PASSWORD")
                keyAlias = credential("keyAlias", "MSL_KEY_ALIAS")
                keyPassword = credential("keyPassword", "MSL_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // ML Kit's OCR library is ~10 MB per architecture and every phone runs one of them.
            // Debug keeps them all so an x86_64 emulator still works.
            ndk { abiFilters += "arm64-v8a" }
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Room exports each schema version here so migrations can be diffed against the real DDL.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.mlkit.text.recognition)
    testImplementation(libs.junit)
    // org.json is an empty stub in unit tests; this supplies the real one.
    testImplementation(libs.json)
    debugImplementation(libs.androidx.compose.ui.tooling)
}