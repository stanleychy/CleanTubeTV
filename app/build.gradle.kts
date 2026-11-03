import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.process.ExecOperations
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
}

/** The short git commit of the build, with "-dirty" when the working tree has changes. "unknown" without git. */
abstract class GitCommit : ValueSource<String, ValueSourceParameters.None> {
    @get:Inject abstract val exec: ExecOperations

    override fun obtain(): String = try {
        val head = git("rev-parse", "--short=10", "HEAD") ?: "unknown"
        if (head != "unknown" && !git("status", "--porcelain").isNullOrEmpty()) "$head-dirty" else head
    } catch (e: Exception) {
        "unknown"
    }

    private fun git(vararg args: String): String? {
        val out = ByteArrayOutputStream()
        val result = exec.exec {
            commandLine("git", *args)
            standardOutput = out
            errorOutput = ByteArrayOutputStream()
            isIgnoreExitValue = true
        }
        return if (result.exitValue == 0) out.toString().trim() else null
    }
}

// Shown in the Settings tab with the version.
val gitCommit = providers.of(GitCommit::class.java) {}.get()

// The only place for the app version. A release tag must be "v" + this value (see .github/workflows/release.yml).
val appVersion = "0.1.2"

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
        resValue("string", "build_commit", gitCommit)
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

    // For the build_commit string resource (resValue in defaultConfig).
    buildFeatures {
        resValues = true
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
