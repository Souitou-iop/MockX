plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Version is derived from the release tag in CI (passed via -PappVersionName=vX.Y.Z or the
// APP_VERSION_NAME env var). Local builds fall back to the version below.
val fallbackVersionName = "1.1beta"

fun resolveVersionName(): String {
    val provided = (project.findProperty("appVersionName") as String?)
        ?: System.getenv("APP_VERSION_NAME")
    return provided?.trim()?.removePrefix("v")?.takeIf { it.isNotEmpty() } ?: fallbackVersionName
}

// Maps a version name (e.g. "1.0.0", "1.0beta", "1.0-beta") to a monotonically increasing integer.
fun resolveVersionCode(versionName: String): Int {
    val clean = versionName.substringBefore("-").takeWhile { it.isDigit() || it == '.' }
    val parts = clean.split(".").filter { it.isNotEmpty() }
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 1
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    val base = major * 10000 + minor * 100 + patch
    return if (base > 0) base else 10000
}

// Signing: CI restores a fixed keystore and exports MOCKX_KEYSTORE_PATH, so every published
// APK shares one signature and users can update in place. Local builds keep AGP's default
// ~/.android/debug.keystore.
val pinnedKeystorePath = System.getenv("MOCKX_KEYSTORE_PATH")?.takeIf { it.isNotBlank() }

val appVersionName = resolveVersionName()
val appVersionCode = resolveVersionCode(appVersionName)

android {
    namespace = "com.noobexon.xposedfakelocation"

    if (pinnedKeystorePath != null) {
        signingConfigs.getByName("debug") {
            storeFile = file(pinnedKeystorePath)
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.souitou.mockx"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        ndk {
            val targetAbi = project.findProperty("targetAbi") as String?
            if (targetAbi == "all") {
                // leave abiFilters empty to package all ABIs
            } else if (!targetAbi.isNullOrBlank()) {
                abiFilters += targetAbi.split(",")
            } else {
                abiFilters += listOf("arm64-v8a")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs["debug"]
        }

    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }


    packaging {
        resources {
            merges += "META-INF/xposed/*"
        }
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf(
            "-XXLanguage:+PropertyParamAnnotationDefaultTargetMode",
            "-Xskip-metadata-version-check"
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.osmdroid.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.gson)
    implementation(libs.hiddenapibypass)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.blur)
    implementation("androidx.navigationevent:navigationevent:1.1.2")
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
}

tasks.matching { it.name.startsWith("check") && it.name.endsWith("AarMetadata") }.configureEach {
    enabled = false
}

configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlin" && requested.name.startsWith("kotlin-stdlib")) {
            useVersion("2.2.10")
        }
    }
}