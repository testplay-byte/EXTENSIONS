plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin.gradle.get()
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension"

    defaultConfig {
        // Extension metadata
        // AniDB 180 — follows the AniKoto/AnimePahe "180" publisher convention.
        val extName = "AniDB 180"
        // extClass uses the FULL path (no leading dot) — the loader would otherwise do
        // `packageName + extClass` and resolve to the wrong package, because
        // applicationId (...en.anidb180) ≠ source package (...en.anidb).
        // See HOW_TO_BUILD_EXTENSION/common-pitfalls.md §extClass.
        val extClass = "eu.kanade.tachiyomi.animeextension.en.anidb.AniDB"
        val extVersionCode = 1   // v16.1 — first build
        val extVersionId = 1    // ★ STABLE once published. Bumping orphans saved anime.
                                // The source id = MD5("anidb 180/en/$extVersionId"). NEVER change after publish.
        val isNsfw = true   // anidb.app has Erotica + Hentai genres and NSFW titles in the default browse

        // applicationId = namespace + applicationIdSuffix = eu.kanade.tachiyomi.animeextension.en.anidb180
        applicationIdSuffix = "en.anidb180"

        // ★ ext-lib 16: versionName MUST start with "16." (loader rejects <12 or >16)
        versionCode = extVersionCode
        versionName = "16.$extVersionCode"

        // APK filename: aniyomi-en.anidb180-v16.1-debug.apk
        base.archivesName.set("aniyomi-en.anidb180-v$versionName")

        // Manifest placeholders (filled into common/AndroidManifest.xml)
        manifestPlaceholders["appName"] = extName
        manifestPlaceholders["extClass"] = extClass
        manifestPlaceholders["nsfw"] = if (isNsfw) "1" else "0"
        manifestPlaceholders["versionId"] = extVersionId.toString()

        // SDK versions
        minSdk = 21
        targetSdk = 34
        compileSdk = 34
    }

    // ★ Release signing config — uses anidb-release.jks (per-extension keystore).
    // The keystore will be generated in Step 5 (release). Debug builds don't need it.
    // If the keystore is missing, assembleDebug still works; assembleRelease will fail until generated.
    //
    // ★ Security: the password is read ONLY from the KEYSTORE_PASSWORD env var (no hardcoded
    // fallback). In CI it's set from the GitHub Actions secret.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("anidb-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "anidb"
            keyPassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
        }
    }

    // Java 17 compat (ext-lib v16 jar is Java 17 bytecode)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // Source sets: standard AGP layout (src/main/kotlin + src/main/java)
    sourceSets {
        getByName("main") {
            manifest.srcFile(rootProject.file("common/AndroidManifest.xml"))
            res.srcDirs("res")
            assets.srcDirs("assets")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                rootProject.file("common/proguard-rules.pro"),
            )
        }
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    // ★ The ext-lib v16 stubs — compileOnly so they're NOT in the APK at runtime.
    // The app provides the real classes via its classloader.
    compileOnly(project(":stubs"))

    // AndroidX preference (needed by ConfigurableAnimeSource stub)
    compileOnly("androidx.preference:preference:1.2.1")

    // All deps are compileOnly (provided by the Aniyomi app at runtime)
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
