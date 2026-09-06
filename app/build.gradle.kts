plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.screentranslator"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.screentranslator"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

   // ML Kit OCR
implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
implementation("com.google.android.gms:play-services-mlkit-text-recognition-chinese:16.0.1")
implementation("com.google.android.gms:play-services-mlkit-text-recognition-japanese:16.0.1")

// ML Kit Translation
implementation("com.google.mlkit:translate:17.0.3")

    // OkHttp untuk API DeepL & Gemini
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines untuk proses di latar belakang
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
