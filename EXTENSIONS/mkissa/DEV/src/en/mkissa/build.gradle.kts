plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin.gradle.get()
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension"

    defaultConfig {
        // Extension metadata — MKissa (mkissa.to) — "180" suffix matches the AniKoto/AnimePahe convention.
        val extName = "MKissa 180"
        // ★ applicationId = ...en.mkissa180, but SOURCE class is in package ...en.mkissa.
        // applicationId ≠ source package → must use FULL extClass path (no leading dot).
        // See HOW_TO_BUILD_EXTENSION/common-pitfalls.md §extClass.
        val extClass = "eu.kanade.tachiyomi.animeextension.en.mkissa.MKissa"
        val extVersionCode = 21  // v16.21 (build 21: fix popup blocking crashing ad JS, add Vn-Hls WebView fallback, early JS injection)
        val extVersionId = 1    // ★ STABLE once published. Bumping orphans saved anime.
                                // The source id = MD5("mkissa 180/en/$extVersionId"). NEVER change after publish.
        val isNsfw = false

        // applicationId = namespace + applicationIdSuffix = eu.kanade.tachiyomi.animeextension.en.mkissa180
        applicationIdSuffix = "en.mkissa180"

        // ★ ext-lib 16: versionName MUST start with "16." (loader rejects <12 or >16)
        // versionName stays "16.17" (same display version — this is a build improvement, not a new feature release)
        versionCode = extVersionCode
        versionName = "16.17"

        base.archivesName.set("aniyomi-en.mkissa180-v$versionName")

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

    // ★ Release signing config — uses mkissa-release.jks (per-extension keystore).
    // Generate with: keytool -genkeypair -keystore mkissa-release.jks -alias mkissa -keyalg RSA -keysize 2048 -validity 10000 -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" -dname "CN=MKissa 180, O=Confused_Creature_180, C=US"
    // Debug builds don't need it; assembleRelease will fail until generated.
    //
    // ★ Security: the password is read ONLY from the KEYSTORE_PASSWORD env var (no hardcoded
    // fallback). In CI it's set from the GitHub Actions secret. For local builds, export it:
    //   export KEYSTORE_PASSWORD=...   (see EXTENSIONS/mkissa/DEV/keystore-info.txt, gitignored)
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("mkissa-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "mkissa"
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
