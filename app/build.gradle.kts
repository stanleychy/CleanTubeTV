plugins {
    alias(libs.plugins.android.application)
}

// The only place for the app version. A release tag must be "v" + this value (see .github/workflows/release.yml).
val appVersion = "0.1.1"

/** MAJOR.MINOR.PATCH to MAJOR * 10000 + MINOR * 100 + PATCH. For example, 1.2.3 gives 10203. */
fun versionCodeOf(version: String): Int {
    val parts = version.split('.').map { it.toInt() }
    require(parts.size == 3 && parts.all { it in 0..99 }) { "appVersion must be MAJOR.MINOR.PATCH with parts 0 to 99" }
    return parts[0] * 10000 + parts[1] * 100 + parts[2]
}

// Release signing comes from environment variables only. No signing data is in the repository.
// When CLEANTUBE_KEYSTORE is not set, the release APK is not signed.
val releaseKeystore = providers.environmentVariable("CLEANTUBE_KEYSTORE").orNull

android {
    namespace = "io.github.stanleychy.cleantubetv"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.stanleychy.cleantubetv"
        minSdk = 31
        targetSdk = 36
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "PKCS12"
                storePassword = providers.environmentVariable("CLEANTUBE_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("CLEANTUBE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("CLEANTUBE_KEY_PASSWORD").get()
                enableV1Signing = false
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
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
