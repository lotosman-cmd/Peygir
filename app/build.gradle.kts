plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "ir.peygir.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "ir.peygir.app"
        minSdk = 23
        targetSdk = 30
        versionCode = 4
        versionName = "1.3"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
