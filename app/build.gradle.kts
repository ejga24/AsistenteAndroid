plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    val nexoKeystoreFile = providers.gradleProperty("NEXO_KEYSTORE_FILE").orNull
    val nexoKeystorePassword = providers.gradleProperty("NEXO_KEYSTORE_PASSWORD").orNull
    val nexoKeyAlias = providers.gradleProperty("NEXO_KEY_ALIAS").orNull
    val nexoKeyPassword = providers.gradleProperty("NEXO_KEY_PASSWORD").orNull

    namespace = "com.eliel.asistente"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.eliel.asistente"
        minSdk = 26
        targetSdk = 35
        versionCode = 18
        versionName = "3.0-rc2"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    signingConfigs {
        if (
            !nexoKeystoreFile.isNullOrBlank() &&
            !nexoKeystorePassword.isNullOrBlank() &&
            !nexoKeyAlias.isNullOrBlank() &&
            !nexoKeyPassword.isNullOrBlank()
        ) {
            create("nexoRelease") {
                storeFile = file(nexoKeystoreFile)
                storePassword = nexoKeystorePassword
                keyAlias = nexoKeyAlias
                keyPassword = nexoKeyPassword
            }
        }
    }

    buildTypes {
        getByName("release") {
            signingConfigs.findByName("nexoRelease")?.let {
                signingConfig = it
            }
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    testImplementation("junit:junit:4.13.2")
}
