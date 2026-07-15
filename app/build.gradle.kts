plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseStorePath = providers.gradleProperty("CAP6_RELEASE_STORE_FILE").orNull
    ?: System.getenv("CAP6_RELEASE_STORE_FILE")
val releaseStorePassword = providers.gradleProperty("CAP6_RELEASE_STORE_PASSWORD").orNull
    ?: System.getenv("CAP6_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = providers.gradleProperty("CAP6_RELEASE_KEY_ALIAS").orNull
    ?: System.getenv("CAP6_RELEASE_KEY_ALIAS")
val releaseKeyPassword = providers.gradleProperty("CAP6_RELEASE_KEY_PASSWORD").orNull
    ?: System.getenv("CAP6_RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(releaseStorePath, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "fr.cap6.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.cap6.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 20400
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("production") {
                storeFile = rootProject.file(requireNotNull(releaseStorePath))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("production")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { buildConfig = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.maplibre.gl:android-sdk:13.0.2")
    testImplementation("junit:junit:4.13.2")
}
