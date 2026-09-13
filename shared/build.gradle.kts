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
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
    }
}

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
