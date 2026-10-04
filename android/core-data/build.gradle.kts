plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.prestwich.autv.data"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
dependencies { implementation("androidx.core:core-ktx:1.17.0") }
