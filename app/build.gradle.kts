plugins {
    id("com.android.application")
}

android {
    namespace = "com.example.twobuttons"
    compileSdk = 34

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
    implementation("androidx.core:core:1.13.1")
}
