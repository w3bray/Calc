plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing: a shared keystore is committed in keystore/ so every build (local or CI)
// produces the same signature and updates install over each other. Override with
// environment variables if you ever want to use your own key (e.g. GitHub secrets).
val calcKeystoreFile = System.getenv("CALC_KEYSTORE_FILE")?.let { file(it) }
    ?: rootProject.file("keystore/calc.jks")
val calcKeystorePassword = System.getenv("CALC_KEYSTORE_PASSWORD") ?: "calc-67-67"
val calcKeyAlias = System.getenv("CALC_KEY_ALIAS") ?: "calc"
val calcKeyPassword = System.getenv("CALC_KEY_PASSWORD") ?: calcKeystorePassword

android {
    namespace = "io.github.w3bray.calc"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.w3bray.calc"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("shared") {
            storeFile = calcKeystoreFile
            storePassword = calcKeystorePassword
            keyAlias = calcKeyAlias
            keyPassword = calcKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("shared")
        }
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    // Keep audio files uncompressed inside the APK so MediaPlayer can open them
    // straight from assets/ via a file descriptor.
    androidResources {
        noCompress += listOf("mp3", "ogg", "wav", "m4a", "aac", "flac", "mp4")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = false
    }
}

// No third-party dependencies: the app only uses the Android framework + Kotlin stdlib.
dependencies {
}
