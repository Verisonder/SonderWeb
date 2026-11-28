plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// SonderCircle ships in the same releases as SonderWeb, with the same version: it is read from
// the browser's own build file, so one version bump covers all three apps.
val browserBuild = rootProject.file("app/build.gradle.kts").readText()
val sharedVersionName: String = Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(browserBuild)!!.groupValues[1]
val sharedVersionCode: Int = Regex("versionCode\\s*=\\s*(\\d+)").find(browserBuild)!!.groupValues[1].toInt()

android {
    namespace = "com.verisonder.sondercircle"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.verisonder.sondercircle"
        // Android 11: the first version that lets an app take a screenshot without asking every time.
        minSdk = 30
        targetSdk = 35
        versionCode = sharedVersionCode
        versionName = sharedVersionName
    }

    signingConfigs {
        create("release") {
            // Same key as SonderWeb (CI secrets). Absent locally: the debug key.
            val storeFilePath = System.getenv("RELEASE_STORE_FILE")?.trim()
            if (!storeFilePath.isNullOrEmpty()) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")?.trim()
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")?.trim()
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")?.trim()
                    ?: System.getenv("RELEASE_STORE_PASSWORD")?.trim()
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (System.getenv("RELEASE_STORE_FILE") != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    // Same version as SonderWeb, so nothing else has to be fetched.
    implementation(libs.kotlinx.coroutines.android)
}
