import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Samsung's S Pen Remote SDK is proprietary, so it isn't committed. Drop the two jars into
// app/libs/ (see app/libs/README.md) to build the real thing. Without them, or with
// -Pairpoint.simulateSpen, the app builds against a simulated S Pen so the UI can be
// developed and demoed on any device or emulator.
val spenSdkJars = fileTree("libs") { include("*.jar") }
val useSpenSdk = !spenSdkJars.isEmpty && !providers.gradleProperty("airpoint.simulateSpen").isPresent

// Optional release signing from keystore.properties (gitignored).
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "io.github.ar13x3.airpoint"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.ar13x3.airpoint"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("boolean", "REAL_SPEN", useSpenSdk.toString())
        buildConfigField("String", "REPO_URL", "\"https://github.com/AR13X3/airpoint\"")
    }

    signingConfigs {
        if (keystoreProps.getProperty("storeFile") != null) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.directories += if (useSpenSdk) "src/spen/kotlin" else "src/simulated/kotlin"
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
}

dependencies {
    if (useSpenSdk) implementation(spenSdkJars)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
