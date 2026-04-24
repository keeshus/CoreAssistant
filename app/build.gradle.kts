plugins {
    id("com.android.application")
    id("kotlin-android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "nl.codeinfinity.coreassistant"
    compileSdk = 36

    defaultConfig {
        applicationId = "nl.codeinfinity.coreassistant"
        minSdk = 26
        targetSdk = 34
        versionCode = project.findProperty("versionCode")?.toString()?.toInt() ?: 3
        versionName = project.findProperty("versionName")?.toString() ?: "0.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
            keepDebugSymbols += "**/libdatastore_shared_counter.so"
        }
    }
}

configurations.all {
    resolutionStrategy {
        force("org.jetbrains.kotlin:kotlin-stdlib:2.3.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:2.3.21")
        force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.3.21")
        force("org.jetbrains.kotlin:kotlin-reflect:2.3.21")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Gemini REST API (Retrofit)
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    
    // ViewModel & Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    
    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.2.1")
    
    // Navigation
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Markdown
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.40.2")

    // Room DB
    implementation("androidx.room:room-runtime:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    // SQLCipher for encrypted database
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
}
