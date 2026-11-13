plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.pako.eminus4"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pako.eminus4"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "2.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("common") {
            storeFile = file("bodrio.keystore")
            storePassword = "android"
            keyAlias = "bodrio"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("common")
        }
        release {
            signingConfig = signingConfigs.getByName("common")
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.material)

    // Red y Serialización JSON
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Carga de imágenes nativa y eficiente
    implementation(libs.coil)

    // WebViewAssetLoader para servir archivos locales al WebView
    implementation(libs.androidx.webkit)

    // Generación offline de códigos QR y códigos de barras (Credencial)
    implementation("com.google.zxing:core:3.5.3")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}