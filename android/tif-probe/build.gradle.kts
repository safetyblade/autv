plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "dev.prestwich.autv.tifprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.prestwich.autv.tifprobe"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-experiment"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(project(":core-data"))
    implementation(project(":core-guide"))
    implementation(project(":core-player"))
}
