import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

/**
 * Signierung aus `local.properties`. Die Datei ist gitignored — Schlüssel und Passwörter
 * gehören nicht ins Repo.
 */
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "de.v404.honorarcraft.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Darf sich nie aendern - sonst ist es fuer den Play Store eine neue App.
        applicationId = "de.v404.honorarcraft"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = libs.versions.androidVersionCode.get().toInt()
        versionName = libs.versions.appVersion.get()
    }

    signingConfigs {
        create("release") {
            val pfad = localProperties.getProperty("release.keystore.path")
            storeFile = pfad?.let { file(it) }
            storePassword = localProperties.getProperty("release.keystore.password")
            keyAlias = localProperties.getProperty("release.key.alias")
            keyPassword = localProperties.getProperty("release.key.password")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(projects.composeApp)
    implementation(projects.shared)
    implementation(compose.runtime)
    implementation(compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
