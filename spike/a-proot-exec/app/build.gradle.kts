plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val spikeTargetSdk: Int = (findProperty("spike.targetSdk") as? String)?.toIntOrNull() ?: 35

android {
    namespace = "com.aos.spikea"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.aos.spikea"
        minSdk = 26
        targetSdk = spikeTargetSdk
        versionCode = 2
        versionName = "0.2.0-ts$spikeTargetSdk"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        // Spike-only: signed with the debug key so a non-debuggable (release) build
        // can be installed to verify the lib*.so naming rule end-to-end.
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Extract native libs to nativeLibraryDir at install time (exec-from-nld probe
    // depends on real files there; targetSdk-35 W^X policy applies to data dir).
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}
