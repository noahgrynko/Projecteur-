plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.projecteur.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.projecteur.remote"
        // Android 9 minimum : l'API BluetoothHidDevice (mode télécommande Bluetooth) date d'Android 9.
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // Signature de release optionnelle, configurée par variables d'environnement
        // (voir docs/COMPILATION.md). Sans elles, seule la variante debug est signée.
        val storeFilePath = System.getenv("PROJECTEUR_KEYSTORE")
        if (storeFilePath != null) {
            create("release") {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("PROJECTEUR_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PROJECTEUR_KEY_ALIAS")
                keyPassword = System.getenv("PROJECTEUR_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Pilotes série USB (CDC-ACM, CH34x, CP210x, FTDI) en espace utilisateur via l'API Android USB Host.
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")

    testImplementation("junit:junit:4.13.2")
    // Test de fumée de l'interface sur JVM (sans téléphone ni émulateur).
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
