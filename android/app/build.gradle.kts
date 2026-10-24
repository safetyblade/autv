plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.prestwich.autv"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.prestwich.autv"
        minSdk = 24
        targetSdk = 35
        versionCode = providers.gradleProperty("autvVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("autvVersionName").orElse("0.5.0").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val signingPath = providers.environmentVariable("AUTV_SIGNING_KEYSTORE_PATH").orNull
    signingConfigs {
        if (signingPath != null) create("distribution") {
            storeFile = file(signingPath)
            storePassword = providers.environmentVariable("AUTV_SIGNING_STORE_PASSWORD").get()
            keyAlias = "autv"
            keyPassword = providers.environmentVariable("AUTV_SIGNING_KEY_PASSWORD").get()
        }
    }
    buildTypes {
        getByName("release") {
            if (signingPath != null) signingConfig = signingConfigs.getByName("distribution")
        }
    }
    // Fail closed: distribution builds must never silently use a new debug key or be unsigned.
    gradle.taskGraph.whenReady {
        if (allTasks.any { it.project == project && it.name in setOf("assembleRelease", "packageRelease", "bundleRelease") }) {
            check(signingPath != null) { "Release signing is required. See docs/android-updates.md." }
            check(defaultConfig.versionCode!! > 1) { "Set -PautvVersionCode to a new distribution build number." }
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.4")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation(project(":core-data"))
    implementation(project(":core-guide"))
    implementation(project(":core-player"))
    val composeBom = platform("androidx.compose:compose-bom:2025.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.compose.material3:material3")
    implementation("io.coil-kt:coil-compose:2.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.media3:media3-ui:1.11.1")
    implementation("com.google.android.gms:play-services-cast:22.3.1")
    implementation("com.google.android.gms:play-services-cast-framework:22.3.1")
}
