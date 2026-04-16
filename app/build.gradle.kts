plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cityhub.agent"
    compileSdk = 34

    defaultConfig {
        applicationId  = "com.cityhub.agent"
        minSdk         = 19      // Android 4.4 KitKat
        targetSdk      = 34
        versionCode    = 9
        versionName    = "1.4.0"

        // Suporte a VectorDrawable em APIs < 21
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") {
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    // Core AppCompat — suporta API 14+
    implementation("androidx.appcompat:appcompat:1.6.1")
    // RecyclerView
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Core KTX
    implementation("androidx.core:core-ktx:1.12.0")
    // ConstraintLayout
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ⚠️ Material Components 1.9+ exige minSdk 21.
    //    Para suportar API 19, usamos apenas AppCompat.
    //    A FloatingActionButton foi substituída por ImageButton customizado.
}
