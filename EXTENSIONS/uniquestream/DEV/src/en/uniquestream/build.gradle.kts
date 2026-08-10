plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.android") version libs.versions.kotlin.gradle.get()
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "eu.kanade.tachiyomi.animeextension"

    defaultConfig {
        // Extension metadata
        val extName = "UniQuestream"
        val extClass = "eu.kanade.tachiyomi.animeextension.en.uniquestream.UniQuestream"
        // extClass uses the FULL path (no leading dot).
        // applicationId = ...en.uniquestream180, source class is in ...en.uniquestream.
        // applicationId ≠ source package → must use FULL extClass path (no dot).
        // See HOW_TO_BUILD_EXTENSION/common-pitfalls.md §extClass + reference-prior-solutions.md §extclass-doubling.
        // Loader code: if sourceClass starts with ".", prepend packageName. Otherwise use as-is.
        val extVersionCode = 12  // v16.12 (add PNG header stripping for CDN-wrapped segments)
        val extVersionId = 1    // ★ STABLE once published. Bumping orphans saved anime.
        val isNsfw = false

        applicationIdSuffix = "en.uniquestream180"

        // ★ ext-lib 16: versionName MUST start with "16." (loader rejects <12 or >16)
        versionCode = extVersionCode
        versionName = "16.$extVersionCode"

        // ★ filename uses uniquestream180 to match the new package name
        base.archivesName.set("aniyomi-en.uniquestream180-v$versionName")

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

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("uniquestream-release.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "uniquestream"
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
    // Extension code + ext-lib stubs are both in src/main/kotlin/
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
    // The ext-lib v16 stubs — compileOnly so they're NOT in the APK at runtime.
    // The app provides the real classes via its classloader.
    // Without this, the stubs' "throw Exception(\"Stub!\")" bodies would execute at runtime.
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