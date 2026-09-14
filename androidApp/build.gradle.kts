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

/**
 * Der Schlüssel, sofern er auf diesem Rechner liegt.
 *
 * Auf dem CI-Rechner und auf fremden Maschinen gibt es ihn nicht. Dann wird das Release
 * **unsigniert** gebaut, statt den Build scheitern zu lassen — sonst könnte dort nicht einmal
 * `./gradlew build` durchlaufen.
 */
val releaseKeystore = localProperties.getProperty("release.keystore.path")
    ?.let { file(it) }
    ?.takeIf { it.exists() }

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
            if (releaseKeystore != null) {
                storeFile = releaseKeystore
                storePassword = localProperties.getProperty("release.keystore.password")
                keyAlias = localProperties.getProperty("release.key.alias")
                keyPassword = localProperties.getProperty("release.key.password")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else null
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
