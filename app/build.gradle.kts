import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// The sing-box core (libbox) is a Go library bound with gomobile.
// It is produced by `scripts/build-libbox.sh` (or the GitHub Actions workflow) and
// dropped into app/libs/libbox.aar. The app compiles and runs without it, but the
// tunnel engine is only available once the AAR is present.
val libboxAar = file("libs/libbox.aar")

android {
    namespace = "com.radx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.radx.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.1.1"
        vectorDrawables.useSupportLibrary = true

        // The bundled Xray core only ships for 64-bit architectures, so a 32-bit ABI
        // would just produce a half-working APK.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties().apply {
        if (keystorePropertiesFile.exists()) {
            keystorePropertiesFile.inputStream().use { load(it) }
        }
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Keep shrinking off for the first stable release: no obfuscation rules to
            // get wrong, and the APK stays easy to debug.
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystorePropertiesFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            if (libboxAar.exists()) {
                java.srcDir("src/libbox/java")
            }
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/versions/**")
        }
        // The Xray core is an executable shipped in jniLibs as libxray.so. Android can
        // only execute files that were unpacked from the APK, so native libraries must
        // be extracted on install.
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    if (libboxAar.exists()) {
        implementation(files(libboxAar))
    }

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.snakeyaml)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
