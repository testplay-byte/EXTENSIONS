plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin.gradle.get()
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension"

    defaultConfig {
        // Extension metadata
        // ★ session 06: name changed to "AnimePahe 180" + applicationIdSuffix to "en.animepahe180"
        // to match the AniKoto 180 convention (distinguishes from other publishers using "animepahe").
        val extName = "AnimePahe 180"
        // extClass uses a leading dot (.) — the loader does `packageName + extClass`.
        // applicationId = ...en.animepahe180, but the SOURCE class is in package ...en.animepahe.
        // ★ This means applicationId ≠ source package → must use FULL extClass path (no dot).
        // See HOW_TO_BUILD_EXTENSION/common-pitfalls.md §extClass + reference-anikoto-solutions.md §extclass-doubling.
        val extClass = "eu.kanade.tachiyomi.animeextension.en.animepahe.AnimePahe"
        val extVersionCode = 10  // v16.2 (session 06: name+package change + video playback)
        val extVersionId = 1    // ★ STABLE once published. Bumping orphans saved anime. See EXTENSIONS/animepahe/MEMORY/sites/site-analysis.md §identity.
                                // The source id = MD5("animepahe 180/en/$extVersionId"). NEVER change after publish.
        val isNsfw = false

        // ★ session 06: applicationIdSuffix changed from en.animepahe to en.animepahe180.
        // applicationId = namespace + applicationIdSuffix = eu.kanade.tachiyomi.animeextension.en.animepahe180
        // ⚠️ Users on the old ...en.animepahe package must UNINSTALL before installing this —
        // Android treats different package names as different apps (no direct update).
        applicationIdSuffix = "en.animepahe180"

        // ★ ext-lib 16: versionName MUST start with "16." (loader rejects <12 or >16)
        versionCode = extVersionCode
        versionName = "16.$extVersionCode"

        // ★ session 06: filename uses animepahe180 to match the new package name
        base.archivesName.set("aniyomi-en.animepahe180-v$versionName")

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

    // ★ Release signing config — uses animepahe-release.jks (per-extension keystore).
    // The keystore will be generated in Step 5 (release). Debug builds don't need it.
    // If the keystore is missing, assembleDebug still works; assembleRelease will fail until generated.
    //
    // ★ Security: the password is read ONLY from the KEYSTORE_PASSWORD env var (no hardcoded
    // fallback). In CI it's set from the GitHub Actions secret. For local builds, export it:
    //   export KEYSTORE_PASSWORD=...   (see EXTENSIONS/animepahe/DEV/keystore-info.txt, gitignored)
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("animepahe-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "animepahe"
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
