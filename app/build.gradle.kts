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

// Diagnostics: make build errors visible as GitHub annotations
tasks.configureEach {
    logging.addStandardErrorListener(org.gradle.api.logging.StandardOutputListener { msg ->
        val s = msg.toString()
        if (s.contains("error", ignoreCase = true) || s.contains("exception", ignoreCase = true)) {
            println("::error::" + s.replace("%", "%25").replace("\r", "").replace("\n", "%0A").take(1500))
        }
    })
}
gradle.buildFinished {
    val f = failure
    if (f != null) {
        var t: Throwable? = f
        val sb = StringBuilder()
        while (t != null) { sb.append(t.message).append(" || "); t = t.cause }
        println("::error::BUILD FAILURE: " + sb.toString().replace("%", "%25").replace("\r", "").replace("\n", "%0A").take(3000))
    }
}
