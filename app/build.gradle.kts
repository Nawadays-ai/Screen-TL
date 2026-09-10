plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val isCiBuild = System.getenv("CI") == "true"
val ciKeystoreFile = System.getenv("SCREEN_TL_KEYSTORE_FILE")
val ciStorePassword = System.getenv("SCREEN_TL_KEYSTORE_PASSWORD")
val ciKeyAlias = System.getenv("SCREEN_TL_KEY_ALIAS")
val ciKeyPassword = System.getenv("SCREEN_TL_KEY_PASSWORD")

android {
    namespace = "com.example.screentranslator"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.screentranslator"
        minSdk = 24
        targetSdk = 34
        versionCode = maxOf(2, System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 2)
        versionName = "1.1"
    }
    if (isCiBuild) {
        require(!ciKeystoreFile.isNullOrBlank()) { "SCREEN_TL_KEYSTORE_FILE is required for CI builds. Refusing to use an ephemeral debug signing key." }
        require(!ciStorePassword.isNullOrBlank()) { "SCREEN_TL_KEYSTORE_PASSWORD is required for CI builds." }
        require(!ciKeyAlias.isNullOrBlank()) { "SCREEN_TL_KEY_ALIAS is required for CI builds." }
        require(!ciKeyPassword.isNullOrBlank()) { "SCREEN_TL_KEY_PASSWORD is required for CI builds." }
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystoreFile!!)
                storePassword = ciStorePassword
                keyAlias = ciKeyAlias
                keyPassword = ciKeyPassword
            }
        }
    }
    buildTypes {
        debug { if (isCiBuild) signingConfig = signingConfigs.getByName("ci") }
        release { isMinifyEnabled = false; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1")
    implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("dev.ffmpegkit-maintained:llama-android:0.1.1")
}
