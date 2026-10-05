plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Signing: a shared keystore is committed in keystore/ so every build (local or CI)
// produces the same signature and updates install over each other. To use your own key,
// set the CALC_* environment variables (the workflow maps GitHub Secrets of the same names;
// empty values are treated as unset so the fallback below keeps working).
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
val calcKeystoreFile = env("CALC_KEYSTORE_FILE")?.let { rootProject.file(it) }
    ?: rootProject.file("keystore/calc.jks")
val calcKeystorePassword = env("CALC_KEYSTORE_PASSWORD") ?: "calc-67-67"
val calcKeyAlias = env("CALC_KEY_ALIAS") ?: "calc"
val calcKeyPassword = env("CALC_KEY_PASSWORD") ?: calcKeystorePassword

android {
    namespace = "io.github.w3bray.calc"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.w3bray.calc"
        minSdk = 21
        targetSdk = 34
        versionCode = 3
        versionName = "1.2"
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

// No third-party runtime dependencies: the app only uses the Android framework + Kotlin stdlib.
// JUnit is used by the JVM unit tests of the calculator logic (./gradlew test).
dependencies {
    testImplementation("junit:junit:4.13.2")
}
