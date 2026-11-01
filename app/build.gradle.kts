plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.stanleychy.cleantubetv"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stanleychy.cleantubetv"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Do not embed the Google-encrypted dependency report in the APK.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.recyclerview)
}
