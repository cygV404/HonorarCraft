plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

kotlin {
    // Ab AGP 9 wird das Android-Target über diesen Block deklariert, nicht über androidTarget().
    androidLibrary {
        namespace = "de.v404.honorarcraft.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        // Damit commonTest auch gegen das Android-Target läuft (ab Phase 3 der CalculationTest).
        withHostTest {}
    }
    jvm()

    // Android und Desktop sind beide JVM-Ziele. Entities und Rechenlogik brauchen
    // java.math.BigDecimal, das in commonMain nicht verfügbar ist; deshalb hängt zwischen
    // commonMain und den beiden Targets dieser Zwischen-Quellsatz. Kommt später ein
    // iOS-Target dazu (offene Frage 8), muss BigDecimal dort ersetzt werden.
    applyDefaultHierarchyTemplate()
    sourceSets {
        val jvmCommonMain by creating { dependsOn(commonMain.get()) }
        val jvmCommonTest by creating { dependsOn(commonTest.get()) }
        androidMain.get().dependsOn(jvmCommonMain)
        jvmMain.get().dependsOn(jvmCommonMain)
        named("androidHostTest").get().dependsOn(jvmCommonTest)
        jvmTest.get().dependsOn(jvmCommonTest)

        commonMain.dependencies {
            implementation(libs.kotlinx.serializationJson)
            implementation(libs.kotlinx.coroutinesCore)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        jvmMain.dependencies {
            // PDFBox zeichnet die Rechnung auf dem Desktop.
            implementation(libs.pdfbox)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        jvmTest.dependencies {
            // MigrationTestHelper prueft die Migrationskette gegen die exportierten Schemas.
            implementation(libs.androidx.room.testing)
        }
    }
}

/**
 * Erzeugt die Programmversion als Kotlin-Konstante aus dem Versionskatalog.
 *
 * Vorher stand sie als Literal in `Constants` und musste bei jedem Release zusätzlich zu
 * `packageVersion` und `versionName` von Hand nachgezogen werden — eine Stelle zu viel.
 */
val appVersionVerzeichnis = layout.buildDirectory.dir("generated/appversion/kotlin")
val erzeugeAppVersion by tasks.registering {
    val version = libs.versions.appVersion.get()
    val ziel = appVersionVerzeichnis
    inputs.property("version", version)
    outputs.dir(ziel)
    doLast {
        val datei = ziel.get().file("de/v404/honorarcraft/shared/data/AppVersion.kt").asFile
        datei.parentFile.mkdirs()
        datei.writeText(
            """
            // Erzeugt von Gradle. Nicht von Hand ändern - der Wert steht in
            // gradle/libs.versions.toml unter "appVersion".
            package de.v404.honorarcraft.shared.data

            internal const val GENERATED_APP_VERSION: String = "$version"

            """.trimIndent()
        )
    }
}

kotlin.sourceSets.named("jvmCommonMain") { kotlin.srcDir(erzeugeAppVersion) }

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    // Room-Compiler muss pro Target eingehängt werden, ein gemeinsames ksp(...) reicht nicht.
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspJvm", libs.androidx.room.compiler)
}

// Die Lint-Model-Tasks lesen das KSP-Ausgabeverzeichnis, ohne die Abhängigkeit zu kennen.
// Ohne diese Zeilen bricht `./gradlew build` mit "implicit dependency" ab.
tasks.matching { it.name.startsWith("lint") || it.name.endsWith("LintModel") }
    .configureEach { dependsOn(tasks.matching { it.name.startsWith("ksp") }) }
