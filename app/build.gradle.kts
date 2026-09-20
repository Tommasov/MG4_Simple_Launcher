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
        versionCode = 31
        versionName = "1.8-beta1"

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

        // The author's probe, which is where the diagnostics log is sent from the car: the
        // head unit has no adb, no usable browser and — checked in the firmware — not one
        // app that accepts a share, so an upload is the only way a report leaves the vehicle.
        buildConfigField(
            "String",
            "PROBE_URL",
            "\"https://ws2.tommasovietina.it/mg4/probe.php\""
        )

        // Write key of the probe. It ships inside the APK, so everyone who installs the
        // launcher has it and anyone who unzips one can read it — which is why the probe
        // does nothing but accept a report: it has no way to read one back, list what is
        // there or delete anything, and reports are collected over FTP instead.
        buildConfigField(
            "String",
            "PROBE_KEY",
            "\"${apiKeys.getProperty("probe.key", "")}\""
        )

        // Which app a report came from: the probe keeps one drawer per app, so the swipe
        // helper and anything else written for this car can post to the same place.
        buildConfigField("String", "PROBE_APP", "\"launcher\"")

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