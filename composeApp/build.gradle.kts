import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {

    // Android-Target als KMP-Library (AGP 9). Die eigentliche Android-App liegt in :androidApp.
    androidLibrary {
        namespace = "de.v404.honorarcraft.ui"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.serializationJson)
            implementation(compose.materialIconsExtended)
        }
        androidMain.dependencies {
            // Die Datei-Dialoge laufen über Activity-Ergebnisse und brauchen die Launcher.
            implementation(libs.androidx.activity.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            // PDFBox noch fuer den alten Desktop-Renderer, der bis zum Umbau von
            // InvoiceGenerator in Betrieb bleibt. JNA holt den Windows-Dokumentenpfad.
            implementation(libs.pdfbox)
            implementation(libs.jna)
            implementation(libs.jna.platform)
        }
    }
}


// Fester Paketname für die generierte Res-Klasse - sonst haengt er am Projektnamen.
compose.resources {
    packageOfResClass = "de.v404.honorarcraft.resources"
}

compose.desktop {
    application {
        mainClass = "de.v404.honorarcraft.MainKt"

        buildTypes {
            release {
                proguard {
                    isEnabled = false
                }
            }
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)



            packageName = "HonorarCraft"
            packageVersion = "1.2.1"
            copyright = "© 2026 Julian Dobrodolac (v404cyg@proton.me)"
            vendor = "Julian Dobrodolac"
            description = "Office App"



            windows {

                iconFile.set(project.file("src/jvmMain/composeResources/drawable/iconWindows.ico"))
                menu = true
                shortcut = true
            }

            macOS {

                iconFile.set(project.file("src/jvmMain/composeResources/drawable/iconMacOS.icns"))
                bundleID = "de.v404.honorarcraft"
            }

            linux {

                iconFile.set(project.file("src/jvmMain/composeResources/drawable/iconDeb.png"))
            }

        }
    }
}


