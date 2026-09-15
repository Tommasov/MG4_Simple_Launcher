import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Release signing credentials live in keystore.properties (git-ignored), so the
// same stable key signs every build and users can always update in place.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        load(FileInputStream(keystorePropertiesFile))
    }
}

// Third-party API keys live in apikeys.properties (git-ignored). A missing key is not a
// build error: the features that need it degrade instead, so a fresh clone still builds.
val apiKeysFile = rootProject.file("apikeys.properties")
val apiKeys = Properties().apply {
    if (apiKeysFile.exists()) {
        load(FileInputStream(apiKeysFile))
    }
}

android {
    namespace = "com.tommasov.mg4simplelauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tommasov.mg4simplelauncher"
        minSdk = 28
        targetSdk = 34
        versionCode = 18
        versionName = "1.6-beta10"

        // Base URL of the update server; the version manifest lives at <base>/version.json
        buildConfigField(
            "String",
            "UPDATE_BASE_URL",
            "\"https://ws2.tommasovietina.it/mg4/MG4_Simple_Launcher/\""
        )

        // Catalogue of the author's other apps for this vehicle. A sibling of the launcher's
        // own update manifests rather than a file inside its folder: it lists other software.
        buildConfigField(
            "String",
            "CATALOG_URL",
            "\"https://ws2.tommasovietina.it/mg4/apps.json\""
        )

        // Open Charge Map key; empty when apikeys.properties is absent.
        buildConfigField(
            "String",
            "OCM_API_KEY",
            "\"${apiKeys.getProperty("ocm.apiKey", "")}\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)
    implementation(libs.viewpager2)
    implementation(libs.osmdroid)
}