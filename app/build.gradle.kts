plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

room {
    // Exported schemas are committed so future versions can migrate (and test migrations of) user data.
    schemaDirectory("$projectDir/schemas")
}

// Release signing (the release key, see RELEASING.md). Read from environment variables (CI) or from
// ~/.gradle/gradle.properties (local). Never commit these values.
fun secret(name: String): String? = providers.environmentVariable(name).orElse(providers.gradleProperty(name)).orNull

val releaseKeystoreFile = secret("RELEASE_KEYSTORE_FILE")

android {
    namespace = "io.github.emilmelgaard.pathwake"
    compileSdk {
        version =
            release(37) {
                minorApiLevel = 2
            }
    }
    defaultConfig {
        applicationId = "io.github.emilmelgaard.pathwake"
        minSdk = 29
        targetSdk = 37
        // Overridden by CI from the git tag, e.g. -PversionName=1.2.3 -PversionCode=10203
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0-dev")
    }

    signingConfigs {
        if (releaseKeystoreFile != null) {
            create("release") {
                storeFile = file(releaseKeystoreFile)
                storePassword = secret("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = secret("RELEASE_KEY_ALIAS")
                keyPassword = secret("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        aidl = false
        buildConfig = false
        shaders = false
    }

    // The dependency metadata blob is only readable by Google; keep it out of APKs
    // (required for F-Droid / reproducible builds) but in the Play bundle.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":puzzle"))

    // Persistence
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    androidTestImplementation(libs.androidx.room.testing)

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation3)
    implementation(libs.androidx.compose.material.icons.core)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    // Navigation keys are @Serializable. Explicit, because Room's migration tests need a newer version than the
    // one the AndroidX libraries bring in.
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
}
