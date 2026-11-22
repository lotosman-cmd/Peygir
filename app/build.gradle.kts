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
        versionCode = 1
        versionName = "1.0"
    }
}
