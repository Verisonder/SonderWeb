import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val ublockVersion: String = libs.versions.ublock.get()

android {
    namespace = "com.verisonder.sonderweb"
    // GeckoView 159 needs Android API 37.2 (same form Mozilla uses for Firefox).
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.verisonder.sonderweb"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        buildConfigField("String", "UBLOCK_VERSION", "\"$ublockVersion\"")
        // The engine is large; ship the 64-bit ARM build only (every current phone).
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    signingConfigs {
        create("release") {
            // Supplied by CI from repository secrets. Absent locally, so the release build
            // falls back to the debug key.
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
        buildConfig = true
    }

    androidResources {
        // The default pattern drops asset folders starting with "_", which removes uBlock
        // Origin's _locales folder and stops it from loading. Keep everything but VCS junk.
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:!CVS:!thumbs.db:!picasa.ini:!*~"
    }

    packaging {
        // Compress the engine's native libraries: about 75 MB less to download. They are
        // unpacked once at install.
        jniLibs.useLegacyPackaging = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
            all {
                it.testLogging {
                    events("passed", "failed", "skipped")
                }
            }
        }
    }
}

// uBlock Origin ships inside the app as a built-in extension. The signed Firefox build is
// downloaded once per version and unpacked into src/main/assets/extensions/ublock (not committed).
val ublockXpi = layout.buildDirectory.file("ublock/uBlock0_$ublockVersion.firefox.signed.xpi")

val downloadUblock by tasks.registering {
    val out = ublockXpi
    outputs.file(out)
    doLast {
        val target = out.get().asFile
        if (target.length() > 0) return@doLast
        target.parentFile.mkdirs()
        val url = "https://github.com/gorhill/uBlock/releases/download/$ublockVersion/" +
            "uBlock0_$ublockVersion.firefox.signed.xpi"
        val tmp = File(target.path + ".part")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        check(tmp.length() > 100_000) { "uBlock Origin download looks wrong (${tmp.length()} bytes)" }
        tmp.renameTo(target)
    }
}

val unpackUblock by tasks.registering(Sync::class) {
    dependsOn(downloadUblock)
    from(zipTree(ublockXpi)) {
        // Signature files only matter for installed add-ons, not built-in ones.
        exclude("META-INF/**")
    }
    into(layout.projectDirectory.dir("src/main/assets/extensions/ublock"))
}

// The built-in "SonderWeb look" extension uses the app's own Inter font files (copied, not committed).
val styleFonts by tasks.registering(Sync::class) {
    from(layout.projectDirectory.dir("src/main/res/font")) {
        include("inter_regular.ttf", "inter_semibold.ttf")
    }
    into(layout.projectDirectory.dir("src/main/assets/extensions/style/fonts"))
}

tasks.named("preBuild") { dependsOn(unpackUblock, styleFonts) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.geckoview)

    testImplementation(libs.junit)
}
