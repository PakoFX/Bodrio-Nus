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
        versionCode = 3
        versionName = "1.3"

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
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}