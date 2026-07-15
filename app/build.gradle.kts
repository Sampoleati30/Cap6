plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "fr.cap6.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.cap6.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 20307
        versionName = "2.3.3-alpha.5"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}
