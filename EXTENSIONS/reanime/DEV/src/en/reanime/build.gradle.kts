plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin.gradle.get()
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension"

    defaultConfig {
        // Extension metadata — Re:ANIME (reanime.to) — "180" suffix matches the project convention.
        val extName = "Re:ANIME 180"
        // ★ applicationId = ...en.reanime180, but SOURCE class is in package ...en.reanime.
        // applicationId ≠ source package → must use FULL extClass path (no leading dot).
        val extClass = "eu.kanade.tachiyomi.animeextension.en.reanime.Reanime"
        val extVersionCode = 3  // v16.1 (build 3: show all servers + plain OkHttp for flixcloud m3u8)
        val extVersionId = 1    // ★ STABLE once published. Bumping orphans saved anime.
                                // The source id = MD5("reanime 180/en/$extVersionId"). NEVER change after publish.
        val isNsfw = false

        // applicationId = namespace + applicationIdSuffix = eu.kanade.tachiyomi.animeextension.en.reanime180
        applicationIdSuffix = "en.reanime180"

        // ★ ext-lib 16: versionName MUST start with "16." (loader rejects <12 or >16)
        versionCode = extVersionCode
        versionName = "16.1"

        base.archivesName.set("aniyomi-en.reanime180-v$versionName")

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

    // ★ Release signing config — uses reanime-release.jks (per-extension keystore).
    // Generate with: keytool -genkeypair -keystore reanime-release.jks -alias reanime -keyalg RSA -keysize 2048 -validity 10000 -storepass "$KEYSTORE_PASSWORD" -keypass "$KEYSTORE_PASSWORD" -dname "CN=Re:ANIME 180, O=Confused_Creature_180, C=US"
    // Debug builds don't need it; assembleRelease will fail until generated.
    signingConfigs {
        create("release") {
            storeFile = rootProject.file("reanime-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "reanime"
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
