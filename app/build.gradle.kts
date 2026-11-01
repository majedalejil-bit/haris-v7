plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.haris.guard"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.haris.guardv7"
        minSdk = 21
        targetSdk = 34
        versionCode = 12
        versionName = "12.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        create("fixed") {
            storeFile = file("haris.keystore")
            storePassword = System.getenv("KS_PASS")
            keyAlias = "haris"
            keyPassword = System.getenv("KS_PASS")
        }
    }
    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("fixed")
            isDebuggable = false
        }
    }
}
