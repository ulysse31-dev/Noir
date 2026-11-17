plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.noir.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noir.launcher"
        minSdk = 26
        targetSdk = 35
        // Le numéro de version monte à chaque compilation GitHub : Android accepte la mise à jour par-dessus.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "0.2"
    }

    // Clé de signature fixe : sans elle, chaque compilation aurait une signature différente
    // et Android refuserait de mettre à jour l'appli (il faudrait la désinstaller).
    signingConfigs {
        create("noir") {
            storeFile = file("noir.keystore")
            storePassword = "noirnoir"
            keyAlias = "noir"
            keyPassword = "noirnoir"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("noir")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
}
