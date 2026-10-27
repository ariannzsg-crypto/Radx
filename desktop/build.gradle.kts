import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Version comes from the root build, which already puts the Kotlin plugin on the
    // classpath for the Android module.
    id("org.jetbrains.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
    application
}

// The desktop build compiles the app's own config pipeline, so both platforms turn the
// same imported links into the same sing-box and Xray configs.
sourceSets {
    main {
        kotlin.srcDirs(
            "../app/src/main/java/com/radx/app/config",
            "../app/src/main/java/com/radx/app/data",
        )
        // DataStore is Android-only; the desktop tool keeps its state in a JSON file.
        kotlin.exclude("**/RadxStore.kt")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// The version the tool reports, stamped by the release workflow (`-PradxVersion=v1.3.0`).
val radxVersion: Provider<String> = providers.gradleProperty("radxVersion").orElse("dev")
val versionDirectory = layout.buildDirectory.dir("generated/radx-version")

val writeVersionFile by tasks.registering {
    val output = versionDirectory.map { it.file("radx-version.txt") }
    inputs.property("radxVersion", radxVersion)
    outputs.file(output)
    doLast {
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(radxVersion.get())
    }
}

sourceSets.named("main") {
    resources.srcDir(versionDirectory)
}

tasks.named("processResources") {
    dependsOn(writeVersionFile)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.okhttp)
    implementation(libs.snakeyaml)

    testImplementation(libs.junit)
}

application {
    applicationName = "radx"
    mainClass = "com.radx.desktop.MainKt"
}

distributions {
    main {
        contents {
            from("dist")
        }
    }
}
