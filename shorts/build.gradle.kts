plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Appli séparée de Noir : le bloqueur de Shorts utilise l'accessibilité, que Play Protect surveille.
// Ainsi, si Play Protect s'en prend un jour au bloqueur, le lanceur Noir n'est pas touché.
android {
    namespace = "com.noir.shorts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noir.shorts"
        minSdk = 26
        targetSdk = 35
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0"
    }

    // Même clé fixe que Noir : les mises à jour s'installent par-dessus.
    signingConfigs {
        create("noir") {
            storeFile = file("../app/noir.keystore")
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
}
