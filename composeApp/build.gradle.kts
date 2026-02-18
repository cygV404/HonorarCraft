import org.jetbrains.compose.desktop.application.dsl.TargetFormat


plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.0"

}

kotlin {


    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
            implementation("org.apache.pdfbox:pdfbox:2.0.30")
            implementation(compose.materialIconsExtended)

        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)


        }
    }
}


compose.desktop {
    application {
        mainClass = "app.accounting.accountingapp.MainKt"

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
                bundleID = "app.accounting.accountingapp"
            }

            linux {

                iconFile.set(project.file("src/jvmMain/composeResources/drawable/iconDeb.png"))
            }

        }
    }
}


