plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.prestwich.autv.guide"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
dependencies { implementation(project(":core-data")) }
