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

    // CI sets GITHUB_RUN_NUMBER: every build gets a higher versionCode, so installs can be compared
    val runNo = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()

    defaultConfig {
        applicationId = "com.example.twobuttons"
        minSdk = 24
        targetSdk = 34
        versionCode = runNo ?: 1
        versionName = if (runNo != null) "1.0.$runNo" else "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core:1.13.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
}
