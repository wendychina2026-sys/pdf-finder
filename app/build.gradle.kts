plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.twobuttons"
    compileSdk = 34

    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.example.twobuttons"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // ML Kit pulls in Kotlin 1.8.22 and 1.6.21 stdlib parts; pin all of them to one version
    // (fixes "Duplicate class kotlin.collections.jdk8..." in checkDebugDuplicateClasses)
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
    implementation("androidx.core:core:1.13.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0") // camera: read text from a photo (model lives in Google Play services, not in the APK)
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0") // scanner: QR / barcode
}
