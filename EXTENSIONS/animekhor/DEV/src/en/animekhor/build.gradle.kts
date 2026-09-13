plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin.gradle.get()
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension"

    defaultConfig {
        val extName = "AnimeKhor 180"
        // extClass is the FULL class path (no leading dot) — the loader uses it as-is.
        // Source code package stays ...en.animekhor; the applicationId gets the "180" suffix
        // to distinguish from other publishers (same pattern as anikoto180).
        val extClass = "eu.kanade.tachiyomi.animeextension.en.animekhor.AnimeKhor"
        val extVersionCode = 1
        val extVersionId = 1 // ★ STABLE — do NOT bump with versionCode. Source ID = MD5("animekhor 180/en/$extVersionId")
        val isNsfw = false

        applicationIdSuffix = "en.animekhor180"

        // ext-lib 16: versionName MUST start with "16."
        versionCode = extVersionCode
        versionName = "16.$extVersionCode"

        base.archivesName.set("aniyomi-en.animekhor180-v$versionName")

        manifestPlaceholders["appName"] = extName
        manifestPlaceholders["extClass"] = extClass
        manifestPlaceholders["nsfw"] = if (isNsfw) "1" else "0"
        manifestPlaceholders["versionId"] = extVersionId.toString()

        minSdk = 21
        targetSdk = 34
        compileSdk = 34
    }

    // ★ Debug-only for now (no release keystore yet — same as mkissa/anidb/rea/miruro).
    // When the user approves a release, create animekhor-release.jks + CI secret, then add
    // the signingConfig (pattern: EXTENSIONS/anikoto/DEV/src/en/anikoto/build.gradle.kts).
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("common/proguard-rules.pro"),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile(rootProject.file("common/AndroidManifest.xml"))
            res.srcDirs("res")
            assets.srcDirs("assets")
        }
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    // ★ The ext-lib v16 stubs — compileOnly so they're NOT in the APK at runtime.
    compileOnly(project(":stubs"))

    compileOnly("androidx.preference:preference:1.2.1")

    // Pure-Kotlin dean-Edwards packer unpacker (vendored copy lives in extractors/JsUnpacker.kt;
    // no external dependency needed).

    // All other deps are compileOnly (provided by the Aniyomi app at runtime)
    compileOnly(libs.coroutines.core)
    compileOnly(libs.coroutines.android)
    compileOnly(libs.injekt.core)
    compileOnly(libs.rxjava)
    compileOnly(libs.kotlin.protobuf)
    compileOnly(libs.kotlin.json)
    compileOnly(libs.kotlin.json.okio)
    compileOnly(libs.jsoup)
    compileOnly(libs.okhttp)
    compileOnly(libs.quickjs)
}
